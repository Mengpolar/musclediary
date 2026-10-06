package com.eelan.musclediary.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eelan.musclediary.domain.Muscle
import com.eelan.musclediary.domain.Side
import com.eelan.musclediary.ui.theme.Ink2
import com.eelan.musclediary.ui.theme.Ink3
import com.eelan.musclediary.ui.theme.TextLo
import kotlin.math.min

/**
 * 人体肌肉概览图：正面 + 背面，模型坐标 120 宽 × 240 高。
 * 灰色 = 未锻炼；刺激分越高颜色越暖（绿→橙→红）。
 */

private sealed class MShape {
    data class Oval(val cx: Float, val cy: Float, val rx: Float, val ry: Float) : MShape()
    data class RRect(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float = 4f) : MShape()
    data class Poly(val pts: List<Offset>) : MShape()

    fun bounds(): Rect = when (this) {
        is Oval -> Rect(cx - rx, cy - ry, cx + rx, cy + ry)
        is RRect -> Rect(x, y, x + w, y + h)
        is Poly -> Rect(
            pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y })
    }

    fun scaled(unit: Float, ox: Float, oy: Float): MShape = when (this) {
        is Oval -> Oval(ox + cx * unit, oy + cy * unit, rx * unit, ry * unit)
        is RRect -> RRect(ox + x * unit, oy + y * unit, w * unit, h * unit, r * unit)
        is Poly -> Poly(pts.map { Offset(ox + it.x * unit, oy + it.y * unit) })
    }
}

private fun DrawScope.drawShape(s: MShape, color: Color, outline: Boolean = false) {
    val style = if (outline) Stroke(2f) else Fill
    when (s) {
        is MShape.Oval -> drawOval(color, topLeft = Offset(s.cx - s.rx, s.cy - s.ry),
            size = Size(s.rx * 2, s.ry * 2), style = style)
        is MShape.RRect -> drawRoundRect(color, topLeft = Offset(s.x, s.y),
            size = Size(s.w, s.h), cornerRadius = CornerRadius(s.r, s.r), style = style)
        is MShape.Poly -> {
            val p = Path().apply {
                moveTo(s.pts[0].x, s.pts[0].y)
                s.pts.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(p, color, style = style)
        }
    }
}

// —— 身体底图（轮廓） ——
private val bodyParts: List<MShape> = listOf(
    MShape.Oval(60f, 16f, 9f, 10f),          // 头
    MShape.RRect(54f, 25f, 12f, 7f, 2f),     // 颈
    MShape.Poly(listOf(Offset(34f, 32f), Offset(86f, 32f), Offset(78f, 96f),   // 躯干
        Offset(74f, 112f), Offset(46f, 112f), Offset(42f, 96f))),
    MShape.Oval(30f, 64f, 7f, 32f),          // 左臂
    MShape.Oval(90f, 64f, 7f, 32f),          // 右臂
    MShape.Oval(51f, 162f, 9f, 52f),         // 左腿
    MShape.Oval(69f, 162f, 9f, 52f),         // 右腿
)

// —— 正面肌群 ——
private val frontShapes: List<Pair<Muscle, MShape>> = listOf(
    Muscle.SHOULDERS to MShape.Oval(37f, 36f, 7.5f, 6.5f),
    Muscle.SHOULDERS to MShape.Oval(83f, 36f, 7.5f, 6.5f),
    Muscle.CHEST to MShape.RRect(43f, 33f, 15f, 15f, 5f),
    Muscle.CHEST to MShape.RRect(62f, 33f, 15f, 15f, 5f),
    Muscle.BICEPS to MShape.Oval(30.5f, 50f, 4.8f, 10f),
    Muscle.BICEPS to MShape.Oval(89.5f, 50f, 4.8f, 10f),
    Muscle.FOREARMS to MShape.Oval(28f, 78f, 4.2f, 13f),
    Muscle.FOREARMS to MShape.Oval(92f, 78f, 4.2f, 13f),
    Muscle.ABS to MShape.RRect(51f, 51f, 18f, 27f, 3f),
    Muscle.QUADS to MShape.RRect(47f, 114f, 11.5f, 40f, 5f),
    Muscle.QUADS to MShape.RRect(61.5f, 114f, 11.5f, 40f, 5f),
    Muscle.CALVES to MShape.RRect(48.5f, 156f, 10f, 26f, 5f),
    Muscle.CALVES to MShape.RRect(61.5f, 156f, 10f, 26f, 5f),
)

// —— 背面肌群 ——
private val backShapes: List<Pair<Muscle, MShape>> = listOf(
    Muscle.TRAPS to MShape.Poly(listOf(Offset(45f, 27f), Offset(75f, 27f),
        Offset(66f, 44f), Offset(54f, 44f))),
    Muscle.LATS to MShape.Poly(listOf(Offset(44f, 44f), Offset(57f, 44f),
        Offset(56f, 74f), Offset(45f, 64f))),
    Muscle.LATS to MShape.Poly(listOf(Offset(63f, 44f), Offset(76f, 44f),
        Offset(75f, 64f), Offset(64f, 74f))),
    Muscle.TRICEPS to MShape.Oval(30.5f, 50f, 4.8f, 10f),
    Muscle.TRICEPS to MShape.Oval(89.5f, 50f, 4.8f, 10f),
    Muscle.FOREARMS to MShape.Oval(28f, 78f, 4.2f, 13f),
    Muscle.FOREARMS to MShape.Oval(92f, 78f, 4.2f, 13f),
    Muscle.LOWER_BACK to MShape.RRect(51f, 70f, 18f, 18f, 3f),
    Muscle.GLUTES to MShape.Oval(52f, 100f, 9.5f, 10.5f),
    Muscle.GLUTES to MShape.Oval(68f, 100f, 9.5f, 10.5f),
    Muscle.HAMSTRINGS to MShape.RRect(47f, 114f, 11.5f, 38f, 5f),
    Muscle.HAMSTRINGS to MShape.RRect(61.5f, 114f, 11.5f, 38f, 5f),
    Muscle.CALVES to MShape.Oval(52f, 164f, 6.5f, 15f),
    Muscle.CALVES to MShape.Oval(68f, 164f, 6.5f, 15f),
)

fun intensityColor(level: Int): Color = when (level) {
    1 -> Color(0xFF2E8B57) // 轻度：绿
    2 -> Color(0xFFE6A23C) // 中度：橙
    3 -> Color(0xFFE0533D) // 高强度：红
    else -> Color(0xFF3E434C) // 未锻炼：灰
}

val intensityLabels = listOf("未锻炼", "轻度", "中度", "高强度")

@Composable
fun MuscleMap(
    scores: Map<String, Double>,
    intensityOf: (Double) -> Int,
    modifier: Modifier = Modifier,
    onPick: (Muscle?) -> Unit,
) {
    var picked by remember { mutableStateOf<Muscle?>(null) }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(260.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MuscleView(Side.FRONT, scores, intensityOf,
                Modifier.weight(1f).fillMaxHeight()) { picked = it; onPick(it) }
            MuscleView(Side.BACK, scores, intensityOf,
                Modifier.weight(1f).fillMaxHeight()) { picked = it; onPick(it) }
        }
        if (picked != null) {
            val score = scores[picked!!.id] ?: 0.0
            val level = intensityOf(score)
            Text(
                "${picked!!.label} · ${intensityLabels[level]} · 刺激分 ${fmt1(score)}",
                fontSize = 13.sp, color = if (level == 0) TextLo else intensityColor(level),
                modifier = Modifier.align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp).clip(RoundedCornerShape(8.dp))
                    .background(Ink2).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        } else {
            Text(
                "点击肌肉部位查看当天刺激情况",
                fontSize = 12.sp, color = TextLo,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun MuscleView(
    side: Side,
    scores: Map<String, Double>,
    intensityOf: (Double) -> Int,
    modifier: Modifier,
    onTap: (Muscle?) -> Unit,
) {
    val shapes = if (side == Side.FRONT) frontShapes else backShapes
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (side == Side.FRONT) "正面" else "背面", fontSize = 11.sp, color = TextLo)
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(top = 4.dp)
                .pointerInput(side) {
                    detectTapGestures { pos ->
                        val unit = min(size.width / 120f, size.height / 240f)
                        val ox = (size.width - 120f * unit) / 2f
                        val oy = (size.height - 240f * unit) / 2f
                        val mx = (pos.x - ox) / unit
                        val my = (pos.y - oy) / unit
                        val hit = shapes.lastOrNull { (_, s) -> s.bounds().contains(Offset(mx, my)) }
                        onTap(hit?.first)
                    }
                }
        ) {
            val unit = min(size.width / 120f, size.height / 240f)
            val ox = (size.width - 120f * unit) / 2f
            val oy = (size.height - 240f * unit) / 2f
            fun T(s: MShape) = s.scaled(unit, ox, oy)

            bodyParts.forEach { s -> drawShape(T(s), Ink2) }
            shapes.forEach { (m, s) ->
                val level = intensityOf(scores[m.id] ?: 0.0)
                drawShape(T(s), intensityColor(level))
            }
            bodyParts.forEach { s ->
                drawShape(T(s), Ink3, outline = true)
            }
        }
    }
}
