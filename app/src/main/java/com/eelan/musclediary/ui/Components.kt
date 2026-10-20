package com.eelan.musclediary.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.composed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
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
            Text(centerLabel, fontSize = 12.sp, color = TextLo)
            Text(centerValue, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = valueColor)
            Text(subValue, fontSize = 12.sp, color = TextLo)
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
        // 质感宏量条：加宽轨道 + 填充区从左往右流动的粒子 + 进度末尾滚动波浪
        val infinite = rememberInfiniteTransition(label = "macroFx")
        val wavePhase by infinite.animateFloat(
            initialValue = 0f, targetValue = (2f * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
            label = "wavePhase",
        )
        val particlePhase by infinite.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
            label = "particlePhase",
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
        ) {
            val w = size.width
            val h = size.height
            val r = androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2)
            val fillW = (animFill * w).toFloat().coerceAtLeast(h)
            // 底槽（内描高光增加立体感）
            drawRoundRect(Ink2, topLeft = Offset(0f, 0f), size = Size(w, h), cornerRadius = r)
            drawRoundRect(
                Color.White.copy(alpha = 0.05f),
                topLeft = Offset(h * 0.12f, h * 0.10f),
                size = Size(w - h * 0.24f, h * 0.34f),
                cornerRadius = r,
            )
            // 已摄入填充（带纵向渐变：上亮下暗，液体感）
            drawRoundRect(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.65f)),
                    startY = 0f, endY = h,
                ),
                topLeft = Offset(0f, 0f),
                size = Size(fillW, h),
                cornerRadius = r,
            )
            // 填充区粒子：从左往右流动的小亮点（裁剪在填充区内）
            clipRect(left = 0f, top = 0f, right = fillW, bottom = h) {
                repeat(6) { i ->
                    val speed = 0.55f + i * 0.13f
                    val px = ((particlePhase * speed + i * 0.17f) % 1f) * w
                    val py = h * (0.28f + 0.44f * ((i * 0.37f) % 1f))
                    val pr = h * (0.055f + 0.03f * ((i * 0.53f) % 1f))
                    drawCircle(Color.White.copy(alpha = 0.30f), radius = pr, center = Offset(px, py))
                }
            }
            // 进度末尾的滚动波浪：正弦波覆盖末尾一小段，随时间滚动
            val waveW = h * 1.6f
            if (fillW < w - 2f) {
                val waveStart = (fillW - waveW * 0.4f).coerceAtLeast(0f)
                val path = androidx.compose.ui.graphics.Path()
                val amp = h * 0.16f
                path.moveTo(waveStart, h / 2)
                var x = waveStart
                while (x <= fillW) {
                    val phase = wavePhase + (x - waveStart) / waveW * (2f * Math.PI).toFloat() * 1.5f
                    path.lineTo(x, h / 2 + amp * kotlin.math.sin(phase))
                    x += 3f
                }
                path.lineTo(fillW, h)
                path.lineTo(waveStart, h)
                path.close()
                clipRect(left = waveStart, top = 0f, right = fillW, bottom = h) {
                    drawPath(path, color.copy(alpha = 0.85f))
                }
            }
            // 刻度：目标所需（白）/ 热量所需（主色）——短齿从轨道上缘垂下，只占一半高度
            fun tick(t: Double, c: Color) {
                val x = (t / scaleMax * w).toFloat().coerceIn(0f, w)
                drawLine(c, Offset(x, -1f), Offset(x, h * 0.55f), strokeWidth = 2.5f)
            }
            tick(baseTarget, Color(0xFFB9BEC5))
            if (kotlin.math.abs(scaledTarget - baseTarget) > 0.5) tick(scaledTarget, Accent)
        }
        Spacer(Modifier.height(2.dp))
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
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextHi)
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

fun fmt1(v: Double): String = if (v >= 100) Math.round(v).toInt().toString() else String.format("%.1f", v)
fun fmt0(v: Double): String = Math.round(v).toInt().toString()

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
    val v = text.toDoubleOrNull()
    val valid = v != null && v > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        title = { Text(title, color = TextHi) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                if (!valid) Text("请输入大于 0 的数字", fontSize = 12.sp, color = Warn)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onConfirm(v!!)
            }) { Text("确定", color = if (valid) Accent else TextLo) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextLo) } },
    )
}

/** 左滑显示「修改」「删」按钮的记录卡片；删除时卡片绕右端点沿弧线飞出（缩小+旋转+淡出） */
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
    // 粉碎删除：0=正常，1=粉碎完成。碎裂前沿从右往左推进，右侧先碎（下坠+缩小+淡出）
    val crush = remember { Animatable(0f) }
    var flying by remember { mutableStateOf(false) }
    val cardW = with(density) { 300.dp.toPx() } // 估算宽度用于推进速度，实际以 draw 裁切为准

    fun deleteWithCrush() {
        flying = true
        scope.launch {
            crush.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            onDelete()
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                if (flying) {
                    // 未碎区域整体：左移（被"吸"向碎裂口）+ 轻微下坠 + 缩小
                    translationX = -size.width * 0.06f * crush.value
                    translationY = size.height * 0.25f * crush.value
                    val sc = 1f - 0.25f * crush.value
                    scaleX = sc; scaleY = sc
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
            }
            .clip(RoundedCornerShape(10.dp))
            .drawWithContent {
                if (!flying) {
                    drawContent()
                } else {
                    // 碎裂进度：前沿从右往左，已碎区域不再绘制（露出列表底色）
                    val front = 1f - crush.value
                    val remainingW = size.width * front
                    clipRect(left = 0f, top = 0f, right = remainingW, bottom = size.height) {
                        this@drawWithContent.drawContent()
                    }
                    // 碎裂口的闪白前沿，推进时可见
                    if (crush.value < 1f && crush.value > 0.02f) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.55f * (1f - crush.value)),
                            start = Offset(remainingW, 0f),
                            end = Offset(remainingW, size.height),
                            strokeWidth = 2f,
                        )
                    }
                }
            },
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
                    .clickable { deleteWithCrush() },
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
        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextHi,
    )
}

/** 标题行 + 右侧「全部 >」入口（进全屏记录历史页） */
@Composable
fun SectionTitleRow(text: String, moreText: String = "全部 >", onMore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextHi,
            modifier = Modifier.weight(1f))
        Text(
            moreText, fontSize = 12.sp, color = Accent,
            modifier = Modifier
                .clickable(onClick = onMore)
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        )
    }
}

/** 快捷输入标签行：最近几次记录（已去重），点击直接填入 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickChips(
    chips: List<String>,
    modifier: Modifier = Modifier,
    onPick: (String) -> Unit,
) {
    if (chips.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("最近：", fontSize = 12.sp, color = TextLo,
            modifier = Modifier.align(Alignment.CenterVertically))
        chips.forEach { c ->
            // 标签舒展：点中瞬间 1 → 1.08 → 1 弹性回落，其余项随布局弹簧让位
            var pressed by remember(c) { mutableStateOf(false) }
            val chipScale by animateFloatAsState(
                targetValue = if (pressed) 0.92f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "chipScale",
            )
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = chipScale
                        scaleY = chipScale
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(Accent.copy(alpha = 0.15f))
                    .clickable {
                        pressed = true
                        onPick(c)
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(c, fontSize = 12.sp, color = Accent)
            }
        }
    }
}

/**
 * 卡片堆叠滚动的宿主容器：Column + verticalScroll，把滚动锚点（容器顶部在窗口中的 y）
 * 共享给子项。子项用 [stackApproach] 即可获得「滚到顶部钉住、被后面卡片叠盖」的堆叠效果。
 */
@Composable
fun StackedColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 32.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    var anchorTop by remember { mutableStateOf(0f) }
    val state = rememberScrollState()
    Column(
        modifier
            .onGloballyPositioned { anchorTop = it.positionInWindow().y }
            .verticalScroll(state),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val pad = contentPadding
        Spacer(Modifier.height(pad.calculateTopPadding()))
        CompositionLocalProvider(LocalStackAnchor provides anchorTop) {
            content()
        }
        Spacer(Modifier.height(pad.calculateBottomPadding()))
    }
}

/** 当前堆叠锚点（容器顶部窗口 y） */
private val LocalStackAnchor = compositionLocalOf { 0f }

/**
 * 卡片堆叠滚动：卡片滚到容器顶部（锚点）后钉住不再上移（translationY 补偿），
 * 后面的卡片继续上滑叠盖在它上面；被叠住的卡片按堆叠深度轻微缩小、变暗，
 * 形成边缘错开的一摞。向下滚动对称展开。须在 Composable 上下文调用。
 */
@Composable
fun Modifier.stackApproach(peekDp: Float = 7f): Modifier {
    val density = LocalDensity.current
    val peekPx = with(density) { peekDp.dp.toPx() }
    val maxSink = peekPx * 6f
    val anchor = LocalStackAnchor.current
    var topY by remember { mutableStateOf(Float.MAX_VALUE) }
    return this
        .zIndex(0f)
        .onGloballyPositioned { topY = it.positionInWindow().y }
        .graphicsLayer {
            if (topY == Float.MAX_VALUE) return@graphicsLayer
            // 卡片顶边超出锚点的量：>0 表示应被钉住
            val over = anchor - topY
            if (over <= 0f) {
                scaleX = 1f; scaleY = 1f; translationY = 0f; alpha = 1f
            } else {
                // 钉住：贴着锚点（补回滚动量），但随被压深度逐渐下沉——
                // 后叠上来的卡停在锚点，先钉住的卡缓缓下沉露出顶部边缘 → 一摞错开的卡
                val sink = (over * 0.18f).coerceAtMost(maxSink)
                val layer = (sink / peekPx).coerceIn(0f, 6f)
                val scale = 1f - 0.03f * layer
                scaleX = scale; scaleY = scale
                translationY = over - sink
                alpha = 1f
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
        }
}

/**
 * 玻璃液体球：进度为球内液体高度，液面带流动正弦波，球体有玻璃高光。
 * progress 变化时液体高度平滑过渡（Animatable），不跳变。
 * centerLabel 可为 null（不显示文字行）。
 */
@Composable
fun LiquidGlobe(
    progress: Float,
    color: Color,
    sizeDp: Dp,
    modifier: Modifier = Modifier,
    centerText: String? = null,
    onClick: (() -> Unit)? = null,
) {
    // 液体高度平滑渐变（数值/日期改变时不突然变化）
    val liquid by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "liquidLevel",
    )
    val infinite = rememberInfiniteTransition(label = "globeFx")
    val wavePhase by infinite.animateFloat(
        initialValue = 0f, targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "globeWave",
    )
    Box(
        modifier
            .size(sizeDp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val c = Offset(cx, cy)
            // 球体底色（深色玻璃）
            drawCircle(Ink2, radius = r, center = c)
            // 液体：以液面 y 裁剪画圆形液体，液面为两段叠加正弦波
            val surfaceY = cy + r - (2f * r) * liquid
            // 圆形裁剪：液体只画在球内
            val circleClip = androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r)) }
            clipPath(circleClip) {
                drawCircle(color.copy(alpha = 0.38f), radius = r, center = c)
                val wavePath = androidx.compose.ui.graphics.Path()
                val amp = r * 0.06f
                wavePath.moveTo(cx - r, surfaceY)
                var x = cx - r
                while (x <= cx + r) {
                    val t = (x - (cx - r)) / (2f * r)
                    val y = surfaceY + amp * kotlin.math.sin(t * (2f * Math.PI).toFloat() * 1.6f + wavePhase) +
                        amp * 0.6f * kotlin.math.sin(t * (2f * Math.PI).toFloat() * 2.7f - wavePhase * 0.7f)
                    wavePath.lineTo(x, y)
                    x += 4f
                }
                wavePath.lineTo(cx + r, cy + r)
                wavePath.lineTo(cx - r, cy + r)
                wavePath.close()
                drawPath(wavePath, color.copy(alpha = 0.85f))
                // 液面下的次级波浪（错相位，浅色）增加流动层次
                val wave2 = androidx.compose.ui.graphics.Path()
                wave2.moveTo(cx - r, surfaceY + r * 0.05f)
                x = cx - r
                while (x <= cx + r) {
                    val t = (x - (cx - r)) / (2f * r)
                    val y = surfaceY + r * 0.05f + amp * kotlin.math.sin(t * (2f * Math.PI).toFloat() * 2.1f - wavePhase * 1.3f)
                    wave2.lineTo(x, y)
                    x += 4f
                }
                wave2.lineTo(cx + r, cy + r)
                wave2.lineTo(cx - r, cy + r)
                wave2.close()
                drawPath(wave2, color.copy(alpha = 0.45f))
                // 液面高光线
                drawLine(
                    Color.White.copy(alpha = 0.35f),
                    Offset(cx - r * 0.7f, surfaceY + amp * 0.3f),
                    Offset(cx + r * 0.7f, surfaceY + amp * 0.3f),
                    strokeWidth = 1.5f,
                )
            }
            // 玻璃质感：外环描边 + 左上高光弧 + 右下内阴影弧
            drawCircle(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(cx - r * 0.35f, cy - r * 0.4f),
                    radius = r * 0.9f,
                ),
                radius = r, center = c,
            )
            drawCircle(Color(0xFF3A3F47), radius = r, center = c, style = Stroke(1.5f))
            drawArc(
                Color.White.copy(alpha = 0.30f),
                startAngle = -140f, sweepAngle = 70f, useCenter = false,
                topLeft = Offset(cx - r * 0.78f, cy - r * 0.78f),
                size = Size(r * 1.56f, r * 1.56f),
                style = Stroke(r * 0.09f, cap = StrokeCap.Round),
            )
            drawArc(
                Color.Black.copy(alpha = 0.25f),
                startAngle = 40f, sweepAngle = 60f, useCenter = false,
                topLeft = Offset(cx - r * 0.78f, cy - r * 0.78f),
                size = Size(r * 1.56f, r * 1.56f),
                style = Stroke(r * 0.06f, cap = StrokeCap.Round),
            )
        }
        if (centerText != null) {
            Text(
                centerText,
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = TextHi,
            )
        }
    }
}
