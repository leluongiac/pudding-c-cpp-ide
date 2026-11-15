package com.example.mytexteditor.data

import android.content.Context
import java.io.File

/**
 * Quản lý tập trung môi trường, đường dẫn và cấu hình cho Clang Compiler và clangd Language Server.
 */
object ToolchainEnvironment {

    var filesDir: File? = null
    var cacheDir: File? = null

    fun getUsrDir(context: Context? = null): File? {
        val base = filesDir ?: context?.filesDir
        return base?.let { File(it, "usr") }
    }

    fun getCacheDir(context: Context? = null): File? {
        return cacheDir ?: context?.cacheDir
    }

    fun getClangPlusPlus(context: Context? = null): File? {
        val usr = getUsrDir(context) ?: return null
        return File(usr, "bin/clang++")
    }

    fun getClangd(context: Context? = null): File? {
        val usr = getUsrDir(context) ?: return null
        return File(usr, "bin/clangd")
    }

    fun getLibDir(context: Context? = null): File? {
        val usr = getUsrDir(context) ?: return null
        return File(usr, "lib")
    }

    fun getIncludeDir(context: Context? = null): File? {
        val usr = getUsrDir(context) ?: return null
        return File(usr, "include")
    }

    fun getCppIncludeDir(context: Context? = null): File? {
        val usr = getUsrDir(context) ?: return null
        return File(usr, "include/c++/v1")
    }

    /**
     * Kiểm tra xem clang++ đã được cài đặt và có thể thực thi.
     */
    fun isClangInstalled(context: Context? = null): Boolean {
        val bin = getClangPlusPlus(context)
        return bin != null && bin.exists()
    }

    /**
     * Kiểm tra xem clangd binary đã được cài đặt và có quyền thực thi.
     */
    fun isClangdInstalled(context: Context? = null): Boolean {
        val bin = getClangd(context)
        return bin != null && bin.exists() && bin.canExecute()
    }

    /**
     * Kiểm tra xem toàn bộ toolchain (compiler + clangd) đã sẵn sàng chưa.
     */
    fun isToolchainInstalled(context: Context? = null): Boolean {
        return isClangInstalled(context) && isClangdInstalled(context)
    }

    /**
     * Thiết lập các biến môi trường chuẩn cho clang / clangd
     */
    fun setupEnvironment(env: MutableMap<String, String>, context: Context? = null) {
        val usr = getUsrDir(context) ?: return
        val cache = getCacheDir(context)

        val usrPath = usr.absolutePath
        val libPath = "$usrPath/lib"
        val binPath = "$usrPath/bin"

        val currentPath = env["PATH"] ?: System.getenv("PATH") ?: "/system/bin"
        env["PATH"] = "$binPath:$currentPath"
        env["LD_LIBRARY_PATH"] = libPath
        if (cache != null) {
            env["TMPDIR"] = cache.absolutePath
        }
    }

    /**
     * Đảm bảo các symlink libffi.so và libzstd.so.1 tồn tại trong usr/lib
     */
    fun verifyRuntimeLibraries(context: Context? = null) {
        val libDir = getLibDir(context) ?: return
        if (!libDir.exists()) return

        try {
            libDir.setReadable(true, false)
            libDir.setExecutable(true, false)
            libDir.listFiles()?.forEach { file ->
                file.setReadable(true, false)
                file.setExecutable(true, false)
            }

            val libFfi = File(libDir, "libffi.so")
            if (!libFfi.exists()) {
                val candidate = libDir.listFiles()?.firstOrNull { it.name.startsWith("libffi.so") }
                if (candidate != null) {
                    try {
                        android.system.Os.symlink(candidate.name, libFfi.absolutePath)
                        AppLog.d("TOOLCHAIN_ENV", "🔗 Symlink ${candidate.name} -> libffi.so")
                    } catch (e: Exception) {
                        AppLog.w("TOOLCHAIN_ENV", "Cảnh báo symlink libffi: ${e.message}")
                    }
                }
            }

            val libZstd1 = File(libDir, "libzstd.so.1")
            if (!libZstd1.exists()) {
                val libZstd = File(libDir, "libzstd.so")
                if (libZstd.exists()) {
                    try {
                        android.system.Os.symlink("libzstd.so", libZstd1.absolutePath)
                    } catch (_: Exception) {
                        try {
                            libZstd.copyTo(libZstd1, overwrite = true)
                        } catch (e: Exception) {
                            AppLog.w("TOOLCHAIN_ENV", "Cảnh báo copy libzstd: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.w("TOOLCHAIN_ENV", "Lỗi verifyRuntimeLibraries: ${e.message}")
        }
    }
}
