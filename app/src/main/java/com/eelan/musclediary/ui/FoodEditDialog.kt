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
import com.eelan.musclediary.ui.theme.*

/**
 * 修改饮食记录克数：模板带量词单位时显示「个数 ↔ 克数」双向联动（如 2个=60g），
 * 无单位模板退化为纯克数输入。确定返回新克数。
 */
@Composable
fun FoodEditDialog(
    entryName: String,
    currentGrams: Double,
    template: FoodTemplate?,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    val hasUnit = template != null && template.unitName.isNotBlank() && template.unitGrams > 0
    val unitName = template?.unitName?.ifBlank { "份" } ?: "份"

    var gramsText by remember { mutableStateOf(fmt1(currentGrams)) }
    var unitText by remember {
        mutableStateOf(if (hasUnit) fmt1(currentGrams / template!!.unitGrams) else "")
    }
    val grams = gramsText.toDoubleOrNull()
    val valid = grams != null && grams > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        title = { Text("修改「$entryName」", color = TextHi, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasUnit) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = unitText, onValueChange = { v ->
                                unitText = v
                                v.toDoubleOrNull()?.let { u -> gramsText = fmt1(u * template!!.unitGrams) }
                            },
                            label = { Text("吃了多少$unitName？", color = TextLo) },
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
                    label = { Text(if (hasUnit) "或输入克数 g" else "克数", color = TextLo) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal),
                    colors = fieldColors(),
                )
                if (valid && template != null) {
                    val k = grams!! / 100.0
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
            }) { Text("确定", color = if (valid) Accent else TextLo) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextLo) } },
    )
}
