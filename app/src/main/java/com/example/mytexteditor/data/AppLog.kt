package com.example.mytexteditor.data

import android.util.Log

object AppLog {
    private const val RUNTIME_TAG = "AndroidRuntime"
    private const val MAX_LOG_LENGTH = 3000

    fun d(tag: String, msg: String) {
        log(Log.DEBUG, tag, msg)
    }

    fun i(tag: String, msg: String) {
        log(Log.INFO, tag, msg)
    }

    fun w(tag: String, msg: String) {
        log(Log.WARN, tag, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        log(Log.ERROR, tag, msg, tr)
    }

    private fun log(priority: Int, tag: String, message: String, tr: Throwable? = null) {
        val fullMsg = if (tr != null) {
            "$message\n${Log.getStackTraceString(tr)}"
        } else {
            message
        }

        // Tách nhỏ nếu chuỗi dài để không bị Logcat cắt mất nội dung
        if (fullMsg.length <= MAX_LOG_LENGTH) {
            printLog(priority, tag, fullMsg)
        } else {
            var i = 0
            val len = fullMsg.length
            var chunkIndex = 1
            val totalChunks = (len + MAX_LOG_LENGTH - 1) / MAX_LOG_LENGTH
            while (i < len) {
                val end = (i + MAX_LOG_LENGTH).coerceAtMost(len)
                val part = fullMsg.substring(i, end)
                printLog(priority, tag, "[$chunkIndex/$totalChunks] $part")
                i = end
                chunkIndex++
            }
        }
    }

    private fun printLog(priority: Int, tag: String, text: String) {
        // 1. Log với tag cụ thể (ví dụ CLANG_ERROR, CLANG_RUNNER, ...)
        Log.println(priority, tag, text)
        // 2. Log với tag AndroidRuntime để lệnh `adb logcat -s AndroidRuntime` hiển thị 100%
        if (tag != RUNTIME_TAG) {
            Log.println(priority, RUNTIME_TAG, "[$tag] $text")
        }
    }
}
