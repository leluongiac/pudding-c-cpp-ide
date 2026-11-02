package com.example.mytexteditor.ui.editor

import com.example.mytexteditor.ui.components.*
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.mytexteditor.data.ExecutionResult
import com.example.mytexteditor.data.FileUtils
import com.example.mytexteditor.state.EditorState
import com.example.mytexteditor.syntax.TreeSitterVisualTransformation
import com.example.mytexteditor.ui.components.ChevronDownIcon
import com.example.mytexteditor.ui.components.ChevronUpIcon
import com.example.mytexteditor.ui.components.GnomeConsoleSheet
import com.example.mytexteditor.ui.theme.EditorColors

private val GnomeLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CenterEditorPanel(
    state: EditorState,
    isRunningCode: Boolean,
    executionResult: ExecutionResult?,
    isConsoleVisible: Boolean,
    onCloseConsole: () -> Unit,
    onReRun: (String) -> Unit,
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
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

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

    // Auto scroll theo con trỏ
    LaunchedEffect(cursorPosition, state.layoutResult, viewportSize) {
        val layout = state.layoutResult ?: return@LaunchedEffect
        if (viewportSize.height == 0 || viewportSize.width == 0) return@LaunchedEffect
        if (cursorPosition !in 0..layout.layoutInput.text.length) return@LaunchedEffect

        val cursorRect = layout.getCursorRect(cursorPosition)
        val lineHeightPx = with(density) { currentLineHeight.toPx() }

        val currentScrollY = verticalScrollState.value
        val viewHeight = viewportSize.height
        if (cursorRect.top < currentScrollY) {
            verticalScrollState.scrollTo(cursorRect.top.toInt())
        } else if (cursorRect.bottom > currentScrollY + viewHeight - lineHeightPx) {
            verticalScrollState.scrollTo((cursorRect.bottom - viewHeight + lineHeightPx).toInt())
        }

        if (!state.isWordWrap) {
            val currentScrollX = codeHorizontalScrollState.value
            val viewWidth = viewportSize.width
            if (cursorRect.left < currentScrollX) {
                codeHorizontalScrollState.scrollTo(cursorRect.left.toInt().coerceAtLeast(0))
            } else if (cursorRect.right > currentScrollX + viewWidth - 60) {
                codeHorizontalScrollState.scrollTo((cursorRect.right - viewWidth + 80).toInt())
            }
        }
    }

    // GNOME Panel Container: Luôn giữ phong cách GNOME nguyên bản
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(EditorColors.editorSurface)
            .border(BorderStroke(1.dp, Color(0xFF333333)), RoundedCornerShape(14.dp))
            .onSizeChanged { viewportSize = it }
            // Pinch-to-zoom shortcut: Zoom kích thước không gọi lại Syntax Tokenizer
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
                        top = if (isHeaderExpanded) 68.dp else 26.dp,
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

                // Short-cut layout: Nếu Word Wrap bật thì loại bỏ modifier cuộn ngang -> Tiết kiệm 50% CPU
                val codeAreaModifier = if (state.isWordWrap) {
                    Modifier.weight(1f).fillMaxWidth()
                } else {
                    Modifier.weight(1f).horizontalScroll(codeHorizontalScrollState)
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
                        modifier = if (state.isWordWrap) Modifier.fillMaxWidth() else Modifier.width(IntrinsicSize.Max)
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

                    // TÍNH NĂNG ĐỌC: NÚT WORD WRAP (Bật / Tắt bẻ dòng)
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

                    // NÚT ĐÓNG HEADER
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
                result = executionResult,
                isRunning = isRunningCode,
                onClose = onCloseConsole,
                onReRun = onReRun,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 220.dp)
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