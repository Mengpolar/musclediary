package com.eelan.musclediary.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun TodayScreen(vm: AppViewModel, onGoWorkout: () -> Unit = {}) {
    val date = vm.selectedDate
    val food = vm.foodOn(date)
    val exKcal = vm.exerciseKcalOn(date)
    val p = vm.profile
    val targets = Calc.targets(p, exKcal)
    val kcal = food.sumOf { it.kcal }
    val protein = food.sumOf { it.protein }
    val carb = food.sumOf { it.carb }
    val fat = food.sumOf { it.fat }
    val water = vm.waterOn(date)?.ml ?: 0.0
    val waterTarget = Calc.waterTargetMl(p.weightKg)

    var showSheet by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    var showWater by remember { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 16.dp)) {
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
                "运动消耗 ${fmt0(exKcal)} kcal 已计入今日活动系数，点击任意数值查看计算过程",
                fontSize = 11.sp, color = TextLo,
            )
        }

        Spacer(Modifier.height(12.dp))
        CardBox(title = "今日状态") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 饮水环
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    ProgressRing(
                        progress = if (waterTarget > 0) (water / waterTarget).toFloat() else 0f,
                        color = Protein, size = 100.dp, stroke = 9.dp,
                        centerLabel = "饮水", centerValue = "${fmt0(water)}",
                        subValue = "目标 ${fmt0(waterTarget)}",
                        onClick = { showWater = true },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("饮水 · 体重×35ml", fontSize = 11.sp, color = TextLo)
                }
                // 锻炼消耗环
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    ProgressRing(
                        progress = if (p.exerciseGoalKcal > 0) (exKcal / p.exerciseGoalKcal).toFloat() else 0f,
                        color = Warn, size = 100.dp, stroke = 9.dp,
                        centerLabel = "锻炼", centerValue = "${fmt0(exKcal)}",
                        subValue = "目标 ${fmt0(p.exerciseGoalKcal)}",
                        onClick = onGoWorkout,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("锻炼消耗 · 点环去记录", fontSize = 11.sp, color = TextLo)
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

/** 饮水记录对话框：追加到所选日期，可改总量、可清空 */
@Composable
fun WaterDialog(vm: AppViewModel, date: LocalDate, targetMl: Double, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var editMode by remember { mutableStateOf(false) }
    val current = vm.waterOn(date)?.ml ?: 0.0
    val dateLabel = if (date == LocalDate.now()) "今日" else "${date.monthValue}月${date.dayOfMonth}日"
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        title = { Text(if (editMode) "设定${dateLabel}饮水总量" else "记录${dateLabel}饮水", color = TextHi, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "当前 ${fmt0(current)} / 目标 ${fmt0(targetMl)} ml",
                    fontSize = 13.sp, color = Protein,
                )
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text(if (editMode) "设为总量 ml" else "本次喝了多少 ml", color = TextLo) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    colors = fieldColors(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("250", "500").forEach { q ->
                        OutlinedButton(onClick = { text = q }, enabled = !editMode) {
                            Text("${q}ml", fontSize = 12.sp, color = TextHi)
                        }
                    }
                    if (current > 0) {
                        TextButton(onClick = { vm.clearWater(date) }) {
                            Text("清空", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                text.toDoubleOrNull()?.let { v ->
                    if (v > 0) {
                        if (editMode) vm.setWaterTotal(v, date) else vm.addWater(v, date)
                        onDismiss()
                    }
                }
            }) { Text(if (editMode) "设定" else "记录", color = Accent) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    if (editMode) { editMode = false; text = "" } else { editMode = true; text = "" }
                }) { Text(if (editMode) "返回追加" else "修改总量", color = TextLo, fontSize = 13.sp) }
                TextButton(onClick = onDismiss) { Text("关闭", color = TextLo, fontSize = 13.sp) }
            }
        },
    )
}

fun fmtQty(e: com.eelan.musclediary.data.ExerciseEntry): String =
    if (e.weightKg > 0) "${fmt1(e.qty)} 个×${fmt1(e.weightKg)}kg" else "${fmt1(e.qty)} 个"

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
