package com.example.mytexteditor.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader

object TerminalSession {

    // Biến lưu chữ đang gõ dở cho STDIN
    val currentStdin = MutableStateFlow("")

    fun appendStdin(char: String) {
        currentStdin.value += char
    }

    fun backspaceStdin() {
        if (currentStdin.value.isNotEmpty()) {
            currentStdin.value = currentStdin.value.dropLast(1)
        }
    }

    fun submitStdin() {
        sendInput(currentStdin.value)
        currentStdin.value = "" // Gửi xong xóa trắng dòng
    }
    
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var job: kotlinx.coroutines.Job? = null

    // Biến này để Compose lắng nghe và vẽ chữ lên màn hình terminal
    private val _terminalOutput = MutableStateFlow("")
    val terminalOutput: StateFlow<String> = _terminalOutput.asStateFlow()

    // Báo trạng thái chương trình còn đang chạy hay đã xong
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /**
     * Bắt đầu chạy file nhị phân solution.out
     */
    fun start(outputFile: File, usrDir: File, scope: CoroutineScope) {
        stop() // Dọn dẹp tiến trình cũ nếu có

        _terminalOutput.value = "" // Xóa màn hình cũ
        _isRunning.value = true

        job = scope.launch(Dispatchers.IO) {
            try {
                outputFile.setExecutable(true, false)
                val pb = ProcessBuilder(outputFile.absolutePath)
                pb.environment()["LD_LIBRARY_PATH"] = "${usrDir.absolutePath}/lib"
                pb.redirectErrorStream(true) // Gộp cả stdout và stderr làm một để đọc cho tiện

                val proc = pb.start()
                process = proc
                writer = proc.outputStream.bufferedWriter()

                // Luồng đọc output liên tục từ C++
                val reader = InputStreamReader(proc.inputStream)
                val buffer = CharArray(1024)
                var bytesRead = 0

                while (proc.isAlive && reader.read(buffer).also { bytesRead = it } != -1) {
                    val text = String(buffer, 0, bytesRead)
                    // Bắn từng ký tự / dòng chữ mới lên giao diện Compose
                    _terminalOutput.value += text
                }

                val exitCode = proc.waitFor()
                _terminalOutput.value += "\n[Chương trình kết thúc với mã: $exitCode]"
            } catch (e: Exception) {
                _terminalOutput.value += "\n[Lỗi chạy]: ${e.message}"
            } finally {
                _isRunning.value = false
                stop()
            }
        }
    }

    /**
     * Hàm này để Giao diện gọi khi người dùng bấm phím hoặc gõ Enter
     */
    fun sendInput(input: String) {
        val w = writer ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // In luôn chữ người dùng vừa gõ vào màn hình terminal (giống như gõ phím thật)
                _terminalOutput.value += input + "\n"
                
                // Bơm dữ liệu vào cho hàm cin >> của C++
                w.write(input + "\n")
                w.flush()
            } catch (e: Exception) {
                AppLog.e("TERMINAL", "Không gửi được input: ${e.message}")
            }
        }
    }

    /**
     * Dừng chương trình (ví dụ người dùng bấm nút Hủy hoặc bị lặp vô tận)
     */
    fun stop() {
        try {
            writer?.close()
            process?.destroyForcibly()
        } catch (_: Exception) {}
        writer = null
        process = null
        _isRunning.value = false
        job?.cancel()
    }
    
    fun showError(errorText: String) {
        _terminalOutput.value = errorText
        _isRunning.value = false
    }
    
}