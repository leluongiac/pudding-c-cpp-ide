package com.example.mytexteditor.ui.editor

import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mytexteditor.model.Direction
import com.example.mytexteditor.model.RightMode
import com.example.mytexteditor.ui.components.*
import com.example.mytexteditor.R
import com.example.mytexteditor.ui.theme.EditorColors

@Composable
fun RightPanel(
    rightMode: RightMode,
    isShiftMode: Boolean,
    isSymbolMode: Boolean,
    isRunningCode: Boolean,
    onKey: (String) -> Unit,
    onJoystickDirection: (Direction) -> Unit,
    onToggleMode: (RightMode) -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onRun: () -> Unit,
    onToggleZen: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(bottom = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // TOP ACTIONS: Ergonomic Bar (Run -> Save -> Zen Mode -> Menu)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Nút Run chuẩn GNOME Accent đặt bên ngoài
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isRunningCode) Color(0xFF383838) else Color(0xFF2EC27E))
                    .clickable(enabled = !isRunningCode) { onRun() },
                contentAlignment = Alignment.Center
            ) {
                if (isRunningCode) {
                    Text(text = "⏳", fontSize = 13.sp)
                } else {
                    Text(
                        text = "▶",
                        color = Color.Black,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }

            PadIconButton(
                iconRes = if (isShiftMode) R.drawable.ic_save_as else R.drawable.ic_save,
                contentDescription = if (isShiftMode) "Save As" else "Save",
                isAccent = isShiftMode,
                onClick = if (isShiftMode) onSaveAs else onSave
            )

            // Nút Zen Mode nhanh
            // Nút Zen Mode nhanh
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF333333))
                    .clickable { onToggleZen() },
                contentAlignment = Alignment.Center
            ) {
                ZenModeIcon(modifier = Modifier.size(16.dp))
            }

            PadIconButton(iconRes = R.drawable.ic_menu, contentDescription = "Menu", onClick = onMenu)
        }

        if (rightMode == RightMode.TYPING) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val transform = remember(isShiftMode) {
                    { c: String -> if (isShiftMode) c.uppercase() else c.lowercase() }
                }

                // ROW 1
                Row(modifier = Modifier.fillMaxWidth()) {
                    val r1 = if (isSymbolMode) {
                        listOf("6" to null, "7" to null, "8" to null, "9" to null, "0" to null)
                    } else {
                        listOf("y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0")
                    }
                    r1.forEach { (pri, sec) ->
                        val p = if (isSymbolMode) pri else transform(pri)
                        KeyButton(
                            primaryText = p,
                            secondaryText = sec,
                            modifier = Modifier.weight(1f),
                            onFlick = sec?.let { { onKey(it) } },
                            onClick = { onKey(p) }
                        )
                    }
                }

                // ROW 2
                Row(modifier = Modifier.fillMaxWidth()) {
                    val r2 = if (isSymbolMode) {
                        listOf("=" to null, "+" to null, "-" to null, "*" to null, "/" to null)
                    } else {
                        listOf("h" to "_", "j" to "+", "k" to "-", "l" to "=", ";" to ":")
                    }
                    r2.forEach { (pri, sec) ->
                        val p = if (pri.length == 1 && pri.first().isLetter()) transform(pri) else pri
                        KeyButton(
                            primaryText = p,
                            secondaryText = sec,
                            modifier = Modifier.weight(1f),
                            onFlick = sec?.let { { onKey(it) } },
                            onClick = { onKey(p) }
                        )
                    }
                }

                // ROW 3
                Row(modifier = Modifier.fillMaxWidth()) {
                    val r3 = if (isSymbolMode) {
                        listOf("&" to null, "|" to null, "!" to null, "%" to null, "_" to null)
                    } else {
                        listOf("b" to "\\", "n" to "<", "m" to ">", "," to "?", "." to "\"")
                    }
                    r3.forEach { (pri, sec) ->
                        val p = if (pri.length == 1 && pri.first().isLetter()) transform(pri) else pri
                        KeyButton(
                            primaryText = p,
                            secondaryText = sec,
                            modifier = Modifier.weight(1f),
                            onFlick = sec?.let { { onKey(it) } },
                            onClick = { onKey(p) }
                        )
                    }
                }

                // ROW 4
                Row(modifier = Modifier.fillMaxWidth()) {
                    KeyButton(primaryText = "⌫", modifier = Modifier.weight(1f), isRepeatable = true) {
                        onKey("BACKSPACE")
                    }
                    KeyButton(primaryText = "SPACE", modifier = Modifier.weight(2f)) {
                        onKey("SPACE")
                    }
                    KeyButton(primaryText = "ENT", iconRes = R.drawable.ic_enter, modifier = Modifier.weight(1f)) {
                        onKey("ENTER")
                    }
                    KeyButton(
                        primaryText = "JOY",
                        iconRes = R.drawable.ic_joystick,
                        modifier = Modifier.weight(1f),
                        bgColor = EditorColors.accent
                    ) { onToggleMode(RightMode.JOYSTICK) }
                }
            }
        } else {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                JoystickController(onDirection = onJoystickDirection)
            }
            KeyButton(
                primaryText = "KEYBOARD",
                modifier = Modifier.fillMaxWidth(),
                bgColor = EditorColors.accent,
                onClick = { onToggleMode(RightMode.TYPING) }
            )
        }
    }
}