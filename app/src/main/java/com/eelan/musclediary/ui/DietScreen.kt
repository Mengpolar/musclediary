package com.eelan.musclediary.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eelan.musclediary.AppViewModel
import com.eelan.musclediary.L
import com.eelan.musclediary.tagLabel
import com.eelan.musclediary.data.FoodEntry
import com.eelan.musclediary.data.FoodTemplate
import com.eelan.musclediary.ui.theme.*

/** 饮食页：当日记录列表 + 模板快速添加 + 自定义食物 */
@Composable
fun DietScreen(vm: AppViewModel, onOpenRecords: () -> Unit = {}) {
    var pickOpen by remember { mutableStateOf(false) }
    var pendingTemplate by remember { mutableStateOf<FoodTemplate?>(null) }
    var customOpen by remember { mutableStateOf(false) }
    var mealOpen by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<FoodEntry?>(null) }
    var showCalendar by remember { mutableStateOf(false) }

    val date = vm.selectedDate
    val entries = vm.foodOn(date).sortedBy { it.createdAt }
    val kcal = entries.sumOf { it.kcal }
    val protein = entries.sumOf { it.protein }
    val carb = entries.sumOf { it.carb }
    val fat = entries.sumOf { it.fat }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { pickOpen = true },
                containerColor = Accent,
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                Icon(Icons.Default.Add, L("添加饮食", "Add food"), tint = Ink0)
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            DateHeader(vm, onOpenCalendar = { showCalendar = true })
            Spacer(Modifier.height(8.dp))
            CardBox(title = L("当日摄入", "Today's intake")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${fmt0(kcal)} kcal", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Accent)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(L("蛋白质 ", "Protein ") + "${fmt1(protein)}g", fontSize = 12.sp, color = Protein)
                        Text(L("碳水 ", "Carbs ") + "${fmt1(carb)}g", fontSize = 12.sp, color = CarbC)
                        Text(L("脂肪 ", "Fat ") + "${fmt1(fat)}g", fontSize = 12.sp, color = FatC)
                    }
                }
            }
            SectionTitleRow(L("饮食记录（", "Food records (") + "${entries.size})", L("全部 >", "All >"), onMore = onOpenRecords)
            if (entries.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Restaurant,
                    text = L("还没有记录，从内置模板快速添加", "No records yet — add from built-in templates"),
                    actionText = L("添加饮食", "Add food"),
                    onAction = { pickOpen = true },
                )
            } else {
                StackedColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                    entries.forEach { f ->
                        // key 强制每个条目独立状态，防止删除/新增时复用残留的钉住偏移（空行 bug）
                        key(f.id) {
                        Box(Modifier.stackApproach()) {
                            SwipeEntryRow(
                                f.name,
                                foodDetail(f),
                                "${fmt0(f.kcal)} kcal",
                                onEdit = { editingEntry = f },
                                onDelete = { vm.deleteFood(f) },
                            )
                        }
                        }
                    }
                }
            }
        }
    }

    if (pickOpen) {
        TemplatePickerDialog(
            title = L("选择食物（每100g营养）", "Pick a food (per 100g)"),
            templates = vm.foodTemplates,
            detail = { t -> L("蛋白", "P") + "${fmt1(t.protein)} " + L("碳水", "C") + "${fmt1(t.carb)} " +
                    L("脂肪", "F") + "${fmt1(t.fat)}" },
            trailing = { t -> "${fmt0(t.kcal)} kcal" },
            onPick = { pickOpen = false; pendingTemplate = it },
            onCustom = { pickOpen = false; customOpen = true },
            onMeal = { pickOpen = false; mealOpen = true },
            onDismiss = { pickOpen = false },
        )
    }

    pendingTemplate?.let { t ->
        FoodGramsDialog(
            title = t.name,
            template = t,
            initialGrams = vm.recentFoodGrams(t).firstOrNull() ?: 100.0,
            recent = vm.recentFoodGrams(t),
            confirmText = L("记录", "Log"),
            onDismiss = { pendingTemplate = null },
            onConfirm = { grams -> vm.addFood(t, grams, vm.selectedDate); pendingTemplate = null },
        )
    }

    editingEntry?.let { e ->
        if (e.grams <= 0.0) {
            // 快速记录的餐食：热量由三维营养自动计算，只编辑三维
            MealEditDialog(e,
                onDismiss = { editingEntry = null },
                onConfirm = { p, c, f -> vm.updateMealEntry(e, p, c, f); editingEntry = null })
        } else {
            FoodEditDialog(
                entryName = e.name,
                currentGrams = e.grams,
                template = vm.foodTemplates.firstOrNull { it.name == e.name },
                onDismiss = { editingEntry = null },
                onConfirm = { grams -> vm.updateFoodEntry(e, grams); editingEntry = null },
            )
        }
    }

    if (customOpen) {
        CustomFoodDialog(
            onDismiss = { customOpen = false },
            onConfirm = { name, p, c, f, tag, unitName, unitGrams ->
                vm.addCustomFood(name, p, c, f, tag, unitName, unitGrams) { saved ->
                    pendingTemplate = saved
                }
                customOpen = false
            },
        )
    }

    if (mealOpen) {
        MealQuickDialog(
            onDismiss = { mealOpen = false },
            onConfirm = { mealName, kcalV, pV, cV, fV ->
                vm.addMealEntry(mealName, kcalV, pV, cV, fV, vm.selectedDate)
                mealOpen = false
            },
        )
    }

    if (showCalendar) {
        CalendarSheet(vm, onDismiss = { showCalendar = false })
    }
}

/** 食物/动作模板选择器 */
@Composable
fun TemplatePickerDialog(
    title: String,
    templates: List<FoodTemplate>,
    detail: (FoodTemplate) -> String,
    trailing: (FoodTemplate) -> String,
    onPick: (FoodTemplate) -> Unit,
    onCustom: () -> Unit,
    onMeal: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        title = { Text(title, color = TextHi, fontSize = 16.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    placeholder = { Text(L("搜索", "Search"), color = TextLo) },
                    singleLine = true,
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(6.dp))
                // 标签筛选（两行流式布局，保证 5 个标签都可见可点）
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    com.eelan.musclediary.data.ALL_TAGS.forEach { tag ->
                        FilterChip(
                            selected = tagFilter == tag,
                            onClick = { tagFilter = if (tagFilter == tag) null else tag },
                            label = { Text(tagLabel(tag), fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Accent.copy(alpha = 0.35f),
                                selectedLabelColor = Accent,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                val filtered = templates.filter {
                    it.name.contains(search, ignoreCase = true) &&
                            (tagFilter == null || it.tag.contains(tagFilter!!))
                }
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(filtered, key = { it.id }) { t ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(t) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(t.name, color = TextHi, fontSize = 14.sp)
                                    if (t.tag.isNotBlank()) {
                                        Spacer(Modifier.width(6.dp))
                                        t.tag.split(',').filter { it.isNotBlank() }.forEach { tg ->
                                            Text(
                                                tagLabel(tg), fontSize = 12.sp, color = Accent,
                                                modifier = Modifier
                                                    .padding(end = 4.dp)
                                                    .background(Accent.copy(alpha = 0.15f),
                                                        RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                }
                                Text(detail(t), color = TextLo, fontSize = 12.sp)
                            }
                            Text(trailing(t), color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = onCustom, modifier = Modifier.weight(1f)) {
                        Text(L("＋ 新建自定义", "＋ New custom"), color = Accent, fontSize = 13.sp)
                    }
                    TextButton(onClick = onMeal, modifier = Modifier.weight(1f)) {
                        Text(L("＋ 快速记录一餐", "＋ Quick meal"), color = Accent, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun fieldColors() = TextFieldDefaults.colors(
    focusedTextColor = TextHi, unfocusedTextColor = TextHi,
    focusedContainerColor = Ink1, unfocusedContainerColor = Ink1,
    focusedIndicatorColor = Accent, unfocusedIndicatorColor = Ink3,
    cursorColor = Accent,
)

@Composable
fun CustomFoodDialog(
    initial: FoodTemplate? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Double, Double, String, String, Double) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var p by remember { mutableStateOf(initial?.let { fmt1(it.protein) } ?: "") }
    var c by remember { mutableStateOf(initial?.let { fmt1(it.carb) } ?: "") }
    var f by remember { mutableStateOf(initial?.let { fmt1(it.fat) } ?: "") }
    var tag by remember { mutableStateOf(initial?.tag ?: "") }
    var unitName by remember { mutableStateOf(initial?.unitName ?: "") }
    var unitGrams by remember { mutableStateOf(initial?.takeIf { it.unitGrams > 0 }?.let { fmt0(it.unitGrams) } ?: "") }
    var kJ by remember { mutableStateOf("") } // 包装能量（千焦），填后自动反推三维
    val pd = p.toDoubleOrNull(); val cd = c.toDoubleOrNull(); val fd = f.toDoubleOrNull()
    val ug = unitGrams.toDoubleOrNull()
    val unitInvalid = unitName.isNotBlank() && (ug == null || ug <= 0)
    val valid = name.isNotBlank() && pd != null && cd != null && fd != null && !unitInvalid
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        title = { Text(
            if (initial == null) L("自定义食物（每100g）", "Custom food (per 100g)")
            else L("修改「", "Edit \"") + initial.name + L("」（每100g）", "\" (per 100g)"),
            color = TextHi, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text(L("名称", "Name"), color = TextLo) }, singleLine = true, colors = fieldColors())
                // 能量直填：包装标签单位是千焦（kJ），填后按 15/50/35 能量占比反推三维营养
                OutlinedTextField(
                    value = kJ, onValueChange = { v ->
                        kJ = v
                        v.toDoubleOrNull()?.let { kj ->
                            if (kj > 0) {
                                val kcal = kj / 4.184
                                p = fmt1(kcal * 0.15 / 4)
                                c = fmt1(kcal * 0.50 / 4)
                                f = fmt1(kcal * 0.35 / 9)
                            }
                        }
                    },
                    label = { Text(L("能量 kJ", "Energy kJ"), color = TextLo, fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal), colors = fieldColors(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = p, onValueChange = { p = it },
                        label = { Text(L("蛋白g", "P g"), color = TextLo, fontSize = 12.sp) }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), colors = fieldColors())
                    OutlinedTextField(value = c, onValueChange = { c = it },
                        label = { Text(L("碳水g", "C g"), color = TextLo, fontSize = 12.sp) }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), colors = fieldColors())
                    OutlinedTextField(value = f, onValueChange = { f = it },
                        label = { Text(L("脂肪g", "F g"), color = TextLo, fontSize = 12.sp) }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), colors = fieldColors())
                }
                // 标签选择（可多选）
                Text(L("标签（可多选）", "Tags (multi-select)"), fontSize = 12.sp, color = TextLo)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    com.eelan.musclediary.data.ALL_TAGS.take(3).forEach { tg ->
                        FilterChip(
                            selected = tag.split(',').contains(tg),
                            onClick = {
                                val cur = tag.split(',').filter { it.isNotBlank() }.toMutableSet()
                                if (tg in cur) cur.remove(tg) else cur.add(tg)
                                tag = cur.joinToString(",")
                            },
                            label = { Text(tagLabel(tg), fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Accent.copy(alpha = 0.35f),
                                selectedLabelColor = Accent,
                            ),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    com.eelan.musclediary.data.ALL_TAGS.drop(3).forEach { tg ->
                        FilterChip(
                            selected = tag.split(',').contains(tg),
                            onClick = {
                                val cur = tag.split(',').filter { it.isNotBlank() }.toMutableSet()
                                if (tg in cur) cur.remove(tg) else cur.add(tg)
                                tag = cur.joinToString(",")
                            },
                            label = { Text(tagLabel(tg), fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Accent.copy(alpha = 0.35f),
                                selectedLabelColor = Accent,
                            ),
                        )
                    }
                }
                // 量词单位（可选）：如名称填「个」、等于填 50，表示 1个=50g
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = unitName, onValueChange = { unitName = it },
                        label = { Text(L("量词单位（可选，如：个/盒/碗）", "Unit (optional: pc/box/bowl)"), color = TextLo, fontSize = 12.sp) },
                        singleLine = true, modifier = Modifier.weight(1.4f), colors = fieldColors())
                    OutlinedTextField(value = unitGrams, onValueChange = { unitGrams = it },
                        label = { Text(L("1单位=?g", "1 unit = ? g"), color = TextLo, fontSize = 12.sp) },
                        singleLine = true,
                        enabled = unitName.isNotBlank(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), colors = fieldColors())
                }
                if (pd != null && cd != null && fd != null) {
                    val formula = pd * 4 + cd * 4 + fd * 9
                    val hint = if (kJ.toDoubleOrNull()?.let { it > 0 } == true)
                        "(" + L("包装 ", "package ") + "${fmt0(kJ.toDoubleOrNull()!!)} kJ ≈ ${fmt0(kJ.toDoubleOrNull()!! / 4.184)} kcal)"
                    else ""
                    Text(L("热量 ≈ ", "~") + "${fmt0(formula)} kcal/100g $hint",
                        color = Accent, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onConfirm(name.trim(), pd!!, cd!!, fd!!, tag,
                    unitName.trim(), if (unitName.isNotBlank()) (ug ?: 0.0) else 0.0)
            }) { Text(L("保存", "Save"), color = if (valid) Accent else TextLo) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("取消", "Cancel"), color = TextLo) } },
    )
}

/**
 * 快速记录一餐：选餐次（早餐/午餐/晚餐/加餐），直接输入这一餐的热量与三大营养素。
 * 适合外食、食堂等不方便按食材拆分的场景；记录名如「午餐」，与普通食物一样参与当日统计。
 */
@Composable
fun MealQuickDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Double, Double, Double) -> Unit,
) {
    val meals = listOf(L("早餐", "Breakfast"), L("午餐", "Lunch"), L("晚餐", "Dinner"), L("加餐", "Snack"))
    // 按当前时间默认选中餐次
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    var meal by remember {
        mutableStateOf(when {
            hour < 10 -> L("早餐", "Breakfast"); hour < 15 -> L("午餐", "Lunch")
            hour < 21 -> L("晚餐", "Dinner"); else -> L("加餐", "Snack")
        })
    }
    var kcal by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    var f by remember { mutableStateOf("") }
    val kd = kcal.toDoubleOrNull(); val pd = p.toDoubleOrNull()
    val cd = c.toDoubleOrNull(); val fd = f.toDoubleOrNull()
    // 热量必填；三维至少填一项（未填的按 0 处理）
    val valid = (kd != null && kd > 0) || (pd != null || cd != null || fd != null)
    // 从三维反推热量参考
    val calcKcal = listOfNotNull(pd, cd, fd).let { if (it.size == 3) pd!! * 4 + cd!! * 4 + fd!! * 9 else null }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        title = { Text(L("快速记录一餐", "Quick meal"), color = TextHi, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 餐次选择
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    meals.forEach { m ->
                        FilterChip(
                            selected = meal == m,
                            onClick = { meal = m },
                            label = { Text(m, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Accent.copy(alpha = 0.35f),
                                selectedLabelColor = Accent,
                            ),
                        )
                    }
                }
                OutlinedTextField(
                    value = kcal, onValueChange = { kcal = it },
                    label = { Text(L("这一餐多少热量 kcal（推荐填）", "Calories for this meal (recommended)"), color = TextLo) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal),
                    colors = fieldColors(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = p, onValueChange = { p = it },
                        label = { Text(L("蛋白g", "P g"), color = TextLo, fontSize = 12.sp) }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), colors = fieldColors())
                    OutlinedTextField(value = c, onValueChange = { c = it },
                        label = { Text(L("碳水g", "C g"), color = TextLo, fontSize = 12.sp) }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), colors = fieldColors())
                    OutlinedTextField(value = f, onValueChange = { f = it },
                        label = { Text(L("脂肪g", "F g"), color = TextLo, fontSize = 12.sp) }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), colors = fieldColors())
                }
                if (calcKcal != null) {
                    Text(L("按三维推算 ≈ ", "From macros ≈ ") + "${fmt0(calcKcal)} kcal" + L("（热量没填时会用它）", " (used if calories empty)"),
                        fontSize = 12.sp, color = TextLo)
                }
                if (!valid) Text(L("至少填写热量或任一营养素", "Fill calories or any macro"), fontSize = 12.sp, color = Warn)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val k = kd ?: calcKcal ?: 0.0
                onConfirm(meal, k, pd ?: 0.0, cd ?: 0.0, fd ?: 0.0)
            }) { Text(L("记录", "Log"), color = if (valid) Accent else TextLo) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("取消", "Cancel"), color = TextLo) } },
    )
}

/** 饮食记录行 detail：按克录入显示克数，快速记录的餐食（grams=0）只显示营养 */
internal fun foodDetail(f: com.eelan.musclediary.data.FoodEntry): String {
    val head = if (f.grams > 0) "${fmt0(f.grams)}g · " else ""
    return head + L("蛋白", "P") + "${fmt1(f.protein)} " + L("碳水", "C") + "${fmt1(f.carb)} " + L("脂肪", "F") + "${fmt1(f.fat)}"
}

/** 修改快速记录的餐食：热量是三维计算属性，只编辑三维，热量实时展示 */
@Composable
internal fun MealEditDialog(
    entry: com.eelan.musclediary.data.FoodEntry,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double, Double) -> Unit,
) {
    var p by remember { mutableStateOf(fmt1(entry.protein)) }
    var c by remember { mutableStateOf(fmt1(entry.carb)) }
    var f by remember { mutableStateOf(fmt1(entry.fat)) }
    val pd = p.toDoubleOrNull()
    val cd = c.toDoubleOrNull()
    val fd = f.toDoubleOrNull()
    val valid = pd != null && cd != null && fd != null &&
        pd >= 0 && cd >= 0 && fd >= 0
    val realKcal = if (valid) pd!! * 4 + cd!! * 4 + fd!! * 9 else null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        title = { Text(L("修改「", "Edit [") + "${entry.name}" + L("」", "]"), color = TextHi, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (realKcal != null) {
                    Text(L("热量（按三维自动计算）≈ ", "Calories (derived from macros) ≈ ") + "${fmt0(realKcal)} kcal",
                        fontSize = 13.sp, color = Accent)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = p, onValueChange = { p = it },
                        label = { Text(L("蛋白g", "P g"), color = TextLo, fontSize = 12.sp) }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), colors = fieldColors())
                    OutlinedTextField(value = c, onValueChange = { c = it },
                        label = { Text(L("碳水g", "C g"), color = TextLo, fontSize = 12.sp) }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), colors = fieldColors())
                    OutlinedTextField(value = f, onValueChange = { f = it },
                        label = { Text(L("脂肪g", "F g"), color = TextLo, fontSize = 12.sp) }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), colors = fieldColors())
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onConfirm(pd!!, cd!!, fd!!); onDismiss()
            }) { Text(L("确定", "OK"), color = if (valid) Accent else TextLo) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("取消", "Cancel"), color = TextLo) } },
    )
}
