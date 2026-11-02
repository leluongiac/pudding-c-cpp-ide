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
        com.example.mytexteditor.data.AppLog.i("MAIN_ACTIVITY", "🚀 MainActivity onCreate! filesDir=${filesDir.absolutePath}, cacheDir=${cacheDir.absolutePath}")
        
        // Ẩn thanh hệ thống (Full màn hình code cho đã mắt)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Giao diện Compose (EditorScreen sẽ tự kiểm tra và hiển thị tiến trình cài Clang nếu cần)
        setContent {
            MaterialTheme {
                EditorScreen()
            }
        }
    }
}