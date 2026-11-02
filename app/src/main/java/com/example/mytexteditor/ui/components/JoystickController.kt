package com.example.mytexteditor.ui.components

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.roundToInt
import com.example.mytexteditor.model.Direction
import com.example.mytexteditor.ui.theme.EditorColors

@Composable
fun JoystickController(onDirection: (Direction) -> Unit) {
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val maxRadiusPx = with(density) { 60.dp.toPx() }
    val deadZonePx = with(density) { 20.dp.toPx() }
    val currentOnDirection by rememberUpdatedState(onDirection)
    val scope = rememberCoroutineScope()

    fun directionFor(offset: Offset): Direction {
        val angle = atan2(offset.y, offset.x)
        val degrees = Math.toDegrees(angle.toDouble()).let { if (it < 0) it + 360 else it }
        return when {
            degrees >= 315 || degrees < 45 -> Direction.RIGHT
            degrees < 135 -> Direction.DOWN
            degrees < 225 -> Direction.LEFT
            else -> Direction.UP
        }
    }

    Box(
        modifier = Modifier
            .size(160.dp)
            .clip(CircleShape)
            .background(Color(0xFF262626)) // GNOME Dark Base
            .border(BorderStroke(1.dp, EditorColors.border), CircleShape)
            .pointerInput(maxRadiusPx, deadZonePx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val center = Offset(size.width / 2f, size.height / 2f)

                    fun updateOffset(position: Offset) {
                        val offset = position - center
                        val distance = offset.getDistance()
                        dragOffset = if (distance > maxRadiusPx) {
                            offset / distance * maxRadiusPx
                        } else {
                            offset
                        }
                    }

                    down.consume()
                    isDragging = true
                    updateOffset(down.position)

                    val repeatJob = scope.launch {
                        val activationDelayMs = 300L

                        while (isActive && isDragging) {
                            if (dragOffset.getDistance() <= deadZonePx) {
                                delay(16)
                                continue
                            }

                            val activationTime = SystemClock.uptimeMillis()
                            // Bước 1: 1 tick đầu tiên
                            currentOnDirection(directionFor(dragOffset))
                            delay(activationDelayMs)

                            // Bước 2 & 3: Lặp và tăng tốc mượt mà theo thời gian
                            while (isActive && isDragging && dragOffset.getDistance() > deadZonePx) {
                                currentOnDirection(directionFor(dragOffset))

                                val heldDurationMs = SystemClock.uptimeMillis() - activationTime
                                // Giảm mượt mà từ 100ms xuống thẳng 20ms trong vòng 1.5 giây
                                val progress =
                                    ((heldDurationMs - activationDelayMs).coerceAtLeast(0L) / 1200f).coerceIn(0f, 1f)
                                val repeatDelayMs = (100L - (progress * 80L)).toLong() // từ 100ms -> 20ms

                                delay(repeatDelayMs)
                            }
                        }
                    }

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                            updateOffset(change.position)
                        }
                    } finally {
                        repeatJob.cancel()
                        isDragging = false
                        dragOffset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Movable Thumb Knob
        Box(
            modifier = Modifier
                .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                .size(60.dp)
                .clip(CircleShape)
                .background(Color(0xFF484848)) // GNOME Adwaita Knob
                .border(BorderStroke(1.dp, Color(0xFF5A5A5A)), CircleShape)
        )
    }
}
