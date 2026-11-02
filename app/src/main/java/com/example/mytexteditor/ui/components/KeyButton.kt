package com.example.mytexteditor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mytexteditor.ui.utils.EditorHaptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun KeyButton(
    primaryText: String = "",
    secondaryText: String? = null,
    iconRes: Int? = null,
    modifier: Modifier = Modifier,
    bgColor: Color = Color(0xFF323232),
    textColor: Color = Color.White,
    isRepeatable: Boolean = false,
    onFlick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    
    // Giữ trạng thái mới nhất bằng rememberUpdatedState để pointerInput(Unit) không bao giờ bị restart
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnFlick by rememberUpdatedState(onFlick)
    val currentIsRepeatable by rememberUpdatedState(isRepeatable)
    val currentSecondaryText by rememberUpdatedState(secondaryText)

    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current

    val flickDistanceThresholdPx = remember(density) { with(density) { 18.dp.toPx() } }

    Box(
        modifier = modifier
            .height(54.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isPressed = true

                    val hasFlick = currentSecondaryText != null && currentOnFlick != null

                    // 1. Phản hồi xúc giác tức thì ngay khi chạm
                    EditorHaptics.keyPress(view, context)

                    // 2. Nếu phím KHÔNG CÓ FLICK: Kích hoạt ngay lập tức tại sự kiện DOWN!
                    // Giúp loại bỏ hoàn toàn 80-140ms dwell time
                    if (!hasFlick && !currentIsRepeatable) {
                        currentOnClick()
                    }

                    var repeatJob: Job? = null
                    if (currentIsRepeatable) {
                        currentOnClick()
                        repeatJob = scope.launch {
                            delay(300)
                            while (isActive) {
                                EditorHaptics.keyPress(view, context)
                                currentOnClick()
                                delay(40)
                            }
                        }
                    }

                    var isFlickTriggered = false
                    val startY = down.position.y

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()

                            // Phát hiện Flick ngay trong lúc di chuyển ngón tay mà không cần chờ nhấc tay!
                            if (hasFlick && !isFlickTriggered) {
                                val deltaY = change.position.y - startY
                                if (deltaY < -flickDistanceThresholdPx) {
                                    isFlickTriggered = true
                                    currentOnFlick?.invoke()
                                }
                            }

                            if (!change.pressed) {
                                // Nếu là phím có flick mà người dùng nhấc tay lên và chưa vuốt -> Coi như click
                                if (hasFlick && !isFlickTriggered) {
                                    currentOnClick()
                                }
                                break
                            }
                        }
                    } finally {
                        repeatJob?.cancel()
                        isPressed = false
                    }
                }
            }
            .padding(horizontal = 1.5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        val buttonBg = if (isPressed) {
            if (bgColor == Color(0xFF323232)) Color(0xFF484848) else bgColor.copy(alpha = 0.7f)
        } else {
            bgColor
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
                .background(buttonBg)
                .border(BorderStroke(1.dp, Color(0xFF383838)), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = primaryText,
                    tint = textColor,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                if (!secondaryText.isNullOrEmpty()) {
                    Text(
                        text = secondaryText,
                        color = Color(0xFF8A8A8E),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp, end = 4.dp)
                    )
                }

                val display = if (primaryText == "BACKSPACE") "⌫" else primaryText
                val isFunctionKey = display.length > 1
                Text(
                    text = display,
                    color = textColor,
                    fontSize = if (isFunctionKey) 12.sp else 17.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isFunctionKey) FontWeight.SemiBold else FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = if (!secondaryText.isNullOrEmpty()) 2.dp else 0.dp)
                )
            }
        }
    }
}