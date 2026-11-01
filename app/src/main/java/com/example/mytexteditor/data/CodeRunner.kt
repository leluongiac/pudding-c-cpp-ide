package com.example.mytexteditor.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ExecutionResult(
    val output: String,
    val isError: Boolean,
    val exitCode: Int,
    val executionTimeMs: Long
)

// 2. Bộ chạy Clang Offline khớp 100% với giao diện cũ
object CodeRunner {

    var filesDir: File? = null
    var cacheDir: File? = null

    suspend fun runCpp(code: String, stdin: String = ""): ExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val usr = filesDir?.let { File(it, "usr") }
        val cache = cacheDir

        // Kiểm tra xem đã giải nén Clang chưa
        if (usr == null || !File(usr, "bin/clang++").exists()) {
            val elapsed = System.currentTimeMillis() - startTime
            return@withContext ExecutionResult(
                output = "Đang cài đặt hoặc chưa tìm thấy Compiler Clang. Vui lòng đợi vài giây rồi thử lại!",
                isError = true,
                exitCode = -1,
                executionTimeMs = elapsed
            )
        }

        val sourceFile = File(cache, "solution.cpp")
        val outputFile = File(cache, "solution.out")

        try {
            // Ghi code ra file tạm
            sourceFile.writeText(code)

            // --- BƯỚC 1: BIÊN DỊCH BẰNG CLANG++ ---
            val compileCmd = listOf(
                "${usr.absolutePath}/bin/clang++",
                "-target", "aarch64-linux-android",
                "-isystem", "${usr.absolutePath}/include",
                "-isystem", "${usr.absolutePath}/include/c++/v1",
                "-L${usr.absolutePath}/lib",
                sourceFile.absolutePath,
                "-o", outputFile.absolutePath
            )

            val pbCompile = ProcessBuilder(compileCmd)
            pbCompile.environment()["LD_LIBRARY_PATH"] = "${usr.absolutePath}/lib"
            pbCompile.environment()["PATH"] = "${usr.absolutePath}/bin:" + System.getenv("PATH")
            pbCompile.environment()["TMPDIR"] = cache?.absolutePath ?: ""

            val compileProcess = pbCompile.start()
            val compilerError = compileProcess.errorStream.bufferedReader().readText()
            val compileExit = compileProcess.waitFor()

            // Nếu biên dịch lỗi (lỗi cú pháp C++)
            if (compileExit != 0) {
                val elapsed = System.currentTimeMillis() - startTime
                return@withContext ExecutionResult(
                    output = "[Lỗi Biên Dịch]:\n$compilerError",
                    isError = true,
                    exitCode = compileExit,
                    executionTimeMs = elapsed
                )
            }

            // --- BƯỚC 2: CHẠY FILE OUTPUT VỪA BUILD ---
            outputFile.setExecutable(true, false)
            val pbRun = ProcessBuilder(outputFile.absolutePath)
            pbRun.environment()["LD_LIBRARY_PATH"] = "${usr.absolutePath}/lib"

            val runProcess = pbRun.start()

            // Nạp dữ liệu STDIN (nếu có nhập vào ô input)
            if (stdin.isNotEmpty()) {
                runProcess.outputStream.bufferedWriter().use { writer ->
                    writer.write(stdin)
                    writer.flush()
                }
            }

            val programOutput = runProcess.inputStream.bufferedReader().readText()
            val programError = runProcess.errorStream.bufferedReader().readText()
            val runExit = runProcess.waitFor()
            val elapsed = System.currentTimeMillis() - startTime

            // Format kết quả giống hệt logic cũ của bro
            val finalOutput = when {
                programOutput.isNotEmpty() -> programOutput
                compilerError.isNotEmpty() -> "[Warnings]:\n$compilerError\n$programOutput"
                programError.isNotEmpty() -> "[Stderr]:\n$programError"
                else -> "[Chương trình kết thúc (không có output)]"
            }

            val isSuccess = (runExit == 0)
            return@withContext ExecutionResult(
                output = finalOutput,
                isError = !isSuccess,
                exitCode = runExit,
                executionTimeMs = elapsed
            )

        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            return@withContext ExecutionResult(
                output = "Lỗi thực thi: ${e.localizedMessage ?: "Unknown Error"}",
                isError = true,
                exitCode = -1,
                executionTimeMs = elapsed
            )
        }
    }
}