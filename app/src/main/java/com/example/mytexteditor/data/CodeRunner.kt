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

/**
 * Bộ biên dịch và thực thi C++ offline bằng Clang.
 * Đồng bộ môi trường và đường dẫn với ToolchainEnvironment.
 */
object CodeRunner {

    var filesDir: File?
        get() = ToolchainEnvironment.filesDir
        set(value) { ToolchainEnvironment.filesDir = value }

    var cacheDir: File?
        get() = ToolchainEnvironment.cacheDir
        set(value) { ToolchainEnvironment.cacheDir = value }

    suspend fun compileCpp(code: String, stdin: String = ""): ExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val usr = ToolchainEnvironment.getUsrDir()
        val cache = ToolchainEnvironment.getCacheDir()

        AppLog.i("CLANG_RUNNER", "==================================================")
        AppLog.i("CLANG_RUNNER", "🚀 BẮT ĐẦU YÊU CẦU BIÊN DỊCH & THỰC THI C++")
        AppLog.d("CLANG_RUNNER", "Độ dài mã nguồn: ${code.length} ký tự, STDIN: '$stdin'")
        AppLog.d("CLANG_RUNNER", "usr dir: ${usr?.absolutePath}, cache dir: ${cache?.absolutePath}")

        // Kiểm tra xem đã giải nén Clang chưa
        val clangPlusPlus = ToolchainEnvironment.getClangPlusPlus()
        if (usr == null || clangPlusPlus == null || !clangPlusPlus.exists()) {
            val elapsed = System.currentTimeMillis() - startTime
            val errMsg = "Đang cài đặt hoặc chưa tìm thấy Compiler Clang. Vui lòng đợi vài giây rồi thử lại!"
            AppLog.e("CLANG_RUNNER", "❌ Lỗi: $errMsg")
            return@withContext ExecutionResult(
                output = errMsg,
                isError = true,
                exitCode = -1,
                executionTimeMs = elapsed
            )
        }

        val sourceFile = File(cache, "solution.cpp")
        val outputFile = File(cache, "solution.out")

        try {
            // Ghi code ra file tạm
            AppLog.d("CLANG_RUNNER", "📝 Đang ghi mã nguồn vào: ${sourceFile.absolutePath}")
            sourceFile.writeText(code)
            AppLog.d("CLANG_RUNNER", "✅ Đã ghi mã nguồn (${sourceFile.length()} bytes)")

            // Đảm bảo runtime libraries và builtins
            ToolchainEnvironment.verifyRuntimeLibraries()

            val clang21Lib = File(usr, "lib/clang/21/lib")
            val expectedDir = File(clang21Lib, "aarch64-unknown-linux-android24")
            val expectedBuiltins = File(expectedDir, "libclang_rt.builtins.a")

            if (!expectedBuiltins.exists()) {
                expectedDir.mkdirs()
                val actualBuiltins = usr.walkTopDown().firstOrNull {
                    it.name.contains("builtins") && it.name.endsWith(".a")
                }
                if (actualBuiltins != null) {
                    try {
                        android.system.Os.symlink(actualBuiltins.absolutePath, expectedBuiltins.absolutePath)
                        AppLog.i("CLANG_RUNNER", "🔗 Đã symlink builtins: ${actualBuiltins.name} -> ${expectedBuiltins.absolutePath}")
                    } catch (_: Exception) {
                        actualBuiltins.copyTo(expectedBuiltins, overwrite = true)
                    }
                }
            }

            // Lệnh biên dịch Clang++
            val compileCmd = listOf(
                clangPlusPlus.absolutePath,
                "-target", "aarch64-linux-android24",
                "-nostdinc++",
                "-isystem", "${usr.absolutePath}/include/c++/v1",
                "-isystem", "${usr.absolutePath}/include",
                "-L${usr.absolutePath}/lib",
                sourceFile.absolutePath,
                "-o", outputFile.absolutePath
            )
            AppLog.i("CLANG_RUNNER", "⚙️ Lệnh Clang: ${compileCmd.joinToString(" ")}")

            val pbCompile = ProcessBuilder(compileCmd)
            ToolchainEnvironment.setupEnvironment(pbCompile.environment())
            pbCompile.redirectErrorStream(true) // Gộp stdout và stderr để đọc an toàn, tránh deadlock buffer

            AppLog.i("CLANG_RUNNER", "⏳ Đang chạy quá trình biên dịch Clang++...")
            val compileProcess = pbCompile.start()
            val compileOutput = compileProcess.inputStream.bufferedReader().readText()
            val compileExit = compileProcess.waitFor()

            AppLog.d("CLANG_RUNNER", "Mã thoát biên dịch (Exit Code): $compileExit")

            if (compileExit != 0) {
                AppLog.e("CLANG_ERROR", "❌ BIÊN DỊCH THẤT BẠI (Exit Code $compileExit):\n$compileOutput")
                val elapsed = System.currentTimeMillis() - startTime
                return@withContext ExecutionResult(
                    output = "[Lỗi Biên Dịch]:\n$compileOutput",
                    isError = true,
                    exitCode = compileExit,
                    executionTimeMs = elapsed
                )
            }

            if (compileOutput.isNotEmpty()) {
                AppLog.w("CLANG_RUNNER", "⚠️ Cảnh báo biên dịch:\n$compileOutput")
            } else {
                AppLog.i("CLANG_RUNNER", "✅ Biên dịch thành công!")
            }

            return@withContext ExecutionResult(
                output = "Biên dịch thành công!",
                isError = false,
                exitCode = 0,
                executionTimeMs = System.currentTimeMillis() - startTime
            )

        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            val errMsg = "Lỗi thực thi: ${e.localizedMessage ?: "Unknown Error"}"
            AppLog.e("CLANG_ERROR", "💥 Ngoại lệ trong CodeRunner: $errMsg", e)
            return@withContext ExecutionResult(
                output = errMsg,
                isError = true,
                exitCode = -1,
                executionTimeMs = elapsed
            )
        }
    }
}