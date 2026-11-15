package com.example.mytexteditor.state

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.mytexteditor.data.AppLog
import com.example.mytexteditor.lsp.*
import com.example.mytexteditor.model.Direction
import com.example.mytexteditor.model.RightMode
import com.example.mytexteditor.syntax.AsyncSyntaxHighlighter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Stable
class EditorState {
    val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    var isZenMode by mutableStateOf(false)
    var isWordWrap by mutableStateOf(false)
    var editorFontSize by mutableFloatStateOf(14f)
    var layoutResult: TextLayoutResult? = null
    val syntaxHighlighter = AsyncSyntaxHighlighter()

    var textFieldValue by mutableStateOf(
        TextFieldValue(
            text = """
                #include <iostream>
                #include <vector>
                #include <string>

                using namespace std;

                // Welcome to Tree-sitter C++ Editor!
                int main() {
                    vector<string> items = {"Hello", "from", "Tree-sitter", "C++!"};

                    for (const auto& item : items) {
                        cout << item << " ";
                    }
                    cout << endl;
                    return 0;
                }
            """.trimIndent(),
            selection = TextRange(0)
        )
    )
    var currentUri by mutableStateOf<Uri?>(null)
    var fileName by mutableStateOf("main.cpp")
    var isModified by mutableStateOf(false)

    var isSelectMode by mutableStateOf(false)
    var rightMode by mutableStateOf(RightMode.TYPING)
    var isShiftMode by mutableStateOf(false)
    var isSymbolMode by mutableStateOf(false)
    var isCtrlPressed by mutableStateOf(false)

    // ================= INTELLISENSE STATE =================
    var isCompletionVisible by mutableStateOf(false)
    var rawCompletionItems by mutableStateOf<List<CompletionItem>>(emptyList())
    var completionItems by mutableStateOf<List<CompletionItem>>(emptyList())
    var completionSelectedIndex by mutableIntStateOf(0)
    var completionTokenStart by mutableIntStateOf(0)

    var isSignatureHelpVisible by mutableStateOf(false)
    var signatureHelpData by mutableStateOf<SignatureHelp?>(null)
    private var completionJob: Job? = null
    private var signatureJob: Job? = null
    private var lastQueriedPrefix = ""
    private var lastQueriedTokenStart = -1
    private var isServerCompletionIncomplete = true
    private var completionRequestId = 0

    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()

    init {
        syntaxHighlighter.updateText(textFieldValue.text)
    }

    fun initLsp(context: Context) {
        ClangdManager.start(context, textFieldValue.text)
        ClangdManager.openDocument(textFieldValue.text)
    }

    fun updateTextFieldValue(newValue: TextFieldValue, markModified: Boolean = true) {
        val textChanged = newValue.text != textFieldValue.text
        val selectionChanged = newValue.selection != textFieldValue.selection
        if (textChanged) {
            if (markModified) isModified = true
            syntaxHighlighter.updateText(newValue.text)
            ClangdManager.onTextChanged(newValue.text)
        }
        textFieldValue = newValue

        if (selectionChanged && !textChanged) {
            if (!newValue.selection.collapsed) {
                isCompletionVisible = false
                completionJob?.cancel()
            } else if (isCompletionVisible) {
                val cursor = newValue.selection.end
                if (cursor < completionTokenStart || cursor > newValue.text.length) {
                    isCompletionVisible = false
                    completionJob?.cancel()
                } else {
                    val prefix = newValue.text.substring(completionTokenStart, cursor)
                    filterCachedCompletions(prefix)
                }
            }
        }

        // Cập nhật trạng thái Signature Help theo vị trí con trỏ mới
        checkAndUpdateSignatureHelpVisibility(newValue.text, newValue.selection.end)
    }

    fun pushUndoSnapshot(snapshot: TextFieldValue) {
        if (undoStack.isNotEmpty()) {
            val last = undoStack.last()
            if (last.text.length == snapshot.text.length &&
                last.selection == snapshot.selection &&
                last.text == snapshot.text
            ) {
                return
            }
        }
        undoStack.addLast(snapshot)
        if (undoStack.size > 100) {
            undoStack.removeFirst()
        }
        redoStack.clear()
    }

    fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
    }

    // ================= XỬ LÝ PHÍM & SHORTCUTS =================
    fun onKey(key: String, context: Context) {
        when (key) {
            "SHIFT" -> {
                isShiftMode = !isShiftMode
                isSelectMode = isShiftMode
                return
            }

            "?123", "123" -> {
                isSymbolMode = true
                return
            }

            "ABC" -> {
                isSymbolMode = false
                return
            }

            "CTRL" -> {
                isCtrlPressed = !isCtrlPressed
                return
            }
        }

        // --- Handle CTRL Shortcuts ---
        if (isCtrlPressed) {
            val lower = key.lowercase()
            when (lower) {
                "z" -> {
                    // Ctrl + Z: Undo
                    if (undoStack.isNotEmpty()) {
                        redoStack.addLast(textFieldValue)
                        updateTextFieldValue(undoStack.removeLast())
                        isCompletionVisible = false
                        Toast.makeText(context, "Hoàn tác (Undo)", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Không còn thao tác để hoàn tác", Toast.LENGTH_SHORT).show()
                    }
                    isCtrlPressed = false
                    return
                }

                "u" -> {
                    // Ctrl + U: Redo
                    if (redoStack.isNotEmpty()) {
                        undoStack.addLast(textFieldValue)
                        updateTextFieldValue(redoStack.removeLast())
                        isCompletionVisible = false
                        Toast.makeText(context, "Làm lại (Redo)", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Không còn thao tác để làm lại", Toast.LENGTH_SHORT).show()
                    }
                    isCtrlPressed = false
                    return
                }

                "c" -> {
                    // Ctrl + C: Copy
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val text = textFieldValue.text
                    val selection = textFieldValue.selection
                    val textToCopy = if (!selection.collapsed) {
                        text.substring(selection.min, selection.max)
                    } else {
                        val startOfLine = text.lastIndexOf('\n', (selection.start - 1).coerceAtLeast(0))
                            .let { if (it == -1) 0 else it + 1 }
                        val endOfLine = text.indexOf('\n', selection.start).let { if (it == -1) text.length else it }
                        text.substring(startOfLine, endOfLine)
                    }
                    if (textToCopy.isNotEmpty()) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("text", textToCopy))
                        Toast.makeText(context, "Đã chép (Copy)", Toast.LENGTH_SHORT).show()
                    }
                    isCtrlPressed = false
                    return
                }

                "v" -> {
                    // Ctrl + V: Paste
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = clipboard.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val pasteText = clip.getItemAt(0).text?.toString() ?: ""
                        if (pasteText.isNotEmpty()) {
                            pushUndoSnapshot(textFieldValue)
                            val text = textFieldValue.text
                            val selection = textFieldValue.selection
                            val min = selection.min
                            val max = selection.max
                            val newText = StringBuilder(text).replace(min, max, pasteText).toString()
                            updateTextFieldValue(TextFieldValue(newText, TextRange(min + pasteText.length)))
                            isCompletionVisible = false
                            Toast.makeText(context, "Đã dán (Paste)", Toast.LENGTH_SHORT).show()
                        }
                    }
                    isCtrlPressed = false
                    return
                }

                "k" -> {
                    // Ctrl + K: Cut
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val text = textFieldValue.text
                    val selection = textFieldValue.selection
                    if (!selection.collapsed) {
                        val min = selection.min
                        val max = selection.max
                        val textToCut = text.substring(min, max)
                        clipboard.setPrimaryClip(ClipData.newPlainText("text", textToCut))
                        pushUndoSnapshot(textFieldValue)
                        val newText = StringBuilder(text).delete(min, max).toString()
                        updateTextFieldValue(TextFieldValue(newText, TextRange(min)))
                        Toast.makeText(context, "Đã cắt (Cut)", Toast.LENGTH_SHORT).show()
                    } else {
                        val startOfLine = text.lastIndexOf('\n', (selection.start - 1).coerceAtLeast(0))
                            .let { if (it == -1) 0 else it + 1 }
                        val endOfLine = text.indexOf('\n', selection.start)
                            .let { if (it == -1) text.length else (it + 1).coerceAtMost(text.length) }
                        if (startOfLine < text.length) {
                            val textToCut = text.substring(startOfLine, endOfLine)
                            clipboard.setPrimaryClip(ClipData.newPlainText("text", textToCut))
                            pushUndoSnapshot(textFieldValue)
                            val newText = StringBuilder(text).delete(startOfLine, endOfLine).toString()
                            updateTextFieldValue(
                                TextFieldValue(newText, TextRange(startOfLine.coerceAtMost(newText.length)))
                            )
                            Toast.makeText(context, "Đã cắt dòng (Cut)", Toast.LENGTH_SHORT).show()
                        }
                    }
                    isCompletionVisible = false
                    isCtrlPressed = false
                    return
                }

                else -> {
                    isCtrlPressed = false
                }
            }
        }

        // ================= XỬ LÝ PHÍM ENTER =================
        if (key == "ENTER" || key == "ENT") {
            // NẾU COMPLETION POPUP ĐANG MỞ -> ƯU TIÊN ACCEPT ITEM ĐANG CHỌN
            if (isCompletionVisible && completionItems.isNotEmpty()) {
                val selectedItem = completionItems.getOrNull(completionSelectedIndex) ?: completionItems.first()
                acceptCompletion(selectedItem)
                return
            }

            // NẾU KHÔNG CÓ POPUP -> AUTO-INDENT THÔNG MINH
            pushUndoSnapshot(textFieldValue)
            val newIndentedValue = CppAutoIndent.handleEnter(textFieldValue)
            updateTextFieldValue(newIndentedValue)
            isCompletionVisible = false
            return
        }

        // ================= AUTO-CLOSE PAIR '{' =================
        if (key == "{") {
            pushUndoSnapshot(textFieldValue)
            val text = textFieldValue.text
            val selection = textFieldValue.selection
            if (selection.collapsed) {
                val pos = selection.start
                val newText = StringBuilder(text).insert(pos, "{}").toString()
                updateTextFieldValue(TextFieldValue(newText, TextRange(pos + 1)))
            } else {
                val min = selection.min
                val max = selection.max
                val selected = text.substring(min, max)
                val newText = StringBuilder(text).replace(min, max, "{$selected}").toString()
                updateTextFieldValue(TextFieldValue(newText, TextRange(min + 1, max + 1)))
            }
            isCompletionVisible = false
            return
        }

        // ================= AUTO-CLOSE PAIR '(' =================
        if (key == "(") {
            pushUndoSnapshot(textFieldValue)
            val text = textFieldValue.text
            val selection = textFieldValue.selection
            if (selection.collapsed) {
                val pos = selection.start
                val newText = StringBuilder(text).insert(pos, "()").toString()
                updateTextFieldValue(TextFieldValue(newText, TextRange(pos + 1)))
                triggerSignatureHelp(newText, pos + 1)
            } else {
                val min = selection.min
                val max = selection.max
                val selected = text.substring(min, max)
                val newText = StringBuilder(text).replace(min, max, "($selected)").toString()
                updateTextFieldValue(TextFieldValue(newText, TextRange(min + 1, max + 1)))
            }
            isCompletionVisible = false
            return
        }

        // ================= AUTO-CLOSE PAIR '[' =================
        if (key == "[") {
            pushUndoSnapshot(textFieldValue)
            val text = textFieldValue.text
            val selection = textFieldValue.selection
            if (selection.collapsed) {
                val pos = selection.start
                val newText = StringBuilder(text).insert(pos, "[]").toString()
                updateTextFieldValue(TextFieldValue(newText, TextRange(pos + 1)))
            } else {
                val min = selection.min
                val max = selection.max
                val selected = text.substring(min, max)
                val newText = StringBuilder(text).replace(min, max, "[$selected]").toString()
                updateTextFieldValue(TextFieldValue(newText, TextRange(min + 1, max + 1)))
            }
            isCompletionVisible = false
            return
        }

        // ================= SKIP CLOSING CHAR NẾU ĐÃ CÓ SẴN NGAY SAU CON TRỎ =================
        if (key in listOf("}", ")", "]")) {
            val text = textFieldValue.text
            val selection = textFieldValue.selection
            if (selection.collapsed && selection.start < text.length && text[selection.start].toString() == key) {
                val newPos = selection.start + 1
                updateTextFieldValue(TextFieldValue(text, TextRange(newPos)))
                if (key == ")") isSignatureHelpVisible = false
                isCompletionVisible = false
                return
            }
        }

        // ================= XỬ LÝ PHÍM '}' (AUTO-UNINDENT) =================
        if (key == "}") {
            pushUndoSnapshot(textFieldValue)
            val newBraceValue = CppAutoIndent.handleClosingBrace(textFieldValue)
            updateTextFieldValue(newBraceValue)
            isCompletionVisible = false
            return
        }

        // ================= PHÍM BACKSPACE =================
        if (key == "BACKSPACE" || key == "⌫") {
            pushUndoSnapshot(textFieldValue)
            val text = textFieldValue.text
            val selection = textFieldValue.selection

            if (selection.collapsed) {
                val start = selection.start
                if (start > 0) {
                    val isPair = start < text.length && (
                        (text[start - 1] == '{' && text[start] == '}') ||
                        (text[start - 1] == '(' && text[start] == ')') ||
                        (text[start - 1] == '[' && text[start] == ']') ||
                        (text[start - 1] == '"' && text[start] == '"') ||
                        (text[start - 1] == '\'' && text[start] == '\'')
                    )
                    val newText = if (isPair) {
                        StringBuilder(text).delete(start - 1, start + 1).toString()
                    } else {
                        StringBuilder(text).delete(start - 1, start).toString()
                    }
                    val newPos = start - 1
                    updateTextFieldValue(TextFieldValue(newText, TextRange(newPos)))
                    checkTriggerCompletion(newText, newPos)
                }
            } else {
                val min = selection.min
                val max = selection.max
                val newText = StringBuilder(text).delete(min, max).toString()
                updateTextFieldValue(TextFieldValue(newText, TextRange(min)))
                isCompletionVisible = false
            }
            return
        }

        // ================= CÁC PHÍM GÕ VĂN BẢN KHÁC =================
        if (key in listOf("SPACE", "TAB")) {
            pushUndoSnapshot(textFieldValue)
        }

        val text = textFieldValue.text
        val selection = textFieldValue.selection

        val insertText = when (key) {
            "SPACE" -> " "
            "TAB" -> "    "
            else -> {
                if (key.length == 1 && key.first().isLetter()) {
                    if (isShiftMode) key.uppercase() else key.lowercase()
                } else {
                    key
                }
            }
        }

        val min = selection.min
        val max = selection.max
        val newText = StringBuilder(text).replace(min, max, insertText).toString()
        val newCursor = min + insertText.length
        updateTextFieldValue(TextFieldValue(newText, TextRange(newCursor)))

        // Phản ứng IntelliSense theo ký tự vừa gõ
        when (insertText) {
            " ", ";" -> {
                isCompletionVisible = false
                completionJob?.cancel()
                rawCompletionItems = emptyList()
                completionItems = emptyList()
                if (insertText == ";") isSignatureHelpVisible = false
            }
            "(" -> {
                isCompletionVisible = false
                completionJob?.cancel()
                rawCompletionItems = emptyList()
                completionItems = emptyList()
                triggerSignatureHelp(newText, newCursor)
            }
            "," -> {
                isCompletionVisible = false
                completionJob?.cancel()
                rawCompletionItems = emptyList()
                completionItems = emptyList()
                triggerSignatureHelp(newText, newCursor)
            }
            ")" -> {
                isCompletionVisible = false
                completionJob?.cancel()
                rawCompletionItems = emptyList()
                completionItems = emptyList()
                isSignatureHelpVisible = false
            }
            else -> {
                checkTriggerCompletion(newText, newCursor)
            }
        }
    }

    // ================= ĐIỀU HƯỚNG BẰNG JOYSTICK =================
    fun onJoystickDirection(direction: Direction, haptic: HapticFeedback) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

        // NẾU COMPLETION POPUP ĐANG HIỂN THỊ -> ĐIỀU HƯỚNG TRONG POPUP
        if (isCompletionVisible && completionItems.isNotEmpty()) {
            when (direction) {
                Direction.UP -> {
                    completionSelectedIndex = (completionSelectedIndex - 1).coerceAtLeast(0)
                    return
                }
                Direction.DOWN -> {
                    completionSelectedIndex = (completionSelectedIndex + 1).coerceAtMost(completionItems.size - 1)
                    return
                }
                Direction.LEFT, Direction.RIGHT -> {
                    // Bỏ qua trôi nhẹ sang trái/phải để không tắt popup hay chạy con trỏ chữ
                    return
                }
            }
        }

        // NẾU KHÔNG CÓ COMPLETION -> DI CHUYỂN CON TRỎ TEXT
        val text = textFieldValue.text
        val selection = textFieldValue.selection
        val currentCursor = selection.end
        val anchor = if (isSelectMode) selection.start else currentCursor

        var newCursor = currentCursor
        when (direction) {
            Direction.RIGHT -> newCursor = (currentCursor + 1).coerceAtMost(text.length)
            Direction.LEFT -> newCursor = (currentCursor - 1).coerceAtLeast(0)
            Direction.DOWN, Direction.UP -> {
                val layout = layoutResult
                if (layout == null) {
                    newCursor = if (direction == Direction.UP) {
                        getFallbackUp(text, currentCursor)
                    } else {
                        getFallbackDown(text, currentCursor)
                    }
                } else {
                    val safeCursor = currentCursor.coerceIn(0, text.length)
                    val currentLine = layout.getLineForOffset(safeCursor)
                    val targetLine = if (direction == Direction.UP) currentLine - 1 else currentLine + 1
                    if (targetLine in 0 until layout.lineCount) {
                        val x = layout.getCursorRect(safeCursor).left
                        val y = (layout.getLineTop(targetLine) + layout.getLineBottom(targetLine)) / 2f
                        newCursor = layout.getOffsetForPosition(Offset(x, y))
                    } else {
                        newCursor = if (direction == Direction.UP) 0 else text.length
                    }
                }
            }
        }

        val newSelection = if (isSelectMode) {
            TextRange(anchor, newCursor)
        } else {
            TextRange(newCursor)
        }

        textFieldValue = textFieldValue.copy(selection = newSelection)
        checkAndUpdateSignatureHelpVisibility(text, newCursor)
    }

    // ================= LOGIC COMPLETION & SIGNATURE HELP =================

    private fun checkTriggerCompletion(text: String, cursor: Int) {
        val safeCursor = cursor.coerceIn(0, text.length)
        if (safeCursor == 0 || !textFieldValue.selection.collapsed) {
            isCompletionVisible = false
            completionJob?.cancel()
            return
        }

        // Tìm token prefix trước con trỏ
        var tokenStart = safeCursor
        while (tokenStart > 0) {
            val ch = text[tokenStart - 1]
            if (ch.isLetterOrDigit() || ch == '_') {
                tokenStart--
            } else {
                break
            }
        }

        val prefix = text.substring(tokenStart, safeCursor)
        completionTokenStart = tokenStart

        // Kiểm tra trigger char: ., ->, ::, <
        var isTriggerChar = false
        if (tokenStart > 0) {
            val prevChar = text[tokenStart - 1]
            if (prevChar == '.' || prevChar == '<') {
                isTriggerChar = true
            } else if (prevChar == '>' && tokenStart >= 2 && text[tokenStart - 2] == '-') {
                isTriggerChar = true
            } else if (prevChar == ':' && tokenStart >= 2 && text[tokenStart - 2] == ':') {
                isTriggerChar = true
            }
        }

        if (!isTriggerChar && prefix.isEmpty()) {
            isCompletionVisible = false
            completionJob?.cancel()
            rawCompletionItems = emptyList()
            completionItems = emptyList()
            lastQueriedPrefix = ""
            lastQueriedTokenStart = -1
            return
        }

        // 1. Lọc nhanh bằng cache trước để mượt mà không nhấp nháy
        if (rawCompletionItems.isNotEmpty()) {
            filterCachedCompletions(prefix)
        }

        // 2. Tránh gửi yêu cầu dư thừa nếu prefix không thay đổi ngữ nghĩa và cache đã đầy đủ
        val canReuseCache = !isTriggerChar &&
                tokenStart == lastQueriedTokenStart &&
                rawCompletionItems.isNotEmpty() &&
                !isServerCompletionIncomplete &&
                prefix.startsWith(lastQueriedPrefix)

        if (canReuseCache) {
            return
        }

        // 3. Debounce yêu cầu tới clangd (khoảng 110ms)
        val currentReqId = ++completionRequestId
        completionJob?.cancel()
        completionJob = coroutineScope.launch {
            delay(110) // Debounce 100-120 ms

            // Đảm bảo sau delay con trỏ và text vẫn ở trạng thái yêu cầu
            if (!textFieldValue.selection.collapsed ||
                textFieldValue.selection.end != safeCursor ||
                textFieldValue.text != text
            ) {
                return@launch
            }

            val position = LspPositionUtils.offsetToPosition(text, safeCursor)
            val result = ClangdManager.requestCompletion(position)

            // Kiểm tra stale response: text hoặc con trỏ đã thay đổi trong lúc chờ?
            if (currentReqId != completionRequestId ||
                textFieldValue.text != text ||
                textFieldValue.selection.end != safeCursor ||
                !textFieldValue.selection.collapsed
            ) {
                return@launch
            }

            if (result.items.isNotEmpty()) {
                rawCompletionItems = result.items
                isServerCompletionIncomplete = result.isIncomplete
                lastQueriedPrefix = prefix
                lastQueriedTokenStart = tokenStart
                filterCachedCompletions(prefix)
            } else if (rawCompletionItems.isEmpty()) {
                isCompletionVisible = false
            }
        }
    }

    private fun filterCachedCompletions(prefix: String) {
        if (prefix.isEmpty()) {
            completionItems = rawCompletionItems
            completionSelectedIndex = 0
            isCompletionVisible = rawCompletionItems.isNotEmpty()
            return
        }

        val filtered = rawCompletionItems.filter { item ->
            item.filterText?.contains(prefix, ignoreCase = true) == true ||
            item.label.contains(prefix, ignoreCase = true)
        }

        if (filtered.isNotEmpty()) {
            completionItems = filtered
            completionSelectedIndex = completionSelectedIndex.coerceIn(0, filtered.size - 1)
            isCompletionVisible = true
        } else {
            isCompletionVisible = false
        }
    }

    fun acceptCompletion(item: CompletionItem) {
        completionJob?.cancel()
        pushUndoSnapshot(textFieldValue)
        val text = textFieldValue.text
        val cursor = textFieldValue.selection.end.coerceIn(0, text.length)
        val tokenStart = completionTokenStart.coerceIn(0, cursor)

        val cleanInsert = LspPositionUtils.cleanSnippet(item.insertText ?: item.label)
        val isCallable = item.kind in listOf(
            CompletionItemKind.Function,
            CompletionItemKind.Method,
            CompletionItemKind.Constructor
        )

        val sb = StringBuilder(text)
        sb.replace(tokenStart, cursor, cleanInsert)
        var newCursor = tokenStart + cleanInsert.length

        // Nếu là hàm / method: tự động chèn () và đặt con trỏ vào giữa nếu chưa có
        if (isCallable) {
            val hasOpenParenAfter = newCursor < sb.length && sb[newCursor] == '('
            if (!hasOpenParenAfter && !cleanInsert.endsWith("()")) {
                sb.insert(newCursor, "()")
                newCursor += 1 // Đặt con trỏ ở giữa '(' và ')'
            }
        }

        val newText = sb.toString()
        updateTextFieldValue(TextFieldValue(newText, TextRange(newCursor)))
        isCompletionVisible = false
        rawCompletionItems = emptyList()
        completionItems = emptyList()
        lastQueriedPrefix = ""
        lastQueriedTokenStart = -1

        if (isCallable) {
            triggerSignatureHelp(newText, newCursor)
        }
    }

    fun triggerSignatureHelp(text: String, cursor: Int) {
        signatureJob?.cancel()
        signatureJob = coroutineScope.launch {
            val safeCursor = cursor.coerceIn(0, text.length)
            val position = LspPositionUtils.offsetToPosition(text, safeCursor)
            val help = ClangdManager.requestSignatureHelp(position)

            // Đảm bảo không ghi đè dữ liệu cũ nếu văn bản hoặc con trỏ đã thay đổi
            if (textFieldValue.text != text || textFieldValue.selection.end != safeCursor) {
                return@launch
            }

            if (help != null && help.signatures.isNotEmpty()) {
                signatureHelpData = help
                checkAndUpdateSignatureHelpVisibility(text, safeCursor)
            } else {
                isSignatureHelpVisible = false
            }
        }
    }

    private fun checkAndUpdateSignatureHelpVisibility(text: String, cursor: Int) {
        val safeCursor = cursor.coerceIn(0, text.length)
        if (signatureHelpData == null || signatureHelpData?.signatures.isNullOrEmpty()) {
            isSignatureHelpVisible = false
            return
        }

        // Tìm dấu '(' mở bao ngoài con trỏ
        var parenDepth = 0
        var openParenIndex = -1
        var lastCommaIndex = -1

        var i = safeCursor - 1
        while (i >= 0) {
            val ch = text[i]
            if (ch == ')') {
                parenDepth++
            } else if (ch == '(') {
                if (parenDepth > 0) {
                    parenDepth--
                } else {
                    openParenIndex = i
                    break
                }
            } else if (ch == ',' && parenDepth == 0 && lastCommaIndex == -1) {
                lastCommaIndex = i
            } else if (ch == '\n' || ch == ';') {
                break
            }
            i--
        }

        if (openParenIndex == -1) {
            isSignatureHelpVisible = false
            return
        }

        // Kiểm tra xem vị trí con trỏ có còn nằm trước dấu ')' đóng không
        val closeParenIndex = text.indexOf(')', openParenIndex)
        if (closeParenIndex != -1 && safeCursor > closeParenIndex) {
            isSignatureHelpVisible = false
            return
        }

        // Đoạn text của đối số hiện tại từ '(' hoặc ',' gần nhất tới con trỏ
        val argStart = (lastCommaIndex.takeIf { it != -1 } ?: openParenIndex) + 1
        val currentArgText = if (argStart <= safeCursor) text.substring(argStart, safeCursor) else ""

        // QUY TẮC TINH TẾ:
        // - Trạng thái nhắc nhập: ngay sau '(' hoặc ',' (chỉ chứa khoảng trắng) -> HIỆN POPUP
        // - Trạng thái đang gõ bên trong đối số (đã có ký tự non-whitespace) -> ẨN POPUP để không che code
        if (currentArgText.isBlank()) {
            // Kiểm tra diagnostics nếu có lỗi cú pháp sai loại tại đây
            val hasError = ClangdManager.currentDiagnostics.value.any { diag ->
                diag.severity == DiagnosticSeverity.Error &&
                (diag.message.contains("no matching function", ignoreCase = true) ||
                 diag.message.contains("too many arguments", ignoreCase = true))
            }
            isSignatureHelpVisible = !hasError
        } else {
            isSignatureHelpVisible = false
        }
    }

    private fun getFallbackUp(text: String, cursor: Int): Int {
        val safeCursor = cursor.coerceIn(0, text.length)
        val currentLineStart = if (safeCursor == 0) 0 else {
            text.lastIndexOf('\n', safeCursor - 1) + 1
        }
        if (currentLineStart == 0) return safeCursor

        val previousLineEnd = currentLineStart - 1
        val previousLineStart = if (previousLineEnd == 0) 0 else {
            text.lastIndexOf('\n', previousLineEnd - 1) + 1
        }
        val column = safeCursor - currentLineStart
        return (previousLineStart + column).coerceAtMost(previousLineEnd)
    }

    private fun getFallbackDown(text: String, cursor: Int): Int {
        val safeCursor = cursor.coerceIn(0, text.length)
        val currentLineStart = if (safeCursor == 0) 0 else {
            text.lastIndexOf('\n', safeCursor - 1) + 1
        }
        val currentLineEnd = text.indexOf('\n', safeCursor).let {
            if (it == -1) text.length else it
        }
        if (currentLineEnd == text.length) return safeCursor

        val nextLineStart = currentLineEnd + 1
        val nextLineEnd = text.indexOf('\n', nextLineStart).let {
            if (it == -1) text.length else it
        }
        val column = safeCursor - currentLineStart
        return (nextLineStart + column).coerceAtMost(nextLineEnd)
    }
}

@Composable
fun rememberEditorState(): EditorState = remember { EditorState() }
