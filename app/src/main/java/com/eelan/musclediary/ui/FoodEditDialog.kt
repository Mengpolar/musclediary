package com.eelan.musclediary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eelan.musclediary.data.FoodTemplate
import com.eelan.musclediary.L
import com.eelan.musclediary.ui.theme.*

/**
 * 食物克数输入对话框（录入与修改共用的核心组件）：
 * - 模板带量词单位时显示「个数 ↔ 克数」双向联动（如 2个=60g），无单位退化为纯克数输入
 * - recent 非空时展示最近记录快捷标签（录入场景），确定返回克数
 */
@Composable
fun FoodGramsDialog(
    title: String,
    template: FoodTemplate?,
    initialGrams: Double,
    recent: List<Double> = emptyList(),
    confirmText: String = "确定",
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    val hasUnit = template != null && template.unitName.isNotBlank() && template.unitGrams > 0
    val unitName = template?.unitName?.ifBlank { L("份", "serving") } ?: L("份", "serving")

    var gramsText by remember { mutableStateOf(fmt1(initialGrams)) }
    var unitText by remember {
        mutableStateOf(if (hasUnit) fmt1(initialGrams / template!!.unitGrams) else "")
    }
    val grams = gramsText.toDoubleOrNull()
    val valid = grams != null && grams > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        title = { Text(title, color = TextHi, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasUnit) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = unitText, onValueChange = { v ->
                                unitText = v
                                v.toDoubleOrNull()?.let { u -> gramsText = fmt1(u * template!!.unitGrams) }
                            },
                            label = { Text(L("吃了多少", "How many ") + "$unitName" + L("？", "?"), color = TextLo) },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f), colors = fieldColors(),
                        )
                        Text("$unitName = ${fmt0(template!!.unitGrams)}g",
                            fontSize = 12.sp, color = TextLo)
                    }
                }
                OutlinedTextField(
                    value = gramsText, onValueChange = { v ->
                        gramsText = v
                        if (hasUnit) {
                            v.toDoubleOrNull()?.let { g -> unitText = fmt1(g / template!!.unitGrams) }
                        }
                    },
                    label = { Text(if (hasUnit) L("或输入克数 g", "or grams") else L("克数", "Grams"), color = TextLo) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal),
                    colors = fieldColors(),
                )
                if (recent.isNotEmpty()) {
                    QuickChips(
                        chips = recent.map { g ->
                            if (hasUnit) "${fmt1(g / template!!.unitGrams)}$unitName" else "${fmt0(g)}g"
                        },
                        onPick = { c ->
                            // 从标签文本反解出克数：取括号里的数字，无括号取整体
                            val g = Regex("（([0-9.]+)g）").find(c)?.groupValues?.get(1)?.toDoubleOrNull()
                                ?: c.removeSuffix("g").toDoubleOrNull()
                            g?.let {
                                gramsText = fmt1(it)
                                if (hasUnit) unitText = fmt1(it / template!!.unitGrams)
                            }
                        },
                    )
                }
                if (valid && template != null) {
                    val k = grams!! / 100.0
                    Text(
                        L("蛋白", "P") + "${fmt1(template.protein * k)}g · " + L("碳水", "C") + "${fmt1(template.carb * k)}g · " +
                                L("脂肪", "F") + "${fmt1(template.fat * k)}g · ${fmt0(template.kcal * k)} kcal",
                        color = Accent, fontSize = 13.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onConfirm(grams!!); onDismiss()
            }) { Text(confirmText, color = if (valid) Accent else TextLo) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("取消", "Cancel"), color = TextLo) } },
    )
}

/** 修改饮食记录：模板带量词单位时支持按个数联动克数（无单位退化为纯克数） */
@Composable
fun FoodEditDialog(
    entryName: String,
    currentGrams: Double,
    template: FoodTemplate?,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    FoodGramsDialog(
        title = L("修改「", "Edit [") + "$entryName" + L("」", "]"),
        template = template,
        initialGrams = currentGrams,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}
