package com.eelan.musclediary.ui

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eelan.musclediary.ui.theme.*

/**
 * 「铁馆夜训」方向专属组件：
 * - [HeroNumber] 灯牌大数字（负字距 + tabular-nums）
 * - [PhaseBadge] 时期描边胶囊徽章
 * - [PillNavBar] 底部悬浮胶囊导航（选中色块平滑移动）
 */

/** 灯牌大数字：热量等核心指标的主展示 */
@Composable
fun HeroNumber(
    value: String,
    unit: String = "",
    modifier: Modifier = Modifier,
    color: Color = TextHi,
    unitColor: Color = TextLo,
) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            value,
            fontSize = 46.sp, fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1.5).sp, color = color,
            lineHeight = 46.sp,
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

/** 底部悬浮胶囊导航 */
@Composable
fun PillNavBar(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .padding(horizontal = 24.dp, vertical = 14.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(Ink1)
            .padding(5.dp),
    ) {
        Row {
            tabs.forEachIndexed { i, label ->
                val bg by animateColorAsState(
                    if (i == selected) Accent else Color.Transparent,
                    label = "navBg",
                )
                val fg by animateColorAsState(
                    if (i == selected) Color(0xFF1A1208) else TextLo,
                    label = "navFg",
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(bg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(i) }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                ) {
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg)
                }
            }
        }
    }
}
