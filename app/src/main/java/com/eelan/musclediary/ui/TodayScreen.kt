package com.eelan.musclediary.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eelan.musclediary.AppViewModel
import com.eelan.musclediary.domain.Calc
import com.eelan.musclediary.ui.theme.*
import java.time.LocalDate

/** 今日总览：目标环（可点击看推导）+ 饮水环 + 锻炼消耗环；点日期弹日历选择 */
@Composable
fun TodayScreen(vm: AppViewModel, onGoWorkout: () -> Unit = {}, onOpenWaterRecords: () -> Unit = {}) {
    val date = vm.selectedDate
    val food = vm.foodOn(date)
    val exKcal = vm.exerciseKcalOn(date)
    val p = vm.profile
    val targets = Calc.targets(p, exKcal)
    val kcal = food.sumOf { it.kcal }
    val protein = food.sumOf { it.protein }
    val carb = food.sumOf { it.carb }
    val fat = food.sumOf { it.fat }
    val water = vm.waterOn(date)
    val waterTarget = Calc.waterTargetMl(p.weightKg)

    var showSheet by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    var showWater by remember { mutableStateOf(false) }

    Column(
        Modifier.padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DateHeader(vm, onOpenCalendar = { showCalendar = true })
        Spacer(Modifier.height(8.dp))
        CardBox(title = "今日目标（${if (targets.isCut) "减脂期" else "增肌期"}）") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(
                    progress = if (targets.tdee > 0) (kcal / targets.tdee).toFloat() else 0f,
                    color = Accent, size = 130.dp, stroke = 12.dp,
                    centerLabel = "热量", centerValue = "${fmt0(kcal)}",
                    subValue = "目标 ${fmt0(targets.tdee)}",
                    onClick = { showSheet = true },
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MacroBar("蛋白质", protein, targets.baseProtein, targets.protein, Protein) { showSheet = true }
                    MacroBar("碳水", carb, targets.baseCarb, targets.carb, CarbC) { showSheet = true }
                    MacroBar("脂肪", fat, targets.baseFat, targets.fat, FatC) { showSheet = true }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "刻度：白=目标所需 · 橙=热量所需（按热量等比分摊）",
                fontSize = 10.sp, color = TextLo,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "运动消耗 ${fmt0(exKcal)} kcal 已计入目标 · 点数值看推导",
                fontSize = 11.sp, color = TextLo,
            )
        }

        Spacer(Modifier.height(12.dp))
        CardBox(title = "今日状态") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 饮水环（点击记录，下方入口看全部记录）
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    ProgressRing(
                        progress = if (waterTarget > 0) (water / waterTarget).toFloat() else 0f,
                        color = Protein, size = 100.dp, stroke = 9.dp,
                        centerLabel = "饮水", centerValue = "${fmt0(water)}",
                        subValue = "目标 ${fmt0(waterTarget)}",
                        onClick = { showWater = true },
                        valueColor = if (water >= waterTarget) Good else TextHi,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("点环记录", fontSize = 11.sp, color = TextLo)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "全部记录 >",
                            fontSize = 11.sp, color = Accent,
                            modifier = Modifier
                                .clickable(onClick = onOpenWaterRecords)
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
                // 锻炼消耗环
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    ProgressRing(
                        progress = if (p.exerciseGoalKcal > 0) (exKcal / p.exerciseGoalKcal).toFloat() else 0f,
                        color = Warn, size = 100.dp, stroke = 9.dp,
                        centerLabel = "锻炼", centerValue = "${fmt0(exKcal)}",
                        subValue = "目标 ${fmt0(p.exerciseGoalKcal)}",
                        onClick = onGoWorkout,
                        valueColor = if (p.exerciseGoalKcal > 0 && exKcal >= p.exerciseGoalKcal) Good else TextHi,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("点环去记录", fontSize = 11.sp, color = TextLo)
                }
            }
            if (date != LocalDate.now()) {
                Spacer(Modifier.height(6.dp))
                Text("当前查看 ${date.monthValue}月${date.dayOfMonth}日 的数据", fontSize = 11.sp, color = Warn)
            }
        }
    }

    if (showSheet) {
        DerivationSheet(
            profile = p, exerciseKcal = exKcal,
            intakeKcal = kcal, intakeProtein = protein, intakeCarb = carb, intakeFat = fat,
            onDismiss = { showSheet = false },
        )
    }
    if (showCalendar) {
        CalendarSheet(vm, onDismiss = { showCalendar = false })
    }
    if (showWater) {
        WaterDialog(vm, date, waterTarget, onDismiss = { showWater = false })
    }
}

/**
 * 饮水记录对话框：快捷按钮点击直接记录并关窗；也可手输 ml 后点「记录」。
 * 当日/历史明细在「全部记录」全屏页查看。
 */
@Composable
fun WaterDialog(vm: AppViewModel, date: LocalDate, targetMl: Double, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val current = vm.waterOn(date)
    val recent = vm.recentWaterMl()
    val dateLabel = if (date == LocalDate.now()) "今日" else "${date.monthValue}月${date.dayOfMonth}日"
    val remain = (targetMl - current).coerceAtLeast(0.0)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        title = { Text("记录${dateLabel}饮水", color = TextHi, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (remain > 0) "当前 ${fmt0(current)} / ${fmt0(targetMl)} ml · 还差 ${fmt0(remain)} ml"
                    else "当前 ${fmt0(current)} / ${fmt0(targetMl)} ml · 已达标",
                    fontSize = 13.sp, color = if (remain > 0) Protein else Good,
                )
                // 快捷按钮：合并最近值与常用值（去重，最多 4 个），点击直接记录并关窗
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val chips = (recent.map { fmt0(it) } + listOf("100", "250", "500")).distinct().take(4)
                    chips.forEach { q ->
                        OutlinedButton(onClick = {
                            q.toDoubleOrNull()?.let { vm.addWater(it, date) }
                            onDismiss()
                        }) { Text("${q}ml", fontSize = 12.sp, color = TextHi) }
                    }
                }
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text("其他毫升数（输入后点「记录」）", color = TextLo) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    colors = fieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                text.toDoubleOrNull()?.let { v ->
                    if (v > 0) {
                        vm.addWater(v, date)
                        onDismiss()
                    }
                }
            }) { Text("记录", color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭", color = TextLo, fontSize = 13.sp) } },
    )
}

@Composable
fun DateHeader(vm: AppViewModel, onOpenCalendar: () -> Unit = {}) {
    val d = vm.selectedDate
    val today = LocalDate.now()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            "${d.year}年${d.monthValue}月${d.dayOfMonth}日 · ${
                listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[d.dayOfWeek.value - 1]
            }",
            fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = TextHi,
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = onOpenCalendar != {}) { onOpenCalendar() },
        )
        IconButton(onClick = { vm.selectDate(d.minusDays(1)) }) {
            Icon(Icons.Default.ChevronLeft, "前一天", tint = TextLo)
        }
        IconButton(onClick = { vm.selectDate(d.plusDays(1)) }, enabled = d < today) {
            Icon(Icons.Default.ChevronRight, "后一天", tint = if (d < today) TextLo else Ink3)
        }
        if (d != today) {
            TextButton(onClick = { vm.selectDate(today) }) {
                Icon(Icons.Default.Today, null, tint = Accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("今天", color = Accent, fontSize = 13.sp)
            }
        }
    }
}
