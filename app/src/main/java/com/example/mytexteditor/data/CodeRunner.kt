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

    suspend fun compileCpp(code: String, stdin: String = ""): ExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val usr = filesDir?.let { File(it, "usr") }
        val cache = cacheDir

        AppLog.i("CLANG_RUNNER", "==================================================")
        AppLog.i("CLANG_RUNNER", "🚀 BẮT ĐẦU YÊU CẦU BIÊN DỊCH & THỰC THI C++")
        AppLog.d("CLANG_RUNNER", "Độ dài mã nguồn: ${code.length} ký tự, STDIN: '${stdin}'")
        AppLog.d("CLANG_RUNNER", "usr dir: ${usr?.absolutePath}, cache dir: ${cache?.absolutePath}")

        // Kiểm tra xem đã giải nén Clang chưa
        if (usr == null || !File(usr, "bin/clang++").exists()) {
            val elapsed = System.currentTimeMillis() - startTime
            val errMsg = "Đang cài đặt hoặc chưa tìm thấy Compiler Clang. Vui lòng đợi vài giây rồi thử lại!"
            AppLog.e("CLANG_RUNNER", "❌ Lỗi: $errMsg (usr=$usr, clang++ exists=${File(usr, "bin/clang++").exists()})")
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
            
            // --- ĐẢM BẢO FILE LIBCLANG_RT.BUILTINS.A NẰM ĐÚNG VỊ TRÍ LINKER CẦN ---
            val clang21Lib = File(usr, "lib/clang/21/lib")
            val expectedDir = File(clang21Lib, "aarch64-unknown-linux-android24")
            val expectedBuiltins = File(expectedDir, "libclang_rt.builtins.a")

            if (!expectedBuiltins.exists()) {
                expectedDir.mkdirs()
                // Quét tìm file builtins có sẵn trong usr/lib/clang
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

            // --- BƯỚC 1: BIÊN DỊCH BẰNG CLANG++ ---
            val compileCmd = listOf(
                "${usr.absolutePath}/bin/clang++",
                "-v",
                "-target", "aarch64-linux-android24",             // 1. Thêm số 24 chuẩn Termux
                "-nostdinc++",                                    // 2. Chặn Clang tìm lung tung
                "-isystem", "${usr.absolutePath}/include/c++/v1", // 3. C++ BẮT BUỘC ĐỨNG ĐẦU
                "-isystem", "${usr.absolutePath}/include",        // 4. C đứng sau
                "-L${usr.absolutePath}/lib",
                sourceFile.absolutePath,
                "-o", outputFile.absolutePath
            )
            AppLog.i("CLANG_RUNNER", "⚙️ Lệnh Clang: ${compileCmd.joinToString(" ")}")
            
            val libDir = File(usr, "lib")
            libDir.setReadable(true, false)
            libDir.setExecutable(true, false)
            libDir.listFiles()?.forEach { file ->
                file.setReadable(true, false)
                file.setExecutable(true, false)
            }

            val libFfi = File(libDir, "libffi.so")
            if (!libFfi.exists()) {
                // Tự động tìm các file dạng libffi.so.8, libffi.so.7... để tạo symlink sang libffi.so
                val candidate = libDir.listFiles()?.firstOrNull { it.name.startsWith("libffi.so") }
                if (candidate != null) {
                    try {
                        AppLog.d("CLANG_RUNNER", "🔗 Tạo symlink: ${candidate.name} -> libffi.so")
                        android.system.Os.symlink(candidate.name, libFfi.absolutePath)
                    } catch (e: Exception) {
                        AppLog.w("CLANG_RUNNER", "Cảnh báo không tạo được symlink libffi: ${e.message}")
                    }
                } else {
                    // Nếu trong usr/lib thực sự không có file libffi nào, báo danh sách file để bạn biết
                    val list = libDir.list()?.take(15)?.joinToString(", ") ?: "trống"
                    val elapsed = System.currentTimeMillis() - startTime
                    val errMsg = "[Lỗi thiếu file]: Không thấy libffi.so trong usr/lib!\nDanh sách file hiện có trong lib: $list"
                    AppLog.e("CLANG_ERROR", errMsg)
                    return@withContext ExecutionResult(
                        output = errMsg,
                        isError = true,
                        exitCode = -1,
                        executionTimeMs = elapsed
                    )
                }
            }
            
            val libZstd1 = File(libDir, "libzstd.so.1")
            if (!libZstd1.exists()) {
                val libZstd = File(libDir, "libzstd.so")
                if (libZstd.exists()) {
                    try {
                        AppLog.d("CLANG_RUNNER", "🔗 Tạo symlink libzstd.so -> libzstd.so.1")
                        android.system.Os.symlink("libzstd.so", libZstd1.absolutePath)
                    } catch (_: Exception) {
                        try {
                            AppLog.d("CLANG_RUNNER", "📋 Copy dự phòng libzstd.so -> libzstd.so.1")
                            libZstd.copyTo(libZstd1, overwrite = true)
                        } catch (e: Exception) {
                            AppLog.w("CLANG_RUNNER", "Cảnh báo copy libzstd: ${e.message}")
                        }
                    }
                }
            }

            val pbCompile = ProcessBuilder(compileCmd)
            pbCompile.environment()["LD_LIBRARY_PATH"] = "${usr.absolutePath}/lib"
            pbCompile.environment()["PATH"] = "${usr.absolutePath}/bin:" + System.getenv("PATH")
            pbCompile.environment()["TMPDIR"] = cache?.absolutePath ?: ""

            AppLog.i("CLANG_RUNNER", "⏳ Đang chạy quá trình biên dịch Clang++...")
            val compileProcess = pbCompile.start()
            val compilerError = compileProcess.errorStream.bufferedReader().readText()
            val compileExit = compileProcess.waitFor()

            AppLog.d("CLANG_RUNNER", "Mã thoát biên dịch (Exit Code): $compileExit")

            // Nếu biên dịch lỗi (lỗi cú pháp C++)
            if (compileExit != 0) {
                AppLog.e("CLANG_ERROR", "❌ BIÊN DỊCH THẤT BẠI (Exit Code $compileExit):\n$compilerError")
                val elapsed = System.currentTimeMillis() - startTime
                return@withContext ExecutionResult(
                    output = "[Lỗi Biên Dịch]:\n$compilerError",
                    isError = true,
                    exitCode = compileExit,
                    executionTimeMs = elapsed
                )
            }

            if (compilerError.isNotEmpty()) {
                AppLog.w("CLANG_RUNNER", "⚠️ Cảnh báo biên dịch (Compiler Warnings):\n$compilerError")
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