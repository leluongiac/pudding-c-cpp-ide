package com.example.mytexteditor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mytexteditor.lsp.CompletionItem
import com.example.mytexteditor.lsp.CompletionItemKind
import com.example.mytexteditor.lsp.SignatureHelp
import com.example.mytexteditor.ui.theme.EditorColors

/**
 * Giao diện Popup Auto-Completion theo phong cách GNOME Adwaita Dark / Catppuccin Mocha.
 */
@Composable
fun CompletionPopup(
    items: List<CompletionItem>,
    selectedIndex: Int,
    onSelectItem: (Int) -> Unit,
    onAcceptItem: (CompletionItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val listState = rememberLazyListState()

    // Tự động cuộn theo item đang được chọn bằng Joystick UP/DOWN
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in items.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    Box(
        modifier = modifier
            .widthIn(min = 240.dp, max = 340.dp)
            .heightIn(max = 190.dp)
            .shadow(12.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1E1E2E)) // Catppuccin Mocha Base
            .border(BorderStroke(1.dp, Color(0xFF45475A)), RoundedCornerShape(10.dp))
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            itemsIndexed(items) { index, item ->
                val isSelected = index == selectedIndex
                val rowBg = if (isSelected) Color(0xFF313244) else Color.Transparent

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBg)
                        .clickable {
                            onSelectItem(index)
                            onAcceptItem(item)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Badge loại ký hiệu (Kind Badge) với màu sắc Catppuccin Mocha
                    val (badgeText, badgeColor) = getKindBadge(item.kind)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeColor.copy(alpha = 0.2f))
                            .border(BorderStroke(0.5.dp, badgeColor.copy(alpha = 0.5f)), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Tên gợi ý (Label)
                    Text(
                        text = item.label,
                        color = if (isSelected) Color.White else Color(0xFFCDD6F4),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Thông tin kiểu dữ liệu (Detail)
                    if (!item.detail.isNullOrBlank()) {
                        Text(
                            text = item.detail,
                            color = Color(0xFF89DCEB), // Catppuccin Sky
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Giao diện Signature Help Popup:
 * - Chỉ hiển thị DUY NHẤT 1 overload (activeSignature).
 * - Tham số tích cực (activeParameter) được highlight vàng Catppuccin + in đậm + gạch chân.
 */
@Composable
fun SignatureHelpPopup(
    signatureHelp: SignatureHelp,
    modifier: Modifier = Modifier
) {
    val signatures = signatureHelp.signatures
    if (signatures.isEmpty()) return

    val activeSig = signatures.getOrNull(signatureHelp.activeSignature) ?: signatures.first()
    val activeParamIndex = signatureHelp.activeParameter

    Box(
        modifier = modifier
            .widthIn(min = 220.dp, max = 380.dp)
            .shadow(12.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF181825)) // Catppuccin Mantle
            .border(BorderStroke(1.dp, EditorColors.accent.copy(alpha = 0.8f)), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Render 1 signature duy nhất với tham số active được highlight
            val annotatedLabel = buildAnnotatedString {
                val fullLabel = activeSig.label
                val params = activeSig.parameters

                if (params.isNotEmpty() && activeParamIndex in params.indices) {
                    val activeParam = params[activeParamIndex]
                    val paramLabel = activeParam.label

                    val startIndex = if (paramLabel.isNotEmpty()) fullLabel.indexOf(paramLabel) else -1
                    if (startIndex != -1) {
                        val endIndex = startIndex + paramLabel.length
                        append(fullLabel.substring(0, startIndex))
                        withStyle(
                            SpanStyle(
                                color = Color(0xFFF9E2AF), // Catppuccin Yellow
                                fontWeight = FontWeight.ExtraBold,
                                textDecoration = TextDecoration.Underline,
                                background = Color(0xFFF9E2AF).copy(alpha = 0.15f)
                            )
                        ) {
                            append(paramLabel)
                        }
                        append(fullLabel.substring(endIndex))
                    } else {
                        append(fullLabel)
                    }
                } else {
                    append(fullLabel)
                }
            }

            Text(
                text = annotatedLabel,
                color = Color(0xFFCDD6F4),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 15.sp
            )

            // Mô tả tham số đang nhập (nếu có)
            val activeParamDoc = activeSig.parameters.getOrNull(activeParamIndex)?.documentation
            val sigDoc = activeSig.documentation
            val docToDisplay = activeParamDoc ?: sigDoc
            if (!docToDisplay.isNullOrBlank()) {
                Text(
                    text = docToDisplay,
                    color = Color(0xFFA6ADC8), // Catppuccin Subtext0
                    fontSize = 9.sp,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun getKindBadge(kind: CompletionItemKind): Pair<String, Color> {
    return when (kind) {
        CompletionItemKind.Class -> "class" to Color(0xFFF9E2AF)       // Yellow
        CompletionItemKind.Struct -> "struct" to Color(0xFFF9E2AF)     // Yellow
        CompletionItemKind.Interface -> "intf" to Color(0xFFF9E2AF)
        CompletionItemKind.Function -> "fn" to Color(0xFF89B4FA)       // Blue
        CompletionItemKind.Method -> "method" to Color(0xFF89B4FA)     // Blue
        CompletionItemKind.Constructor -> "ctor" to Color(0xFF89B4FA)
        CompletionItemKind.Field -> "field" to Color(0xFFFAB387)      // Peach
        CompletionItemKind.Variable -> "var" to Color(0xFFFAB387)      // Peach
        CompletionItemKind.Keyword -> "kw" to Color(0xFFCBA6F7)        // Mauve
        CompletionItemKind.Snippet -> "snip" to Color(0xFF94E2D5)      // Teal
        CompletionItemKind.Module -> "mod" to Color(0xFFB4BEFE)        // Lavender
        CompletionItemKind.Enum -> "enum" to Color(0xFFF9E2AF)
        CompletionItemKind.EnumMember -> "const" to Color(0xFFFAB387)
        CompletionItemKind.Constant -> "const" to Color(0xFFFAB387)
        CompletionItemKind.Operator -> "op" to Color(0xFF89DCEB)       // Sky
        CompletionItemKind.TypeParameter -> "type" to Color(0xFFF9E2AF)
        else -> "text" to Color(0xFF6C7086)
    }
}
