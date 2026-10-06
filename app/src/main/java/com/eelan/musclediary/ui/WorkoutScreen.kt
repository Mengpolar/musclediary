package com.eelan.musclediary.ui

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eelan.musclediary.AppViewModel
import com.eelan.musclediary.data.ExerciseTemplate
import com.eelan.musclediary.data.QtyType
import com.eelan.musclediary.domain.Calc
import com.eelan.musclediary.domain.Muscle
import com.eelan.musclediary.ui.theme.*
import org.json.JSONObject

/** 锻炼页：肌肉概览图 + 当日记录 + 模板快速添加 */
@Composable
fun WorkoutScreen(vm: AppViewModel) {
    var pickOpen by remember { mutableStateOf(false) }
    var pendingTemplate by remember { mutableStateOf<ExerciseTemplate?>(null) }
    var customOpen by remember { mutableStateOf(false) }

    val date = vm.selectedDate
    val entries = vm.exerciseOn(date).sortedBy { it.createdAt }

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
        floatingActionButton = {
            FloatingActionButton(onClick = { pickOpen = true }, containerColor = Accent) {
                Icon(Icons.Default.Add, "添加锻炼", tint = Ink0)
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            DateHeader(vm)
            Spacer(Modifier.height(8.dp))
            CardBox {
                Text("肌肉概览", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextHi)
                Spacer(Modifier.height(4.dp))
                MuscleMap(scores, { Calc.intensity(it) }, onPick = { })
            }
            SectionTitle("锻炼记录（${entries.size}） · 共消耗 ${fmt0(entries.sumOf { it.calories })} kcal")
            if (entries.isEmpty()) {
                Text(
                    "还没有锻炼记录，点右下角 + 开始记录", fontSize = 13.sp, color = TextLo,
                    modifier = Modifier.fillMaxWidth()
                        .background(Ink1, RoundedCornerShape(12.dp)).padding(14.dp),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    entries.forEach { e ->
                        EntryRow(
                            e.name,
                            workoutDetail(e),
                            "${fmt0(e.calories)} kcal",
                            onDelete = { vm.deleteExercise(e) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
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
        WorkoutQtyDialog(
            template = t, bodyWeight = vm.profile.weightKg,
            onDismiss = { pendingTemplate = null },
            onConfirm = { qty, weight ->
                vm.addExercise(t, qty, weight, vm.selectedDate)
                pendingTemplate = null
            },
        )
    }

    if (customOpen) {
        CustomExerciseDialog(
            onDismiss = { customOpen = false },
            onConfirm = { t ->
                vm.addCustomExercise(t) { saved -> pendingTemplate = saved }
                customOpen = false
            },
        )
    }
}

private fun workoutDetail(e: com.eelan.musclediary.data.ExerciseEntry): String {
    val qty = when {
        e.weightKg > 0 -> "${fmt1(e.qty)}个×${fmt1(e.weightKg)}kg"
        else -> "${fmt1(e.qty)}${if (e.name.contains("跑") || e.name.contains("步") || e.name.contains("骑")) "公里" else "个/秒"}"
    }
    val muscles = runCatching {
        JSONObject(e.musclesJson).keys().asSequence().toList()
            .sortedByDescending { JSONObject(e.musclesJson).optDouble(it, 0.0) }
            .take(2).joinToString("、") { Muscle.labelOf(it) }
    }.getOrDefault("")
    return "$qty · ${fmt1(e.minutes)} 分钟" + if (muscles.isNotBlank()) " · $muscles" else ""
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
        title = { Text("选择锻炼", color = TextHi, fontSize = 16.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    placeholder = { Text("搜索", color = TextLo) },
                    singleLine = true, colors = com.eelan.musclediary.ui.fieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                val filtered = templates.filter { it.name.contains(search, ignoreCase = true) }
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(filtered, key = { it.id }) { t ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(t) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(t.name, color = TextHi, fontSize = 14.sp)
                                Text(
                                    "主练${Muscle.labelOf(t.primaryMuscle)} · ${
                                        when (t.qtyType) {
                                            QtyType.REPS -> "按次"
                                            QtyType.DISTANCE -> "按公里"
                                            QtyType.SECONDS -> "按秒"
                                        }
                                    }",
                                    color = TextLo, fontSize = 11.sp,
                                )
                            }
                            Text("MET ${fmt1(t.met)}", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                TextButton(onClick = onCustom) { Text("＋ 新建自定义动作", color = Accent, fontSize = 13.sp) }
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
    onDismiss: () -> Unit,
    onConfirm: (Double, Double) -> Unit,
) {
    val unitLabel = Calc.unitLabel(template)
    var qty by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf(if (template.name.contains("哑铃")) "10" else "") }
    val q = qty.toDoubleOrNull()
    val valid = q != null && q > 0
    val preview = if (q != null) Calc.exerciseCalories(template, q, bodyWeight) else null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        title = { Text(template.name, color = TextHi, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = qty, onValueChange = { qty = it },
                    label = { Text(if (unitLabel == "个") "做了多少个？" else if (unitLabel == "公里") "多少公里？" else "坚持了多少秒？", color = TextLo) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = com.eelan.musclediary.ui.fieldColors(),
                )
                if (template.name.contains("哑铃")) {
                    OutlinedTextField(
                        value = weight, onValueChange = { weight = it },
                        label = { Text("哑铃单只重量 kg（可选）", color = TextLo) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = com.eelan.musclediary.ui.fieldColors(),
                    )
                }
                if (preview != null) {
                    Text(
                        "预计消耗 ${fmt0(preview.first)} kcal · ${fmt1(preview.second)} 分钟 · 主练${Muscle.labelOf(template.primaryMuscle)}",
                        color = Accent, fontSize = 13.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onConfirm(q!!, weight.toDoubleOrNull() ?: 0.0)
            }) { Text("记录", color = if (valid) Accent else TextLo) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextLo) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomExerciseDialog(
    initial: ExerciseTemplate? = null,
    onDismiss: () -> Unit,
    onConfirm: (ExerciseTemplate) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
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
        title = { Text(if (initial == null) "自定义动作" else "修改「${initial.name}」", color = TextHi, fontSize = 16.sp) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("名称", color = TextLo) }, singleLine = true,
                    colors = com.eelan.musclediary.ui.fieldColors())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = type == QtyType.REPS, onClick = { type = QtyType.REPS },
                        label = { Text("按次", fontSize = 12.sp) })
                    FilterChip(selected = type == QtyType.DISTANCE, onClick = { type = QtyType.DISTANCE },
                        label = { Text("按公里", fontSize = 12.sp) })
                    FilterChip(selected = type == QtyType.SECONDS, onClick = { type = QtyType.SECONDS },
                        label = { Text("按秒", fontSize = 12.sp) })
                }
                OutlinedTextField(value = met, onValueChange = { met = it },
                    label = { Text("MET 强度（走路3.5 慢跑8 力量训练5~8）", color = TextLo) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = com.eelan.musclediary.ui.fieldColors())
                when (type) {
                    QtyType.REPS -> OutlinedTextField(value = perRep, onValueChange = { perRep = it },
                        label = { Text("单次耗时（秒，默认3）", color = TextLo) }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = com.eelan.musclediary.ui.fieldColors())
                    QtyType.DISTANCE -> OutlinedTextField(value = pace, onValueChange = { pace = it },
                        label = { Text("配速（分钟/公里，默认8）", color = TextLo) }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = com.eelan.musclediary.ui.fieldColors())
                    QtyType.SECONDS -> {}
                }
                Text("主练部位（单选）", fontSize = 12.sp, color = TextLo)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Muscle.entries.forEach { m ->
                        FilterChip(
                            selected = primary == m.id, onClick = { primary = m.id },
                            label = { Text(m.label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Accent.copy(alpha = 0.35f),
                            ),
                        )
                    }
                }
                Text("兼练部位（可多选）", fontSize = 12.sp, color = TextLo)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Muscle.entries.forEach { m ->
                        val on = m.id in secondary
                        FilterChip(
                            selected = on, onClick = {
                                secondary = if (on) secondary - m.id else secondary + m.id
                            },
                            label = { Text(m.label, fontSize = 11.sp) },
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
            }) { Text("保存", color = if (valid) Accent else TextLo) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextLo) } },
    )
}
