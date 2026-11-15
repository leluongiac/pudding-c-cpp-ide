package com.example.mytexteditor.lsp

/**
 * Tiện ích chuyển đổi 2 chiều giữa UTF-16 Char offset trong Compose TextFieldValue
 * và LSP Position(line, character) theo chuẩn UTF-16 code units của LSP 3.17.
 * Đảm bảo độ chính xác tuyệt đối khi có comment tiếng Việt có dấu hoặc ký tự đa byte.
 */
object LspPositionUtils {

    /**
     * Chuyển đổi từ UTF-16 Char offset (Compose index) sang LSP Position (0-indexed line, character).
     */
    fun offsetToPosition(text: String, offset: Int): LspPosition {
        val safeOffset = offset.coerceIn(0, text.length)
        var line = 0
        var lineStart = 0

        for (i in 0 until safeOffset) {
            if (text[i] == '\n') {
                line++
                lineStart = i + 1
            }
        }
        val character = safeOffset - lineStart
        return LspPosition(line = line, character = character)
    }

    /**
     * Chuyển đổi từ LSP Position sang UTF-16 Char offset trong văn bản Compose.
     */
    fun positionToOffset(text: String, position: LspPosition): Int {
        var currentLine = 0
        var lineStart = 0

        for (i in text.indices) {
            if (currentLine == position.line) {
                // Đã tìm thấy dòng mục tiêu
                var lineEnd = text.indexOf('\n', lineStart).let { if (it == -1) text.length else it }
                if (lineEnd > lineStart && text[lineEnd - 1] == '\r') {
                    lineEnd--
                }
                val targetOffset = (lineStart + position.character).coerceIn(lineStart, lineEnd)
                return targetOffset
            }
            if (text[i] == '\n') {
                currentLine++
                lineStart = i + 1
            }
        }

        // Nếu position.line là dòng cuối cùng sau ký tự newline
        if (currentLine == position.line) {
            var lineEnd = text.length
            if (lineEnd > lineStart && text[lineEnd - 1] == '\r') {
                lineEnd--
            }
            val targetOffset = (lineStart + position.character).coerceIn(lineStart, lineEnd)
            return targetOffset
        }

        return text.length
    }

    /**
     * Làm sạch cú pháp Snippet LSP dạng ${1:arg}, $0... thành chuỗi văn bản thường hoặc placeholder sạch.
     */
    fun cleanSnippet(snippet: String): String {
        return snippet
            .replace(Regex("""\$\{\d+:?(.*?)\}"""), "$1")
            .replace(Regex("""\$\d+"""), "")
    }
}
