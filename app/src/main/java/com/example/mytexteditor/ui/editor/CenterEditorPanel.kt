package com.example.mytexteditor.ui.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.mytexteditor.data.FileUtils
import com.example.mytexteditor.state.EditorState
import com.example.mytexteditor.syntax.TreeSitterVisualTransformation
import com.example.mytexteditor.ui.components.*
import com.example.mytexteditor.ui.theme.EditorColors
import kotlin.math.roundToInt

private val GnomeLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CenterEditorPanel(
    state: EditorState,
    isConsoleVisible: Boolean,
    onCloseConsole: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isHeaderExpanded by remember { mutableStateOf(false) }
    var showPopover by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val density = LocalDensity.current

    // Khóa tỷ lệ line-height = 1.714 * font size để Gutter và Text luôn khớp tuyệt đối
    val currentLineHeight = (state.editorFontSize * 1.714f).sp

    val tokens = state.syntaxHighlighter.tokens.value
    val visualTransformation = remember(tokens) {
        TreeSitterVisualTransformation(tokens)
    }

    val fullFileName = state.fileName
    val fullPath = remember(state.currentUri, state.fileName) {
        FileUtils.getFilePath(context, state.currentUri)
    }
    val dirPath = remember(state.currentUri) {
        FileUtils.getDirectoryPath(context, state.currentUri)
    }
    val displayFileName = remember(fullFileName) { FileUtils.truncateMiddle(fullFileName, 22) }
    val displayDirPath = remember(dirPath) { FileUtils.truncatePathMiddle(dirPath, 26) }
    val dotColor = if (state.isModified) Color(230, 69, 83) else Color(64, 160, 43)

    val verticalScrollState = rememberScrollState()
    val codeHorizontalScrollState = rememberScrollState()
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    var codeAreaSize by remember { mutableStateOf(IntSize.Zero) }

    val editorText = state.textFieldValue.text
    val cursorPosition = state.textFieldValue.selection.end.coerceIn(0, editorText.length)

    val totalLines = remember(editorText) {
        editorText.count { it == '\n' } + 1
    }

    val plainLineNumbers = remember(totalLines) {
        StringBuilder(totalLines * 4).apply {
            for (i in 1..totalLines) {
                if (i > 1) append('\n')
                append(i)
            }
        }.toString()
    }

    val topPaddingDp = if (isHeaderExpanded) 68.dp else 26.dp
    val topPaddingPx = with(density) { topPaddingDp.toPx() }
    val bottomPaddingPx = with(density) { 16.dp.toPx() }
    val safePaddingY = with(density) { 32.dp.toPx() }
    val safePaddingX = with(density) { 36.dp.toPx() }

    // ================= AUTO-SCROLL ĐI THEO CON TRỎ =================
    // Tính toán viewport thực tế kết hợp TextLayoutResult để viewport luôn bám sát con trỏ
    LaunchedEffect(cursorPosition, state.layoutResult, panelSize, codeAreaSize, isHeaderExpanded) {
        val layout = state.layoutResult ?: return@LaunchedEffect
        if (panelSize.height == 0 || panelSize.width == 0) return@LaunchedEffect
        if (cursorPosition !in 0..layout.layoutInput.text.length) return@LaunchedEffect

        val cursorRect = layout.getCursorRect(cursorPosition)

        // 1. Cuộn dọc (Vertical Auto-Scroll)
        val visibleHeight = (panelSize.height - topPaddingPx - bottomPaddingPx).coerceAtLeast(100f)
        val currentScrollY = verticalScrollState.value.toFloat()

        if (cursorRect.top < currentScrollY + safePaddingY) {
            val targetY = (cursorRect.top - safePaddingY).toInt().coerceAtLeast(0)
            verticalScrollState.scrollTo(targetY)
        } else if (cursorRect.bottom > currentScrollY + visibleHeight - safePaddingY) {
            val targetY = (cursorRect.bottom - visibleHeight + safePaddingY).toInt().coerceAtLeast(0)
            verticalScrollState.scrollTo(targetY)
        }

        // 2. Cuộn ngang (Horizontal Auto-Scroll khi tắt Word Wrap)
        if (!state.isWordWrap && codeAreaSize.width > 0) {
            val visibleWidth = codeAreaSize.width.toFloat()
            val currentScrollX = codeHorizontalScrollState.value.toFloat()

            if (cursorRect.left < currentScrollX + safePaddingX) {
                val targetX = (cursorRect.left - safePaddingX).toInt().coerceAtLeast(0)
                codeHorizontalScrollState.scrollTo(targetX)
            } else if (cursorRect.right > currentScrollX + visibleWidth - safePaddingX) {
                val targetX = (cursorRect.right - visibleWidth + safePaddingX).toInt().coerceAtLeast(0)
                codeHorizontalScrollState.scrollTo(targetX)
            }
        }
    }

    // GNOME Panel Container
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(EditorColors.editorSurface)
            .border(BorderStroke(1.dp, Color(0xFF333333)), RoundedCornerShape(14.dp))
            .onSizeChanged { panelSize = it }
            // Pinch-to-zoom: Phóng to / thu nhỏ cỡ chữ không làm gián đoạn syntax
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom != 1f) {
                        val newSize = (state.editorFontSize * zoom).coerceIn(10f, 26f)
                        state.editorFontSize = newSize
                    }
                }
            }
    ) {
        @Suppress("DEPRECATION")
        CompositionLocalProvider(LocalTextInputService provides null) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = topPaddingDp,
                        bottom = 16.dp
                    )
                    .verticalScroll(verticalScrollState)
            ) {
                GnomeGutter(
                    plainLineNumbers = plainLineNumbers,
                    fontSize = (state.editorFontSize - 3f).coerceAtLeast(9f).sp,
                    lineHeight = currentLineHeight
                )
                Spacer(modifier = Modifier.width(6.dp))

                val codeAreaModifier = if (state.isWordWrap) {
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onSizeChanged { codeAreaSize = it }
                } else {
                    Modifier
                        .weight(1f)
                        .horizontalScroll(codeHorizontalScrollState)
                        .onSizeChanged { codeAreaSize = it }
                }

                Box(modifier = codeAreaModifier) {
                    BasicTextField(
                        value = state.textFieldValue,
                        onValueChange = { newValue -> state.updateTextFieldValue(newValue) },
                        visualTransformation = visualTransformation,
                        onTextLayout = { state.layoutResult = it },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.None
                        ),
                        textStyle = TextStyle(
                            color = Color(0xFFEAEAEA),
                            fontFamily = FontFamily.Monospace,
                            fontSize = state.editorFontSize.sp,
                            lineHeight = currentLineHeight,
                            lineHeightStyle = GnomeLineHeightStyle
                        ),
                        cursorBrush = SolidColor(EditorColors.accent),
                        modifier = (if (state.isWordWrap) Modifier.fillMaxWidth() else Modifier.width(IntrinsicSize.Max))
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.Enter,
                                        Key.NumPadEnter -> {
                                            state.onKey("ENTER", context)
                                            true
                                        }
                                        Key.Tab -> {
                                            state.onKey("TAB", context)
                                            true
                                        }
                                        else -> {
                                            when (event.utf16CodePoint.toChar()) {
                                                '{' -> { state.onKey("{", context); true }
                                                '}' -> { state.onKey("}", context); true }
                                                '(' -> { state.onKey("(", context); true }
                                                ')' -> { state.onKey(")", context); true }
                                                '[' -> { state.onKey("[", context); true }
                                                ']' -> { state.onKey("]", context); true }
                                                else -> false
                                            }
                                        }
                                    }
                                } else {
                                    false
                                }
                            }
                    )
                }
            }
        }

        // ================= POPUP COMPLETION & SIGNATURE HELP TẠI CON TRỎ =================
        val layout = state.layoutResult
        if (layout != null && cursorPosition in 0..layout.layoutInput.text.length && panelSize.width > 0) {
            val cursorRect = layout.getCursorRect(cursorPosition)
            val gutterOffsetPx = with(density) { 52.dp.toPx() }
            val margin12Px = with(density) { 12.dp.toPx() }
            val padding4Px = with(density) { 4.dp.toPx() }
            val cursorScreenX = gutterOffsetPx + cursorRect.left - codeHorizontalScrollState.value
            val cursorScreenY = topPaddingPx + cursorRect.bottom - verticalScrollState.value + padding4Px

            // 1. Completion Popup
            if (state.isCompletionVisible && state.completionItems.isNotEmpty()) {
                val popupWidthPx = with(density) { 280.dp.toPx() }
                val popupMaxHeightPx = with(density) { 190.dp.toPx() }

                val clampedX = cursorScreenX.coerceIn(
                    margin12Px,
                    (panelSize.width - popupWidthPx - margin12Px).coerceAtLeast(margin12Px)
                )

                val clampedY = if (cursorScreenY + popupMaxHeightPx > panelSize.height) {
                    (topPaddingPx + cursorRect.top - verticalScrollState.value - popupMaxHeightPx).coerceAtLeast(topPaddingPx)
                } else {
                    cursorScreenY
                }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(clampedX.roundToInt(), clampedY.roundToInt()) }
                ) {
                    CompletionPopup(
                        items = state.completionItems,
                        selectedIndex = state.completionSelectedIndex,
                        onSelectItem = { state.completionSelectedIndex = it },
                        onAcceptItem = { state.acceptCompletion(it) }
                    )
                }
            }

            // 2. Signature Help Popup (chỉ hiện khi Completion đóng và đang ở trạng thái nhắc nhập)
            if (!state.isCompletionVisible && state.isSignatureHelpVisible && state.signatureHelpData != null) {
                val sigWidthPx = with(density) { 280.dp.toPx() }
                val sigHeightPx = with(density) { 70.dp.toPx() }

                val clampedX = cursorScreenX.coerceIn(
                    margin12Px,
                    (panelSize.width - sigWidthPx - margin12Px).coerceAtLeast(margin12Px)
                )

                val clampedY = if (cursorScreenY + sigHeightPx > panelSize.height) {
                    (topPaddingPx + cursorRect.top - verticalScrollState.value - sigHeightPx).coerceAtLeast(topPaddingPx)
                } else {
                    cursorScreenY
                }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(clampedX.roundToInt(), clampedY.roundToInt()) }
                ) {
                    SignatureHelpPopup(
                        signatureHelp = state.signatureHelpData!!
                    )
                }
            }
        }

        // GNOME Minimalist HeaderBar Collapsed Pill
        if (!isHeaderExpanded) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                    .background(Color(0xFF2A2A2A))
                    .border(BorderStroke(1.dp, Color(0xFF3D3D3D)), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                    .clickable { isHeaderExpanded = true }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.isZenMode) {
                    Text(text = "ZEN", color = EditorColors.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                ChevronDownIcon(color = Color(0xFF9A9996))
            }
        }

        // HeaderBar khi mở rộng
        AnimatedVisibility(
            visible = isHeaderExpanded,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier.padding(top = 10.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Row(
                    modifier = Modifier
                        .shadow(12.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF282828))
                        .border(BorderStroke(1.dp, Color(0xFF404040)), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showPopover = !showPopover }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = displayFileName, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                        Text(text = displayDirPath, color = Color(0xFF9A9996), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }

                    // Nút Word Wrap
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (state.isWordWrap) EditorColors.accent else Color(0xFF383838))
                            .clickable { state.isWordWrap = !state.isWordWrap },
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.isWordWrap) {
                            FormatTextWrapIcon(modifier = Modifier.size(13.dp))
                        } else {
                            FormatTextNoWrapIcon(modifier = Modifier.size(13.dp))
                        }
                    }

                    // Nút Giảm cỡ chữ (A-)
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF383838))
                            .clickable { state.editorFontSize = (state.editorFontSize - 1.5f).coerceAtLeast(10f) },
                        contentAlignment = Alignment.Center
                    ) {
                        FormatTextSmallerIcon(modifier = Modifier.size(13.dp))
                    }

                    // Nút Tăng cỡ chữ (A+)
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF383838))
                            .clickable { state.editorFontSize = (state.editorFontSize + 1.5f).coerceAtMost(26f) },
                        contentAlignment = Alignment.Center
                    ) {
                        FormatTextLargerIcon(modifier = Modifier.size(13.dp))
                    }

                    // Nút Zen Mode toggle
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (state.isZenMode) EditorColors.accent else Color(0xFF383838))
                            .clickable { state.isZenMode = !state.isZenMode },
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.isZenMode) {
                            ZenModeExitIcon(modifier = Modifier.size(14.dp))
                        } else {
                            ZenModeIcon(modifier = Modifier.size(14.dp))
                        }
                    }

                    // Nút Đóng Header
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF383838))
                            .clickable {
                                isHeaderExpanded = false
                                showPopover = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        ChevronUpIcon(color = Color(0xFFDDDDDD))
                    }
                }

                if (showPopover) {
                    Popup(
                        alignment = Alignment.TopCenter,
                        offset = androidx.compose.ui.unit.IntOffset(0, 160),
                        onDismissRequest = { showPopover = false },
                        properties = PopupProperties(focusable = true)
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(min = 280.dp, max = 380.dp)
                                .shadow(16.dp, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2C2C2C))
                                .border(BorderStroke(1.dp, Color(0xFF444444)), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(dotColor))
                                    Text(text = fullFileName, color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E1E1E))
                                        .border(BorderStroke(1.dp, Color(0xFF363636)), RoundedCornerShape(8.dp))
                                        .padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(EditorColors.accent)
                                            .clickable {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("path", fullPath))
                                                Toast.makeText(context, "Đã sao chép đường dẫn!", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "Copy", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                                        Text(text = fullPath, color = Color(0xFFCCCCCC), fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // GNOME CONSOLE BOTTOM SHEET OVERLAY
        AnimatedVisibility(
            visible = isConsoleVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            GnomeConsoleSheet(
                onClose = onCloseConsole
            )
        }
    }
}

@Composable
private fun GnomeGutter(
    plainLineNumbers: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(end = 10.dp)
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                val x = size.width
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = strokeWidth
                )
            }
            .padding(end = 8.dp)
    ) {
        Text(
            text = plainLineNumbers,
            style = TextStyle(
                color = Color.White.copy(alpha = 0.3f),
                fontFamily = FontFamily.Monospace,
                fontSize = fontSize,
                lineHeight = lineHeight,
                lineHeightStyle = GnomeLineHeightStyle,
                textAlign = TextAlign.End
            ),
            modifier = Modifier.widthIn(min = 20.dp),
            softWrap = false
        )
    }
}