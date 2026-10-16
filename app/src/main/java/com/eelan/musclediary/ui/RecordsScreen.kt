package com.eelan.musclediary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.eelan.musclediary.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth

/**
 * 全屏记录历史页（仿模板管理）：搜索 + 按月分组（分组头带统计）+ 左滑修改/删除。
 * kind 决定展示的数据与行内容：0 体重 / 1 饮水 / 2 饮食 / 3 锻炼。
 */
@Composable
fun RecordsScreen(vm: AppViewModel, kind: Int, onClose: () -> Unit) {
    var search by remember { mutableStateOf("") }

    val title = when (kind) {
        0 -> "体重记录"; 1 -> "饮水记录"; 2 -> "饮食记录"; else -> "锻炼记录"
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "返回", tint = TextLo) }
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextHi, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(
            value = search, onValueChange = { search = it },
            placeholder = { Text("搜索日期（如 10-16 或 2026-10）", color = TextLo) },
            singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        RecordsBody(vm, kind, search, onClose)
    }
}

/** 分类型渲染正文（体重/饮水/饮食/锻炼各自实现分组与统计） */
@Composable
private fun RecordsBody(vm: AppViewModel, kind: Int, search: String, onClose: () -> Unit) {
    fun hit(vararg fields: String) = fields.any { it.contains(search.trim(), ignoreCase = true) }

    when (kind) {
        0 -> {
            // 体重：按月分组，分组头 = 条数 · 均值 · 与上月环比
            val filtered = vm.weightEntries.sortedByDescending { it.date }
                .filter { hit(it.date, "${fmt1(it.weightKg)}") }
            val byMonth = filtered.groupBy { YearMonth.from(LocalDate.parse(it.date)) }
                .toSortedMap(compareByDescending { it })
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                byMonth.forEach { (month, es) ->
                    val avg = es.map { it.weightKg }.average()
                    val prevMonthEs = vm.weightEntries.filter {
                        YearMonth.from(LocalDate.parse(it.date)) == month.minusMonths(1)
                    }
                    val prevAvg = prevMonthEs.map { it.weightKg }.average()
                    val delta = if (prevMonthEs.isNotEmpty()) avg - prevAvg else null
                    item(key = "h$month") {
                        GroupHeader(
                            "${month.year}年${month.monthValue}月 · ${es.size} 条",
                            "均值 ${fmt1(avg)} kg" + delta?.let { d -> " · 环比 ${if (d >= 0) "+" else "−"}${fmt1(kotlin.math.abs(d))} kg" }.orEmpty(),
                        )
                    }
                    items(es, key = { "w${it.id}" }) { e ->
                        val prev = vm.weightEntries.filter { it.date < e.date }.maxByOrNull { it.date }
                        val d = prev?.let { e.weightKg - it.weightKg }
                        SwipeEntryRow(
                            "${e.date.substring(5)} · ${fmt1(e.weightKg)} kg",
                            d?.let { x -> "较上次 ${if (x >= 0) "+" else "−"}${fmt1(kotlin.math.abs(x))} kg" } ?: "首次记录",
                            "${fmt1(e.weightKg)} kg",
                            onEdit = { /* 弹框修改由父级处理 */ },
                            onDelete = { vm.deleteWeight(e) },
                        )
                    }
                }
                if (byMonth.isEmpty()) item { EmptyText("没有匹配的记录") }
            }
        }
        1 -> {
            // 饮水：按天分组（当日多笔），月汇总头 = 日均 · 达标天数
            val target = com.eelan.musclediary.domain.Calc.waterTargetMl(vm.profile.weightKg)
            val filtered = vm.waterEntries.sortedByDescending { it.createdAt }
                .filter { hit(it.date, "${fmt0(it.ml)}") }
            val byDay = filtered.groupBy { it.date }.toSortedMap(compareByDescending { it })
            val byMonth = filtered.groupBy { YearMonth.from(LocalDate.parse(it.date)) }
                .toSortedMap(compareByDescending { it })
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                byMonth.forEach { (month, monthEs) ->
                    val monthDays = monthEs.groupBy { it.date }
                    val avg = monthDays.values.map { g -> g.sumOf { it.ml } }.average()
                    val okDays = monthDays.values.count { g -> g.sumOf { it.ml } >= target }
                    val days = month.lengthOfMonth()
                    item(key = "h$month") {
                        GroupHeader(
                            "${month.year}年${month.monthValue}月",
                            "日均 ${fmt0(avg)} ml · 达标 $okDays/$days 天",
                        )
                    }
                    byDay.filterKeys { YearMonth.from(LocalDate.parse(it)) == month }.forEach { (day, es) ->
                        val total = es.sumOf { it.ml }
                        item(key = "d$day") {
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
                        }
                        items(es, key = { "wl${it.id}" }) { e ->
                            val t = java.time.Instant.ofEpochMilli(e.createdAt)
                                .atZone(java.time.ZoneId.systemDefault())
                            SwipeEntryRow(
                                String.format("%02d:%02d", t.hour, t.minute),
                                "饮水",
                                "+${fmt0(e.ml)} ml",
                                onEdit = {},
                                onDelete = { vm.deleteWater(e) },
                            )
                        }
                    }
                }
                if (byMonth.isEmpty()) item { EmptyText("没有匹配的记录") }
            }
        }
        2 -> {
            // 饮食：按月分组，分组头 = 条数 · 摄入均值
            val filtered = vm.foodEntries.sortedByDescending { it.createdAt }
                .filter { hit(it.date, it.name) }
            val byMonth = filtered.groupBy { YearMonth.from(LocalDate.parse(it.date)) }
                .toSortedMap(compareByDescending { it })
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                byMonth.forEach { (month, es) ->
                    val days = es.map { it.date }.distinct().size.coerceAtLeast(1)
                    val avgKcal = es.sumOf { it.kcal } / days
                    item(key = "h$month") {
                        GroupHeader("${month.year}年${month.monthValue}月 · ${es.size} 条",
                            "日均 ${fmt0(avgKcal)} kcal")
                    }
                    items(es, key = { "f${it.id}" }) { e ->
                        SwipeEntryRow(
                            e.name,
                            "${e.date.substring(5)} · " + foodDetail(e).trimStart(),
                            "${fmt0(e.kcal)} kcal",
                            onEdit = {},
                            onDelete = { vm.deleteFood(e) },
                        )
                    }
                }
                if (byMonth.isEmpty()) item { EmptyText("没有匹配的记录") }
            }
        }
        else -> {
            // 锻炼：按月分组，分组头 = 条数 · 合计消耗
            val filtered = vm.exerciseEntries.sortedByDescending { it.createdAt }
                .filter { hit(it.date, it.name) }
            val byMonth = filtered.groupBy { YearMonth.from(LocalDate.parse(it.date)) }
                .toSortedMap(compareByDescending { it })
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                byMonth.forEach { (month, es) ->
                    item(key = "h$month") {
                        GroupHeader("${month.year}年${month.monthValue}月 · ${es.size} 条",
                            "合计 ${fmt0(es.sumOf { it.calories })} kcal")
                    }
                    items(es, key = { "x${it.id}" }) { e ->
                        SwipeEntryRow(
                            e.name,
                            "${e.date.substring(5)} · ${fmt0(e.calories)} kcal · ${fmt1(e.minutes)} 分钟",
                            "${fmt0(e.calories)} kcal",
                            onEdit = {},
                            onDelete = { vm.deleteExercise(e) },
                        )
                    }
                }
                if (byMonth.isEmpty()) item { EmptyText("没有匹配的记录") }
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
        if (stat.isNotBlank()) Text(stat, fontSize = 11.sp, color = TextLo)
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(text, fontSize = 13.sp, color = TextLo, modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}
