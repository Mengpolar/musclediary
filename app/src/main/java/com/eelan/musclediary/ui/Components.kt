package com.eelan.musclediary.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eelan.musclediary.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 环形进度（数值变化带平滑动画），点击可弹推导面板 */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    size: Dp,
    stroke: Dp = 10.dp,
    centerLabel: String,
    centerValue: String,
    subValue: String,
    onClick: () -> Unit,
    valueColor: Color = TextHi,
) {
    val track = Ink3
    val animProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(700),
        label = "ringProgress",
    )
    Box(
        modifier = Modifier
            .size(size)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            drawArc(
                color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                style = Stroke(s, cap = StrokeCap.Round),
            )
            drawArc(
                color = color, startAngle = -90f,
                sweepAngle = animProgress * 360f, useCenter = false,
                style = Stroke(s, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerLabel, fontSize = 11.sp, color = TextLo)
            Text(centerValue, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = valueColor)
            Text(subValue, fontSize = 10.sp, color = TextLo)
        }
    }
}

/**
 * 宏量营养横条：两个刻度标记——「目标所需」(每公斤基础) 与「热量所需」(热量等比分摊后)。
 * 摄入达到较低刻度 → 数值标绿；超过较高刻度 → 数值标红。
 */
@Composable
fun MacroBar(
    label: String,
    value: Double,
    baseTarget: Double,
    scaledTarget: Double,
    color: Color,
    onClick: () -> Unit,
) {
    val scaleMax = maxOf(value, baseTarget, scaledTarget, 1.0)
    val low = minOf(baseTarget, scaledTarget)
    val high = maxOf(baseTarget, scaledTarget)
    val valueColor = when {
        high > 0 && value > high -> Color(0xFFEF5350)   // 超过热量所需 → 红
        low > 0 && value >= low -> Good                  // 满足目标所需 → 绿
        else -> TextHi
    }
    val animFill by animateFloatAsState(
        targetValue = (value / scaleMax).toFloat(),
        animationSpec = tween(700),
        label = "macroFill",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, color = TextLo)
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier.size(6.dp).background(color, androidx.compose.foundation.shape.CircleShape)
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${fmt1(value)} / ${fmt1(scaledTarget)} g",
                fontSize = 12.sp, fontWeight = FontWeight.Medium, color = valueColor,
            )
        }
        Spacer(Modifier.height(4.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
        ) {
            val w = size.width
            val h = size.height
            val r = androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2)
            // 底槽
            drawRoundRect(Ink2, topLeft = Offset(0f, 0f), size = Size(w, h), cornerRadius = r)
            // 已摄入
            drawRoundRect(
                color,
                topLeft = Offset(0f, 0f),
                size = Size((animFill * w).toFloat().coerceAtLeast(h), h),
                cornerRadius = r,
            )
            // 刻度：目标所需（白）/ 热量所需（主色）
            fun tick(t: Double, c: Color) {
                val x = (t / scaleMax * w).toFloat().coerceIn(0f, w)
                drawLine(c, Offset(x, -2f), Offset(x, h + 2f), strokeWidth = 2.5f)
            }
            tick(baseTarget, Color(0xFFB9BEC5))
            if (kotlin.math.abs(scaledTarget - baseTarget) > 0.5) tick(scaledTarget, Accent)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "刻度：白=目标所需 ${fmt1(baseTarget)}g · 橙=热量所需 ${fmt1(scaledTarget)}g",
            fontSize = 9.sp, color = TextLo,
        )
    }
}

/** 统一卡片：可选标题栏 */
@Composable
fun CardBox(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink1)
            .padding(16.dp),
    ) {
        if (title != null) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextHi)
            Spacer(Modifier.height(8.dp))
        }
        content()
    }
}

/** 统一空状态：图标 + 文案 + 可选操作按钮 */
@Composable
fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Ink1)
            .padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = Ink3, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(10.dp))
        Text(text, fontSize = 13.sp, color = TextLo, textAlign = TextAlign.Center)
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onAction) { Text(actionText, color = Accent, fontSize = 13.sp) }
        }
    }
}

fun fmt1(v: Double): String = if (v >= 100) v.toInt().toString() else String.format("%.1f", v)
fun fmt0(v: Double): String = v.toInt().toString()

/** 数字输入对话框 */
@Composable
fun NumberDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        title = { Text(title, color = TextHi) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text(label, color = TextLo) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = TextHi, unfocusedTextColor = TextHi,
                    focusedContainerColor = Ink1, unfocusedContainerColor = Ink1,
                    focusedIndicatorColor = Accent, unfocusedIndicatorColor = Ink3,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                text.toDoubleOrNull()?.let { if (it > 0) onConfirm(it) }
            }) { Text("确定", color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextLo) } },
    )
}

/** 左滑显示「修改」「删」按钮的记录卡片 */
@Composable
fun SwipeEntryRow(
    title: String,
    detail: String,
    trailing: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxSwipePx = with(density) { 132.dp.toPx() }
    val offset = remember { Animatable(0f) }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
    ) {
        // 背景操作按钮（右侧）
        Row(
            Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                Modifier
                    .width(66.dp)
                    .fillMaxHeight()
                    .background(Protein.copy(alpha = 0.85f))
                    .clickable {
                        scope.launch { offset.animateTo(0f) }
                        onEdit()
                    },
                contentAlignment = Alignment.Center,
            ) { Text("修改", color = Ink0, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            Box(
                Modifier
                    .width(66.dp)
                    .fillMaxHeight()
                    .background(Color(0xFFEF5350))
                    .clickable {
                        scope.launch { offset.animateTo(0f) }
                        onDelete()
                    },
                contentAlignment = Alignment.Center,
            ) { Text("删除", color = Ink0, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
        // 前景内容（自身撑开高度，背景按钮 matchParentSize 跟随）
        Row(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .background(Ink2)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val target = if (offset.value < -maxSwipePx / 2) -maxSwipePx else 0f
                                offset.animateTo(target, tween(150))
                            }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offset.snapTo((offset.value + dragAmount).coerceIn(-maxSwipePx, 0f))
                        }
                    }
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, color = TextHi, fontWeight = FontWeight.Medium)
                Text(detail, fontSize = 12.sp, color = TextLo)
            }
            Text(trailing, fontSize = 14.sp, color = Accent, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End)
        }
    }
}

/** 页面标题 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier.padding(vertical = 8.dp),
        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextHi,
    )
}
