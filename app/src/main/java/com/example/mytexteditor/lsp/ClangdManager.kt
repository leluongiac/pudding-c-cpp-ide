package com.example.mytexteditor.lsp

import android.content.Context
import com.example.mytexteditor.data.AppLog
import com.example.mytexteditor.data.ToolchainEnvironment
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Quản lý vòng đời, kết nối và yêu cầu LSP tới tiến trình clangd binary.
 * Hỗ trợ Graceful Fallback hoàn toàn: nếu thiếu clangd thì app không crash.
 */
object ClangdManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var transport: LspJsonRpcTransport? = null
    private var process: Process? = null

    private val documentVersion = AtomicInteger(1)
    private var activeDocumentUri: String = ""
    private var lastSyncedText: String = ""
    private var pendingChangeText: String? = null
    private var pendingInitialText: String? = null
    @Volatile
    private var isDocumentOpened = false
    private var debounceJob: Job? = null
    private val syncLock = Any()
    private var serverSyncKind = 2 // 1: Full, 2: Incremental (mặc định của clangd)

    private val _isLspReady = MutableStateFlow(false)
    val isLspReady: StateFlow<Boolean> = _isLspReady.asStateFlow()

    private val _currentDiagnostics = MutableStateFlow<List<Diagnostic>>(emptyList())
    val currentDiagnostics: StateFlow<List<Diagnostic>> = _currentDiagnostics.asStateFlow()

    private fun toFileUri(file: File): String {
        val path = file.absolutePath
        return if (path.startsWith("/")) "file://$path" else "file:///$path"
    }

    /**
     * Khởi chạy clangd nếu có sẵn binary và môi trường hợp lệ.
     */
    fun start(context: Context, initialText: String? = null) {
        if (initialText != null) {
            pendingInitialText = initialText
        }

        if (_isLspReady.value || process?.isAlive == true) {
            if (_isLspReady.value && initialText != null && !isDocumentOpened) {
                sendDidOpen(initialText)
            }
            return
        }

        scope.launch {
            try {
                ToolchainEnvironment.verifyRuntimeLibraries(context)

                val clangdFile = ToolchainEnvironment.getClangd(context)
                if (clangdFile == null || !clangdFile.exists() || !clangdFile.canExecute()) {
                    AppLog.w("CLANGD_MGR", "⚠️ Binary clangd chưa sẵn sàng hoặc không có quyền thực thi. Tắt tính năng LSP an toàn.")
                    _isLspReady.value = false
                    return@launch
                }

                val usrDir = ToolchainEnvironment.getUsrDir(context) ?: return@launch
                val cacheDir = ToolchainEnvironment.getCacheDir(context) ?: return@launch

                // 1. Tự động sinh compile_commands.json chuẩn cho C++17 và Android Clang
                generateCompileCommands(cacheDir, usrDir)

                // 2. Tạo file mẫu solution.cpp chuẩn để clangd theo dõi
                val dummyFile = File(cacheDir, "solution.cpp")
                if (!dummyFile.exists() || dummyFile.length() == 0L) {
                    val defaultContent = pendingInitialText ?: "// Clangd workspace ready\n"
                    dummyFile.writeText(defaultContent)
                }
                activeDocumentUri = toFileUri(dummyFile)
                AppLog.d("CLANGD_MGR", "📍 Active Document URI: $activeDocumentUri")

                // 3. Khởi chạy tiến trình clangd với các cờ tối ưu hóa cho mobile
                val cmd = listOf(
                    clangdFile.absolutePath,
                    "--background-index=false",
                    "--pch-storage=memory",
                    "--header-insertion=never",
                    "--completion-style=detailed",
                    "--compile-commands-dir=${cacheDir.absolutePath}"
                )

                val pb = ProcessBuilder(cmd)
                ToolchainEnvironment.setupEnvironment(pb.environment(), context)

                AppLog.i("CLANGD_MGR", "🚀 Khởi chạy clangd: ${cmd.joinToString(" ")}")
                val proc = pb.start()
                process = proc

                val newTransport = LspJsonRpcTransport(
                    inputStream = proc.inputStream,
                    outputStream = proc.outputStream,
                    errorStream = proc.errorStream,
                    scope = scope,
                    onNotification = { method, params ->
                        handleServerNotification(method, params)
                    },
                    onClose = { cause ->
                        handleTransportClosed(cause)
                    }
                )
                transport = newTransport

                // Giám sát khi tiến trình clangd đột ngột kết thúc
                scope.launch(Dispatchers.IO) {
                    try {
                        val exitCode = proc.waitFor()
                        AppLog.w("CLANGD_MGR", "⚠️ Tiến trình clangd đã kết thúc với mã thoát: $exitCode")
                        handleTransportClosed(IOException("clangd exited with code $exitCode"))
                    } catch (_: Exception) {}
                }

                // 4. Gửi LSP Initialize và kiểm tra phản hồi thành công trước khi bật cờ ready
                initializeLsp(cacheDir)
                _isLspReady.value = true
                AppLog.i("CLANGD_MGR", "✅ clangd Language Server đã handshake thành công!")

                // 5. GỬI NGAY textDocument/didOpen CHO TÀI LIỆU
                val textToOpen = pendingInitialText ?: dummyFile.readText()
                sendDidOpen(textToOpen)

            } catch (e: Exception) {
                AppLog.e("CLANGD_MGR", "💥 Không thể khởi chạy clangd: ${e.message}", e)
                _isLspReady.value = false
                stop()
            }
        }
    }

    private fun handleTransportClosed(cause: Throwable?) {
        AppLog.w("CLANGD_MGR", "⚠️ LSP Transport đóng/mất kết nối: ${cause?.message}")
        _isLspReady.value = false
        isDocumentOpened = false
        _currentDiagnostics.value = emptyList()
        transport = null
        process?.let { proc ->
            if (proc.isAlive) {
                try { proc.destroyForcibly() } catch (_: Exception) {}
            }
        }
        process = null
    }

    private suspend fun initializeLsp(cacheDir: File) {
        val t = transport ?: throw IOException("LSP Transport chưa sẵn sàng")

        val capabilities = JSONObject().apply {
            put("textDocument", JSONObject().apply {
                put("synchronization", JSONObject().apply {
                    put("dynamicRegistration", false)
                    put("willSave", false)
                    put("willSaveWaitUntil", false)
                    put("didSave", false)
                })
                put("completion", JSONObject().apply {
                    put("completionItem", JSONObject().apply {
                        put("snippetSupport", true)
                    })
                })
                put("signatureHelp", JSONObject().apply {
                    put("signatureInformation", JSONObject().apply {
                        put("parameterInformation", JSONObject().apply {
                            put("labelOffsetSupport", false)
                        })
                    })
                })
            })
        }

        val initParams = JSONObject().apply {
            put("processId", android.os.Process.myPid())
            put("rootUri", toFileUri(cacheDir))
            put("capabilities", capabilities)
        }

        val response = t.sendRequest("initialize", initParams, timeoutMs = 10000)
        if (response.has("error")) {
            val errObj = response.optJSONObject("error")
            val code = errObj?.optInt("code", -1)
            val msg = errObj?.optString("message", "Lỗi khởi tạo clangd không xác định")
            throw IOException("clangd initialize thất bại [$code]: $msg")
        }

        val resultObj = response.optJSONObject("result")
            ?: throw IOException("Phản hồi initialize từ clangd không có trường 'result'")

        // Xác định chế độ document sync server hỗ trợ
        val syncCap = resultObj.opt("textDocumentSync")
        serverSyncKind = when (syncCap) {
            is Int -> syncCap
            is JSONObject -> syncCap.optInt("change", 2)
            else -> 2
        }

        t.sendNotification("initialized", JSONObject())
    }

    private fun handleServerNotification(method: String, params: JSONObject) {
        when (method) {
            "textDocument/publishDiagnostics" -> {
                val diagArray = params.optJSONArray("diagnostics") ?: JSONArray()
                val list = mutableListOf<Diagnostic>()

                for (i in 0 until diagArray.length()) {
                    val d = diagArray.getJSONObject(i)
                    val rangeObj = d.getJSONObject("range")
                    val startObj = rangeObj.getJSONObject("start")
                    val endObj = rangeObj.getJSONObject("end")

                    val range = LspRange(
                        start = LspPosition(startObj.getInt("line"), startObj.getInt("character")),
                        end = LspPosition(endObj.getInt("line"), endObj.getInt("character"))
                    )
                    val severity = DiagnosticSeverity.fromInt(d.optInt("severity", 1))
                    val message = d.optString("message", "")
                    val source = d.optString("source", "clangd")

                    list.add(Diagnostic(range, severity, message, source))
                }

                _currentDiagnostics.value = list
            }
        }
    }

    private fun generateCompileCommands(cacheDir: File, usrDir: File) {
        val compileCommandsFile = File(cacheDir, "compile_commands.json")
        val solutionFile = File(cacheDir, "solution.cpp")

        val command = StringBuilder().apply {
            append("${usrDir.absolutePath}/bin/clang++ ")
            append("-target aarch64-linux-android24 ")
            append("-nostdinc++ ")
            append("-isystem ${usrDir.absolutePath}/include/c++/v1 ")
            append("-isystem ${usrDir.absolutePath}/include ")
            append("-std=c++17 ")
            append("-c solution.cpp")
        }.toString()

        val jsonArray = JSONArray().apply {
            put(JSONObject().apply {
                put("directory", cacheDir.absolutePath)
                put("command", command)
                put("file", solutionFile.absolutePath)
            })
        }

        compileCommandsFile.writeText(jsonArray.toString(2))
        AppLog.d("CLANGD_MGR", "📄 Đã tạo compile_commands.json tại: ${compileCommandsFile.absolutePath}")
    }

    /**
     * Mở tài liệu C++ vào LSP session.
     */
    fun openDocument(text: String) {
        pendingInitialText = text
        if (!_isLspReady.value) {
            AppLog.d("CLANGD_MGR", "⏳ clangd chưa ready, đã lưu pending text (${text.length} ký tự)")
            return
        }

        synchronized(syncLock) {
            if (isDocumentOpened) {
                sendDidClose()
            }
            sendDidOpen(text)
        }
    }

    private fun sendDidClose() {
        val t = transport ?: return
        if (activeDocumentUri.isEmpty() || !isDocumentOpened) return
        val params = JSONObject().apply {
            put("textDocument", JSONObject().apply {
                put("uri", activeDocumentUri)
            })
        }
        t.sendNotification("textDocument/didClose", params)
        isDocumentOpened = false
    }

    private fun sendDidOpen(text: String) {
        val t = transport ?: return
        if (activeDocumentUri.isEmpty()) return

        synchronized(syncLock) {
            lastSyncedText = text
            documentVersion.set(1)
            pendingChangeText = null

            val params = JSONObject().apply {
                put("textDocument", JSONObject().apply {
                    put("uri", activeDocumentUri)
                    put("languageId", "cpp")
                    put("version", documentVersion.get())
                    put("text", text)
                })
            }
            t.sendNotification("textDocument/didOpen", params)
            isDocumentOpened = true
            AppLog.i("CLANGD_MGR", "📄 Đã gửi textDocument/didOpen thành công: $activeDocumentUri (${text.length} ký tự)")
        }
    }

    /**
     * Cập nhật nội dung văn bản (coalescing và debounce 150ms khi gõ thường).
     */
    fun onTextChanged(newText: String) {
        if (!_isLspReady.value) {
            pendingInitialText = newText
            return
        }

        if (!isDocumentOpened) {
            sendDidOpen(newText)
            return
        }

        if (newText == lastSyncedText) {
            pendingChangeText = null
            return
        }

        pendingChangeText = newText
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(150)
            flushPendingChange()
        }
    }

    /**
     * Tính toán thay đổi incremental giữa 2 phiên bản văn bản theo chuẩn UTF-16 code units của LSP.
     * Trả về Pair(LspRange, insertedText) hoặc null nếu không thể tính toán.
     */
    fun computeIncrementalChange(oldText: String, newText: String): Pair<LspRange, String>? {
        if (oldText == newText) return null

        val oldLen = oldText.length
        val newLen = newText.length

        // 1. Tìm độ dài common prefix
        var prefixLen = 0
        val maxPrefix = minOf(oldLen, newLen)
        while (prefixLen < maxPrefix && oldText[prefixLen] == newText[prefixLen]) {
            prefixLen++
        }

        // 2. Tìm độ dài common suffix (không chồng lấn với prefix)
        var suffixLen = 0
        val maxSuffix = minOf(oldLen - prefixLen, newLen - prefixLen)
        while (suffixLen < maxSuffix && oldText[oldLen - 1 - suffixLen] == newText[newLen - 1 - suffixLen]) {
            suffixLen++
        }

        val startOffset = prefixLen
        val endOffset = oldLen - suffixLen

        val startPos = LspPositionUtils.offsetToPosition(oldText, startOffset)
        val endPos = LspPositionUtils.offsetToPosition(oldText, endOffset)

        val insertedText = newText.substring(prefixLen, newLen - suffixLen)

        return Pair(LspRange(startPos, endPos), insertedText)
    }

    /**
     * Đẩy thay đổi mới nhất tới clangd (ưu tiên incremental sync, bảo toàn thứ tự và version).
     */
    fun flushPendingChange() {
        synchronized(syncLock) {
            if (!isDocumentOpened) {
                pendingInitialText?.let { sendDidOpen(it) }
                return
            }

            val textToSync = pendingChangeText ?: return
            pendingChangeText = null
            debounceJob?.cancel()

            if (textToSync == lastSyncedText) return
            val t = transport ?: return

            val oldText = lastSyncedText
            val newVersion = documentVersion.incrementAndGet()
            lastSyncedText = textToSync

            val incremental = if (serverSyncKind != 1 && oldText.isNotEmpty()) {
                computeIncrementalChange(oldText, textToSync)
            } else {
                null
            }

            val changeEvent = JSONObject().apply {
                if (incremental != null) {
                    val (range, insertedText) = incremental
                    put("range", JSONObject().apply {
                        put("start", JSONObject().apply {
                            put("line", range.start.line)
                            put("character", range.start.character)
                        })
                        put("end", JSONObject().apply {
                            put("line", range.end.line)
                            put("character", range.end.character)
                        })
                    })
                    put("text", insertedText)
                } else {
                    put("text", textToSync)
                }
            }

            val params = JSONObject().apply {
                put("textDocument", JSONObject().apply {
                    put("uri", activeDocumentUri)
                    put("version", newVersion)
                })
                put("contentChanges", JSONArray().apply { put(changeEvent) })
            }
            t.sendNotification("textDocument/didChange", params)
        }
    }

    /**
     * Gửi yêu cầu Auto-Completion tới clangd.
     */
    suspend fun requestCompletion(position: LspPosition): CompletionResult = withContext(Dispatchers.IO) {
        if (!_isLspReady.value) return@withContext CompletionResult()
        val t = transport ?: return@withContext CompletionResult()

        // Đồng bộ các thay đổi chưa flush nếu có
        flushPendingChange()

        try {
            val params = JSONObject().apply {
                put("textDocument", JSONObject().apply { put("uri", activeDocumentUri) })
                put("position", JSONObject().apply {
                    put("line", position.line)
                    put("character", position.character)
                })
            }

            val response = try {
                t.sendRequest("textDocument/completion", params, timeoutMs = 1500)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("CLANGD_MGR", "Lỗi lấy completion: ${e.message}")
                return@withContext CompletionResult()
            }

            val resultObj = response.opt("result") ?: return@withContext CompletionResult()
            val (itemsArray, isIncomplete) = when (resultObj) {
                is JSONArray -> Pair(resultObj, false)
                is JSONObject -> Pair(
                    resultObj.optJSONArray("items") ?: JSONArray(),
                    resultObj.optBoolean("isIncomplete", false)
                )
                else -> Pair(JSONArray(), false)
            }

            val resultList = ArrayList<CompletionItem>(itemsArray.length())
            for (i in 0 until itemsArray.length()) {
                val item = itemsArray.getJSONObject(i)
                val label = item.getString("label")
                val kind = CompletionItemKind.fromInt(item.optInt("kind", 1))
                val detail = item.optString("detail").takeIf { it.isNotEmpty() }
                val doc = item.opt("documentation")?.let {
                    if (it is JSONObject) it.optString("value") else it.toString()
                }

                val insertTextFormat = item.optInt("insertTextFormat", 1)
                val isSnippet = insertTextFormat == 2

                var insertText = item.optString("insertText")
                if (insertText.isEmpty()) {
                    val textEdit = item.optJSONObject("textEdit")
                    if (textEdit != null) {
                        insertText = textEdit.optString("newText")
                    }
                }
                if (insertText.isEmpty()) {
                    insertText = label
                }

                val filterText = item.optString("filterText", label)
                val sortText = item.optString("sortText", label)

                resultList.add(
                    CompletionItem(
                        label = label,
                        kind = kind,
                        detail = detail,
                        documentation = doc,
                        insertText = insertText,
                        filterText = filterText,
                        sortText = sortText,
                        isSnippet = isSnippet
                    )
                )
            }

            return@withContext CompletionResult(isIncomplete = isIncomplete, items = resultList)

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("CLANGD_MGR", "Lỗi lấy completion: ${e.message}")
            return@withContext CompletionResult()
        }
    }

    /**
     * Gửi yêu cầu Signature Help tới clangd.
     */
    suspend fun requestSignatureHelp(position: LspPosition): SignatureHelp? = withContext(Dispatchers.IO) {
        if (!_isLspReady.value) return@withContext null
        val t = transport ?: return@withContext null

        flushPendingChange()

        try {
            val params = JSONObject().apply {
                put("textDocument", JSONObject().apply { put("uri", activeDocumentUri) })
                put("position", JSONObject().apply {
                    put("line", position.line)
                    put("character", position.character)
                })
            }

            val response = try {
                t.sendRequest("textDocument/signatureHelp", params, timeoutMs = 1000)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withContext null
            }

            val resultObj = response.optJSONObject("result") ?: return@withContext null
            val sigArray = resultObj.optJSONArray("signatures") ?: JSONArray()
            if (sigArray.length() == 0) return@withContext null

            val activeSigIndex = resultObj.optInt("activeSignature", 0)
            val activeParamIndex = resultObj.optInt("activeParameter", 0)

            val signaturesList = ArrayList<SignatureInformation>(sigArray.length())
            for (i in 0 until sigArray.length()) {
                val sig = sigArray.getJSONObject(i)
                val label = sig.getString("label")
                val doc = sig.opt("documentation")?.let {
                    if (it is JSONObject) it.optString("value") else it.toString()
                }

                val paramArray = sig.optJSONArray("parameters") ?: JSONArray()
                val paramsList = ArrayList<ParameterInformation>(paramArray.length())

                for (j in 0 until paramArray.length()) {
                    val p = paramArray.getJSONObject(j)
                    val pLabel = p.opt("label")
                    val pLabelStr = when (pLabel) {
                        is String -> pLabel
                        is JSONArray -> {
                            val start = pLabel.optInt(0, 0)
                            val end = pLabel.optInt(1, label.length)
                            if (start in 0..label.length && end in start..label.length) {
                                label.substring(start, end)
                            } else {
                                ""
                            }
                        }
                        else -> ""
                    }
                    val pDoc = p.opt("documentation")?.let {
                        if (it is JSONObject) it.optString("value") else it.toString()
                    }
                    paramsList.add(ParameterInformation(label = pLabelStr, documentation = pDoc))
                }

                val sigActiveParam = sig.optInt("activeParameter", activeParamIndex)
                signaturesList.add(
                    SignatureInformation(
                        label = label,
                        documentation = doc,
                        parameters = paramsList,
                        activeParameter = sigActiveParam
                    )
                )
            }

            return@withContext SignatureHelp(
                signatures = signaturesList,
                activeSignature = activeSigIndex.coerceIn(0, (signaturesList.size - 1).coerceAtLeast(0)),
                activeParameter = activeParamIndex
            )

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("CLANGD_MGR", "Lỗi lấy signature help: ${e.message}")
            return@withContext null
        }
    }

    fun stop() {
        debounceJob?.cancel()
        try {
            transport?.sendNotification("exit", JSONObject())
            transport?.close()
        } catch (_: Exception) {}
        transport = null
        try {
            process?.destroyForcibly()
        } catch (_: Exception) {}
        process = null
        _isLspReady.value = false
        isDocumentOpened = false
        _currentDiagnostics.value = emptyList()
    }
}
