package com.example.mytexteditor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mytexteditor.data.ExecutionResult

@Composable
fun GnomeConsoleSheet(
    result: ExecutionResult?,
    isRunning: Boolean,
    onClose: () -> Unit,
    onReRun: (stdin: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var stdinText by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .background(Color(0xFF181818))
            .border(BorderStroke(1.dp, Color(0xFF333333)), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
    ) {
        // --- CONSOLE HEADER (Adwaita HeaderBar Style) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF222222))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Status dot
                val dotColor = when {
                    isRunning -> Color(0xFFF6D32D) // Vàng GNOME
                    result?.isError == true -> Color(0xFFE01B24) // Đỏ GNOME
                    result != null -> Color(0xFF2EC27E) // Xanh GNOME
                    else -> Color(0xFF9A9996)
                }
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
                Text(
                    text = when {
                        isRunning -> "Compiling & Running..."
                        result?.isError == true -> "Finished (Error ${result.exitCode})"
                        result != null -> "Success (${result.executionTimeMs}ms)"
                        else -> "Console"
                    },
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Close Action
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF303030))
                    .clickable { onClose() }
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(text = "✕", color = Color(0xFFCCCCCC), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // --- OUTPUT TEXT AREA ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .verticalScroll(scrollState)
        ) {
            val displayText = when {
                isRunning -> "Compiling and executing with Clang..."
                result != null -> result.output
                else -> "Ready."
            }
            val textColor = if (result?.isError == true) Color(0xFFFF7B63) else Color(0xFFDEDDDA)

            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    text = displayText,
                    color = textColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        // --- STDIN INPUT DOCK (Dành cho bài toán cần cin >>) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
                .border(BorderStroke(0.5.dp, Color(0xFF2C2C2C)))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "stdin › ", color = Color(0xFF777777), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            BasicTextField(
                value = stdinText,
                onValueChange = { stdinText = it },
                textStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(Color(0xFF3584E4)),
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                singleLine = true
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF3584E4))
                    .clickable(enabled = !isRunning) { onReRun(stdinText) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(text = "Send & Run", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}