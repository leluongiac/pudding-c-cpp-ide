package com.example.mytexteditor.lsp

import com.example.mytexteditor.data.AppLog
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Giao tiếp JSON-RPC 2.0 chuẩn theo Language Server Protocol với Process clangd.
 * - Framing Content-Length: <bytes>\r\n\r\n<payload>
 * - Đọc stdout và stderr hoàn toàn trên Dispatchers.IO, không bao giờ chặn Main Thread.
 * - Đọc stderr liên tục tránh tràn buffer gây deadlock tiến trình.
 */
class LspJsonRpcTransport(
    private val inputStream: InputStream,
    private val outputStream: OutputStream,
    private val errorStream: InputStream,
    private val scope: CoroutineScope,
    private val onNotification: (method: String, params: JSONObject) -> Unit
) {
    private val nextRequestId = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>()
    private val writeLock = Any()

    @Volatile
    private var isRunning = true

    init {
        // Luồng đọc stdout liên tục
        scope.launch(Dispatchers.IO) {
            readStdoutLoop()
        }

        // Luồng đọc stderr liên tục để tránh deadlock buffer
        scope.launch(Dispatchers.IO) {
            readStderrLoop()
        }
    }

    /**
     * Gửi một Request JSON-RPC và đợi Response trả về (bất đồng bộ qua CompletableDeferred).
     */
    suspend fun sendRequest(method: String, params: JSONObject): JSONObject {
        val id = nextRequestId.getAndIncrement()
        val deferred = CompletableDeferred<JSONObject>()
        pendingRequests[id] = deferred

        val json = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }

        sendPayload(json.toString())
        return deferred.await()
    }

    /**
     * Gửi một Notification JSON-RPC (không cần Response).
     */
    fun sendNotification(method: String, params: JSONObject) {
        val json = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("method", method)
            put("params", params)
        }
        sendPayload(json.toString())
    }

    private fun sendPayload(payload: String) {
        if (!isRunning) return
        try {
            val bytes = payload.toByteArray(StandardCharsets.UTF_8)
            val header = "Content-Length: ${bytes.size}\r\n\r\n"
            val headerBytes = header.toByteArray(StandardCharsets.US_ASCII)

            synchronized(writeLock) {
                outputStream.write(headerBytes)
                outputStream.write(bytes)
                outputStream.flush()
            }
        } catch (e: Exception) {
            AppLog.e("LSP_RPC", "Lỗi gửi gói tin: ${e.message}")
        }
    }

    private fun readStdoutLoop() {
        try {
            while (isRunning) {
                // Đọc Header Content-Length
                var contentLength = -1
                var line = readHeaderLine(inputStream) ?: break

                while (line.isNotEmpty()) {
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        val lenStr = line.substring("Content-Length:".length).trim()
                        contentLength = lenStr.toIntOrNull() ?: -1
                    }
                    line = readHeaderLine(inputStream) ?: break
                }

                if (contentLength <= 0) continue

                // Đọc chính xác contentLength bytes
                val bodyBytes = ByteArray(contentLength)
                var bytesReadTotal = 0
                while (bytesReadTotal < contentLength) {
                    val read = inputStream.read(bodyBytes, bytesReadTotal, contentLength - bytesReadTotal)
                    if (read == -1) break
                    bytesReadTotal += read
                }

                if (bytesReadTotal == contentLength) {
                    val bodyString = String(bodyBytes, StandardCharsets.UTF_8)
                    handleMessage(bodyString)
                }
            }
        } catch (e: Exception) {
            if (isRunning) {
                AppLog.e("LSP_RPC", "Lỗi vòng lặp đọc stdout: ${e.message}")
            }
        } finally {
            close()
        }
    }

    private fun handleMessage(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)

            // Kiểm tra xem là Response của Request trước đó hay là Notification từ server
            if (json.has("id") && (json.has("result") || json.has("error"))) {
                val id = json.getInt("id")
                val deferred = pendingRequests.remove(id)
                deferred?.complete(json)
            } else if (json.has("method")) {
                val method = json.getString("method")
                val params = json.optJSONObject("params") ?: JSONObject()
                onNotification(method, params)
            }
        } catch (e: Exception) {
            AppLog.e("LSP_RPC", "Lỗi phân tích JSON nhận được: ${e.message}")
        }
    }

    private fun readStderrLoop() {
        try {
            val reader = errorStream.bufferedReader()
            while (isRunning) {
                val errLine = reader.readLine() ?: break
                if (errLine.isNotBlank()) {
                    AppLog.d("CLANGD_STDERR", errLine)
                }
            }
        } catch (e: Exception) {
            // Process kết thúc hoặc stream bị đóng
        }
    }

    private fun readHeaderLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isNotEmpty()) sb.toString() else null
            val ch = b.toChar()
            if (ch == '\r') {
                val next = input.read()
                if (next == '\n'.code) {
                    return sb.toString()
                } else if (next != -1) {
                    sb.append(ch)
                    sb.append(next.toChar())
                }
            } else if (ch == '\n') {
                return sb.toString()
            } else {
                sb.append(ch)
            }
        }
    }

    fun close() {
        isRunning = false
        pendingRequests.values.forEach {
            it.cancel(CancellationException("LSP Transport đã đóng"))
        }
        pendingRequests.clear()
        try {
            outputStream.close()
            inputStream.close()
            errorStream.close()
        } catch (_: Exception) {}
    }
}
