package com.example.mytexteditor.lsp

import com.example.mytexteditor.data.AppLog
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.IOException
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
 * - Quản lý vòng đời request, timeout, hủy bỏ $/cancelRequest và thu hồi tài nguyên an toàn.
 */
class LspJsonRpcTransport(
    private val inputStream: InputStream,
    private val outputStream: OutputStream,
    private val errorStream: InputStream,
    private val scope: CoroutineScope,
    private val onNotification: (method: String, params: JSONObject) -> Unit,
    private val onClose: ((Throwable?) -> Unit)? = null
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
     * Có thiết lập timeout, tự động gửi $/cancelRequest khi timeout/hủy bỏ, và luôn dọn dẹp pending request.
     */
    suspend fun sendRequest(
        method: String,
        params: JSONObject,
        timeoutMs: Long = 5000
    ): JSONObject {
        if (!isRunning) {
            throw IOException("LSP Transport đã đóng, không thể gửi yêu cầu: $method")
        }

        val id = nextRequestId.getAndIncrement()
        val deferred = CompletableDeferred<JSONObject>()
        pendingRequests[id] = deferred

        val json = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }

        try {
            sendPayload(json.toString())
        } catch (e: Exception) {
            pendingRequests.remove(id)
            deferred.cancel(CancellationException("Không thể gửi yêu cầu LSP: ${e.message}", e))
            throw e
        }

        return try {
            withTimeout(timeoutMs) {
                deferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            sendCancelRequest(id)
            throw e
        } catch (e: CancellationException) {
            sendCancelRequest(id)
            throw e
        } finally {
            pendingRequests.remove(id)
        }
    }

    /**
     * Gửi một Notification JSON-RPC (không cần Response).
     */
    fun sendNotification(method: String, params: JSONObject) {
        if (!isRunning) return
        val json = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("method", method)
            put("params", params)
        }
        try {
            sendPayload(json.toString())
        } catch (e: Exception) {
            AppLog.e("LSP_RPC", "Lỗi gửi notification $method: ${e.message}")
        }
    }

    /**
     * Gửi thông báo $/cancelRequest chuẩn LSP hủy request đang chờ xử lý trên server.
     */
    fun sendCancelRequest(id: Int) {
        if (!isRunning) return
        try {
            val json = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("method", "$/cancelRequest")
                put("params", JSONObject().apply { put("id", id) })
            }
            sendPayload(json.toString())
            AppLog.d("LSP_RPC", "🛑 Đã gửi $/cancelRequest cho request ID: $id")
        } catch (e: Exception) {
            AppLog.w("LSP_RPC", "Không thể gửi $/cancelRequest cho ID $id: ${e.message}")
        }
    }

    @Throws(IOException::class)
    private fun sendPayload(payload: String) {
        if (!isRunning) throw IOException("LSP Transport đã đóng")
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        val header = "Content-Length: ${bytes.size}\r\n\r\n"
        val headerBytes = header.toByteArray(StandardCharsets.US_ASCII)

        synchronized(writeLock) {
            if (!isRunning) throw IOException("LSP Transport đã đóng")
            try {
                outputStream.write(headerBytes)
                outputStream.write(bytes)
                outputStream.flush()
            } catch (e: Exception) {
                AppLog.e("LSP_RPC", "Lỗi ghi dữ liệu tới clangd: ${e.message}", e)
                close(e)
                throw IOException("Lỗi ghi dữ liệu JSON-RPC tới clangd: ${e.message}", e)
            }
        }
    }

    private fun readStdoutLoop() {
        var failureCause: Throwable? = null
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
                } else {
                    failureCause = IOException("Stream kết thúc đột ngột: đọc $bytesReadTotal / $contentLength bytes")
                    break
                }
            }
        } catch (e: Exception) {
            if (isRunning) {
                failureCause = e
                AppLog.e("LSP_RPC", "Lỗi vòng lặp đọc stdout: ${e.message}")
            }
        } finally {
            close(failureCause)
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

    fun close(cause: Throwable? = null) {
        synchronized(writeLock) {
            if (!isRunning) return
            isRunning = false
        }

        val cancelException = CancellationException(
            cause?.message ?: "LSP Transport đã đóng",
            cause
        )

        val pending = ArrayList(pendingRequests.values)
        pendingRequests.clear()
        pending.forEach { deferred ->
            deferred.cancel(cancelException)
        }

        try {
            outputStream.close()
        } catch (_: Exception) {}
        try {
            inputStream.close()
        } catch (_: Exception) {}
        try {
            errorStream.close()
        } catch (_: Exception) {}

        try {
            onClose?.invoke(cause)
        } catch (e: Exception) {
            AppLog.e("LSP_RPC", "Lỗi khi gọi onClose: ${e.message}")
        }
    }
}
