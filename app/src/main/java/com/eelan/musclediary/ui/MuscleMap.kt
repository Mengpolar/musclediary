package com.eelan.musclediary.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eelan.musclediary.L
import com.eelan.musclediary.domain.Muscle
import com.eelan.musclediary.domain.Side
import com.eelan.musclediary.ui.theme.Ink2
import com.eelan.musclediary.ui.theme.Ink3
import com.eelan.musclediary.ui.theme.TextHi
import com.eelan.musclediary.ui.theme.TextLo
import kotlin.math.min

/**
 * 人体肌肉概览图（素材见 MuscleArt.kt）：正面 + 背面。
 * 灰色 = 未锻炼；刺激分越高颜色越暖（灰→绿→橙→红），训练部位叠加同色强度光晕。
 * 命中判定用与绘制同源的离散多边形（所见即所点），选中肌群呼吸描边。
 */

// 未锻炼：从 Ink3 提亮一档，与底图（Ink2）拉开层次
private val IdleColor = Color(0xFF4A505B)
private val OutlineColor = Color(0xFF5A616C)

fun intensityColor(level: Int): Color = when (level) {
    1 -> Color(0xFF66BB6A) // 轻度：绿
    2 -> Color(0xFFE6A23C) // 中度：橙
    3 -> Color(0xFFE0533D) // 高强度：红
    else -> IdleColor
}

/** 档位名：调用时取词，跟随语言切换刷新（不能用顶层 val——类加载时求值一次就冻结了） */
fun intensityLabel(level: Int): String = when (level) {
    1 -> L("轻度", "Light")
    2 -> L("中度", "Moderate")
    3 -> L("高强度", "Intense")
    else -> L("未锻炼", "Idle")
}

@Composable
fun MuscleMap(
    scores: Map<String, Double>,
    intensityOf: (Double) -> Int,
    modifier: Modifier = Modifier,
    onPick: (Muscle?) -> Unit,
) {
    var picked by remember { mutableStateOf<Muscle?>(null) }
    // 选中描边呼吸：以 lambda 传入，draw 阶段按需读取（未选中时不订阅、零重绘）
    val pulseTransition = rememberInfiniteTransition(label = "pick")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "pickPulse",
    )
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(260.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MuscleView(Side.FRONT, scores, intensityOf, picked, { pulse },
                Modifier.weight(1f).fillMaxHeight()) { picked = it; onPick(it) }
            MuscleView(Side.BACK, scores, intensityOf, picked, { pulse },
                Modifier.weight(1f).fillMaxHeight()) { picked = it; onPick(it) }
        }
        Spacer(Modifier.height(8.dp))
        // 色带图例铭牌：绿→橙→红 对应 轻/中/高
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(1, 2, 3).forEach { lv ->
                Row(
                    Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(Ink2)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).background(intensityColor(lv), CircleShape))
                    Text(intensityLabel(lv), fontSize = 10.sp, color = TextLo,
                        modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (picked != null) {
            val score = scores[picked!!.id] ?: 0.0
            val level = intensityOf(score)
            Row(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Ink2)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).background(intensityColor(level), CircleShape))
                Text(
                    " ${Muscle.labelOf(picked!!.id)} · ${intensityLabel(level)} · " + L("刺激分 ", "score ") + "${fmt1(score)}",
                    fontSize = 13.sp, color = if (level == 0) TextHi else intensityColor(level),
                )
            }
        } else {
            Text(
                L("点击肌肉部位查看当天刺激情况", "Tap a muscle to see today's stimulus"),
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
    pulse: () -> Float,
    modifier: Modifier,
    onTap: (Muscle?) -> Unit,
) {
    val muscles = if (side == Side.FRONT) artFront else artBack
    val deco = if (side == Side.FRONT) artFrontDeco else emptyList()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (side == Side.FRONT) L("正面", "Front") else L("背面", "Back"),
            fontSize = 10.sp, color = TextLo, letterSpacing = 1.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(Ink2)
                .padding(horizontal = 10.dp, vertical = 2.dp),
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(top = 6.dp)
                .pointerInput(side) {
                    detectTapGestures { pos ->
                        val unit = min(size.width / 120f, size.height / 240f)
                        val ox = (size.width - 120f * unit) / 2f
                        val oy = (size.height - 240f * unit) / 2f
                        val mx = (pos.x - ox) / unit
                        val my = (pos.y - oy) / unit
                        val p = Offset(mx, my)
                        // 命中判定与绘制同源（从最后绘制的往前找，小块肌群优先）
                        val hit = muscles.lastOrNull { it.contains(p) }?.muscle
                        onTap(hit)
                    }
                }
        ) {
            val unit = min(size.width / 120f, size.height / 240f)
            val ox = (size.width - 120f * unit) / 2f
            val oy = (size.height - 240f * unit) / 2f

            // 底图（躯干四肢），单色填充 + 描边
            artBody.forEach { s ->
                s.draw(this, unit, ox, oy, color = Ink2)
                s.draw(this, unit, ox, oy, color = OutlineColor, outline = true, strokeWidth = 1.5f)
            }

            // 肌群：左侧用纵向渐变（上亮下暗）制造顶灯体积感；镜像侧用反向渐变
            // 逐侧绘制：left 形状用 (top→bottom) 渐变，right 镜像形状单独绘制同样渐变
            fun muscleBrush(): Brush = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
            )
            muscles.forEach { s ->
                val sc = scores[s.muscle!!.id] ?: 0.0
                val c = levelColor(intensityOf(sc), sc)
                // 光晕（有训练时）：同色低透明粗描边垫在实体下
                if (sc > 0.0) {
                    val halo = c.copy(alpha = ((0.10 + sc / 60.0).coerceAtMost(0.35)).toFloat())
                    s.draw(this, unit, ox, oy, color = halo, outline = true, strokeWidth = unit * 1.8f)
                }
                s.draw(this, unit, ox, oy, color = c)
                // 体积高光：同形状再叠一层顶部渐白
                s.draw(this, unit, ox, oy, brush = muscleBrush())
            }
            deco.forEach { s ->
                s.draw(this, unit, ox, oy, color = OutlineColor, outline = true, strokeWidth = 1.2f)
            }

            // 轮廓（最后描一圈，压住肌群与底图的接缝）
            artBody.forEach { s ->
                s.draw(this, unit, ox, oy, color = OutlineColor, outline = true, strokeWidth = 1.5f)
            }

            // 选中肌群高亮描边呼吸（把同肌群所有形状一起描；仅在选中时读取动画值）
            if (picked != null) {
                val a = 0.5f + 0.5f * pulse()
                muscles.forEach { s ->
                    if (s.muscle == picked) s.draw(this, unit, ox, oy,
                        color = Color.White.copy(alpha = a), outline = true, strokeWidth = 3.5f)
                }
            }
        }
    }
}

/** 每个形状的颜色：按刺激分在档位间平滑插值（0→灰、0→20 绿渐深、20→50 绿→橙、50+ 橙→红） */
private fun levelColor(level: Int, score: Double): Color {
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
