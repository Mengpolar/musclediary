package com.eelan.musclediary.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eelan.musclediary.AppViewModel
import com.eelan.musclediary.L
import com.eelan.musclediary.data.ExerciseTemplate
import com.eelan.musclediary.data.QtyType
import com.eelan.musclediary.domain.Calc
import com.eelan.musclediary.domain.Muscle
import com.eelan.musclediary.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONObject

/** 锻炼页：肌肉概览图 + 当日记录 + 模板快速添加 */
@Composable
fun WorkoutScreen(vm: AppViewModel, onOpenWeightRecords: () -> Unit = {}, onOpenRecords: () -> Unit = {}) {
    var pickOpen by remember { mutableStateOf(false) }
    var pendingTemplate by remember { mutableStateOf<ExerciseTemplate?>(null) }
    var customOpen by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<com.eelan.musclediary.data.ExerciseEntry?>(null) }
    var showCalendar by remember { mutableStateOf(false) }

    val date = vm.selectedDate
    val entries = vm.exerciseOn(date).sortedBy { it.createdAt }

    // 删除撤销：主页左滑删除立即生效，Snackbar 期间可一键恢复
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun deleteWithUndo(e: com.eelan.musclediary.data.ExerciseEntry) {
        vm.deleteExercise(e)
        scope.launch {
            if (snackbar.showSnackbar(
                    L("已删除 ", "Deleted ") + e.name, actionLabel = L("撤销", "Undo")
                ) == SnackbarResult.ActionPerformed
            ) vm.restoreExercise(e)
        }
    }

    // 汇总当天各肌群刺激分
    val scores = remember(entries) {
        val map = mutableMapOf<String, Double>()
        entries.forEach { e ->
            runCatching {
                val obj = JSONObject(e.musclesJson)
                obj.keys().forEach { k -> map[k] = (map[k] ?: 0.0) + obj.optDouble(k, 0.0) }
            }
        }
        map.toMap()
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { pickOpen = true },
                containerColor = Accent,
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                Icon(Icons.Default.Add, L("添加锻炼", "Add exercise"), tint = Ink0)
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            DateHeader(vm, onOpenCalendar = { showCalendar = true })
            Spacer(Modifier.height(8.dp))
            CardBox {
                Text(L("肌肉概览", "Muscle map"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextHi)
                Spacer(Modifier.height(4.dp))
                MuscleMap(scores, { Calc.intensity(it) }, onPick = { })
            }
            Spacer(Modifier.height(12.dp))
            CardBox {
                Text("BMI", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextHi)
                Spacer(Modifier.height(4.dp))
                BmiBar(Calc.bmi(vm.profile.weightKg, vm.profile.heightCm),
                    vm.profile.weightKg, vm.profile.heightCm)
            }
            Spacer(Modifier.height(12.dp))
            WeightCard(vm, onOpenRecords = onOpenWeightRecords)
            SectionTitle(L("消耗统计", "Calories Burned"))
            if (entries.isEmpty()) {
                Text(L("当天还没有锻炼记录", "No exercise recorded for this day"), fontSize = 12.sp, color = TextLo)
            } else {
                CardBox {
                    // 同名动作合并为一条统计项，n 为该动作当天记录条数
                    val stats = entries.groupBy { it.name }
                        .map { (name, es) -> Triple(name, es.sumOf { it.calories }, es.size) }
                        .sortedByDescending { it.second }
                    val maxKcal = stats.maxOf { it.second }.coerceAtLeast(1.0)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        stats.forEach { (name, kcal, count) ->
                            val animBar by animateFloatAsState(
                                targetValue = (kcal / maxKcal).toFloat(),
                                animationSpec = tween(700), label = "statBar",
                            )
                            Column {
                                Row(Modifier.fillMaxWidth()) {
                                    Text(if (count > 1) "$name ×$count" else name,
                                        fontSize = 13.sp, color = TextHi)
                                    Spacer(Modifier.weight(1f))
                                    Text("${fmt0(kcal)} kcal", fontSize = 13.sp, color = Accent,
                                        fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(3.dp))
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .background(Ink2, RoundedCornerShape(4.dp))
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(animBar.coerceIn(0.02f, 1f))
                                            .fillMaxHeight()
                                            .background(Accent, RoundedCornerShape(4.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SectionTitleRow(L("锻炼记录（", "Workout records (") + "${entries.size})", L("全部 >", "All >"), onMore = onOpenRecords)
            if (entries.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.FitnessCenter,
                    text = L("今天还没练，选个动作开始记录", "No workout yet - pick an exercise to log"),
                    actionText = L("添加锻炼", "Add exercise"),
                    onAction = { pickOpen = true },
                )
            } else {
                // 记录列表：整页滚动模型（与饮食页的内滚列表不同），卡片不钉顶
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    ),
                ) {
                    // 删除带粉碎动画；新增/删除条目时整列尺寸弹性过渡（整页滚动，卡片不钉顶）
                    entries.forEach { e ->
                        key(e.id) {
                            SwipeEntryRow(
                                e.name,
                                workoutDetail(e, vm.exerciseTemplates),
                                "${fmt0(e.calories)} kcal",
                                onEdit = { editingEntry = e },
                                onDelete = { deleteWithUndo(e) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    if (showCalendar) {
        CalendarSheet(vm, onDismiss = { showCalendar = false })
    }

    if (pickOpen) {
        ExercisePickerDialog(
            templates = vm.exerciseTemplates,
            onPick = { pickOpen = false; pendingTemplate = it },
            onCustom = { pickOpen = false; customOpen = true },
            onDismiss = { pickOpen = false },
        )
    }

    pendingTemplate?.let { t ->
        val recent = vm.recentExerciseQty(t)
        WorkoutQtyDialog(
            template = t, bodyWeight = vm.profile.weightKg,
            lastQty = recent.firstOrNull()?.first, lastWeight = recent.firstOrNull()?.second,
            recent = recent,
            onDismiss = { pendingTemplate = null },
            onConfirm = { qty, weight ->
                vm.addExercise(t, qty, weight, vm.selectedDate)
                pendingTemplate = null
            },
        )
    }

    editingEntry?.let { e ->
        val unit = (vm.exerciseTemplates.firstOrNull { it.id == e.templateId }
            ?: vm.exerciseTemplates.firstOrNull { it.name == e.name || it.dispName == e.name })
            ?.let { Calc.unitLabel(it) }
            ?: if (isDistanceName(e.name)) L("公里", "km") else L("数量", "qty")
        NumberDialog(
            title = L("修改「", "Edit 「") + "${e.name}" + L("」", "」"),
            label = if (unit == L("数量", "qty")) L("数量", "qty") else L("数量（", "Qty (") + "$unit)",
            initial = fmt1(e.qty),
            onDismiss = { editingEntry = null },
            onConfirm = { qty -> vm.updateExerciseEntry(e, qty); editingEntry = null },
        )
    }

    if (customOpen) {
        CustomExerciseDialog(
            builtinExists = { n -> vm.exerciseTemplates.any { !it.isCustom && (it.name == n || it.nameEn == n) } },
            onDismiss = { customOpen = false },
            onConfirm = { t ->
                vm.addCustomExercise(t) { saved -> pendingTemplate = saved }
                customOpen = false
            },
        )
    }
}

/** 哑铃类动作判定（中英文名都认）：决定是否展示附加重量输入 */
private fun isDumbbell(t: ExerciseTemplate): Boolean =
    t.name.contains("哑铃") || t.nameEn.contains("Dumbbell")

/** 锻炼行 detail：单位按模板类型区分（个/公里/秒） */
private fun workoutDetail(e: com.eelan.musclediary.data.ExerciseEntry, templates: List<ExerciseTemplate>): String {
    // 模板可能已被删（或重播种后 id 变化），先按 id 再按名称回查
    val unit = (templates.firstOrNull { it.id == e.templateId }
        ?: templates.firstOrNull { it.name == e.name || it.dispName == e.name })
        ?.let { Calc.unitLabel(it) }
        ?: if (isDistanceName(e.name)) L("公里", "km") else L("个", " reps")
    val qty = when {
        e.weightKg > 0 -> "${fmt1(e.qty)}" + L("个", " reps") + "x${fmt1(e.weightKg)}kg"
        else -> "${fmt1(e.qty)}$unit"
    }
    val muscles = runCatching {
        JSONObject(e.musclesJson).keys().asSequence().toList()
            .sortedByDescending { JSONObject(e.musclesJson).optDouble(it, 0.0) }
            .take(2).joinToString(", ") { Muscle.labelOf(it) }
    }.getOrDefault("")
    return "$qty · ${fmt1(e.minutes)} " + L("分钟", "min") + if (muscles.isNotBlank()) " · $muscles" else ""
}

@Composable
private fun ExercisePickerDialog(
    templates: List<ExerciseTemplate>,
    onPick: (ExerciseTemplate) -> Unit,
    onCustom: () -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        title = { Text(L("选择锻炼", "Pick an exercise"), color = TextHi, fontSize = 16.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    placeholder = { Text(L("搜索", "Search"), color = TextLo) },
                    singleLine = true, colors = com.eelan.musclediary.ui.fieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                val filtered = templates.filter { it.name.contains(search, ignoreCase = true) || it.nameEn.contains(search, ignoreCase = true) }
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(filtered, key = { it.id }) { t ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(t) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(t.dispName, color = TextHi, fontSize = 14.sp)
                                Text(
                                    L("主练", "Primary: ") + "${Muscle.labelOf(t.primaryMuscle)} · ${
                                        when (t.qtyType) {
                                            QtyType.REPS -> L("按次", "reps")
                                            QtyType.DISTANCE -> L("按公里", "km")
                                            QtyType.SECONDS -> L("按秒", "sec")
                                        }
                                    }",
                                    color = TextLo, fontSize = 12.sp,
                                )
                            }
                            Text("MET ${fmt1(t.met)}", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                TextButton(onClick = onCustom) { Text(L("＋ 新建自定义动作", "+ New custom exercise"), color = Accent, fontSize = 13.sp) }
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}

@Composable
private fun WorkoutQtyDialog(
    template: ExerciseTemplate,
    bodyWeight: Double,
    lastQty: Double?,
    lastWeight: Double?,
    recent: List<Pair<Double, Double>> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (Double, Double) -> Unit,
) {
    var qty by remember { mutableStateOf(lastQty?.let { fmt1(it) } ?: "") }
    var weight by remember {
        mutableStateOf(lastWeight?.takeIf { it > 0 }?.let { fmt1(it) }
            ?: if (isDumbbell(template)) "10" else "")
    }
    val q = qty.toDoubleOrNull()
    val valid = q != null && q > 0
    val preview = if (q != null) Calc.exerciseCalories(template, q, bodyWeight) else null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        title = { Text(template.dispName, color = TextHi, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = qty, onValueChange = { qty = it },
                    // 按 qtyType 分支取问题文案：unitLabel 已本地化，拿它比较在英文模式下会全部失配
                    label = { Text(when (template.qtyType) {
                        QtyType.REPS -> L("做了多少个？", "How many reps?")
                        QtyType.DISTANCE -> L("多少公里？", "How many km?")
                        QtyType.SECONDS -> L("坚持了多少秒？", "How many seconds?")
                    }, color = TextLo) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = com.eelan.musclediary.ui.fieldColors(),
                )
                if (isDumbbell(template)) {
                    OutlinedTextField(
                        value = weight, onValueChange = { weight = it },
                        label = { Text(L("哑铃单只重量 kg（可选）", "Dumbbell weight kg (optional)"), color = TextLo) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = com.eelan.musclediary.ui.fieldColors(),
                    )
                }
                // 最近 3 次记录（去重）：点击直接填入；标签与数值成对生成，点击按标签直查
                val chipMap = recent.associate { (qty0, w0) ->
                    val base = when (template.qtyType) {
                        QtyType.DISTANCE -> "${fmt1(qty0)}" + L("公里", "km")
                        QtyType.SECONDS -> "${fmt0(qty0)}" + L("秒", "s")
                        else -> "${fmt0(qty0)}" + L("个", " reps")
                    }
                    (if (w0 > 0) "$base(x${fmt1(w0)}kg)" else base) to (qty0 to w0)
                }
                QuickChips(
                    chips = chipMap.keys.toList(),
                    onPick = { c ->
                        chipMap[c]?.let { (q0, w0) ->
                            qty = fmt1(q0)
                            if (w0 > 0) weight = fmt1(w0)
                        }
                    },
                )
                if (preview != null) {
                    Text(
                        L("预计消耗 ", "Est. burn ") + "${fmt0(preview.first)} kcal · ${fmt1(preview.second)} " + L("分钟", "min") + " · " + L("主练", "primary: ") + "${Muscle.labelOf(template.primaryMuscle)}",
                        color = Accent, fontSize = 13.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onConfirm(q!!, weight.toDoubleOrNull() ?: 0.0)
            }) { Text(L("记录", "Log"), color = if (valid) Accent else TextLo) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("取消", "Cancel"), color = TextLo) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomExerciseDialog(
    initial: ExerciseTemplate? = null,
    builtinExists: (String) -> Boolean = { false },
    onDismiss: () -> Unit,
    onConfirm: (ExerciseTemplate) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.dispName ?: "") }
    var type by remember { mutableStateOf(initial?.qtyType ?: QtyType.REPS) }
    var met by remember { mutableStateOf(initial?.let { fmt1(it.met) } ?: "") }
    var perRep by remember { mutableStateOf(initial?.takeIf { it.perRepSeconds > 0 }?.let { fmt1(it.perRepSeconds) } ?: "3") }
    var pace by remember { mutableStateOf(initial?.takeIf { it.paceMinPerKm > 0 }?.let { fmt1(it.paceMinPerKm) } ?: "8") }
    var primary by remember { mutableStateOf(initial?.primaryMuscle ?: Muscle.CHEST.id) }
    var secondary by remember {
        mutableStateOf(initial?.secondaryMuscles?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: setOf<String>())
    }

    val metD = met.toDoubleOrNull()
    val valid = name.isNotBlank() && metD != null && metD > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        title = { Text(if (initial == null) L("自定义动作", "Custom exercise") else L("修改「", "Edit [") + "${initial.dispName}" + L("」", "]"), color = TextHi, fontSize = 16.sp) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text(L("名称", "Name"), color = TextLo) }, singleLine = true,
                    colors = com.eelan.musclediary.ui.fieldColors())
                // 与内置模板重名：仅提示不阻止保存（编辑自身名称时不提示）
                val dup = name.trim().let { n ->
                    n.isNotEmpty() && n != initial?.name && n != initial?.nameEn && builtinExists(n)
                }
                if (dup) Text(L("已存在同名内置模板，仍可保存", "A built-in template with this name exists (still saveable)"),
                    fontSize = 12.sp, color = Warn)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = type == QtyType.REPS, onClick = { type = QtyType.REPS },
                        label = { Text(L("按次", "Reps"), fontSize = 12.sp) })
                    FilterChip(selected = type == QtyType.DISTANCE, onClick = { type = QtyType.DISTANCE },
                        label = { Text(L("按公里", "Km"), fontSize = 12.sp) })
                    FilterChip(selected = type == QtyType.SECONDS, onClick = { type = QtyType.SECONDS },
                        label = { Text(L("按秒", "Sec"), fontSize = 12.sp) })
                }
                OutlinedTextField(value = met, onValueChange = { met = it },
                    label = { Text(L("MET 强度（走路3.5 慢跑8 力量训练5~8）", "MET intensity (walk 3.5 jog 8 lifting 5~8)"), color = TextLo) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = com.eelan.musclediary.ui.fieldColors())
                when (type) {
                    QtyType.REPS -> OutlinedTextField(value = perRep, onValueChange = { perRep = it },
                        label = { Text(L("单次耗时（秒，默认3）", "Seconds per rep (default 3)"), color = TextLo) }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = com.eelan.musclediary.ui.fieldColors())
                    QtyType.DISTANCE -> OutlinedTextField(value = pace, onValueChange = { pace = it },
                        label = { Text(L("配速（分钟/公里，默认8）", "Pace (min/km, default 8)"), color = TextLo) }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = com.eelan.musclediary.ui.fieldColors())
                    QtyType.SECONDS -> {}
                }
                Text(L("主练部位（单选）", "Primary muscle (single)"), fontSize = 12.sp, color = TextLo)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Muscle.entries.forEach { m ->
                        FilterChip(
                            selected = primary == m.id, onClick = { primary = m.id },
                            label = { Text(Muscle.labelOf(m.id), fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Accent.copy(alpha = 0.35f),
                            ),
                        )
                    }
                }
                Text(L("兼练部位（可多选）", "Secondary muscles (multi)"), fontSize = 12.sp, color = TextLo)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Muscle.entries.forEach { m ->
                        val on = m.id in secondary
                        FilterChip(
                            selected = on, onClick = {
                                secondary = if (on) secondary - m.id else secondary + m.id
                            },
                            label = { Text(Muscle.labelOf(m.id), fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Accent.copy(alpha = 0.35f),
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onConfirm(
                    (initial ?: ExerciseTemplate(name = "", qtyType = type, met = metD!!, primaryMuscle = primary)).copy(
                        name = name.trim(), qtyType = type, met = metD!!,
                        perRepSeconds = perRep.toDoubleOrNull() ?: 3.0,
                        paceMinPerKm = pace.toDoubleOrNull() ?: 8.0,
                        primaryMuscle = primary,
                        secondaryMuscles = secondary.joinToString(","),
                        isCustom = initial?.isCustom ?: true,
                    )
                )
            }) { Text(L("保存", "Save"), color = if (valid) Accent else TextLo) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("取消", "Cancel"), color = TextLo) } },
    )
}
