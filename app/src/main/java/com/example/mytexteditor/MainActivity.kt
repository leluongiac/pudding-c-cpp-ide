package com.example.mytexteditor

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.mytexteditor.data.ToolchainInstaller
import com.example.mytexteditor.ui.editor.EditorScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        com.example.mytexteditor.data.CodeRunner.filesDir = filesDir
        com.example.mytexteditor.data.CodeRunner.cacheDir = cacheDir
        
        // Ẩn thanh hệ thống (Full màn hình code cho đã mắt)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // 1. TỰ ĐỘNG CÀI ĐẶT CLANG CHẠY NGẦM KHI MỞ APP
        ToolchainInstaller.installIfNeeded(
            context = this,
            onStatusUpdate = { status: String ->
                Log.d("TOOLCHAIN", status)
            },
            onComplete = {
                Log.d("TOOLCHAIN", ">>> Trình biên dịch đã sẵn sàng <<<")
            }
        )

        // 2. Giao diện Compose của bro
        setContent {
            MaterialTheme {
                EditorScreen()
            }
        }
    }
}