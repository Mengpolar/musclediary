package com.eelan.musclediary.ui

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
        floatingActionButton = {
            FloatingActionButton(onClick = { pickOpen = true }, containerColor = Accent) {
                Icon(Icons.Default.Add, "添加饮食", tint = Ink0)
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            DateHeader(vm, onOpenCalendar = { showCalendar = true })
            Spacer(Modifier.height(8.dp))
            CardBox(title = "当日摄入") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${fmt0(kcal)} kcal", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Accent)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("蛋白质 ${fmt1(protein)}g", fontSize = 12.sp, color = Protein)
                        Text("碳水 ${fmt1(carb)}g", fontSize = 12.sp, color = CarbC)
                        Text("脂肪 ${fmt1(fat)}g", fontSize = 12.sp, color = FatC)
                    }
                }
            }
            SectionTitleRow("饮食记录（${entries.size}）", "全部 >", onMore = onOpenRecords)
            if (entries.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Restaurant,
                    text = "还没有记录，从内置模板快速添加",
                    actionText = "添加饮食",
                    onAction = { pickOpen = true },
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(entries, key = { it.id }) { f ->
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

    if (pickOpen) {
        TemplatePickerDialog(
            title = "选择食物（每100g营养）",
            templates = vm.foodTemplates,
            detail = { t -> "蛋白${fmt1(t.protein)} 碳水${fmt1(t.carb)} 脂肪${fmt1(t.fat)}" },
            trailing = { t -> "${fmt0(t.kcal)} kcal" },
            onPick = { pickOpen = false; pendingTemplate = it },
            onCustom = { pickOpen = false; customOpen = true },
            onMeal = { pickOpen = false; mealOpen = true },
            onDismiss = { pickOpen = false },
        )
    }

    pendingTemplate?.let { t ->
        FoodQtyDialog(
            template = t,
            recent = vm.recentFoodGrams(t),
            onDismiss = { pendingTemplate = null },
            onConfirm = { grams -> vm.addFood(t, grams, vm.selectedDate); pendingTemplate = null },
        )
    }

    editingEntry?.let { e ->
        if (e.grams <= 0.0) {
            // 快速记录的餐食：直接改热量（营养不按比例缩放，保持手输值）
            MealEditDialog(e,
                onDismiss = { editingEntry = null },
                onConfirm = { kcal -> vm.updateMealEntry(e, kcal); editingEntry = null })
        } else {
            NumberDialog(
                title = "修改「${e.name}」", label = "克数", initial = fmt1(e.grams),
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

/**
 * 食物记录对话框：
 * - 模板设了量词单位（如 1个=50g、1盒=250ml）时，可按「X 个/盒」或克数输入，二者联动
 * - 输入框下方展示最近 3 次记录（去重）快捷标签，点击直接填入
 */
@Composable
private fun FoodQtyDialog(
    template: FoodTemplate,
    recent: List<Double>,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    val hasUnit = template.unitName.isNotBlank() && template.unitGrams > 0
    val unitName = template.unitName.ifBlank { "份" }

    // grams 始终为真值来源；unitText 独立显示，仅在联动时更新
    var gramsText by remember(template.id) {
        mutableStateOf(fmt0(recent.firstOrNull() ?: 100.0))
    }
    var unitText by remember(template.id) {
        mutableStateOf(
            if (hasUnit) fmt1((recent.firstOrNull() ?: (template.unitGrams)) / template.unitGrams) else ""
        )
    }

    val grams = gramsText.toDoubleOrNull()
    val valid = grams != null && grams > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        title = { Text(template.name, color = TextHi, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasUnit) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = unitText, onValueChange = { v ->
                                unitText = v
                                v.toDoubleOrNull()?.let { u -> gramsText = fmt1(u * template.unitGrams) }
                            },
                            label = { Text("吃了多少$unitName？", color = TextLo) },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f), colors = fieldColors(),
                        )
                        Text("$unitName = ${fmt0(template.unitGrams)}g",
                            fontSize = 12.sp, color = TextLo)
                    }
                }
                OutlinedTextField(
                    value = gramsText, onValueChange = { v ->
                        gramsText = v
                        if (hasUnit) {
                            v.toDoubleOrNull()?.let { g -> unitText = fmt1(g / template.unitGrams) }
                        }
                    },
                    label = { Text(if (hasUnit) "或输入克数 g" else "吃了多少克？", color = TextLo) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal),
                    colors = fieldColors(),
                )
                QuickChips(
                    chips = recent.map { g ->
                        if (hasUnit) "${fmt1(g / template.unitGrams)}$unitName（${fmt0(g)}g）" else "${fmt0(g)}g"
                    },
                    onPick = { c ->
                        // 从标签文本反解出克数：取括号里的数字，无括号取整体
                        val g = Regex("（([0-9.]+)g）").find(c)?.groupValues?.get(1)?.toDoubleOrNull()
                            ?: c.removeSuffix("g").toDoubleOrNull()
                        g?.let {
                            gramsText = fmt1(it)
                            if (hasUnit) unitText = fmt1(it / template.unitGrams)
                        }
                    },
                )
                if (valid && grams != null) {
                    val k = grams / 100.0
                    Text(
                        "蛋白${fmt1(template.protein * k)}g · 碳水${fmt1(template.carb * k)}g · " +
                                "脂肪${fmt1(template.fat * k)}g · ${fmt0(template.kcal * k)} kcal",
                        color = Accent, fontSize = 13.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onConfirm(grams!!); onDismiss()
            }) { Text("记录", color = if (valid) Accent else TextLo) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextLo) } },
    )
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
                    placeholder = { Text("搜索", color = TextLo) },
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
                            label = { Text(tag, fontSize = 12.sp) },
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
                                                tg, fontSize = 12.sp, color = Accent,
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
                        Text("＋ 新建自定义", color = Accent, fontSize = 13.sp)
                    }
                    TextButton(onClick = onMeal, modifier = Modifier.weight(1f)) {
                        Text("🍽 快速记录一餐", color = Accent, fontSize = 13.sp)
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
        title = { Text(if (initial == null) "自定义食物（每100g）" else "修改「${initial.name}」（每100g）", color = TextHi, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("名称", color = TextLo) }, singleLine = true, colors = fieldColors())
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
                    label = { Text("能量 kJ（可选，填包装上的千焦值自动算营养）", color = TextLo, fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal), colors = fieldColors(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = p, onValueChange = { p = it },
                        label = { Text("蛋白g", color = TextLo, fontSize = 12.sp) }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), colors = fieldColors())
                    OutlinedTextField(value = c, onValueChange = { c = it },
                        label = { Text("碳水g", color = TextLo, fontSize = 12.sp) }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), colors = fieldColors())
                    OutlinedTextField(value = f, onValueChange = { f = it },
                        label = { Text("脂肪g", color = TextLo, fontSize = 12.sp) }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), colors = fieldColors())
                }
                // 标签选择（可多选）
                Text("标签（可多选）", fontSize = 12.sp, color = TextLo)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    com.eelan.musclediary.data.ALL_TAGS.take(3).forEach { tg ->
                        FilterChip(
                            selected = tag.split(',').contains(tg),
                            onClick = {
                                val cur = tag.split(',').filter { it.isNotBlank() }.toMutableSet()
                                if (tg in cur) cur.remove(tg) else cur.add(tg)
                                tag = cur.joinToString(",")
                            },
                            label = { Text(tg, fontSize = 12.sp) },
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
                            label = { Text(tg, fontSize = 12.sp) },
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
                        label = { Text("量词单位（可选，如：个/盒/碗）", color = TextLo, fontSize = 12.sp) },
                        singleLine = true, modifier = Modifier.weight(1.4f), colors = fieldColors())
                    OutlinedTextField(value = unitGrams, onValueChange = { unitGrams = it },
                        label = { Text("1单位=?g", color = TextLo, fontSize = 12.sp) },
                        singleLine = true,
                        enabled = unitName.isNotBlank(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), colors = fieldColors())
                }
                if (pd != null && cd != null && fd != null) {
                    val formula = pd * 4 + cd * 4 + fd * 9
                    val hint = if (kJ.toDoubleOrNull()?.let { it > 0 } == true)
                        "（包装 ${fmt0(kJ.toDoubleOrNull()!!)} kJ ≈ ${fmt0(kJ.toDoubleOrNull()!! / 4.184)} kcal）"
                    else ""
                    Text("热量 ≈ ${fmt0(formula)} kcal/100g $hint",
                        color = Accent, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onConfirm(name.trim(), pd!!, cd!!, fd!!, tag,
                    unitName.trim(), if (unitName.isNotBlank()) (ug ?: 0.0) else 0.0)
            }) { Text("保存", color = if (valid) Accent else TextLo) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextLo) } },
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
    val meals = listOf("早餐", "午餐", "晚餐", "加餐")
    // 按当前时间默认选中餐次
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    var meal by remember {
        mutableStateOf(when {
            hour < 10 -> "早餐"; hour < 15 -> "午餐"; hour < 21 -> "晚餐"; else -> "加餐"
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
        title = { Text("快速记录一餐", color = TextHi, fontSize = 16.sp) },
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
                    label = { Text("这一餐多少热量 kcal（推荐填）", color = TextLo) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal),
                    colors = fieldColors(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = p, onValueChange = { p = it },
                        label = { Text("蛋白g", color = TextLo, fontSize = 12.sp) }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), colors = fieldColors())
                    OutlinedTextField(value = c, onValueChange = { c = it },
                        label = { Text("碳水g", color = TextLo, fontSize = 12.sp) }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), colors = fieldColors())
                    OutlinedTextField(value = f, onValueChange = { f = it },
                        label = { Text("脂肪g", color = TextLo, fontSize = 12.sp) }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), colors = fieldColors())
                }
                if (calcKcal != null) {
                    Text("按三维推算 ≈ ${fmt0(calcKcal)} kcal（热量没填时会用它）",
                        fontSize = 12.sp, color = TextLo)
                }
                if (!valid) Text("至少填写热量或任一营养素", fontSize = 12.sp, color = Warn)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val k = kd ?: calcKcal ?: 0.0
                onConfirm(meal, k, pd ?: 0.0, cd ?: 0.0, fd ?: 0.0)
            }) { Text("记录", color = if (valid) Accent else TextLo) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextLo) } },
    )
}

/** 饮食记录行 detail：按克录入显示克数，快速记录的餐食（grams=0）只显示营养 */
internal fun foodDetail(f: com.eelan.musclediary.data.FoodEntry): String {
    val head = if (f.grams > 0) "${fmt0(f.grams)}g · " else ""
    return "${head}蛋白${fmt1(f.protein)} 碳水${fmt1(f.carb)} 脂肪${fmt1(f.fat)}"
}

/** 修改快速记录的餐食热量 */
@Composable
internal fun MealEditDialog(
    entry: com.eelan.musclediary.data.FoodEntry,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var kcal by remember { mutableStateOf(fmt0(entry.kcal)) }
    val kd = kcal.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        title = { Text("修改「${entry.name}」热量", color = TextHi, fontSize = 16.sp) },
        text = {
            OutlinedTextField(
                value = kcal, onValueChange = { kcal = it },
                label = { Text("热量 kcal", color = TextLo) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Decimal),
                colors = fieldColors(),
            )
        },
        confirmButton = {
            TextButton(enabled = kd != null && kd > 0, onClick = {
                onConfirm(kd!!); onDismiss()
            }) { Text("确定", color = if (kd != null && kd > 0) Accent else TextLo) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextLo) } },
    )
}
