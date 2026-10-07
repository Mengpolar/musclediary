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

    val all: List<Any> = if (kind == 0) vm.foodTemplates else vm.exerciseTemplates
    val filtered = all.filter { nameOf(it).contains(search, ignoreCase = true) }
        .sortedBy { nameOf(it).let { n -> if (isCustom(it)) "～$n" else n } }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "返回", tint = TextLo) }
            Text(
                if (kind == 0) "食物模板管理" else "锻炼模板管理",
                fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextHi,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                vm.restoreBuiltins { }
            }) { Text("补回内置", color = Accent, fontSize = 13.sp) }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "共 ${filtered.size} 个 · 「编辑」「删」作用于模板本身，历史记录不受影响",
            fontSize = 11.sp, color = TextLo,
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = search, onValueChange = { search = it },
            placeholder = { Text("搜索", color = TextLo) },
            singleLine = true,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        TextButton(onClick = { if (kind == 0) addFood = true else addExercise = true }) {
            Icon(Icons.Default.Add, null, tint = Accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("新增${if (kind == 0) "食物" else "动作"}", color = Accent, fontSize = 13.sp)
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            items(filtered, key = { if (kind == 0) "f${(it as FoodTemplate).id}" else "x${(it as ExerciseTemplate).id}" }) { t ->
                if (kind == 0) FoodManageRow(t as FoodTemplate,
                    onEdit = { editFood = t },
                    onDelete = { confirmDelete = t })
                else ExerciseManageRow(t as ExerciseTemplate,
                    onEdit = { editExercise = t },
                    onDelete = { confirmDelete = t })
            }
        }
    }

    // —— 编辑 / 新增 ——
    if (editFood != null || addFood) {
        CustomFoodDialog(
            initial = editFood,
            onDismiss = { editFood = null; addFood = false },
            onConfirm = { name, p, c, f ->
                if (editFood != null) {
                    vm.saveFoodTemplate(editFood!!.copy(name = name, protein = p, carb = c, fat = f))
                } else {
                    vm.addCustomFood(name, p, c, f) { }
                }
                editFood = null; addFood = false
            },
        )
    }
    if (editExercise != null || addExercise) {
        CustomExerciseDialog(
            initial = editExercise,
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
            title = { Text("删除「${nameOf(t)}」？", color = TextHi, fontSize = 16.sp) },
            text = { Text("只删除模板，已记录的历史数据保留。", fontSize = 13.sp, color = TextLo) },
            confirmButton = {
                TextButton(onClick = {
                    if (t is FoodTemplate) vm.deleteFoodTemplate(t) else vm.deleteExerciseTemplate(t as ExerciseTemplate)
                    confirmDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("取消", color = TextLo) }
            },
        )
    }
}

private fun nameOf(t: Any): String = when (t) {
    is FoodTemplate -> t.name
    is ExerciseTemplate -> t.name
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
    SwipeEntryRow(
        title = t.name + if (t.isCustom) " · 自定义" else "",
        detail = "蛋白${fmt1(t.protein)} 碳水${fmt1(t.carb)} 脂肪${fmt1(t.fat)} · 左滑可修改/删除",
        trailing = "${fmt0(t.kcal)} kcal",
        onEdit = onEdit,
        onDelete = onDelete,
    )
}

@Composable
private fun ExerciseManageRow(t: ExerciseTemplate, onEdit: () -> Unit, onDelete: () -> Unit) {
    SwipeEntryRow(
        title = t.name + if (t.isCustom) " · 自定义" else "",
        detail = "${Calc.unitLabel(t)} · MET ${fmt1(t.met)} · 主练${Muscle.labelOf(t.primaryMuscle)} · 左滑可修改/删除",
        trailing = "MET ${fmt1(t.met)}",
        onEdit = onEdit,
        onDelete = onDelete,
    )
}

@Composable
private fun Tag(text: String) {
    Text(
        text, fontSize = 9.sp, color = Accent,
        modifier = Modifier
            .padding(start = 6.dp)
            .background(Accent.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}
