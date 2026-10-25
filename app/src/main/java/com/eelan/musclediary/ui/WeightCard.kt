package com.eelan.musclediary.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eelan.musclediary.AppViewModel
import com.eelan.musclediary.L
import com.eelan.musclediary.data.WeightEntry
import com.eelan.musclediary.domain.Calc
import com.eelan.musclediary.ui.theme.*
import java.time.LocalDate

/** 「我的」页体重卡片：曲线 + 记录今日体重 + 全部记录入口（完整历史在全屏记录页查看） */
@Composable
fun WeightCard(vm: AppViewModel, onOpenRecords: () -> Unit = {}) {
    var recordOpen by remember { mutableStateOf(false) }
    val entries = vm.weightEntries.sortedBy { it.date }
    val latest = entries.lastOrNull()

    CardBox(title = L("体重记录", "Weight")) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    latest?.let { L("最近 ", "Latest ") + "${it.date.substring(5)} · ${fmt1(it.weightKg)} kg" }
                        ?: L("还没有记录", "No records yet"),
                    fontSize = 12.sp, color = TextLo,
                )
            }
            Text(
                L("＋ 记录今日体重", "+ Log today's weight"), fontSize = 13.sp, color = Accent, fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable { recordOpen = true }
                    .padding(start = 8.dp, top = 10.dp, bottom = 10.dp, end = 2.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        WeightChart(entries)
        if (entries.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                L("全部记录（", "All records (") + "${entries.size}" + L(" 条）", ")") + ">",
                fontSize = 12.sp, color = Accent,
                modifier = Modifier
                    .clickable(onClick = onOpenRecords)
                    .padding(top = 8.dp, bottom = 8.dp, end = 8.dp),
            )
        }
    }

    if (recordOpen) {
        NumberDialog(
            title = L("记录今日体重", "Log today's weight"), label = L("公斤", "kg"),
            initial = vm.profile.let { fmt1(it.weightKg) },
            onDismiss = { recordOpen = false },
            onConfirm = { vm.addWeight(it); recordOpen = false },
        )
    }
}

/**
 * BMI 条：偏瘦 <18.5 / 正常 18.5~24 / 超重 24~28 / 肥胖 ≥28（中国标准）。
 * 分区渐变胶囊条（向右渐浓）+ 分界刻度线 + 白滑块指针；数值/颜色/指针位均带过渡动画。
 */
@Composable
fun BmiBar(bmi: Double, weightKg: Double, heightCm: Double) {
    val zone = Calc.bmiZone(bmi)
    val zoneNames = listOf(L("偏瘦", "Under"), L("正常", "Normal"), L("超重", "Over"), L("肥胖", "Obese"))
    val zoneColors = listOf(Protein, Good, Warn, Color(0xFFEF5350))
    val bmiMin = 14.0
    val bmiMax = 36.0
    // 体重/身高修改后数字、颜色与指针平滑过渡
    val animBmi by animateFloatAsState(bmi.toFloat(), tween(450), label = "bmi")
    val zoneColor by animateColorAsState(zoneColors[zone], tween(450), label = "bmiColor")
    val measurer = rememberTextMeasurer()

    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("BMI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextLo, letterSpacing = 2.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                fmt1(animBmi.toDouble()),
                fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = zoneColor,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
            Spacer(Modifier.width(10.dp))
            // 区间徽章：描边胶囊，同色系弱底
            Text(
                zoneNames[zone], fontSize = 11.sp, fontWeight = FontWeight.Bold, color = zoneColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(zoneColor.copy(alpha = 0.10f))
                    .border(1.dp, zoneColor.copy(alpha = 0.45f), RoundedCornerShape(99.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(L("体重 ", "Wt ") + "${fmt1(weightKg)} kg", fontSize = 11.sp, color = TextLo)
                Text(L("身高 ", "Ht ") + "${fmt1(heightCm)} cm", fontSize = 11.sp, color = TextLo)
            }
        }
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.fillMaxWidth().height(34.dp)) {
            val w = size.width
            val barH = 16.dp.toPx()
            val r = CornerRadius(barH / 2, barH / 2)
            fun zoneX(b: Double) = ((b - bmiMin) / (bmiMax - bmiMin) * w).toFloat()

            // 胶囊底 + 四区渐变（多色标一次成画：区内向右渐浓，分界处硬切换）
            val capsule = Path().apply { addRoundRect(RoundRect(0f, 0f, w, barH, r)) }
            drawPath(capsule, Ink2)
            val stops = arrayOf(
                0f to zoneColors[0].copy(alpha = 0.30f),
                zoneX(18.5) / w to zoneColors[0].copy(alpha = 0.78f),
                zoneX(18.5) / w to zoneColors[1].copy(alpha = 0.30f),
                zoneX(24.0) / w to zoneColors[1].copy(alpha = 0.78f),
                zoneX(24.0) / w to zoneColors[2].copy(alpha = 0.30f),
                zoneX(28.0) / w to zoneColors[2].copy(alpha = 0.78f),
                zoneX(28.0) / w to zoneColors[3].copy(alpha = 0.30f),
                1f to zoneColors[3].copy(alpha = 0.78f),
            )
            drawPath(capsule, Brush.horizontalGradient(*stops))
            // 分界刻度线（卡片底色细线，兼作分段隔断）
            listOf(18.5, 24.0, 28.0).forEach { b ->
                drawLine(Ink1, Offset(zoneX(b), 0f), Offset(zoneX(b), barH), 2f)
            }

            // 指针：zone 色指向线 + 白滑块 + 光环
            val pos = ((animBmi - bmiMin) / (bmiMax - bmiMin)).coerceIn(0.0, 1.0)
            val x = (pos * w).toFloat()
            drawLine(zoneColor.copy(alpha = 0.65f), Offset(x, barH), Offset(x, barH + 5f), 2f)
            drawCircle(zoneColor.copy(alpha = 0.30f), 9.dp.toPx(), Offset(x, barH / 2))
            drawCircle(Color.White, 4.dp.toPx(), Offset(x, barH / 2))

            // 分界数字：宽度实测后中心对齐各自分界线
            val bounds = listOf(18.5, 24.0, 28.0)
            listOf("18.5", "24", "28").forEachIndexed { i, label ->
                val t = measurer.measure(
                    AnnotatedString(label), TextStyle(fontSize = 9.sp, color = TextLo))
                drawText(t, topLeft = Offset(zoneX(bounds[i]) - t.size.width / 2f, barH + 4f))
            }
        }
    }
}

@Composable
fun WeightChart(entries: List<WeightEntry>) {
    if (entries.isEmpty()) {
        Text(L("记录 2 次后生成曲线", "Chart appears after 2 entries"), fontSize = 12.sp, color = TextLo)
        return
    }
    val sorted = entries.sortedBy { it.date }
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val minW = sorted.minOf { it.weightKg }
        val maxW = sorted.maxOf { it.weightKg }
        val yLo = minW - 2.0
        val yHi = maxW + 2.0
        val days = sorted.map { LocalDate.parse(it.date).toEpochDay() }
        val x0 = days.first()
        val x1 = maxOf(days.last(), x0 + 7) // 至少 7 天跨度，避免单点/密集点挤在一起
        fun px(d: Long) = ((d - x0).toDouble() / (x1 - x0) * size.width).toFloat()
        fun py(w: Double) = ((yHi - w) / (yHi - yLo) * (size.height - 16f)).toFloat() + 8f

        // 底部基线
        drawLine(Ink3, Offset(0f, size.height - 4f), Offset(size.width, size.height - 4f), 2f)

        if (sorted.size == 1) {
            drawCircle(Accent, 6f, Offset(px(days[0]), py(sorted[0].weightKg)))
            return@Canvas
        }
        val path = Path()
        days.forEachIndexed { i, d ->
            val p = Offset(px(d), py(sorted[i].weightKg))
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, Accent, style = Stroke(4f, cap = StrokeCap.Round))
        days.forEachIndexed { i, d ->
            drawCircle(Accent, 5f, Offset(px(d), py(sorted[i].weightKg)))
            drawCircle(Ink1, 2.5f, Offset(px(d), py(sorted[i].weightKg)))
        }
    }
    Spacer(Modifier.height(2.dp))
    // 单点时首尾相同只标一次
    if (sorted.size == 1) {
        Text(
            "${sorted.first().date.substring(5)} ${fmt1(sorted.first().weightKg)}kg",
            fontSize = 12.sp, color = TextLo,
        )
    } else {
        Row(Modifier.fillMaxWidth()) {
            Text(
                "${sorted.first().date.substring(5)} ${fmt1(sorted.first().weightKg)}kg",
                fontSize = 12.sp, color = TextLo,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${sorted.last().date.substring(5)} ${fmt1(sorted.last().weightKg)}kg",
                fontSize = 12.sp, color = TextLo,
            )
        }
    }
}
