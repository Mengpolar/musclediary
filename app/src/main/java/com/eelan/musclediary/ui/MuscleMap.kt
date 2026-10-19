package com.eelan.musclediary.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import com.eelan.musclediary.ui.theme.Ink1
import com.eelan.musclediary.ui.theme.Ink2
import com.eelan.musclediary.ui.theme.Ink3
import com.eelan.musclediary.ui.theme.TextHi
import com.eelan.musclediary.ui.theme.TextLo
import kotlin.math.min

/**
 * 人体肌肉概览图：正面 + 背面，模型坐标 120 宽 × 240 高。
 * 灰色 = 未锻炼；刺激分越高颜色越暖（绿→橙→红）。
 * 命中判定用形状路径（点在形内），选中肌群高亮描边，颜色变化带过渡动画。
 */

private class MShape(val pts: List<Offset>, val oval: OvalSpec? = null) {
    data class OvalSpec(val cx: Float, val cy: Float, val rx: Float, val ry: Float)

    fun scaled(unit: Float, ox: Float, oy: Float): MShape = MShape(
        pts.map { Offset(ox + it.x * unit, oy + it.y * unit) },
        oval?.let { OvalSpec(ox + it.cx * unit, oy + it.cy * unit, it.rx * unit, it.ry * unit) },
    )

    fun contains(p: Offset): Boolean = when {
        // 椭圆：标准方程判定
        oval != null -> {
            val dx = (p.x - oval.cx) / oval.rx
            val dy = (p.y - oval.cy) / oval.ry
            dx * dx + dy * dy <= 1f
        }
        // 多边形：射线法
        else -> {
            var inside = false
            var j = pts.size - 1
            for (i in pts.indices) {
                val a = pts[i]; val b = pts[j]
                if ((a.y > p.y) != (b.y > p.y) &&
                    p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x
                ) inside = !inside
                j = i
            }
            inside
        }
    }

    fun path(): Path = Path().apply {
        if (oval != null) {
            addOval(androidx.compose.ui.geometry.Rect(
                oval.cx - oval.rx, oval.cy - oval.ry, oval.cx + oval.rx, oval.cy + oval.ry))
        } else {
            moveTo(pts[0].x, pts[0].y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
    }
}

private fun poly(vararg xy: Float) = MShape(
    List(xy.size / 2) { i -> Offset(xy[i * 2], xy[i * 2 + 1]) })

private fun oval(cx: Float, cy: Float, rx: Float, ry: Float) =
    MShape(emptyList(), MShape.OvalSpec(cx, cy, rx, ry))

// —— 身体底图（轮廓）：躯干曲线化，四肢贴合肌群位置 ——
private val bodyParts: List<MShape> = listOf(
    oval(60f, 15f, 8.5f, 10f),      // 头
    poly(54f, 24f, 66f, 24f, 64f, 31f, 56f, 31f),  // 颈
    // 躯干：肩宽 → 收腰 → 臀，两侧曲线过渡
    poly(38f, 32f, 50f, 31f, 70f, 31f, 82f, 32f,
         84f, 46f, 80f, 68f, 76f, 86f, 74f, 100f, 76f, 106f,
         66f, 112f, 54f, 112f, 44f, 106f, 46f, 100f,
         44f, 86f, 40f, 68f, 36f, 46f),
    // 左臂：肩到腕
    poly(36f, 34f, 41f, 36f, 36f, 62f, 31f, 88f, 25f, 89f, 28f, 60f),
    // 右臂
    poly(84f, 34f, 79f, 36f, 84f, 62f, 89f, 88f, 95f, 89f, 92f, 60f),
    // 左腿：大腿到脚
    poly(45f, 108f, 57f, 111f, 55f, 155f, 53f, 196f, 50f, 212f, 42f, 212f, 45f, 175f, 41f, 140f),
    // 右腿
    poly(75f, 108f, 63f, 111f, 65f, 155f, 67f, 196f, 70f, 212f, 78f, 212f, 75f, 175f, 79f, 140f),
)

// —— 正面肌群 ——
private val frontShapes: List<Pair<Muscle, MShape>> = listOf(
    // 肩：三角肌贴肩点
    Muscle.SHOULDERS to poly(36f, 33f, 44f, 32f, 45f, 42f, 41f, 46f, 35f, 42f),
    Muscle.SHOULDERS to poly(84f, 33f, 76f, 32f, 75f, 42f, 79f, 46f, 85f, 42f),
    // 胸：左右两块，中间留缝
    Muscle.CHEST to poly(44f, 33f, 59f, 33f, 59f, 48f, 48f, 50f, 43f, 44f),
    Muscle.CHEST to poly(76f, 33f, 61f, 33f, 61f, 48f, 72f, 50f, 77f, 44f),
    // 肱二头：上臂中段
    Muscle.BICEPS to oval(33f, 50f, 4.5f, 9f),
    Muscle.BICEPS to oval(87f, 50f, 4.5f, 9f),
    // 前臂
    Muscle.FOREARMS to oval(29f, 78f, 4f, 11f),
    Muscle.FOREARMS to oval(91f, 78f, 4f, 11f),
    // 腹：外框 + 分块视觉（用一块多边形，画时叠加中线）
    Muscle.ABS to poly(50f, 54f, 70f, 54f, 71f, 72f, 68f, 80f, 52f, 80f, 49f, 72f),
    // 股四头：大腿前侧
    Muscle.QUADS to poly(45f, 112f, 57f, 112f, 56f, 152f, 46f, 150f, 43f, 128f),
    Muscle.QUADS to poly(75f, 112f, 63f, 112f, 64f, 152f, 74f, 150f, 77f, 128f),
    // 小腿
    Muscle.CALVES to oval(51f, 168f, 6f, 14f),
    Muscle.CALVES to oval(69f, 168f, 6f, 14f),
)

// —— 背面肌群 ——
private val backShapes: List<Pair<Muscle, MShape>> = listOf(
    // 斜方肌：颈部两侧延伸
    Muscle.TRAPS to poly(48f, 30f, 72f, 30f, 68f, 46f, 60f, 50f, 52f, 46f),
    // 背阔肌：腋下到腰，左右
    Muscle.LATS to poly(44f, 36f, 55f, 48f, 56f, 74f, 48f, 82f, 42f, 66f),
    Muscle.LATS to poly(76f, 36f, 65f, 48f, 64f, 74f, 72f, 82f, 78f, 66f),
    // 肱三头：上臂后侧
    Muscle.TRICEPS to oval(33f, 50f, 4.5f, 9f),
    Muscle.TRICEPS to oval(87f, 50f, 4.5f, 9f),
    // 前臂
    Muscle.FOREARMS to oval(29f, 78f, 4f, 11f),
    Muscle.FOREARMS to oval(91f, 78f, 4f, 11f),
    // 下背：腰腹对应位置
    Muscle.LOWER_BACK to poly(50f, 62f, 70f, 62f, 69f, 78f, 51f, 78f),
    // 臀：左右两瓣
    Muscle.GLUTES to oval(53f, 96f, 8f, 9f),
    Muscle.GLUTES to oval(67f, 96f, 8f, 9f),
    // 腘绳肌：大腿后侧
    Muscle.HAMSTRINGS to poly(45f, 108f, 57f, 108f, 56f, 150f, 46f, 148f, 43f, 126f),
    Muscle.HAMSTRINGS to poly(75f, 108f, 63f, 108f, 64f, 150f, 74f, 148f, 77f, 126f),
    // 小腿
    Muscle.CALVES to oval(51f, 168f, 6f, 14f),
    Muscle.CALVES to oval(69f, 168f, 6f, 14f),
)

// 未锻炼：从 Ink3 提亮一档，与底图（Ink2）和轮廓（Ink3）拉开层次
private val IdleColor = Color(0xFF4A505B)
private val OutlineColor = Color(0xFF5A616C)

fun intensityColor(level: Int): Color = when (level) {
    1 -> Color(0xFF66BB6A) // 轻度：绿
    2 -> Color(0xFFE6A23C) // 中度：橙
    3 -> Color(0xFFE0533D) // 高强度：红
    else -> IdleColor
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
            MuscleView(Side.FRONT, scores, intensityOf, picked,
                Modifier.weight(1f).fillMaxHeight()) { picked = it; onPick(it) }
            MuscleView(Side.BACK, scores, intensityOf, picked,
                Modifier.weight(1f).fillMaxHeight()) { picked = it; onPick(it) }
        }
        Spacer(Modifier.height(6.dp))
        // 色带图例：绿→橙→红 对应 轻/中/高
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(intensityColor(1), CircleShape))
            Text("轻度", fontSize = 11.sp, color = TextLo, modifier = Modifier.padding(start = 3.dp, end = 10.dp))
            Box(Modifier.size(8.dp).background(intensityColor(2), CircleShape))
            Text("中度", fontSize = 11.sp, color = TextLo, modifier = Modifier.padding(start = 3.dp, end = 10.dp))
            Box(Modifier.size(8.dp).background(intensityColor(3), CircleShape))
            Text("高强度", fontSize = 11.sp, color = TextLo, modifier = Modifier.padding(start = 3.dp))
        }
        Spacer(Modifier.height(4.dp))
        if (picked != null) {
            val score = scores[picked!!.id] ?: 0.0
            val level = intensityOf(score)
            Text(
                "${picked!!.label} · ${intensityLabels[level]} · 刺激分 ${fmt1(score)}",
                fontSize = 13.sp, color = if (level == 0) TextHi else intensityColor(level),
                modifier = Modifier.align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Ink2).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        } else {
            Text(
                "点击肌肉部位查看当天刺激情况",
                fontSize = 12.sp, color = TextLo,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun MuscleView(
    side: Side,
    scores: Map<String, Double>,
    intensityOf: (Double) -> Int,
    picked: Muscle?,
    modifier: Modifier,
    onTap: (Muscle?) -> Unit,
) {
    val shapes = if (side == Side.FRONT) frontShapes else backShapes
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (side == Side.FRONT) "正面" else "背面", fontSize = 12.sp, color = TextLo)
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(top = 4.dp)
                .pointerInput(side, shapes) {
                    detectTapGestures { pos ->
                        val unit = min(size.width / 120f, size.height / 240f)
                        val ox = (size.width - 120f * unit) / 2f
                        val oy = (size.height - 240f * unit) / 2f
                        val mx = (pos.x - ox) / unit
                        val my = (pos.y - oy) / unit
                        val p = Offset(mx, my)
                        // 点在形内才算命中（从最后绘制的往上找，保证小块肌群优先）
                        val hit = shapes.lastOrNull { (_, s) -> s.contains(p) }?.first
                        onTap(hit)
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
                drawShape(T(s), levelColor(level, scores[m.id] ?: 0.0, intensityOf))
            }
            // 轮廓
            bodyParts.forEach { s -> drawShape(T(s), OutlineColor, outline = true) }
            // 腹肌中线与横线（仅正面）
            if (side == Side.FRONT) {
                val u = unit
                drawLine(OutlineColor, Offset(ox + 60f * u, oy + 55f * u), Offset(ox + 60f * u, oy + 79f * u), 1.2f)
                drawLine(OutlineColor, Offset(ox + 51f * u, oy + 63f * u), Offset(ox + 69f * u, oy + 63f * u), 1.2f)
                drawLine(OutlineColor, Offset(ox + 51f * u, oy + 71f * u), Offset(ox + 69f * u, oy + 71f * u), 1.2f)
            }
            // 选中肌群高亮描边（把同肌群所有形状一起描）
            if (picked != null) {
                shapes.forEach { (m, s) ->
                    if (m == picked) drawShape(T(s), Color.White, outline = true, strokeWidth = 4f)
                }
            }
        }
    }
}

/** 每个形状的颜色：按刺激分在档位间平滑插值（0→灰、0→20 绿渐深、20→50 绿→橙、50+ 橙→红） */
private fun levelColor(level: Int, score: Double, intensityOf: (Double) -> Int): Color {
    val green = intensityColor(1); val orange = intensityColor(2); val red = intensityColor(3)
    return when {
        score <= 0.0 -> IdleColor
        score < 10.0 -> lerp(IdleColor, green, (score / 10.0).toFloat())
        score < 20.0 -> green
        score < 35.0 -> lerp(green, orange, ((score - 20.0) / 15.0).toFloat())
        score < 50.0 -> orange
        score < 70.0 -> lerp(orange, red, ((score - 50.0) / 20.0).toFloat())
        else -> red
    }
}

private fun lerp(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

private fun DrawScope.drawShape(s: MShape, color: Color, outline: Boolean = false, strokeWidth: Float = 2f) {
    val style = if (outline) Stroke(strokeWidth) else Fill
    drawPath(s.path(), color, style = style)
}
