package com.eelan.musclediary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eelan.musclediary.AppViewModel
import com.eelan.musclediary.L
import com.eelan.musclediary.tagLabel
import com.eelan.musclediary.data.ExerciseTemplate
import com.eelan.musclediary.data.FoodTemplate
import com.eelan.musclediary.data.QtyType
import com.eelan.musclediary.domain.Calc
import com.eelan.musclediary.domain.Muscle
import com.eelan.musclediary.ui.theme.*

/**
 * 模板管理页：kind = 0 食物模板 / 1 锻炼模板。
 * 内置与自定义模板均可修改、删除，可一键补回被删掉的内置模板。
 */
@Composable
fun ManageTemplatesScreen(vm: AppViewModel, kind: Int, onClose: () -> Unit) {
    var search by remember { mutableStateOf("") }
    var editFood by remember { mutableStateOf<FoodTemplate?>(null) }
    var editExercise by remember { mutableStateOf<ExerciseTemplate?>(null) }
    var addFood by remember { mutableStateOf(false) }
    var addExercise by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Any?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    // 筛选：null 全部 / false 内置 / true 自定义
    var customFilter by remember { mutableStateOf<Boolean?>(null) }

    val all: List<Any> = if (kind == 0) vm.foodTemplates else vm.exerciseTemplates
    val filtered = all.filter { nameOf(it).contains(search, ignoreCase = true) }
        .filter { customFilter == null || isCustom(it) == customFilter }
        .sortedBy { nameOf(it).let { n -> if (isCustom(it)) "～$n" else n } }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, L("返回", "Back"), tint = TextLo) }
            Text(
                if (kind == 0) L("食物模板管理", "Food Templates") else L("锻炼模板管理", "Exercise Templates"),
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextHi,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { confirmClear = true }) {
                Text(L("清空", "Clear"), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            TextButton(onClick = {
                vm.restoreBuiltins { }
            }) { Text(L("补回内置", "Restore built-ins"), color = Accent, fontSize = 13.sp) }
        }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = search, onValueChange = { search = it },
            placeholder = { Text(L("搜索", "Search"), color = TextLo) },
            singleLine = true,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = customFilter == null, onClick = { customFilter = null },
                label = { Text(L("全部", "All"), fontSize = 12.sp) })
            FilterChip(selected = customFilter == false, onClick = { customFilter = false },
                label = { Text(L("内置", "Built-in"), fontSize = 12.sp) })
            FilterChip(selected = customFilter == true, onClick = { customFilter = true },
                label = { Text(L("自定义", "Custom"), fontSize = 12.sp) })
        }
        Spacer(Modifier.height(8.dp))

        TextButton(onClick = { if (kind == 0) addFood = true else addExercise = true }) {
            Icon(Icons.Default.Add, null, tint = Accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(L("新增", "New ") + (if (kind == 0) L("食物", "food") else L("动作", "exercise")), color = Accent, fontSize = 13.sp)
        }

        StackedColumn {
            filtered.forEach { t ->
                key(if (kind == 0) "f${(t as FoodTemplate).id}" else "x${(t as ExerciseTemplate).id}") {
                Box(Modifier.stackApproach()) {
                    if (kind == 0) FoodManageRow(t as FoodTemplate,
                        onEdit = { editFood = t },
                        onDelete = { confirmDelete = t })
                    else ExerciseManageRow(t as ExerciseTemplate,
                        onEdit = { editExercise = t },
                        onDelete = { confirmDelete = t })
                }
                }
            }
        }
    }

    // —— 编辑 / 新增 ——
    if (editFood != null || addFood) {
        CustomFoodDialog(
            initial = editFood,
            builtinExists = { n -> vm.foodTemplates.any { !it.isCustom && (it.name == n || it.nameEn == n) } },
            onDismiss = { editFood = null; addFood = false },
            onConfirm = { name, p, c, f, tag, unitName, unitGrams ->
                if (editFood != null) {
                    vm.saveFoodTemplate(editFood!!.copy(name = name, protein = p, carb = c, fat = f, tag = tag,
                        unitName = unitName, unitGrams = unitGrams))
                } else {
                    vm.addCustomFood(name, p, c, f, tag, unitName, unitGrams) { }
                }
                editFood = null; addFood = false
            },
        )
    }
    if (editExercise != null || addExercise) {
        CustomExerciseDialog(
            initial = editExercise,
            builtinExists = { n -> vm.exerciseTemplates.any { !it.isCustom && (it.name == n || it.nameEn == n) } },
            onDismiss = { editExercise = null; addExercise = false },
            onConfirm = { t ->
                if (editExercise != null) vm.saveExerciseTemplate(t)
                else vm.addCustomExercise(t) { }
                editExercise = null; addExercise = false
            },
        )
    }

    // —— 删除确认 ——
    confirmDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = Ink2,
            title = { Text(L("删除「", "Delete [") + "${nameOf(t)}" + L("」", "]") + L("？", "?"), color = TextHi, fontSize = 16.sp) },
            text = { Text(L("只删除模板，已记录的历史数据保留。", "Only the template is deleted; logged history is kept."), fontSize = 13.sp, color = TextLo) },
            confirmButton = {
                TextButton(onClick = {
                    if (t is FoodTemplate) vm.deleteFoodTemplate(t) else vm.deleteExerciseTemplate(t as ExerciseTemplate)
                    confirmDelete = null
                }) { Text(L("删除", "Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text(L("取消", "Cancel"), color = TextLo) }
            },
        )
    }

    // —— 清空确认 ——
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Ink2,
            title = { Text(L("清空全部", "Clear all ") + (if (kind == 0) L("食物模板", "food templates") else L("锻炼模板", "exercise templates")) + L("？", "?"), color = TextHi, fontSize = 16.sp) },
            text = { Text(L("将删除该类型的全部内置与自定义模板，历史记录不受影响；内置模板可随时补回，自定义模板删除后无法恢复。",
                "This deletes ALL built-in and custom templates of this type. History is unaffected; built-ins can be restored anytime, custom templates cannot."),
                fontSize = 13.sp, color = TextLo) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAllTemplates(kind)
                    confirmClear = false
                }) { Text(L("清空", "Clear"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(L("取消", "Cancel"), color = TextLo) } },
        )
    }
}

private fun nameOf(t: Any): String = when (t) {
    is FoodTemplate -> t.dispName
    is ExerciseTemplate -> t.dispName
    else -> ""
}

private fun isCustom(t: Any): Boolean = when (t) {
    is FoodTemplate -> t.isCustom
    is ExerciseTemplate -> t.isCustom
    else -> false
}

@Composable
private fun ActionChip(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label, fontSize = 12.sp, color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun FoodManageRow(t: FoodTemplate, onEdit: () -> Unit, onDelete: () -> Unit) {
    val unitInfo = if (t.unitName.isNotBlank() && t.unitGrams > 0) " · 1${foodUnitLabel(t.unitName)}=${fmt0(t.unitGrams)}g" else ""
    SwipeEntryRow(
        title = t.dispName + if (t.isCustom) L(" · 自定义", " · custom") else L(" · 内置", " · built-in"),
        detail = L("蛋白", "P") + "${fmt1(t.protein)} " + L("碳水", "C") + "${fmt1(t.carb)} " + L("脂肪", "F") + "${fmt1(t.fat)}$unitInfo",
        trailing = "${fmt0(t.kcal)} kcal",
        onEdit = onEdit,
        onDelete = onDelete,
    )
}

@Composable
private fun ExerciseManageRow(t: ExerciseTemplate, onEdit: () -> Unit, onDelete: () -> Unit) {
    SwipeEntryRow(
        title = t.dispName + if (t.isCustom) L(" · 自定义", " · custom") else L(" · 内置", " · built-in"),
        detail = "${Calc.unitLabel(t)} · MET ${fmt1(t.met)} · " + L("主练", "primary: ") + "${Muscle.labelOf(t.primaryMuscle)}",
        trailing = "MET ${fmt1(t.met)}",
        onEdit = onEdit,
        onDelete = onDelete,
    )
}

