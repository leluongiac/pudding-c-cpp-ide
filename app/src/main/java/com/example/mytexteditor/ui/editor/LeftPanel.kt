package com.example.mytexteditor.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.mytexteditor.ui.components.*
import com.example.mytexteditor.R
import com.example.mytexteditor.ui.theme.EditorColors

@Composable
fun LeftPanel(
    isShiftMode: Boolean,
    isSymbolMode: Boolean,
    isCtrlPressed: Boolean,
    onKey: (String) -> Unit,
    onNewFile: () -> Unit,
    onOpenFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(bottom = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PadIconButton(iconRes = R.drawable.ic_new, contentDescription = "New File", onClick = onNewFile)
            Spacer(modifier = Modifier.width(8.dp))
            PadIconButton(iconRes = R.drawable.ic_open, contentDescription = "Open File", onClick = onOpenFile)
        }

        // Cố định Layout 4 hàng, chỉ đổi nội dung dữ liệu bên trong để Zero Latency
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val transform = remember(isShiftMode) {
                { c: String -> if (isShiftMode) c.uppercase() else c.lowercase() }
            }

            // ROW 1 (5 phím)
            Row(modifier = Modifier.fillMaxWidth()) {
                val r1Keys = if (isSymbolMode) {
                    listOf("1" to null, "2" to null, "3" to null, "4" to null, "5" to null)
                } else {
                    listOf("q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5")
                }
                r1Keys.forEach { (pri, sec) ->
                    val primary = if (isSymbolMode) pri else transform(pri)
                    KeyButton(
                        primaryText = primary,
                        secondaryText = sec,
                        modifier = Modifier.weight(1f),
                        onFlick = sec?.let { { onKey(it) } },
                        onClick = { onKey(primary) }
                    )
                }
            }

            // ROW 2 (5 phím)
            Row(modifier = Modifier.fillMaxWidth()) {
                val r2Keys = if (isSymbolMode) {
                    listOf("(" to null, ")" to null, "[" to null, "]" to null, "\"" to null)
                } else {
                    listOf("a" to "~", "s" to "!", "d" to "@", "f" to "#", "g" to "$")
                }
                r2Keys.forEach { (pri, sec) ->
                    val primary = if (isSymbolMode) pri else transform(pri)
                    KeyButton(
                        primaryText = primary,
                        secondaryText = sec,
                        modifier = Modifier.weight(1f),
                        onFlick = sec?.let { { onKey(it) } },
                        onClick = { onKey(primary) }
                    )
                }
            }

            // ROW 3 (1 chức năng + 4 phím)
            Row(modifier = Modifier.fillMaxWidth()) {
                if (isSymbolMode) {
                    val r3Sym = listOf("{" to null, "}" to null, "<" to null, ">" to null, "'" to null)
                    r3Sym.forEach { (pri, _) ->
                        KeyButton(primaryText = pri, modifier = Modifier.weight(1f)) { onKey(pri) }
                    }
                } else {
                    KeyButton(
                        primaryText = "SHIFT",
                        iconRes = R.drawable.ic_shift,
                        modifier = Modifier.weight(1f),
                        bgColor = if (isShiftMode) Color(0xFFE5A50A) else EditorColors.keySurface,
                        textColor = if (isShiftMode) Color.Black else Color.White
                    ) { onKey("SHIFT") }

                    listOf("z" to "^", "x" to "&", "c" to "*", "v" to "/").forEach { (pri, sec) ->
                        KeyButton(
                            primaryText = transform(pri),
                            secondaryText = sec,
                            modifier = Modifier.weight(1f),
                            onFlick = { onKey(sec) },
                            onClick = { onKey(transform(pri)) }
                        )
                    }
                }
            }

            // ROW 4 (CTRL, TAB, MODE)
            Row(modifier = Modifier.fillMaxWidth()) {
                KeyButton(
                    primaryText = "CTRL",
                    modifier = Modifier.weight(1f),
                    bgColor = if (isCtrlPressed) EditorColors.accent else EditorColors.keySurface
                ) { onKey("CTRL") }

                KeyButton(primaryText = "TAB", modifier = Modifier.weight(1f)) { onKey("TAB") }

                KeyButton(
                    primaryText = if (isSymbolMode) "ABC" else "?123",
                    modifier = Modifier.weight(1f),
                    bgColor = EditorColors.accent
                ) { onKey(if (isSymbolMode) "ABC" else "?123") }
            }
        }
    }
}