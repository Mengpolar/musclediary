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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.eelan.musclediary.AppViewModel
import com.eelan.musclediary.domain.Calc
import com.eelan.musclediary.ui.theme.*
import java.time.LocalDate

/** 今日总览（铁馆夜训）：灯牌大热量 + 宏量细条 + 双列状态；点日期弹日历选择 */
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
    val kcalProgress = if (targets.tdee > 0) (kcal / targets.tdee).coerceIn(0.0, 1.0) else 0.0
    val kcalLeft = (targets.tdee - kcal).coerceAtLeast(0.0)

    var showSheet by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    var showWater by remember { mutableStateOf(false) }

    Column(
        Modifier.padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 日期头：英文字距星期 + 大日期 + 时期徽章
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) {
                DateHeader(vm, onOpenCalendar = { showCalendar = true })
            }
            PhaseBadge(if (targets.isCut) "减脂期" else "增肌期")
        }
        // 灯光短线
        Box(
            Modifier.padding(top = 10.dp)
                .width(44.dp).height(2.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Accent),
        )
        Spacer(Modifier.height(14.dp))

        // 灯牌主区：大摄入数字 + 右侧小环
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f).clickable { showSheet = true }) {
                Text("今日摄入", fontSize = 12.sp, color = TextLo)
                Spacer(Modifier.height(2.dp))
                HeroNumber(fmt0(kcal), "kcal", color = TextHi)
                Spacer(Modifier.height(4.dp))
                Text(
                    buildAnnotatedString {
                        append("目标 ${fmt0(targets.tdee)} · 还可吃 ")
                        withStyle(SpanStyle(color = Accent, fontWeight = FontWeight.Bold)) {
                            append(fmt0(kcalLeft))
                        }
                    },
                    fontSize = 12.sp, color = TextLo,
                )
            }
            Box(Modifier.clickable { showSheet = true }) {
                ProgressRing(
                    progress = kcalProgress.toFloat(),
                    color = Accent, size = 84.dp, stroke = 9.dp,
                    centerLabel = "", centerValue = "${(kcalProgress * 100).roundToInt()}%", subValue = "",
                    onClick = {},
                )
            }
        }
        Spacer(Modifier.height(18.dp))

        // 三大宏量：细条 + 行内百分比
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MacroBar("蛋白质", protein, targets.baseProtein, targets.protein, Protein) { showSheet = true }
            MacroBar("碳水", carb, targets.baseCarb, targets.carb, CarbC) { showSheet = true }
            MacroBar("脂肪", fat, targets.baseFat, targets.fat, FatC) { showSheet = true }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "刻度：白齿 = 目标所需 · 橙齿 = 热量所需",
            fontSize = 11.sp, color = TextLo,
        )
        Text(
            "运动消耗 ${fmt0(exKcal)} kcal 已计入目标 · 点数值看推导",
            fontSize = 12.sp, color = TextLo,
        )
        Spacer(Modifier.height(18.dp))

        // 今日状态：双列大数字分栏
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Ink1)
                .padding(vertical = 16.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.weight(1f).clickable { showWater = true },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("饮水", fontSize = 11.sp, color = TextLo)
                    Text(
                        fmt0(water),
                        fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                        color = if (water >= waterTarget) Good else Protein,
                    )
                    Text("目标 ${fmt0(waterTarget)} ml · 点+记录", fontSize = 11.sp, color = TextLo)
                }
                Box(
                    Modifier.width(1.dp).height(52.dp)
                        .background(Ink3).align(Alignment.CenterVertically),
                )
                Column(
                    Modifier.weight(1f).clickable(onClick = onGoWorkout),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("锻炼消耗", fontSize = 11.sp, color = TextLo)
                    Text(
                        fmt0(exKcal),
                        fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                        color = if (p.exerciseGoalKcal > 0 && exKcal >= p.exerciseGoalKcal) Good else Warn,
                    )
                    Text("目标 ${fmt0(p.exerciseGoalKcal)} kcal · 去记录", fontSize = 11.sp, color = TextLo)
                }
            }
        }
        if (date != LocalDate.now()) {
            Spacer(Modifier.height(6.dp))
            Text("当前查看 ${date.monthValue}月${date.dayOfMonth}日 的数据", fontSize = 12.sp, color = Warn)
        }
        Spacer(Modifier.height(12.dp))
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val chips = (recent.map { fmt0(it) } + listOf("100", "250", "500")).distinct().take(4)
                    chips.forEach { q ->
                        OutlinedButton(
                            onClick = {
                                q.toDoubleOrNull()?.let { vm.addWater(it, date) }
                                onDismiss()
                            },
                            modifier = Modifier.height(44.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        ) { Text("${q}ml", fontSize = 13.sp, color = TextHi) }
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
        Column(
            Modifier
                .weight(1f)
                .clickable(enabled = onOpenCalendar != {}) { onOpenCalendar() }
        ) {
            Text(
                listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")[d.dayOfWeek.value - 1] +
                    if (d == today) " · 训练日" else "",
                fontSize = 11.sp, color = TextLo, letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${d.monthValue}月${d.dayOfMonth}日",
                    fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextHi,
                )
                if (d != today) {
                    Spacer(Modifier.width(8.dp))
                    Text("查看历史", fontSize = 12.sp, color = Warn)
                }
            }
        }
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
