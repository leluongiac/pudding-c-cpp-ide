package com.example.mytexteditor.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mytexteditor.ui.theme.EditorColors

@Composable
fun PadIconButton(
    iconRes: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    isAccent: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(width = 40.dp, height = 34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isAccent) EditorColors.accent else Color(0xFF2C2C2C))
            .border(
                BorderStroke(
                    1.dp,
                    if (isAccent) EditorColors.accent else Color(0xFF3E3E3E)
                ),
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun HeaderActionPill(
    text: String,
    isAccent: Boolean = false,
    bgColor: Color? = null,
    onClick: () -> Unit
) {
    val background = bgColor ?: if (isAccent) EditorColors.accent else EditorColors.border
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ChevronDownIcon(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFAAAAAA)
) {
    Canvas(modifier = modifier.size(12.dp, 6.dp)) {
        val strokeWidth = 2.dp.toPx()
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width / 2f, size.height)
            lineTo(size.width, 0f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun ChevronUpIcon(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFAAAAAA)
) {
    Canvas(modifier = modifier.size(12.dp, 6.dp)) {
        val strokeWidth = 2.dp.toPx()
        val path = Path().apply {
            moveTo(0f, size.height)
            lineTo(size.width / 2f, 0f)
            lineTo(size.width, size.height)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun ZenModeIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier = modifier.size(16.dp)) {
        val s = size.minDimension / 16f
        val stroke = Stroke(width = 1.4f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)

        drawLine(color, Offset(2f * s, 3.5f * s), Offset(2f * s, 12.5f * s), strokeWidth = stroke.width, cap = stroke.cap)
        drawLine(color, Offset(14f * s, 3.5f * s), Offset(14f * s, 12.5f * s), strokeWidth = stroke.width, cap = stroke.cap)
        drawLine(color, Offset(8f * s, 5f * s), Offset(8f * s, 11f * s), strokeWidth = stroke.width, cap = stroke.cap)

        val leftArrow = Path().apply {
            moveTo(5.5f * s, 6.5f * s)
            lineTo(3.5f * s, 8f * s)
            lineTo(5.5f * s, 9.5f * s)
        }
        drawPath(leftArrow, color, style = stroke)

        val rightArrow = Path().apply {
            moveTo(10.5f * s, 6.5f * s)
            lineTo(12.5f * s, 8f * s)
            lineTo(10.5f * s, 9.5f * s)
        }
        drawPath(rightArrow, color, style = stroke)
    }
}

// 2. Icon Thoát Zen Mode (Thu gọn vào trong)
@Composable
fun ZenModeExitIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier = modifier.size(16.dp)) {
        val s = size.minDimension / 16f
        val stroke = Stroke(width = 1.4f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)

        drawLine(color, Offset(2f * s, 3.5f * s), Offset(2f * s, 12.5f * s), strokeWidth = stroke.width, cap = stroke.cap)
        drawLine(color, Offset(14f * s, 3.5f * s), Offset(14f * s, 12.5f * s), strokeWidth = stroke.width, cap = stroke.cap)
        drawLine(color, Offset(8f * s, 5f * s), Offset(8f * s, 11f * s), strokeWidth = stroke.width, cap = stroke.cap)

        val leftArrow = Path().apply {
            moveTo(3.5f * s, 6.5f * s)
            lineTo(5.5f * s, 8f * s)
            lineTo(3.5f * s, 9.5f * s)
        }
        drawPath(leftArrow, color, style = stroke)

        val rightArrow = Path().apply {
            moveTo(12.5f * s, 6.5f * s)
            lineTo(10.5f * s, 8f * s)
            lineTo(12.5f * s, 9.5f * s)
        }
        drawPath(rightArrow, color, style = stroke)
    }
}

// 3. Icon Chữ A Nhỏ (A-)
@Composable
fun FormatTextSmallerIcon(modifier: Modifier = Modifier, color: Color = Color(0xFFDDDDDD)) {
    Canvas(modifier = modifier.size(16.dp)) {
        val s = size.minDimension / 16f
        val stroke = Stroke(width = 1.25f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)

        val aLegs = Path().apply {
            moveTo(2f * s, 13f * s)
            lineTo(5.5f * s, 3f * s)
            lineTo(9f * s, 13f * s)
        }
        drawPath(aLegs, color, style = stroke)
        drawLine(color, Offset(3.3f * s, 9.5f * s), Offset(7.7f * s, 9.5f * s), strokeWidth = stroke.width, cap = stroke.cap)
        drawLine(color, Offset(11.5f * s, 8f * s), Offset(15.5f * s, 8f * s), strokeWidth = stroke.width, cap = stroke.cap)
    }
}

// 4. Icon Chữ A Lớn (A+)
@Composable
fun FormatTextLargerIcon(modifier: Modifier = Modifier, color: Color = Color(0xFFDDDDDD)) {
    Canvas(modifier = modifier.size(16.dp)) {
        val s = size.minDimension / 16f
        val stroke = Stroke(width = 1.25f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)

        val aLegs = Path().apply {
            moveTo(2f * s, 13f * s)
            lineTo(5.5f * s, 3f * s)
            lineTo(9f * s, 13f * s)
        }
        drawPath(aLegs, color, style = stroke)
        drawLine(color, Offset(3.3f * s, 9.5f * s), Offset(7.7f * s, 9.5f * s), strokeWidth = stroke.width, cap = stroke.cap)
        drawLine(color, Offset(11.5f * s, 8f * s), Offset(15.5f * s, 8f * s), strokeWidth = stroke.width, cap = stroke.cap)
        drawLine(color, Offset(13.5f * s, 6f * s), Offset(13.5f * s, 10f * s), strokeWidth = stroke.width, cap = stroke.cap)
    }
}

// 5. Icon Bật Wrap Dòng
@Composable
fun FormatTextWrapIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier = modifier.size(16.dp)) {
        val s = size.minDimension / 16f
        val stroke = Stroke(width = 1.6f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Hàng trên
        drawLine(color, Offset(2f * s, 3.5f * s), Offset(14f * s, 3.5f * s), strokeWidth = stroke.width, cap = stroke.cap)

        // Đường uốn vòng ngược lại
        val wrapPath = Path().apply {
            moveTo(2f * s, 8f * s)
            lineTo(11f * s, 8f * s)
            arcTo(
                rect = Rect(8.75f * s, 8f * s, 13.25f * s, 12.5f * s),
                startAngleDegrees = -90f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            lineTo(5.5f * s, 12.5f * s)
        }
        drawPath(wrapPath, color, style = stroke)

        // Mũi tên trỏ về
        val arrow = Path().apply {
            moveTo(7.5f * s, 10.5f * s)
            lineTo(5.5f * s, 12.5f * s)
            lineTo(7.5f * s, 14.5f * s)
        }
        drawPath(arrow, color, style = stroke)
    }
}

// 6. Icon Không Wrap Dòng (Chữ chạy thẳng tràn lề)
@Composable
fun FormatTextNoWrapIcon(modifier: Modifier = Modifier, color: Color = Color(0xFFDDDDDD)) {
    Canvas(modifier = modifier.size(16.dp)) {
        val s = size.minDimension / 16f
        val stroke = Stroke(width = 1.6f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Hàng 1
        drawLine(color, Offset(2f * s, 3.5f * s), Offset(12f * s, 3.5f * s), strokeWidth = stroke.width, cap = stroke.cap)
        // Hàng 2 chạy dài
        drawLine(color, Offset(2f * s, 8f * s), Offset(14f * s, 8f * s), strokeWidth = stroke.width, cap = stroke.cap)

        // Mũi tên trỏ thẳng ra ngoài
        val arrow = Path().apply {
            moveTo(12f * s, 6f * s)
            lineTo(14f * s, 8f * s)
            lineTo(12f * s, 10f * s)
        }
        drawPath(arrow, color, style = stroke)

        // Hàng 3
        drawLine(color, Offset(2f * s, 12.5f * s), Offset(8f * s, 12.5f * s), strokeWidth = stroke.width, cap = stroke.cap)
    }
}