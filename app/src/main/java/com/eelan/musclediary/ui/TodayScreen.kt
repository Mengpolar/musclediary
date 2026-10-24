package com.eelan.musclediary.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.eelan.musclediary.AppViewModel
import com.eelan.musclediary.L
import com.eelan.musclediary.dMD
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
        // 日期头：英文字距星期 + 大日期（右上角不再显示时期徽章）
        DateHeader(vm, onOpenCalendar = { showCalendar = true })
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
                Text(L("今日摄入", "Today's intake"), fontSize = 12.sp, color = TextLo)
                Spacer(Modifier.height(2.dp))
                HeroNumber(fmt0(kcal), "kcal", color = TextHi)
                Spacer(Modifier.height(4.dp))
                Text(
                    buildAnnotatedString {
                        append(L("目标 ", "Goal ") + "${fmt0(targets.tdee)} · " +
                                L("还可吃 ", "left ") + " ")
                        withStyle(SpanStyle(color = Accent, fontWeight = FontWeight.Bold)) {
                            append(fmt0(kcalLeft))
                        }
                    },
                    fontSize = 12.sp, color = TextLo,
                )
            }
            Box(Modifier.clickable { showSheet = true }) {
                LiquidGlobe(
                    progress = kcalProgress.toFloat(),
                    color = Accent, sizeDp = 84.dp,
                    centerText = "${(kcalProgress * 100).roundToInt()}%",
                    onClick = { showSheet = true },
                )
            }
        }
        Spacer(Modifier.height(18.dp))

        // 三大宏量：细条 + 行内百分比
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MacroBar(L("蛋白质", "Protein"), protein, targets.baseProtein, targets.protein, Protein) { showSheet = true }
            MacroBar(L("碳水", "Carbs"), carb, targets.baseCarb, targets.carb, CarbC) { showSheet = true }
            MacroBar(L("脂肪", "Fat"), fat, targets.baseFat, targets.fat, FatC) { showSheet = true }
        }
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
                    Modifier.weight(1f).clickable { showWater = true }
                        .padding(horizontal = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(L("饮水", "Water"), fontSize = 11.sp, color = TextLo)
                    Spacer(Modifier.height(6.dp))
                    val waterProgress = if (waterTarget > 0) (water / waterTarget).toFloat().coerceIn(0f, 1f) else 0f
                    // 玻璃液体球：液体高度=进度，液面流动，百分比保留在球心
                    LiquidGlobe(
                        progress = waterProgress,
                        color = if (waterProgress >= 1f) Good else Protein,
                        sizeDp = 64.dp,
                        centerText = "${(waterProgress * 100).roundToInt()}%",
                        onClick = { showWater = true },
                    )
                    Spacer(Modifier.height(6.dp))
                    AnimatedCountText(
                        fmt0(water),
                        fontSize = 24.sp,
                        color = if (water >= waterTarget) Good else Protein,
                    )
                    Text(L("目标 ", "Goal ") + "${fmt0(waterTarget)} ml", fontSize = 11.sp, color = TextLo)
                }
                Box(
                    Modifier.width(1.dp).height(96.dp)
                        .background(Ink3).align(Alignment.CenterVertically),
                )
                Column(
                    Modifier.weight(1f).clickable(onClick = onGoWorkout)
                        .padding(horizontal = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(L("锻炼消耗", "Exercise"), fontSize = 11.sp, color = TextLo)
                    Spacer(Modifier.height(6.dp))
                    val exProgress = if (p.exerciseGoalKcal > 0) (exKcal / p.exerciseGoalKcal).toFloat().coerceIn(0f, 1f) else 0f
                    LiquidGlobe(
                        progress = exProgress,
                        color = if (exProgress >= 1f) Good else Warn,
                        sizeDp = 64.dp,
                        centerText = "${(exProgress * 100).roundToInt()}%",
                        onClick = onGoWorkout,
                    )
                    Spacer(Modifier.height(6.dp))
                    AnimatedCountText(
                        fmt0(exKcal),
                        fontSize = 24.sp,
                        color = if (p.exerciseGoalKcal > 0 && exKcal >= p.exerciseGoalKcal) Good else Warn,
                    )
                    Text(L("目标 ", "Goal ") + "${fmt0(p.exerciseGoalKcal)} kcal", fontSize = 11.sp, color = TextLo)
                }
            }
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
        WaterDialog(vm, date, waterTarget, onDismiss = { showWater = false }, onOpenRecords = onOpenWaterRecords)
    }
}

/**
 * 饮水记录对话框：快捷按钮点击直接记录并关窗；也可手输 ml 后点「记录」。
 * 当日/历史明细在「全部记录」全屏页查看。
 */
@Composable
fun WaterDialog(vm: AppViewModel, date: LocalDate, targetMl: Double, onDismiss: () -> Unit, onOpenRecords: () -> Unit = {}) {
    var text by remember { mutableStateOf("") }
    val current = vm.waterOn(date)
    val recent = vm.recentWaterMl()
    val dateLabel = if (date == LocalDate.now()) L("今日", "Today") else dMD(date.monthValue, date.dayOfMonth)
    val remain = (targetMl - current).coerceAtLeast(0.0)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        title = { Text(L("记录", "Log ") + dateLabel + L("饮水", " water"), color = TextHi, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (remain > 0) L("当前 ", "Now ") + "${fmt0(current)} / ${fmt0(targetMl)} ml · " +
                            L("还差 ", "left ") + "${fmt0(remain)} ml"
                    else L("当前 ", "Now ") + "${fmt0(current)} / ${fmt0(targetMl)} ml · " + L("已达标", "goal met"),
                    fontSize = 13.sp, color = if (remain > 0) Protein else Good,
                )
                // 快捷按钮：合并最近值与常用值（去重，最多 4 个），点击直接记录并关窗
                // weight(1f) 均分宽度：窄屏下 4 个按钮不会互相挤压截断
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    val chips = (recent.map { fmt0(it) } + listOf("100", "250", "500")).distinct().take(4)
                    chips.forEach { q ->
                        OutlinedButton(
                            onClick = {
                                q.toDoubleOrNull()?.let { vm.addWater(it, date) }
                                onDismiss()
                            },
                            modifier = Modifier.height(44.dp).weight(1f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                        ) { Text("${q}ml", fontSize = 13.sp, color = TextHi, maxLines = 1) }
                    }
                }
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text(L("自定义毫升数", "Custom amount (ml)"), color = TextLo) },
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
            }) { Text(L("记录", "Log"), color = Accent) }
        },
        dismissButton = {
            Row {
                // 全部记录入口：查看当日/历史明细
                TextButton(onClick = onOpenRecords) { Text(L("全部记录", "All records"), color = Protein, fontSize = 13.sp) }
                TextButton(onClick = onDismiss) { Text(L("关闭", "Close"), color = TextLo, fontSize = 13.sp) }
            }
        },
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
                .clickable { onOpenCalendar() }
        ) {
            Text(
                (listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")[d.dayOfWeek.value - 1]) +
                    if (d == today) L(" · 训练日", " · TRAINING DAY") else "",
                fontSize = 11.sp, color = TextLo, letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                dMD(d.monthValue, d.dayOfMonth),
                fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                color = if (d == today) TextHi else Warn,
                maxLines = 1,
            )
        }
        // 「今天」快捷钮在箭头左侧：出现/消失不会引起左右箭头位移（占位等宽）
        if (d != today) {
            IconButton(onClick = { vm.selectDate(today) }) {
                Icon(Icons.Default.Today, L("回到今天", "Back to today"), tint = Accent, modifier = Modifier.size(20.dp))
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
        IconButton(onClick = { vm.selectDate(d.minusDays(1)) }) {
            Icon(Icons.Default.ChevronLeft, L("前一天", "Previous day"), tint = TextLo)
        }
        IconButton(onClick = { vm.selectDate(d.plusDays(1)) }, enabled = d < today) {
            Icon(Icons.Default.ChevronRight, L("后一天", "Next day"), tint = if (d < today) TextLo else Ink3)
        }
    }
}
