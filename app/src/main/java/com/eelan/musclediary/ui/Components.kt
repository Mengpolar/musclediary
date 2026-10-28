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
import com.eelan.musclediary.L
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
        // 宏量条：克制风格——纯色填充 + 少量随机流动微粒（每条独立随机轨迹），无波浪无高光
        val infinite = rememberInfiniteTransition(label = "macroFx")
        val particlePhase by infinite.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
            label = "particlePhase",
        )
        // 每个条用 label+color 的稳定种子生成随机参数，三条互不相同且重组稳定
        val rand = remember(color) { java.util.Random(31L * color.hashCode() + label.hashCode()) }
        val seeds = remember(color) {
            List(5) {
                Triple(rand.nextFloat(), rand.nextFloat(), 0.6f + rand.nextFloat() * 0.8f)
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
        ) {
            val w = size.width
            val h = size.height
            val r = androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2)
            val fillW = (animFill * w).toFloat().coerceAtLeast(h)
            // 底槽
            drawRoundRect(Ink2, topLeft = Offset(0f, 0f), size = Size(w, h), cornerRadius = r)
            // 纯色填充（不透明度略降，与底槽区分即可）
            drawRoundRect(
                color.copy(alpha = 0.9f),
                topLeft = Offset(0f, 0f),
                size = Size(fillW, h),
                cornerRadius = r,
            )
            // 微粒：小、半透明、随机 y 与速度，在填充区内从左往右缓慢漂移
            if (animFill > 0.03f) {
                clipRect(left = 0f, top = 0f, right = fillW, bottom = h) {
                    seeds.forEachIndexed { i, (p0, yF, sF) ->
                        val px = ((particlePhase * sF + p0) % 1f) * fillW
                        val py = h * (0.22f + 0.56f * yF)
                        drawCircle(Color.White.copy(alpha = 0.28f), radius = h * 0.07f, center = Offset(px, py))
                    }
                }
            }
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

// Locale.US：点号小数。默认 Locale 在逗号小数语区（de/fr/ru 等）产出 "0,7"，回填输入框后 toDoubleOrNull 解析失败
fun fmt1(v: Double): String = if (v >= 100) Math.round(v).toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)
fun fmt0(v: Double): String = Math.round(v).toInt().toString()

/** 食物量词单位英文显示：内置单位为中文数据，英文界面按映射翻译；自定义/未知单位原样返回 */
private val FOOD_UNIT_EN = mapOf(
    "个" to "pc", "颗" to "pc", "碗" to "bowl", "杯" to "cup", "罐" to "can", "盒" to "carton",
    "瓶" to "bottle", "根" to "stick", "片" to "slice", "块" to "piece", "瓣" to "segment",
    "串" to "skewer", "份" to "serving", "条" to "roll",
)
fun foodUnitLabel(u: String): String =
    if (com.eelan.musclediary.Lang.isEn) FOOD_UNIT_EN[u] ?: u else u

/** 历史条目找不到模板时的公里类兜底判定（中英文条目名都认） */
fun isDistanceName(n: String): Boolean =
    n.contains("跑") || n.contains("步") || n.contains("骑") ||
            n.contains("Run") || n.contains("Walk") || n.contains("Cycl")

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
                if (!valid) Text(L("请输入大于 0 的数字", "Enter a number greater than 0"), fontSize = 12.sp, color = Warn)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onConfirm(v!!)
            }) { Text(L("确定", "OK"), color = if (valid) Accent else TextLo) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("取消", "Cancel"), color = TextLo) } },
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
    // 灰飞烟灭：0=正常，1=粒子飘散完成。卡片栅格化为小粒子，向上飘出并逐渐淡化
    val dust = remember { Animatable(0f) }
    var flying by remember { mutableStateOf(false) }
    // 粒子属性在首次绘制粒子帧时按卡片尺寸生成（伪随机但确定，避免重组闪烁）
    var particles by remember { mutableStateOf<List<Particle>>(emptyList()) }

    fun deleteWithCrush() {
        flying = true
        scope.launch {
            dust.animateTo(1f, tween(850, easing = LinearEasing))
            onDelete()
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            // 不裁剪：粒子要飘出卡片边界（圆角裁剪下移到前景内容层）
            .drawWithContent {
                if (!flying) {
                    drawContent()
                } else {
                    val p = dust.value
                    if (particles.isEmpty()) {
                        // 生成粒子：每 14px 网格 1 颗 + 抖动，携带各自的速度/大小/寿命
                        val list = mutableListOf<Particle>()
                        val step = 14f
                        var seed = 12345
                        fun rnd(): Float { seed = seed * 1103515245 + 12345; return ((seed shr 16) and 0x7fff) / 32768f }
                        var gy = 0f
                        while (gy < size.height) {
                            var gx = 0f
                            while (gx < size.width) {
                                list += Particle(
                                    x = gx + rnd() * step, y = gy + rnd() * step,
                                    vx = (rnd() - 0.5f) * 34f,
                                    vy = -(34f + rnd() * 66f),          // 向上飘
                                    r = 1.2f + rnd() * 1.9f,
                                    delay = rnd() * 0.25f,              // 起飘时间错开
                                )
                                gx += step
                            }
                            gy += step
                        }
                        particles = list
                    }
                    // 卡片本体快速碎化淡出（前 25% 进度）
                    val bodyAlpha = (1f - p / 0.25f).coerceIn(0f, 1f)
                    if (bodyAlpha > 0f) {
                        clipRect(left = 0f, top = 0f, right = size.width, bottom = size.height) {
                            this@drawWithContent.drawContent()
                        }
                        // 本体淡出用叠底色模拟（drawContent 无法直接调 alpha）
                        drawRect(Ink0, topLeft = Offset.Zero, size = size, alpha = 1f - bodyAlpha)
                    }
                    // 粒子：错峰起飘，向上加速、横向散开、飘出卡片边界，缩小淡去
                    particles.forEach { pt ->
                        val t = ((p - pt.delay) / 0.75f).coerceIn(0f, 1f)
                        if (t <= 0f) return@forEach
                        val eased = t * t                                   // 越飘越快
                        val x = pt.x + pt.vx * eased * 4.6f                  // 横向扩散（突破左右边界）
                        val y = pt.y + pt.vy * eased * 5.2f                 // 上飘（突破上边界）
                        val alpha = (1f - t).coerceIn(0f, 1f) * 0.9f
                        if (alpha <= 0.01f) return@forEach
                        drawCircle(
                            TextHi.copy(alpha = alpha),
                            radius = pt.r * (1f - 0.55f * t),
                            center = Offset(x, y),
                        )
                    }
                }
            },
    ) {
        // 背景操作按钮（右侧）：右缘与卡片整体轮廓同圆角；左缘直角（与滑开的卡片无缝相接）
        Row(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 10.dp, bottomEnd = 10.dp, bottomStart = 0.dp)),
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
            ) { Text(L("修改", "Edit"), color = Ink0, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            Box(
                Modifier
                    .width(66.dp)
                    .fillMaxHeight()
                    .background(Color(0xFFEF5350))
                    .clickable { deleteWithCrush() },
                contentAlignment = Alignment.Center,
            ) { Text(L("删除", "Delete"), color = Ink0, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
        // 前景内容（自身撑开高度，背景按钮 matchParentSize 跟随）。
        // 圆角动态化：静止时四角圆（全局一致）；左滑打开时右缘圆角随进度收为直角，
        // 与按钮区交界无缝（graphicsLayer 延迟读取 offset，拖动帧内直接更新 layer 不重组）
        Row(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .graphicsLayer {
                    val progress = (-offset.value / maxSwipePx).coerceIn(0f, 1f)
                    shape = RoundedCornerShape(
                        topStart = 10.dp, bottomStart = 10.dp,
                        topEnd = 10.dp * (1f - progress), bottomEnd = 10.dp * (1f - progress),
                    )
                    clip = true
                }
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

/** 删除粒子：卡片栅格化生成的一颗尘埃（模型即屏幕坐标，速度向上） */
private data class Particle(
    val x: Float, val y: Float,     // 初始位置
    val vx: Float, val vy: Float,   // 速度（px/进度单位）
    val r: Float,                   // 半径
    val delay: Float,               // 起飘延迟（0~0.25）
)

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
fun SectionTitleRow(text: String, moreText: String = L("全部 >", "All >"), onMore: () -> Unit) {
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
        Text(L("最近：", "Recent: "), fontSize = 12.sp, color = TextLo,
            modifier = Modifier.align(Alignment.CenterVertically))
        chips.forEach { c ->
            // 标签舒展：点中瞬间 1 → 1.08 → 1 弹性回落，其余项随布局弹簧让位
            var pulse by remember(c) { mutableStateOf(false) }
            val chipScale by animateFloatAsState(
                targetValue = if (pulse) 1.08f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "chipScale",
            )
            LaunchedEffect(pulse) {
                if (pulse) {
                    kotlinx.coroutines.delay(200)
                    pulse = false
                }
            }
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = chipScale
                        scaleY = chipScale
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(Accent.copy(alpha = 0.15f))
                    .clickable {
                        pulse = true
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
    // 波面每帧重画：Path 复用（rewind 清空重填），避免每帧分配两个对象
    val circleClip = remember { androidx.compose.ui.graphics.Path() }
    val wavePath = remember { androidx.compose.ui.graphics.Path() }
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
            // 液体：以液面 y 裁剪画圆形液体，液面为单一缓正弦波
            val surfaceY = cy + r - (2f * r) * liquid
            // 圆形裁剪：液体只画在球内
            circleClip.rewind()
            circleClip.addOval(androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r))
            clipPath(circleClip) {
                drawCircle(color.copy(alpha = 0.32f), radius = r, center = c)
                val amp = r * 0.035f
                wavePath.rewind()
                wavePath.moveTo(cx - r, surfaceY)
                var x = cx - r
                while (x <= cx + r) {
                    val t = (x - (cx - r)) / (2f * r)
                    val y = surfaceY + amp * kotlin.math.sin(t * (2f * Math.PI).toFloat() * 1.4f + wavePhase)
                    wavePath.lineTo(x, y)
                    x += 4f
                }
                wavePath.lineTo(cx + r, cy + r)
                wavePath.lineTo(cx - r, cy + r)
                wavePath.close()
                drawPath(wavePath, color.copy(alpha = 0.62f))
            }
            // 玻璃质感收敛：一圈细描边 + 左上一小块柔和高光渐变（无弧线、无内阴影）
            drawCircle(Color(0xFF3A3F47), radius = r, center = c, style = Stroke(1f))
            drawCircle(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(cx - r * 0.35f, cy - r * 0.42f),
                    radius = r * 0.75f,
                ),
                radius = r, center = c,
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
