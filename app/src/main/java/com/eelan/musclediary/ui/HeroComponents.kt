package com.eelan.musclediary.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eelan.musclediary.ui.theme.*
import kotlin.math.roundToInt

/**
 * 「铁馆夜训」方向专属组件：
 * - [HeroNumber] 灯牌大数字（负字距 + tabular-nums）
 * - [PhaseBadge] 时期描边胶囊徽章
 * - [PillNavBar] 底部悬浮胶囊导航（选中色块平滑移动）
 */

/** 通用滚动计数文本：数值变化时从旧值平滑滚动到新值（450ms）；非数字内容静态展示 */
@Composable
fun AnimatedCountText(
    value: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 46.sp,
    color: Color = TextHi,
    letterSpacing: androidx.compose.ui.unit.TextUnit = 0.sp,
) {
    val target = value.toDoubleOrNull()
    val shown = if (target != null) {
        val anim by animateIntAsState(
            targetValue = target.roundToInt(),
            animationSpec = tween(450, easing = FastOutSlowInEasing),
            label = "countAnim",
        )
        anim.toString()
    } else value
    Text(
        shown,
        fontSize = fontSize, color = color,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = letterSpacing,
        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
        modifier = modifier,
    )
}

/** 灯牌大数字：热量等核心指标的主展示；数值变化时从旧值滚动到新值（450ms） */
@Composable
fun HeroNumber(
    value: String,
    unit: String = "",
    modifier: Modifier = Modifier,
    color: Color = TextHi,
    unitColor: Color = TextLo,
) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        AnimatedCountText(
            value,
            fontSize = 46.sp,
            color = color,
            letterSpacing = (-1.5).sp,
        )
        if (unit.isNotEmpty()) {
            Text(
                " $unit",
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = unitColor,
                modifier = Modifier.padding(bottom = 7.dp),
            )
        }
    }
}

/** 时期徽章：描边胶囊（增肌期/减脂期） */
@Composable
fun PhaseBadge(text: String, color: Color = Accent) {
    Text(
        text,
        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(99.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** 底部悬浮胶囊导航：均分占满可用宽度 */
@Composable
fun PillNavBar(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 液态 Tab：文字不动，色块在标签间流动——按运动方向拉伸、接近目标回弹收缩
    val animSelected by animateFloatAsState(
        targetValue = selected.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "liquidPos",
    )

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(Ink1)
            .padding(5.dp),
    ) {
        // 流动的液态色块：位于 selected 与上一位置之间，途经中间时横向拉伸
        BoxWithConstraints(Modifier.matchParentSize()) {
            val tabW = maxWidth / tabs.size
            // 相对上一位置的偏移量（速度感）：拉伸量与移动距离成正比，两端小中间大
            val stretch = (1f - kotlin.math.abs(animSelected - selected)).coerceIn(0f, 1f)
            val stretchW = tabW * (1f + 0.45f * stretch)
            Box(
                Modifier
                    .offset(x = tabW * animSelected + (tabW - stretchW) / 2)
                    .width(stretchW)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(99.dp))
                    .background(Accent),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { i, label ->
                val fg by animateColorAsState(
                    if (i == selected) Color(0xFF1A1208) else TextLo,
                    animationSpec = tween(260),
                    label = "navFg",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(i) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg)
                }
            }
        }
    }
}
