package com.example.mytexteditor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mytexteditor.data.TerminalSession

@Composable
fun GnomeConsoleSheet(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Lắng nghe dữ liệu realtime từ TerminalSession
    val liveOutput by TerminalSession.terminalOutput.collectAsState()
    val isRunning by TerminalSession.isRunning.collectAsState()

    var stdinText by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    // Tự động cuộn xuống đáy khi C++ in thêm chữ
    LaunchedEffect(liveOutput) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

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
                // Chấm trạng thái: Đang chạy thì Vàng, Kết thúc thì Xanh
                val dotColor = if (isRunning) Color(0xFFF6D32D) else Color(0xFF2EC27E)
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
                Text(
                    text = if (isRunning) "Running (Waiting input)..." else "Finished",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Nút đóng ✕
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF303030))
                    .clickable {
                        TerminalSession.stop()
                        onClose()
                    }
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
            val displayText = liveOutput.ifEmpty { if (isRunning) "Executing with Clang..." else "Ready." }

            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    text = displayText,
                    color = Color(0xFFDEDDDA),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        // --- STDIN INPUT DOCK (Dùng 100% bàn phím tự chế, Gboard không bao giờ nhảy lên) ---
        if (isRunning) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E))
                    .border(BorderStroke(0.5.dp, Color(0xFF2C2C2C)))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "stdin › ", 
                    color = Color(0xFF3584E4), 
                    fontSize = 12.sp, 
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                // Đọc trực tiếp từ TerminalSession:
                val stdinText by TerminalSession.currentStdin.collectAsState()

                // Chỗ hiển thị:
                Text(
                    text = stdinText + "▋",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}