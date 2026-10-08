package com.eelan.musclediary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
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
import com.eelan.musclediary.data.PlanItem
import com.eelan.musclediary.data.QtyType
import com.eelan.musclediary.domain.Calc
import com.eelan.musclediary.domain.WorkoutSession
import com.eelan.musclediary.ui.theme.*
import org.json.JSONObject

/**
 * 训练编排页：选动作 → 设组数/每组数量/重量/休息 → 消耗预览 → 保存计划或开始。
 */
@Composable
fun WorkoutBuilderScreen(
    vm: AppViewModel,
    initialItems: List<PlanItem>,
    onDismiss: () -> Unit,
    onStart: (List<PlanItem>) -> Unit,
) {
    val items = remember(initialItems) { mutableStateListOf<PlanItem>().apply { addAll(initialItems) } }
    var pickOpen by remember { mutableStateOf(false) }
    var saveOpen by remember { mutableStateOf(false) }
    var editingIdx by remember { mutableStateOf<Int?>(null) }

    val totalKcal = WorkoutSession.totalCalories(items, vm.profile.weightKg)
    val totalMin = WorkoutSession.totalSeconds(items) / 60.0

    Column(
        Modifier.fillMaxSize().background(Ink0)
            .padding(horizontal = 16.dp).verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("编排训练", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextHi,
                modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("取消", color = TextLo) }
        }

        // 计划选择
        val plans = vm.sortedPlans()
        if (plans.isNotEmpty()) {
            CardBox(title = "从计划快速载入") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    plans.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth()
                                .background(Ink2, RoundedCornerShape(10.dp))
                                .clickable {
                                    items.clear()
                                    items.addAll(vm.parsePlanItems(p))
                                    vm.touchPlan(p)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontSize = 14.sp, color = TextHi, fontWeight = FontWeight.Medium)
                                Text("${vm.parsePlanItems(p).size} 个动作 · 左滑删除",
                                    fontSize = 11.sp, color = TextLo)
                            }
                            Text("载入", fontSize = 13.sp, color = Accent, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 8.dp))
                            Icon(Icons.Default.Delete, "删除计划", tint = TextLo,
                                modifier = Modifier
                                    .padding(start = 10.dp)
                                    .clickable { vm.deletePlan(p) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // 队列
        CardBox(title = "动作队列（${items.size}）") {
            if (items.isEmpty()) {
                Text("还没有动作，点下方「添加动作」开始编排", fontSize = 13.sp, color = TextLo)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEachIndexed { idx, it ->
                        val kcal = WorkoutSession.setCalories(it, vm.profile.weightKg) * it.sets
                        Row(
                            Modifier.fillMaxWidth()
                                .background(Ink2, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).clickable { editingIdx = idx }) {
                                Text(it.name, fontSize = 14.sp, color = TextHi, fontWeight = FontWeight.Medium)
                                val qtyLabel = when (it.qtyType) {
                                    "REPS" -> "${it.sets}组 × ${fmt1(it.reps)}个"
                                    "DISTANCE" -> "${it.sets}组 × ${fmt1(it.reps)}公里"
                                    else -> "${it.sets}组 × ${fmt1(it.reps)}秒"
                                }
                                Text("$qtyLabel · 休息${it.restSec}秒" +
                                        if (it.weightKg > 0) " · ${fmt1(it.weightKg)}kg" else "",
                                    fontSize = 11.sp, color = TextLo)
                            }
                            Text("${fmt0(kcal)} kcal", fontSize = 13.sp, color = Accent,
                                fontWeight = FontWeight.Bold, modifier = Modifier.clickable { editingIdx = idx })
                            Column {
                                Icon(Icons.Default.KeyboardArrowUp, "上移", tint = TextLo,
                                    modifier = Modifier.clickable {
                                        if (idx > 0) { items[idx] = items[idx - 1].also { items[idx - 1] = it } }
                                    })
                                Icon(Icons.Default.KeyboardArrowDown, "下移", tint = TextLo,
                                    modifier = Modifier.clickable {
                                        if (idx < items.lastIndex) { items[idx + 1] = items[idx].also { items[idx + 1] = it } }
                                    })
                            }
                            Icon(Icons.Default.Delete, "移除", tint = TextLo,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clickable { items.removeAt(idx) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { pickOpen = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, null, tint = Accent)
                Spacer(Modifier.width(4.dp))
                Text("添加动作", color = Accent)
            }
        }

        Spacer(Modifier.height(12.dp))

        // 消耗预览
        CardBox(title = "消耗预览") {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("预计消耗 ", fontSize = 13.sp, color = TextLo)
                Text("${fmt0(totalKcal)}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Accent)
                Text(" kcal", fontSize = 13.sp, color = TextLo)
                Spacer(Modifier.weight(1f))
                Text("总时长 ≈ ${fmt0(totalMin)} 分钟（含休息）", fontSize = 12.sp, color = TextLo)
            }
        }

        Spacer(Modifier.height(12.dp))

        // 操作
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { if (items.isNotEmpty()) saveOpen = true },
                enabled = items.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Save, null, tint = Accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("保存为计划", color = Accent)
            }
            Button(
                onClick = { if (items.isNotEmpty()) onStart(items.toList()) },
                enabled = items.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink0),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(4.dp))
                Text("开始训练", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (pickOpen) {
        ExercisePickerForBuilder(vm, onPick = { t ->
            // 默认编排：3 组 × 12 个（按秒动作 45 秒，按公里 1 公里），休息 60 秒
            val reps = when (t.qtyType) {
                QtyType.REPS -> 12.0
                QtyType.DISTANCE -> 1.0
                QtyType.SECONDS -> 45.0
            }
            items.add(PlanItem(
                templateId = t.id, name = t.name,
                qtyType = t.qtyType.name, sets = 3, reps = reps,
                weightKg = if (t.name.contains("哑铃")) 10.0 else 0.0,
                restSec = 60, voiceKey = t.voiceKey,
                met = t.met, perRepSeconds = t.perRepSeconds, paceMinPerKm = t.paceMinPerKm,
                primaryMuscle = t.primaryMuscle, secondaryMuscles = t.secondaryMuscles,
            ))
            pickOpen = false
        }, onDismiss = { pickOpen = false })
    }

    editingIdx?.let { idx ->
        val it0 = items[idx]
        EditPlanItemDialog(
            item = it0,
            onDismiss = { editingIdx = null },
            onConfirm = { ni -> items[idx] = ni; editingIdx = null },
        )
    }

    if (saveOpen) {
        SavePlanDialog(onDismiss = { saveOpen = false }, onSave = { name ->
            vm.savePlan(name, items.toList())
            saveOpen = false
        })
    }
}

@Composable
private fun ExercisePickerForBuilder(vm: AppViewModel, onPick: (ExerciseTemplate) -> Unit, onDismiss: () -> Unit) {
    var search by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        title = { Text("选择动作加入队列", color = TextHi, fontSize = 16.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    placeholder = { Text("搜索", color = TextLo) },
                    singleLine = true, colors = fieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                val filtered = vm.exerciseTemplates.filter { it.name.contains(search, ignoreCase = true) }
                LazyColumnHeight300(filtered = filtered, onPick = onPick)
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}

@Composable
private fun LazyColumnHeight300(filtered: List<ExerciseTemplate>, onPick: (ExerciseTemplate) -> Unit) {
    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.height(300.dp)) {
        items(filtered, key = { tpl: ExerciseTemplate -> tpl.id }) { t: ExerciseTemplate ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(t) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(t.name, color = TextHi, fontSize = 14.sp)
                    val unit = when (t.qtyType) {
                        QtyType.REPS -> "按次"
                        QtyType.DISTANCE -> "按公里"
                        QtyType.SECONDS -> "按秒"
                    }
                    Text(
                        "$unit · 主练${com.eelan.musclediary.domain.Muscle.labelOf(t.primaryMuscle)}",
                        color = TextLo, fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditPlanItemDialog(item: PlanItem, onDismiss: () -> Unit, onConfirm: (PlanItem) -> Unit) {
    var sets by remember { mutableStateOf(item.sets.toString()) }
    var reps by remember { mutableStateOf(fmt1(item.reps)) }
    var weight by remember { mutableStateOf(if (item.weightKg > 0) fmt1(item.weightKg) else "") }
    var rest by remember { mutableStateOf(item.restSec.toString()) }
    val setD = sets.toIntOrNull()
    val repD = reps.toDoubleOrNull()
    val restD = rest.toIntOrNull()
    val valid = setD != null && setD in 1..20 && repD != null && repD > 0 &&
            restD != null && restD in 0..600

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        title = { Text("调整「${item.name}」", color = TextHi, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val unit = when (item.qtyType) { "REPS" -> "每组个数"; "DISTANCE" -> "每组公里"; else -> "每组秒数" }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallNumField("组数", sets, { sets = it }, Modifier.weight(1f))
                    SmallNumField(unit, reps, { reps = it }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallNumField("休息秒", rest, { rest = it }, Modifier.weight(1f))
                    if (item.name.contains("哑铃")) {
                        SmallNumField("重量kg", weight, { weight = it }, Modifier.weight(1f))
                    }
                }
                if (setD != null && repD != null && setD > 0 && repD > 0) {
                    val kcal = WorkoutSession.setCalories(item.copy(sets = setD, reps = repD), 0.0)
                    val perSet = item.met * 0.0 // 占位，实际用体重计算
                    Text(
                        "本动作合计 ≈ ${fmt0(WorkoutSession.setCalories(item, 65.0) * setD)} kcal（按65kg估算）",
                        fontSize = 12.sp, color = Accent,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onConfirm(item.copy(
                    sets = setD!!, reps = repD!!,
                    weightKg = weight.toDoubleOrNull() ?: 0.0,
                    restSec = restD!!,
                ))
            }) { Text("确定", color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextLo) } },
    )
}

@Composable
private fun SmallNumField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, color = TextLo, fontSize = 11.sp) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = fieldColors(), modifier = modifier,
    )
}

@Composable
private fun SavePlanDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        title = { Text("保存为训练计划", color = TextHi, fontSize = 16.sp) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("计划名（如：胸日、居家20分钟）", color = TextLo) },
                singleLine = true, colors = fieldColors(),
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) {
                Text("保存", color = if (name.isNotBlank()) Accent else TextLo) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextLo) } },
    )
}
