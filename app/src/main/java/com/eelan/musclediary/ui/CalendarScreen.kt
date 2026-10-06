package com.eelan.musclediary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eelan.musclediary.AppViewModel
import com.eelan.musclediary.domain.Calc
import com.eelan.musclediary.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth

/** 日历页：月视图 + 达标标记，点击切换日期 */
@Composable
fun CalendarScreen(vm: AppViewModel) {
    var month by remember { mutableStateOf(YearMonth.from(vm.selectedDate)) }
    val selected = vm.selectedDate
    val today = LocalDate.now()

    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(Icons.Default.ChevronLeft, "上个月", tint = TextLo)
            }
            Text(
                "${month.year}年${month.monthValue}月",
                fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextHi,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
            )
            IconButton(onClick = { month = month.plusMonths(1) }) {
                Icon(Icons.Default.ChevronRight, "下个月", tint = TextLo)
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(it, fontSize = 12.sp, color = TextLo, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(4.dp))

        val lead = (month.atDay(1).dayOfWeek.value + 6) % 7 // 周一开头
        val cells: List<LocalDate?> =
            List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }

        cells.chunked(7).forEach { week ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                week.forEach { d ->
                    Box(Modifier.weight(1f)) {
                        if (d != null) {
                            DayCell(
                                date = d, isToday = d == today,
                                isSelected = d == selected,
                                status = dayStatus(vm, d),
                                onClick = { vm.selectDate(d) },
                            )
                        }
                    }
                }
                if (week.size < 7) repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(12.dp))
        CardBox {
            val d = selected
            val food = vm.foodOn(d)
            val ex = vm.exerciseOn(d)
            val t = Calc.targets(vm.profile, ex.sumOf { it.calories })
            Text("${d.monthValue}月${d.dayOfMonth}日 · 摄入 ${fmt0(food.sumOf { it.kcal })} / 目标 ${fmt0(t.tdee)} kcal",
                fontSize = 14.sp, color = TextHi, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(
                "蛋白质 ${fmt1(food.sumOf { it.protein })} / ${fmt0(t.protein)}g · 锻炼 ${ex.size} 项消耗 ${fmt0(ex.sumOf { it.calories })} kcal",
                fontSize = 12.sp, color = TextLo,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot(Good, "全部达标")
                LegendDot(Warn, "部分达标")
                LegendDot(Ink3, "有记录未达标")
                LegendDot(null, "无记录")
            }
        }
    }
}

/** 当天状态：null 无记录；0 记录了但远未达标；1 部分达标；2 全部达标 */
fun dayStatus(vm: AppViewModel, d: LocalDate): Int? {
    val food = vm.foodOn(d)
    val ex = vm.exerciseOn(d)
    if (food.isEmpty() && ex.isEmpty()) return null
    val t = Calc.targets(vm.profile, ex.sumOf { it.calories })
    val kcal = food.sumOf { it.kcal }
    val protein = food.sumOf { it.protein }
    val kcalOk = t.tdee > 0 && kcal >= t.tdee * 0.9
    val pOk = t.protein > 0 && protein >= t.protein * 0.9
    return if (kcalOk && pOk) 2
    else if (kcal >= t.tdee * 0.5 || protein >= t.protein * 0.5) 1 else 0
}

@Composable
private fun LegendDot(color: Color?, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .then(if (color != null) Modifier.background(color, CircleShape)
                      else Modifier.border(1.dp, Ink3, CircleShape))
        )
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, color = TextLo)
    }
}

@Composable
private fun DayCell(date: LocalDate, isToday: Boolean, isSelected: Boolean, status: Int?, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Ink2 else Color.Transparent)
            .then(if (isSelected) Modifier.border(1.dp, Accent, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Text(
            "${date.dayOfMonth}",
            fontSize = 13.sp,
            color = if (isToday) Accent else TextHi,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
        )
        Spacer(Modifier.height(3.dp))
        status?.let { s ->
            Box(
                Modifier
                    .size(7.dp)
                    .background(
                        when (s) {
                            2 -> Good
                            1 -> Warn
                            else -> Ink3
                        },
                        CircleShape,
                    )
            )
        }
    }
}
