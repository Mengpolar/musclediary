package com.eelan.musclediary.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.eelan.musclediary.data.matchesEntryName
import com.eelan.musclediary.ui.theme.*
import kotlinx.coroutines.launch

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

    // 删除撤销：主页左滑删除立即生效，Snackbar 短时显示（4s）可一键恢复，尽快还出底部操作区
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun deleteWithUndo(f: FoodEntry) {
        vm.deleteFood(f)
        scope.launch {
            if (snackbar.showSnackbar(
                    L("已删除 ", "Deleted ") + f.name, actionLabel = L("撤销", "Undo"),
                    duration = SnackbarDuration.Short,
                ) == SnackbarResult.ActionPerformed
            ) vm.restoreFood(f)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
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
                        Text("${fmt0(kcal)} kcal", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Accent)
                        Spacer(Modifier.height(6.dp))
                        FoodMacroChips(
                            protein = protein,
                            carb = carb,
                            fat = fat,
                        )
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
                                title = f.name,
                                trailing = "${fmt0(f.kcal)} kcal",
                                onEdit = { editingEntry = f },
                                onDelete = { deleteWithUndo(f) },
                            ) {
                                FoodMacroChips(
                                    protein = f.protein,
                                    carb = f.carb,
                                    fat = f.fat,
                                    grams = f.grams,
                                )
                            }
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
            trailing = { t -> "${fmt0(t.kcal)} kcal" },
            onPick = { pickOpen = false; pendingTemplate = it },
            onCustom = { pickOpen = false; customOpen = true },
            onMeal = { pickOpen = false; mealOpen = true },
            onDismiss = { pickOpen = false },
        )
    }

    pendingTemplate?.let { t ->
        FoodGramsDialog(
            title = t.dispName,
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
                template = vm.foodTemplates.firstOrNull { it.matchesEntryName(e.name) },
                onDismiss = { editingEntry = null },
                onConfirm = { grams -> vm.updateFoodEntry(e, grams); editingEntry = null },
            )
        }
    }

    if (customOpen) {
        CustomFoodDialog(
            builtinExists = { n -> vm.foodTemplates.any { !it.isCustom && (it.name == n || it.nameEn == n) } },
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
    trailing: (FoodTemplate) -> String,
    onPick: (FoodTemplate) -> Unit,
    onCustom: () -> Unit,
    onMeal: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    AppDialog(
        title = title,
        subtitle = L("点选食材直接录入，也可新建或快速记整餐", "Tap a food to log, or create custom / quick meal"),
        badge = "${templates.size}",
        onDismiss = onDismiss,
        dismissText = L("关闭", "Close"),
    ) {
        OutlinedTextField(
            value = search, onValueChange = { search = it },
            placeholder = { Text(L("搜索食物名称…", "Search food…"), color = TextLo) },
            singleLine = true,
            shape = appFieldShape,
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(),
        )
        Spacer(Modifier.height(8.dp))
        // 标签筛选（两行流式布局，保证 5 个标签都可见可点）
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            com.eelan.musclediary.data.ALL_TAGS.forEach { tag ->
                FilterChip(
                    selected = tagFilter == tag,
                    onClick = { tagFilter = if (tagFilter == tag) null else tag },
                    label = { Text(tagLabel(tag), fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Accent.copy(alpha = 0.28f),
                        selectedLabelColor = Accent,
                    ),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val filtered = templates.filter {
            (it.name.contains(search, ignoreCase = true) || it.nameEn.contains(search, ignoreCase = true)) &&
                    (tagFilter == null || it.tag.contains(tagFilter!!))
        }
        LazyColumn(
            modifier = Modifier.height(290.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(filtered, key = { it.id }) { t ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ink2)
                        .border(0.75.dp, Ink3.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .clickable { onPick(t) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.dispName, color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            if (t.tag.isNotBlank()) {
                                Spacer(Modifier.width(6.dp))
                                t.tag.split(',').filter { it.isNotBlank() }.take(2).forEach { tg ->
                                    Text(
                                        tagLabel(tg), fontSize = 10.sp, color = Accent,
                                        modifier = Modifier
                                            .padding(end = 4.dp)
                                            .background(Accent.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 5.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        val unitBadge = if (t.unitName.isNotBlank() && t.unitGrams > 0)
                            "1${foodUnitLabel(t.unitName)}=${fmt0(t.unitGrams)}g"
                        else null
                        FoodMacroChips(
                            protein = t.protein,
                            carb = t.carb,
                            fat = t.fat,
                            extraBadge = unitBadge,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(trailing(t), color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Accent.copy(alpha = 0.12f))
                    .border(1.dp, Accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .clickable(onClick = onCustom)
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(L("＋ 新建自定义", "＋ New custom"), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Protein.copy(alpha = 0.12f))
                    .border(1.dp, Protein.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .clickable(onClick = onMeal)
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(L("＋ 快速记一餐", "＋ Quick meal"), color = Protein, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextHi, unfocusedTextColor = TextHi,
    focusedContainerColor = Ink2, unfocusedContainerColor = Ink2,
    disabledContainerColor = Ink2.copy(alpha = 0.5f),
    focusedBorderColor = Accent, unfocusedBorderColor = Ink3,
    disabledBorderColor = Ink3.copy(alpha = 0.4f),
    cursorColor = Accent,
)

@Composable
fun CustomFoodDialog(
    initial: FoodTemplate? = null,
    builtinExists: (String) -> Boolean = { false },
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Double, Double, String, String, Double) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.dispName ?: "") }
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
    AppDialog(
        title = if (initial == null) L("自定义食物", "Custom Food")
        else L("修改「", "Edit \"") + initial.dispName + L("」", "\""),
        subtitle = L("营养数值均按每 100g 填写", "All nutrition values are per 100g"),
        badge = "100g",
        onDismiss = onDismiss,
        confirmText = L("保存", "Save"),
        confirmEnabled = valid,
        onConfirm = {
            onConfirm(name.trim(), pd!!, cd!!, fd!!, tag,
                unitName.trim(), if (unitName.isNotBlank()) (ug ?: 0.0) else 0.0)
        },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text(L("名称", "Name"), color = TextLo) },
                singleLine = true, shape = appFieldShape,
                modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
            )
            // 与内置模板重名：仅提示不阻止保存（编辑自身名称时不提示）
            val dup = name.trim().let { n ->
                n.isNotEmpty() && n != initial?.name && n != initial?.nameEn && builtinExists(n)
            }
            if (dup) Text(L("已存在同名内置模板，仍可保存", "A built-in template with this name exists (still saveable)"),
                fontSize = 12.sp, color = Warn)
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
                label = { Text(L("包装能量 kJ（选填，自动推算三维）", "Package kJ (optional, auto-fills macros)"), color = TextLo, fontSize = 12.sp) },
                singleLine = true, shape = appFieldShape,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Decimal), colors = fieldColors(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = p, onValueChange = { p = it },
                    label = { Text(L("蛋白g", "P g"), color = TextLo, fontSize = 12.sp) }, singleLine = true,
                    shape = appFieldShape,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), colors = fieldColors())
                OutlinedTextField(value = c, onValueChange = { c = it },
                    label = { Text(L("碳水g", "C g"), color = TextLo, fontSize = 12.sp) }, singleLine = true,
                    shape = appFieldShape,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), colors = fieldColors())
                OutlinedTextField(value = f, onValueChange = { f = it },
                    label = { Text(L("脂肪g", "F g"), color = TextLo, fontSize = 12.sp) }, singleLine = true,
                    shape = appFieldShape,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), colors = fieldColors())
            }
            // 标签选择（可多选）
            Text(L("标签（可多选）", "Tags (multi-select)"), fontSize = 12.sp, color = TextLo)
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.eelan.musclediary.data.ALL_TAGS.forEach { tg ->
                    FilterChip(
                        selected = tag.split(',').contains(tg),
                        onClick = {
                            val cur = tag.split(',').filter { it.isNotBlank() }.toMutableSet()
                            if (tg in cur) cur.remove(tg) else cur.add(tg)
                            tag = cur.joinToString(",")
                        },
                        label = { Text(tagLabel(tg), fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Accent.copy(alpha = 0.28f),
                            selectedLabelColor = Accent,
                        ),
                    )
                }
            }
            // 量词单位（可选）：如名称填「个」、等于填 50，表示 1个=50g
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = unitName, onValueChange = { unitName = it },
                    label = { Text(L("单位（如：个/盒）", "Unit (pc/box)"), color = TextLo, fontSize = 12.sp) },
                    singleLine = true, shape = appFieldShape, modifier = Modifier.weight(1.2f), colors = fieldColors())
                OutlinedTextField(value = unitGrams, onValueChange = { unitGrams = it },
                    label = { Text(L("1单位=?g", "1 unit = ? g"), color = TextLo, fontSize = 12.sp) },
                    singleLine = true, shape = appFieldShape,
                    enabled = unitName.isNotBlank(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f), colors = fieldColors())
            }
            if (pd != null && cd != null && fd != null) {
                val formula = pd * 4 + cd * 4 + fd * 9
                val hint = if (kJ.toDoubleOrNull()?.let { it > 0 } == true)
                    " (${L("包装 ", "pkg ")}${fmt0(kJ.toDoubleOrNull()!!)} kJ)"
                else ""
                DialogPreviewPill(L("热量 ≈ ", "~") + "${fmt0(formula)} kcal / 100g$hint")
            }
        }
    }
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

    AppDialog(
        title = L("快速记录一餐", "Quick Meal"),
        subtitle = L("外食或食堂不便拆分时，直接记整餐热量与营养", "Log whole meal calories or macros directly"),
        badge = meal,
        accentColor = Protein,
        onDismiss = onDismiss,
        confirmText = L("记录", "Log"),
        confirmEnabled = valid,
        onConfirm = {
            val k = kd ?: calcKcal ?: 0.0
            onConfirm(meal, k, pd ?: 0.0, cd ?: 0.0, fd ?: 0.0)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 餐次选择
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                meals.forEach { m ->
                    FilterChip(
                        selected = meal == m,
                        onClick = { meal = m },
                        label = { Text(m, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Protein.copy(alpha = 0.28f),
                            selectedLabelColor = Protein,
                        ),
                    )
                }
            }
            OutlinedTextField(
                value = kcal, onValueChange = { kcal = it },
                label = { Text(L("整餐热量 kcal（推荐填）", "Meal calories kcal (recommended)"), color = TextLo) },
                singleLine = true, shape = appFieldShape,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Decimal),
                colors = fieldColors(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = p, onValueChange = { p = it },
                    label = { Text(L("蛋白g", "P g"), color = TextLo, fontSize = 12.sp) }, singleLine = true,
                    shape = appFieldShape,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f), colors = fieldColors())
                OutlinedTextField(value = c, onValueChange = { c = it },
                    label = { Text(L("碳水g", "C g"), color = TextLo, fontSize = 12.sp) }, singleLine = true,
                    shape = appFieldShape,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f), colors = fieldColors())
                OutlinedTextField(value = f, onValueChange = { f = it },
                    label = { Text(L("脂肪g", "F g"), color = TextLo, fontSize = 12.sp) }, singleLine = true,
                    shape = appFieldShape,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f), colors = fieldColors())
            }
            if (calcKcal != null) {
                DialogPreviewPill(
                    L("按三维推算 ≈ ", "From macros ≈ ") + "${fmt0(calcKcal)} kcal" + L("（热量留空时采用）", " (used if kcal empty)"),
                    color = Protein,
                )
            }
            if (!valid) Text(L("至少填写热量或任一营养素", "Fill calories or any macro"), fontSize = 12.sp, color = Warn)
        }
    }
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
    AppDialog(
        title = L("修改「", "Edit [") + "${entry.name}" + L("」", "]"),
        subtitle = L("调整三大营养素，热量自动重算", "Adjust macros; calories update automatically"),
        onDismiss = onDismiss,
        confirmText = L("确定", "OK"),
        confirmEnabled = valid,
        onConfirm = { onConfirm(pd!!, cd!!, fd!!); onDismiss() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = p, onValueChange = { p = it },
                    label = { Text(L("蛋白g", "P g"), color = TextLo, fontSize = 12.sp) }, singleLine = true,
                    shape = appFieldShape,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f), colors = fieldColors())
                OutlinedTextField(value = c, onValueChange = { c = it },
                    label = { Text(L("碳水g", "C g"), color = TextLo, fontSize = 12.sp) }, singleLine = true,
                    shape = appFieldShape,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f), colors = fieldColors())
                OutlinedTextField(value = f, onValueChange = { f = it },
                    label = { Text(L("脂肪g", "F g"), color = TextLo, fontSize = 12.sp) }, singleLine = true,
                    shape = appFieldShape,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f), colors = fieldColors())
            }
            if (realKcal != null) {
                DialogPreviewPill(L("热量 ≈ ", "Calories ≈ ") + "${fmt0(realKcal)} kcal")
            }
        }
    }
}
