package com.eelan.musclediary.ui

import androidx.compose.foundation.layout.*
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

/** 今日总览：目标环 + 摄入/运动摘要 */
@Composable
fun TodayScreen(vm: AppViewModel, onGoDiet: () -> Unit, onGoWorkout: () -> Unit) {
    val date = vm.selectedDate
    val food = vm.foodOn(date)
    val exKcal = vm.exerciseKcalOn(date)
    val p = vm.profile
    val targets = Calc.targets(p, exKcal)
    val kcal = food.sumOf { it.kcal }
    val protein = food.sumOf { it.protein }
    val carb = food.sumOf { it.carb }
    val fat = food.sumOf { it.fat }

    var showSheet by remember { mutableStateOf(false) }
    var sheetWhich by remember { mutableStateOf("kcal") }

    Column(Modifier.padding(horizontal = 16.dp)) {
        DateHeader(vm)
        Spacer(Modifier.height(8.dp))
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(
                    progress = if (targets.tdee > 0) (kcal / targets.tdee).toFloat() else 0f,
                    color = Accent, size = 130.dp, stroke = 12.dp,
                    centerLabel = "热量", centerValue = "${fmt0(kcal)}",
                    subValue = "目标 ${fmt0(targets.tdee)}",
                    onClick = { sheetWhich = "kcal"; showSheet = true },
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MacroBar("蛋白质", protein, targets.protein, Protein) { sheetWhich = "protein"; showSheet = true }
                    MacroBar("碳水", carb, targets.carb, CarbC) { sheetWhich = "carb"; showSheet = true }
                    MacroBar("脂肪", fat, targets.fat, FatC) { sheetWhich = "fat"; showSheet = true }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "运动消耗 ${fmt0(exKcal)} kcal 已计入今日活动系数，点击任意数值查看计算过程",
                fontSize = 11.sp, color = TextLo,
            )
        }

        SectionTitle("今日饮食（${food.size}）")
        if (food.isEmpty()) EmptyHint("还没有记录，去添加吧") { onGoDiet() }
        else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            food.takeLast(3).forEach { f ->
                EntryRow(f.name, "${fmt0(f.grams)}g · 蛋白${fmt1(f.protein)} 碳水${fmt1(f.carb)} 脂肪${fmt1(f.fat)}",
                    "${fmt0(f.kcal)} kcal", onDelete = { vm.deleteFood(f) })
            }
            if (food.size > 3) MoreHint("还有 ${food.size - 3} 条") { onGoDiet() }
        }

        SectionTitle("今日锻炼（${vm.exerciseOn(date).size}）")
        if (vm.exerciseOn(date).isEmpty()) EmptyHint("还没锻炼，练起来！") { onGoWorkout() }
        else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            vm.exerciseOn(date).takeLast(3).forEach { e ->
                EntryRow(e.name, "${fmtQty(e)} · ${fmt1(e.minutes)} 分钟", "${fmt0(e.calories)} kcal",
                    onDelete = { vm.deleteExercise(e) })
            }
            if (vm.exerciseOn(date).size > 3) MoreHint("还有 ${vm.exerciseOn(date).size - 3} 条") { onGoWorkout() }
        }
    }

    if (showSheet) {
        DerivationSheet(
            profile = p, exerciseKcal = exKcal,
            intakeKcal = kcal, intakeProtein = protein, intakeCarb = carb, intakeFat = fat,
            onDismiss = { showSheet = false },
        )
    }
}

fun fmtQty(e: com.eelan.musclediary.data.ExerciseEntry): String =
    if (e.weightKg > 0) "${fmt1(e.qty)} 个×${fmt1(e.weightKg)}kg" else "${fmt1(e.qty)} 个"

@Composable
fun DateHeader(vm: AppViewModel) {
    val d = vm.selectedDate
    val today = LocalDate.now()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            "${d.year}年${d.monthValue}月${d.dayOfMonth}日 · ${
                listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[d.dayOfWeek.value - 1]
            }",
            fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = TextHi,
            modifier = Modifier.weight(1f),
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

@Composable
private fun EmptyHint(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick, color = Ink1, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, fontSize = 13.sp, color = TextLo, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun MoreHint(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(text, color = Accent, fontSize = 13.sp) }
}
