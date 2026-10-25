package com.eelan.musclediary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eelan.musclediary.AppViewModel
import com.eelan.musclediary.L
import com.eelan.musclediary.dYM
import com.eelan.musclediary.dMD
import com.eelan.musclediary.data.ExerciseEntry
import com.eelan.musclediary.data.ExerciseTemplate
import com.eelan.musclediary.data.FoodEntry
import com.eelan.musclediary.data.WaterEntry
import com.eelan.musclediary.data.WeightEntry
import com.eelan.musclediary.domain.Calc
import com.eelan.musclediary.ui.theme.*
import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.YearMonth

/**
 * 全屏记录历史页（仿模板管理）：搜索 + 按月分组（分组头带统计）+ 左滑修改/删除（删除带确认）。
 * kind 决定展示的数据与行内容：0 体重 / 1 饮水 / 2 饮食 / 3 锻炼。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RecordsScreen(vm: AppViewModel, kind: Int, onClose: () -> Unit) {
    var search by remember { mutableStateOf("") }
    // 待确认删除的记录（弹确认框），确认后执行
    var pendingDelete by remember { mutableStateOf<Any?>(null) }
    // 待修改的记录
    var editingWeight by remember { mutableStateOf<WeightEntry?>(null) }
    var editingWater by remember { mutableStateOf<WaterEntry?>(null) }
    var editingFood by remember { mutableStateOf<FoodEntry?>(null) }
    var editingExercise by remember { mutableStateOf<ExerciseEntry?>(null) }

    val title = when (kind) {
        0 -> L("体重记录", "Weight Records"); 1 -> L("饮水记录", "Water Records")
        2 -> L("饮食记录", "Food Records"); else -> L("锻炼记录", "Workout Records")
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, L("返回", "Back"), tint = TextLo) }
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextHi, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(
            value = search, onValueChange = { search = it },
            placeholder = { Text(L("搜索日期（如 10-16 或 2026-10）", "Search date (e.g. 10-16 or 2026-10)"), color = TextLo) },
            singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        RecordsBody(
            vm, kind, search,
            onEdit = { entry ->
                when (entry) {
                    is WeightEntry -> editingWeight = entry
                    is WaterEntry -> editingWater = entry
                    is FoodEntry -> editingFood = entry
                    is ExerciseEntry -> editingExercise = entry
                }
            },
            onDelete = { pendingDelete = it },
        )
    }

    pendingDelete?.let { entry ->
        val name = when (entry) {
            is WeightEntry -> "${entry.date} " + L("体重", "weight") + " ${fmt1(entry.weightKg)}kg"
            is WaterEntry -> "${entry.date} " + L("饮水", "water") + " ${fmt0(entry.ml)}ml"
            is FoodEntry -> "${entry.date} ${entry.name}"
            else -> (entry as ExerciseEntry).let { "${entry.date} ${it.name}" }
        }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = Ink2,
            title = { Text(L("删除这条记录？", "Delete this record?"), color = TextHi, fontSize = 16.sp) },
            text = { Text(name, color = TextLo, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    when (entry) {
                        is WeightEntry -> vm.deleteWeight(entry)
                        is WaterEntry -> vm.deleteWater(entry)
                        is FoodEntry -> vm.deleteFood(entry)
                        is ExerciseEntry -> vm.deleteExercise(entry)
                    }
                    pendingDelete = null
                }) { Text(L("删除", "Delete"), color = Color(0xFFEF5350)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(L("取消", "Cancel"), color = TextLo) }
            },
        )
    }

    editingWeight?.let { e ->
        NumberDialog(
            title = L("修改体重（", "Edit weight (") + "${e.date})", label = L("公斤", "kg"), initial = fmt1(e.weightKg),
            onDismiss = { editingWeight = null },
            onConfirm = { v -> vm.updateWeight(e, v); editingWeight = null },
        )
    }
    editingWater?.let { e ->
        NumberDialog(
            title = L("修改饮水量（", "Edit water (") + "${e.date})", label = L("毫升", "ml"), initial = fmt0(e.ml),
            onDismiss = { editingWater = null },
            onConfirm = { v -> vm.updateWater(e, v); editingWater = null },
        )
    }
    editingFood?.let { e ->
        if (e.grams > 0) {
            FoodEditDialog(
                entryName = e.name,
                currentGrams = e.grams,
                template = vm.foodTemplates.firstOrNull { it.dispName == e.name || it.name == e.name },
                onDismiss = { editingFood = null },
                onConfirm = { v -> vm.updateFoodEntry(e, v); editingFood = null },
            )
        } else {
            MealEditDialog(
                entry = e, onDismiss = { editingFood = null },
                onConfirm = { p, c, f -> vm.updateMealEntry(e, p, c, f); editingFood = null },
            )
        }
    }
    editingExercise?.let { e ->
        NumberDialog(
            title = L("修改「", "Edit [") + "${e.name}" + L("」", "]"),
            label = qtyLabel(e, vm.exerciseTemplates),
            initial = fmt1(e.qty),
            onDismiss = { editingExercise = null },
            onConfirm = { v -> vm.updateExerciseEntry(e, v); editingExercise = null },
        )
    }
}

/** 锻炼修改弹框的数量单位标签：按模板类型显示 个/公里/秒，模板已删按名称兜底 */
private fun qtyLabel(e: ExerciseEntry, templates: List<ExerciseTemplate>): String {
    val unit = (templates.firstOrNull { it.id == e.templateId }
        ?: templates.firstOrNull { it.name == e.name || it.dispName == e.name })
        ?.let { Calc.unitLabel(it) }
        ?: if (isDistanceName(e.name)) L("公里", "km") else L("数量", "qty")
    return if (unit == L("数量", "qty")) L("数量", "qty") else L("数量（", "Qty (") + "$unit)"
}

/** 分类型渲染正文（体重/饮水/饮食/锻炼各自实现分组与统计） */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RecordsBody(
    vm: AppViewModel,
    kind: Int,
    search: String,
    onEdit: (Any) -> Unit,
    onDelete: (Any) -> Unit,
) {
    fun hit(vararg fields: String) = fields.any { it.contains(search.trim(), ignoreCase = true) }

    when (kind) {
        0 -> {
            // 体重：按月分组，分组头 = 条数 · 均值 · 与上月环比
            // 一次预计算月分组与「上一条」映射（原先每月/每条都全表过滤，O(n²)）
            val allByMonth = vm.weightEntries.groupBy { YearMonth.from(LocalDate.parse(it.date)) }
            val asc = vm.weightEntries.sortedBy { it.date }
            val prevByEntry = (1 until asc.size).associate { asc[it] to asc[it - 1] }
            val filtered = vm.weightEntries.sortedByDescending { it.date }
                .filter { hit(it.date, "${fmt1(it.weightKg)}") }
            val byMonth = filtered.groupBy { YearMonth.from(LocalDate.parse(it.date)) }
                .toSortedMap(compareByDescending { it })
            StackedColumn {
                byMonth.forEach { (month, es) ->
                    val avg = es.map { it.weightKg }.average()
                    val prevMonthEs = allByMonth[month.minusMonths(1)].orEmpty()
                    val delta = if (prevMonthEs.isNotEmpty()) avg - prevMonthEs.map { it.weightKg }.average() else null
                    GroupHeader(
                        "${dYM(month.year, month.monthValue)} · ${es.size} " + L("条", ""),
                        L("均值 ", "avg ") + "${fmt1(avg)} kg" + delta?.let { d -> " · " + L("环比", "vs prev") + " ${if (d >= 0) "+" else "-"}${fmt1(kotlin.math.abs(d))} kg" }.orEmpty(),
                    )
                    es.forEach { e ->
                        val d = prevByEntry[e]?.let { e.weightKg - it.weightKg }
                        key(e.id) {
                        Box(Modifier.stackApproach()) {
                            SwipeEntryRow(
                                "${e.date.substring(5)} · ${fmt1(e.weightKg)} kg",
                                d?.let { x -> L("较上次 ", "vs last ") + "${if (x >= 0) "+" else "-"}${fmt1(kotlin.math.abs(x))} kg" } ?: L("首次记录", "first entry"),
                                "${fmt1(e.weightKg)} kg",
                                onEdit = { onEdit(e) },
                                onDelete = { onDelete(e) },
                            )
                        }
                        }
                    }
                }
                if (byMonth.isEmpty()) EmptyText(L("没有匹配的记录", "No matching records"))
            }
        }
        1 -> {
            // 饮水：按天分组（当日多笔），月汇总头 = 日均 · 达标天数（不含未来日期）
            val target = com.eelan.musclediary.domain.Calc.waterTargetMl(vm.profile.weightKg)
            val filtered = vm.waterEntries.sortedByDescending { it.createdAt }
                .filter { hit(it.date, "${fmt0(it.ml)}") }
            val byDay = filtered.groupBy { it.date }.toSortedMap(compareByDescending { it })
            val byMonth = filtered.groupBy { YearMonth.from(LocalDate.parse(it.date)) }
                .toSortedMap(compareByDescending { it })
            StackedColumn {
                byMonth.forEach { (month, monthEs) ->
                    val monthDays = monthEs.groupBy { it.date }
                    val avg = monthDays.values.map { g -> g.sumOf { it.ml } }.average()
                    val okDays = monthDays.values.count { g -> g.sumOf { it.ml } >= target }
                    // 分母 = 当月已过的天数（当月按今天算，历史月按月末），未来天数不参与达标率
                    val today = LocalDate.now()
                    val lastCounted = when {
                        month < YearMonth.from(today) -> month.atEndOfMonth()
                        month == YearMonth.from(today) -> today
                        else -> month.atDay(1).minusDays(1) // 未来月（理论上不会出现）
                    }
                    val days = lastCounted.dayOfMonth.coerceAtLeast(1)
                    GroupHeader(
                        dYM(month.year, month.monthValue),
                        L("日均 ", "daily avg ") + "${fmt0(avg)} ml · " + L("达标 ", "goal met ") + "$okDays/$days" + L(" 天", " days"),
                    )
                    byDay.filterKeys { YearMonth.from(LocalDate.parse(it)) == month }.forEach { (day, es) ->
                        val total = es.sumOf { it.ml }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(day.substring(5), fontSize = 13.sp, color = TextHi, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text("${fmt0(total)} ml",
                                fontSize = 13.sp,
                                color = if (total >= target) Good else Accent,
                                fontWeight = FontWeight.Bold)
                        }
                        es.forEach { e ->
                            val t = java.time.Instant.ofEpochMilli(e.createdAt)
                                .atZone(java.time.ZoneId.systemDefault())
                            key(e.id) {
                            Box(Modifier.stackApproach()) {
                                SwipeEntryRow(
                                    String.format("%02d:%02d", t.hour, t.minute),
                                    L("饮水", "Water"),
                                    "+${fmt0(e.ml)} ml",
                                    onEdit = { onEdit(e) },
                                    onDelete = { onDelete(e) },
                                )
                            }
                            }
                        }
                    }
                }
                if (byMonth.isEmpty()) EmptyText(L("没有匹配的记录", "No matching records"))
            }
        }
        2 -> {
            // 饮食：按月分组，分组头 = 条数 · 摄入均值
            val filtered = vm.foodEntries.sortedByDescending { it.createdAt }
                .filter { hit(it.date, it.name) }
            val byMonth = filtered.groupBy { YearMonth.from(LocalDate.parse(it.date)) }
                .toSortedMap(compareByDescending { it })
            StackedColumn {
                byMonth.forEach { (month, es) ->
                    val days = es.map { it.date }.distinct().size.coerceAtLeast(1)
                    val avgKcal = es.sumOf { it.kcal } / days
                    GroupHeader("${dYM(month.year, month.monthValue)} · ${es.size} " + L("条", ""),
                        L("日均 ", "daily avg ") + "${fmt0(avgKcal)} kcal")
                    es.forEach { e ->
                        key(e.id) {
                        Box(Modifier.stackApproach()) {
                            SwipeEntryRow(
                                e.name,
                                "${e.date.substring(5)} · " + foodDetail(e).trimStart(),
                                "${fmt0(e.kcal)} kcal",
                                onEdit = { onEdit(e) },
                                onDelete = { onDelete(e) },
                            )
                        }
                        }
                    }
                }
                if (byMonth.isEmpty()) EmptyText(L("没有匹配的记录", "No matching records"))
            }
        }
        else -> {
            // 锻炼：按月分组，分组头 = 条数 · 合计消耗
            val filtered = vm.exerciseEntries.sortedByDescending { it.createdAt }
                .filter { hit(it.date, it.name) }
            val byMonth = filtered.groupBy { YearMonth.from(LocalDate.parse(it.date)) }
                .toSortedMap(compareByDescending { it })
            StackedColumn {
                byMonth.forEach { (month, es) ->
                    GroupHeader("${dYM(month.year, month.monthValue)} · ${es.size} " + L("条", ""),
                        L("合计 ", "total ") + "${fmt0(es.sumOf { it.calories })} kcal")
                    es.forEach { e ->
                        key(e.id) {
                        Box(Modifier.stackApproach()) {
                            SwipeEntryRow(
                                e.name,
                                "${e.date.substring(5)} · ${fmt0(e.calories)} kcal · ${fmt1(e.minutes)} " + L("分钟", "min"),
                                "${fmt0(e.calories)} kcal",
                                onEdit = { onEdit(e) },
                                onDelete = { onDelete(e) },
                            )
                        }
                        }
                    }
                }
                if (byMonth.isEmpty()) EmptyText(L("没有匹配的记录", "No matching records"))
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String, stat: String) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Ink1)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Accent)
        if (stat.isNotBlank()) Text(stat, fontSize = 12.sp, color = TextLo)
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(text, fontSize = 13.sp, color = TextLo, modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}
