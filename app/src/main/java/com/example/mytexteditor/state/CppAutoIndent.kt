package com.example.mytexteditor.state

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Xử lý Auto-Indent thông minh độc lập cho C/C++ trên Mobile Editor.
 * Hoạt động tức thì, không phụ thuộc vào clangd.
 */
object CppAutoIndent {

    private const val INDENT_UNIT = "    " // 4 spaces

    /**
     * Lấy chuỗi indent (khoảng trắng/tabs đầu dòng).
     */
    fun getIndent(line: String): String {
        val sb = StringBuilder()
        for (ch in line) {
            if (ch == ' ' || ch == '\t') {
                sb.append(ch)
            } else {
                break
            }
        }
        return sb.toString()
    }

    /**
     * Xử lý khi nhấn ENTER.
     */
    fun handleEnter(textFieldValue: TextFieldValue): TextFieldValue {
        val text = textFieldValue.text
        val selection = textFieldValue.selection
        val min = selection.min.coerceIn(0, text.length)
        val max = selection.max.coerceIn(0, text.length)

        // Xóa vùng đang chọn nếu có trước khi xuống dòng
        val baseText = if (min != max) {
            StringBuilder(text).delete(min, max).toString()
        } else {
            text
        }
        val cursor = min

        val lineStart = if (cursor == 0) 0 else (baseText.lastIndexOf('\n', cursor - 1) + 1)
        val lineEnd = baseText.indexOf('\n', cursor).let { if (it == -1) baseText.length else it }

        val lineBeforeCursor = baseText.substring(lineStart, cursor)
        val lineAfterCursor = baseText.substring(cursor, lineEnd)
        val currentIndent = getIndent(lineBeforeCursor)

        val beforeTrimmed = lineBeforeCursor.trimEnd()
        val afterTrimmed = lineAfterCursor.trimStart()

        // 1. TÁCH CẶP NGOẶC { | }
        // Khi ngay trước con trỏ là '{' và ngay sau con trỏ là '}' (cho phép có khoảng trắng)
        if (beforeTrimmed.endsWith("{") && afterTrimmed.startsWith("}")) {
            val prefix = baseText.substring(0, lineStart) + beforeTrimmed
            val middleIndent = currentIndent + INDENT_UNIT
            val suffix = baseText.substring(lineEnd)
            val remainderAfterBrace = afterTrimmed.substring(1).trimStart()

            val closingLine = if (remainderAfterBrace.isNotEmpty()) {
                currentIndent + "}" + remainderAfterBrace
            } else {
                currentIndent + "}"
            }

            val newText = "$prefix\n$middleIndent\n$closingLine$suffix"
            val newCursorPos = prefix.length + 1 + middleIndent.length

            return TextFieldValue(
                text = newText,
                selection = TextRange(newCursorPos)
            )
        }

        // 2. TÍNH INDENT LỒNG NHAU (NESTED INDENTATION)
        var nextIndent = currentIndent

        // Kiểm tra dấu mở khối '{' hoặc nhãn hợp lệ (public:, private:, protected:, case ...:, default:)
        val isBlockOpener = beforeTrimmed.endsWith("{")
        val isLabel = isCppLabel(beforeTrimmed)

        if (isBlockOpener || isLabel) {
            nextIndent = currentIndent + INDENT_UNIT
        } else if (isSingleStatementHeader(beforeTrimmed)) {
            // Câu lệnh điều khiển đơn chưa có {}: if (...), else, for (...), while (...)
            nextIndent = currentIndent + INDENT_UNIT
        } else if (beforeTrimmed.endsWith(";") && !beforeTrimmed.contains("{")) {
            // Trả lề sau câu lệnh đơn: nếu dòng hiện tại kết thúc bằng ';' và dòng trước đó là single-statement header
            val previousHeaderIndent = findPreviousSingleStatementHeaderIndent(baseText, lineStart)
            if (previousHeaderIndent != null && currentIndent.length > previousHeaderIndent.length) {
                nextIndent = previousHeaderIndent
            }
        }

        // Cắt bỏ khoảng trắng dư ở đầu đoạn text bị đẩy xuống dòng mới
        val remainder = lineAfterCursor.trimStart()
        val textBefore = baseText.substring(0, cursor)
        val textAfter = baseText.substring(lineEnd)

        val newText = "$textBefore\n$nextIndent$remainder$textAfter"
        val newCursorPos = textBefore.length + 1 + nextIndent.length

        return TextFieldValue(
            text = newText,
            selection = TextRange(newCursorPos)
        )
    }

    /**
     * Xử lý khi gõ ký tự '}' (Closing Brace Auto-Unindent).
     */
    fun handleClosingBrace(textFieldValue: TextFieldValue): TextFieldValue {
        val text = textFieldValue.text
        val selection = textFieldValue.selection
        val min = selection.min.coerceIn(0, text.length)
        val max = selection.max.coerceIn(0, text.length)

        val baseText = if (min != max) {
            StringBuilder(text).delete(min, max).toString()
        } else {
            text
        }
        val cursor = min

        val lineStart = if (cursor == 0) 0 else (baseText.lastIndexOf('\n', cursor - 1) + 1)
        val lineBeforeCursor = baseText.substring(lineStart, cursor)

        // Chỉ auto-unindent nếu trước con trỏ trên dòng này CHỈ toàn khoảng trắng
        if (lineBeforeCursor.all { it == ' ' || it == '\t' }) {
            val matchingIndent = findMatchingOpeningBraceIndent(baseText, cursor)
            val newIndent = matchingIndent ?: run {
                val curIndent = getIndent(lineBeforeCursor)
                if (curIndent.length >= 4) curIndent.dropLast(4) else ""
            }

            val lineEnd = baseText.indexOf('\n', cursor).let { if (it == -1) baseText.length else it }
            val lineAfterCursor = baseText.substring(cursor, lineEnd)
            val remainder = lineAfterCursor.trimStart()

            val textBeforeLine = baseText.substring(0, lineStart)
            val textAfterLine = baseText.substring(lineEnd)

            val newText = "$textBeforeLine$newIndent}$remainder$textAfterLine"
            val newCursorPos = textBeforeLine.length + newIndent.length + 1

            return TextFieldValue(
                text = newText,
                selection = TextRange(newCursorPos)
            )
        }

        // Nếu trước con trỏ đã có ký tự khác, chèn '}' bình thường
        val newText = StringBuilder(baseText).insert(cursor, "}").toString()
        return TextFieldValue(
            text = newText,
            selection = TextRange(cursor + 1)
        )
    }

    /**
     * Tìm indent của dấu '{' mở tương ứng chưa đóng (bỏ qua string, comment).
     */
    fun findMatchingOpeningBraceIndent(text: String, beforeOffset: Int): String? {
        val safeLimit = beforeOffset.coerceIn(0, text.length)
        val openBraceStack = ArrayDeque<Int>() // lưu vị trí offset của các '{'

        var i = 0
        while (i < safeLimit) {
            val ch = text[i]

            // Bỏ qua line comment //
            if (ch == '/' && i + 1 < safeLimit && text[i + 1] == '/') {
                i += 2
                while (i < safeLimit && text[i] != '\n') i++
                continue
            }

            // Bỏ qua block comment /* ... */
            if (ch == '/' && i + 1 < safeLimit && text[i + 1] == '*') {
                i += 2
                while (i + 1 < safeLimit && !(text[i] == '*' && text[i + 1] == '/')) i++
                i += 2
                continue
            }

            // Bỏ qua string literal "..."
            if (ch == '"') {
                i++
                while (i < safeLimit && text[i] != '"') {
                    if (text[i] == '\\' && i + 1 < safeLimit) i += 2 else i++
                }
                i++
                continue
            }

            // Bỏ qua char literal '...'
            if (ch == '\'') {
                i++
                while (i < safeLimit && text[i] != '\'') {
                    if (text[i] == '\\' && i + 1 < safeLimit) i += 2 else i++
                }
                i++
                continue
            }

            if (ch == '{') {
                openBraceStack.addLast(i)
            } else if (ch == '}') {
                if (openBraceStack.isNotEmpty()) {
                    openBraceStack.removeLast()
                }
            }
            i++
        }

        if (openBraceStack.isNotEmpty()) {
            val matchingBraceOffset = openBraceStack.last()
            val braceLineStart = if (matchingBraceOffset == 0) 0 else (text.lastIndexOf('\n', matchingBraceOffset - 1) + 1)
            val braceLineEnd = text.indexOf('\n', matchingBraceOffset).let { if (it == -1) text.length else it }
            val braceLine = text.substring(braceLineStart, braceLineEnd)
            return getIndent(braceLine)
        }

        return null
    }

    private fun isCppLabel(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed == "public:" || trimmed == "private:" || trimmed == "protected:" || trimmed == "default:") {
            return true
        }
        if (trimmed.startsWith("case ") && trimmed.endsWith(":")) {
            return true
        }
        return false
    }

    private fun isSingleStatementHeader(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.endsWith(";") || trimmed.endsWith("{") || trimmed.endsWith("}")) {
            return false
        }
        val pattern = Regex("""^(if\s*\(.*?\)|else\s+if\s*\(.*?\)|else|for\s*\(.*?\)|while\s*\(.*?\))$""")
        return trimmed.matches(pattern)
    }

    private fun findPreviousSingleStatementHeaderIndent(text: String, currentLineStart: Int): String? {
        var searchPos = currentLineStart - 1
        while (searchPos >= 0) {
            val prevLineEnd = searchPos
            val prevLineStart = if (prevLineEnd == 0) 0 else (text.lastIndexOf('\n', prevLineEnd - 1) + 1)
            val line = text.substring(prevLineStart, prevLineEnd).trim()

            if (line.isNotEmpty() && !line.startsWith("//") && !line.startsWith("/*") && !line.startsWith("*")) {
                if (isSingleStatementHeader(line)) {
                    val fullPrevLine = text.substring(prevLineStart, prevLineEnd)
                    return getIndent(fullPrevLine)
                }
                break
            }
            searchPos = prevLineStart - 1
        }
        return null
    }
}
