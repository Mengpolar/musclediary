package com.eelan.musclediary.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eelan.musclediary.AppViewModel
import com.eelan.musclediary.data.WeightEntry
import com.eelan.musclediary.ui.theme.*
import java.time.LocalDate

/** 「我的」页体重卡片：曲线 + 记录今日体重 + 历史记录（不必每天记，一天最多一条） */
@Composable
fun WeightCard(vm: AppViewModel) {
    var recordOpen by remember { mutableStateOf(false) }
    val entries = vm.weightEntries.sortedBy { it.date }
    val latest = entries.lastOrNull()

    CardBox {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("体重曲线", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextHi)
                Text(
                    latest?.let { "最近 ${it.date.substring(5)} · ${fmt1(it.weightKg)} kg（共 ${entries.size} 条记录）" }
                        ?: "还没有记录",
                    fontSize = 11.sp, color = TextLo,
                )
            }
            Text(
                "＋ 记录今日体重", fontSize = 13.sp, color = Accent, fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable { recordOpen = true }
                    .padding(6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        WeightChart(entries)
        if (entries.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                entries.takeLast(4).reversed().forEach { e ->
                    EntryRow(
                        title = e.date,
                        detail = "体重记录",
                        trailing = "${fmt1(e.weightKg)} kg",
                        onDelete = { vm.deleteWeight(e) },
                    )
                }
            }
            if (entries.size > 4) {
                Text("更早的记录可在曲线上查看", fontSize = 11.sp, color = TextLo)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("记录后自动更新身体档案，目标计算随之调整；不必每天记录", fontSize = 11.sp, color = TextLo)
    }

    if (recordOpen) {
        NumberDialog(
            title = "记录今日体重", label = "公斤",
            initial = vm.profile.let { fmt1(it.weightKg) },
            onDismiss = { recordOpen = false },
            onConfirm = { vm.addWeight(it); recordOpen = false },
        )
    }
}

@Composable
fun WeightChart(entries: List<WeightEntry>) {
    if (entries.isEmpty()) {
        Text("记满 2 条后显示曲线", fontSize = 12.sp, color = TextLo)
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
    Row(Modifier.fillMaxWidth()) {
        Text(
            "${sorted.first().date.substring(5)} ${fmt1(sorted.first().weightKg)}kg",
            fontSize = 11.sp, color = TextLo,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "${sorted.last().date.substring(5)} ${fmt1(sorted.last().weightKg)}kg",
            fontSize = 11.sp, color = TextLo,
        )
    }
}
