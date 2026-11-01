package com.example.mytexteditor.data

import android.content.Context
import android.system.Os
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.*

object ToolchainInstaller {

    fun installIfNeeded(context: Context, onStatusUpdate: (String) -> Unit, onComplete: () -> Unit) {
        val usrDir = File(context.filesDir, "usr")
        val clangBin = File(usrDir, "bin/clang")

        // Nếu đã có clang rồi thì không giải nén lại
        if (clangBin.exists()) {
            onComplete()
            return
        }

        Thread {
            try {
                onStatusUpdate("Đang cài đặt compiler Clang lần đầu...")
                val assetStream = context.assets.open("clang-toolchain.tar.gz")
                val tarIn = TarArchiveInputStream(GzipCompressorInputStream(BufferedInputStream(assetStream)))

                var entry = tarIn.nextEntry
                while (entry != null) {
                    val destFile = File(usrDir, entry.name)

                    if (entry.isSymbolicLink) {
                        // TẠO LẠI SYMLINK CHUẨN (clang++ -> clang)
                        destFile.parentFile?.mkdirs()
                        destFile.delete()
                        try {
                            Os.symlink(entry.linkName, destFile.absolutePath)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { output ->
                            tarIn.copyTo(output)
                        }
                        // Cấp quyền chạy ngay nếu nằm trong bin/
                        if (destFile.parentFile?.name == "bin") {
                            destFile.setExecutable(true, false)
                        }
                    }
                    entry = tarIn.nextEntry
                }
                tarIn.close()

                // Cấp lại quyền thực thi toàn bộ thư mục bin cho chắc
                File(usrDir, "bin").listFiles()?.forEach { it.setExecutable(true, false) }

                onStatusUpdate("Cài đặt hoàn tất!")
                onComplete()
            } catch (e: Exception) {
                e.printStackTrace()
                onStatusUpdate("Lỗi cài đặt: ${e.message}")
            }
        }.start()
    }
}