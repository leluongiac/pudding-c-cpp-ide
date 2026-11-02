package com.example.mytexteditor.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.system.Os
import androidx.core.app.NotificationCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import java.io.*
import java.nio.file.Files

object ToolchainInstaller {

    private val lock = Any()

    @Volatile
    private var isInstalling = false

    @Volatile
    private var currentStatus: String = "Đang chuẩn bị..."

    private val statusListeners = mutableListOf<(String) -> Unit>()
    private val completeListeners = mutableListOf<() -> Unit>()

    private val mainHandler = Handler(Looper.getMainLooper())

    fun isInstalled(context: Context): Boolean {
        val clang = File(context.filesDir, "usr/bin/clang")
        val clangPlus = File(context.filesDir, "usr/bin/clang++")
        val clang21 = File(context.filesDir, "usr/bin/clang-21")

        // Kiểm tra thư mục resource của Clang
        val clangLib = File(context.filesDir, "usr/lib/clang")

        // Kiểm tra runtime cần thiết cho linker Android ARM64
        val builtins = File(
            context.filesDir,
            "usr/lib/clang/21/lib/aarch64-unknown-linux-android24/libclang_rt.builtins.a"
        )

        return (clang.exists() || clangPlus.exists() || clang21.exists()) &&
                clangLib.exists() &&
                builtins.isFile
    }

    private const val CHANNEL_ID = "toolchain_download_channel"
    private const val NOTIFICATION_ID = 1001

    private const val DOWNLOAD_URL = "https://github.com/leluongiac/my_text_editor/releases/download/v1.0.0/clang-toolchain.tar.zst"

    fun installIfNeeded(
        context: Context,
        onStatusUpdate: (String) -> Unit,
        onComplete: () -> Unit
    ) {
        val appContext = context.applicationContext

        AppLog.i("TOOLCHAIN", "📦 Gọi installIfNeeded: filesDir=${appContext.filesDir}")

        if (isInstalled(appContext)) {
            AppLog.i("TOOLCHAIN", "✅ Clang đã được cài đặt từ trước! Bỏ qua tải về.")
            mainHandler.post { onComplete() }
            return
        }

        synchronized(lock) {
            statusListeners.add(onStatusUpdate)
            completeListeners.add(onComplete)

            val statusNow = currentStatus
            mainHandler.post { onStatusUpdate(statusNow) }

            if (isInstalling) {
                AppLog.d("TOOLCHAIN", "⏳ Đã có tiến trình cài đặt đang chạy ngầm, đăng ký listener thành công.")
                return
            }
            isInstalling = true
            AppLog.i("TOOLCHAIN", "🚀 Bắt đầu tiến trình tải và cài đặt Clang...")
        }

        Thread {
            val notifyManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            createNotificationChannel(notifyManager)

            val notifyBuilder = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Cài đặt Clang Toolchain")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)

            appContext.filesDir.mkdirs()
            val tempArchive = File(appContext.filesDir, "temp_toolchain.tar.zst")
            val usrDir = File(appContext.filesDir, "usr")

            try {
                // ================= BƯỚC 1: TẢI FILE TỪ GITHUB =================
                notifyStatus("Đang kết nối tới máy chủ GitHub...")
                notifyBuilder.setContentText("Đang tải gói cài đặt...").setProgress(100, 0, false)
                notifyManager.notify(NOTIFICATION_ID, notifyBuilder.build())

                AppLog.i("TOOLCHAIN", "🌐 Kết nối tới URL: $DOWNLOAD_URL")

                val client = OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url(DOWNLOAD_URL)
                    .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                    .build()

                val response = client.newCall(request).execute()

                AppLog.d("TOOLCHAIN", "📡 Phản hồi GitHub: HTTP ${response.code}")

                if (!response.isSuccessful) {
                    throw IOException("GitHub từ chối tải: Mã lỗi HTTP ${response.code}")
                }

                val body = response.body ?: throw IOException("Phản hồi từ GitHub không có dữ liệu (Body null)")
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                AppLog.i("TOOLCHAIN", "📥 Bắt đầu tải file (${totalBytes / (1024 * 1024)} MB)...")

                if (tempArchive.exists()) tempArchive.delete()

                body.byteStream().use { input ->
                    FileOutputStream(tempArchive).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var read: Int
                        var lastPercent = -1

                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read

                            val percent = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else -1
                            if (percent != lastPercent) {
                                lastPercent = percent
                                val status = if (totalBytes > 0) {
                                    val dlMb = downloadedBytes / (1024 * 1024)
                                    val totMb = totalBytes / (1024 * 1024)
                                    "Đang tải Clang: $percent% ($dlMb / $totMb MB)"
                                } else {
                                    val dlMb = downloadedBytes / (1024 * 1024)
                                    "Đang tải Clang: $dlMb MB"
                                }
                                notifyStatus(status)
                                notifyBuilder.setProgress(100, percent.coerceAtLeast(0), totalBytes <= 0)
                                    .setContentText(status)
                                notifyManager.notify(NOTIFICATION_ID, notifyBuilder.build())

                                if (percent % 10 == 0) {
                                    AppLog.d("TOOLCHAIN", "Tiến độ tải: $status")
                                }
                            }
                        }
                        output.flush()
                    }
                }

                if (!tempArchive.exists() || tempArchive.length() == 0L) {
                    throw IOException("Tải thất bại: File tải về không tồn tại hoặc có dung lượng 0 byte!")
                }
                AppLog.i("TOOLCHAIN", "✅ Đã tải xong file nén: ${tempArchive.length()} bytes")

                // ================= BƯỚC 2: GIẢI NÉN BẰNG ZSTD =================
                notifyStatus("Đang giải nén bộ biên dịch Clang (Zstd)...")
                notifyBuilder.setContentTitle("Đang giải nén Toolchain")
                    .setContentText("Đang bung các file nhị phân...")
                    .setProgress(0, 0, true)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                notifyManager.notify(NOTIFICATION_ID, notifyBuilder.build())

                AppLog.i("TOOLCHAIN", "📦 Đang giải nén vào thư mục: ${usrDir.absolutePath}")
                usrDir.mkdirs()

                var entryCount = 0
                FileInputStream(tempArchive).use { fis ->
                    BufferedInputStream(fis).use { bis ->
                        ZstdCompressorInputStream(bis).use { zstdStream ->
                            TarArchiveInputStream(zstdStream).use { tarIn ->
                                var entry = tarIn.nextEntry
                                while (entry != null) {
                                    entryCount++
                                    val cleanName = entry.name.removePrefix("clang-toolchain/").removePrefix("./")
                                    val destFile = File(usrDir, cleanName)

                                    if (entry.isSymbolicLink) {
                                        destFile.parentFile?.mkdirs()
                                        // XÓA TRIỆT ĐỂ: Xóa symlink hoặc thư mục cũ tránh lỗi EEXIST
                                        try {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                Files.deleteIfExists(destFile.toPath())
                                            } else {
                                                if (destFile.isDirectory) destFile.deleteRecursively() else destFile.delete()
                                            }
                                        } catch (_: Exception) {}

                                        try {
                                            Os.symlink(entry.linkName, destFile.absolutePath)
                                        } catch (e: Exception) {
                                            AppLog.w("TOOLCHAIN", "Lỗi tạo symlink ${entry.linkName} -> ${destFile.name}: ${e.message}")
                                        }
                                    } else if (entry.isDirectory) {
                                        destFile.mkdirs()
                                    } else {
                                        destFile.parentFile?.mkdirs()
                                        FileOutputStream(destFile).use { output ->
                                            tarIn.copyTo(output)
                                        }
                                        if (destFile.parentFile?.name == "bin") {
                                            destFile.setExecutable(true, false)
                                        }
                                    }
                                    entry = tarIn.nextEntry
                                }
                            }
                        }
                    }
                }
                AppLog.i("TOOLCHAIN", "✅ Đã giải nén $entryCount entries!")

                // Cấp quyền thực thi toàn bộ thư mục bin
                File(usrDir, "bin").listFiles()?.forEach { it.setExecutable(true, false) }
                AppLog.d("TOOLCHAIN", "🔑 Đã cấp quyền executable cho các file trong usr/bin")

                // FIX LỖI CLANG RESOURCE-DIR / STDDEF.H THIẾU
                val clangResourceBase = File(usrDir, "lib/clang")
                if (clangResourceBase.exists()) {
                    val clang21Dir = File(clangResourceBase, "21")
                    if (!clang21Dir.exists()) {
                        // Tìm thư mục version thực tế (ví dụ: "latest", "21.1.8", ...)
                        val targetDir = clangResourceBase.listFiles()?.firstOrNull { it.isDirectory && it.name != "21" }
                        if (targetDir != null) {
                            try {
                                AppLog.i("TOOLCHAIN", "🔗 Tạo symlink sửa lỗi: 21 -> ${targetDir.name}")
                                Os.symlink(targetDir.name, clang21Dir.absolutePath)
                            } catch (e: Exception) {
                                AppLog.w("TOOLCHAIN", "Không thể symlink 21: ${e.message}")
                            }
                        }
                    }
                }
                
                // FIX LỖI LINKER: libclang_rt.builtins.a
                val clang21LibDir = File(usrDir, "lib/clang/21/lib")
                if (clang21LibDir.exists()) {
                    // 1. Tạo thư mục mà Clang 21 đòi hỏi
                    val targetExpectedDir = File(clang21LibDir, "aarch64-unknown-linux-android24")
                    targetExpectedDir.mkdirs()

                    // 2. Tìm file builtins thực tế trong usrDir
                    val foundBuiltins = usrDir.walkTopDown().firstOrNull { 
                        it.name.contains("builtins") && it.name.endsWith(".a") 
                    }

                    if (foundBuiltins != null) {
                        val destBuiltins = File(targetExpectedDir, "libclang_rt.builtins.a")
                        if (!destBuiltins.exists()) {
                            try {
                                Os.symlink(foundBuiltins.absolutePath, destBuiltins.absolutePath)
                                AppLog.i("TOOLCHAIN", "🔗 Đã symlink builtins: ${foundBuiltins.name} -> ${destBuiltins.absolutePath}")
                            } catch (e: Exception) {
                                foundBuiltins.copyTo(destBuiltins, true)
                            }
                        }
                    } else {
                        AppLog.w("TOOLCHAIN", "⚠️ Không tìm thấy file builtins .a nào trong toolchain!")
                    }
                }

                // TỰ ĐỘNG FIX LUÔN LIBZSTD.SO.1 VÀ LIBFFI.SO
                val libDir = File(usrDir, "lib")
                if (libDir.exists()) {
                    val libZstd = File(libDir, "libzstd.so")
                    val libZstd1 = File(libDir, "libzstd.so.1")
                    if (libZstd.exists() && !libZstd1.exists()) {
                        try { Os.symlink("libzstd.so", libZstd1.absolutePath) } catch (_: Exception) { libZstd.copyTo(libZstd1, true) }
                    }

                    val libFfi = File(libDir, "libffi.so")
                    val candidateFfi = libDir.listFiles()?.firstOrNull { it.name.startsWith("libffi.so") }
                    if (candidateFfi != null && !libFfi.exists()) {
                        try { Os.symlink(candidateFfi.name, libFfi.absolutePath) } catch (_: Exception) {}
                    }
                }

                // Xóa file nén tạm sau khi hoàn tất
                tempArchive.delete()
                AppLog.d("TOOLCHAIN", "🗑️ Đã xóa temp_toolchain.tar.zst")

                // ================= BƯỚC 3: HOÀN TẤT =================
                AppLog.i("TOOLCHAIN", "🎉 Cài đặt Clang Toolchain hoàn tất 100%!")
                notifyStatus("Cài đặt hoàn tất!")
                notifyComplete()

                notifyBuilder.setContentTitle("Hoàn tất!")
                    .setContentText("Trình biên dịch Clang đã sẵn sàng hoạt động.")
                    .setProgress(0, 0, false)
                    .setOngoing(false)
                notifyManager.notify(NOTIFICATION_ID, notifyBuilder.build())

            } catch (e: Exception) {
                tempArchive.delete()
                AppLog.e("TOOLCHAIN", "💥 Lỗi khi cài đặt Toolchain: ${e.message}", e)

                val errorMsg = "Lỗi cài đặt: ${e.message}"
                notifyError(errorMsg)

                notifyBuilder.setContentTitle("Lỗi cài đặt")
                    .setContentText(errorMsg)
                    .setProgress(0, 0, false)
                    .setOngoing(false)
                notifyManager.notify(NOTIFICATION_ID, notifyBuilder.build())
            }
        }.start()
    }

    private fun notifyStatus(status: String) {
        val targets: List<(String) -> Unit>
        synchronized(lock) {
            currentStatus = status
            targets = statusListeners.toList()
        }
        mainHandler.post {
            targets.forEach { it(status) }
        }
    }

    private fun notifyComplete() {
        val targets: List<() -> Unit>
        synchronized(lock) {
            isInstalling = false
            targets = completeListeners.toList()
            statusListeners.clear()
            completeListeners.clear()
        }
        mainHandler.post {
            targets.forEach { it() }
        }
    }

    private fun notifyError(errorMsg: String) {
        val targets: List<(String) -> Unit>
        synchronized(lock) {
            isInstalling = false
            currentStatus = errorMsg
            targets = statusListeners.toList()
            statusListeners.clear()
            completeListeners.clear()
        }
        mainHandler.post {
            targets.forEach { it(errorMsg) }
        }
    }

    private fun createNotificationChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cài đặt Toolchain",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }
}