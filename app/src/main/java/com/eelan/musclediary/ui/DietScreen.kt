package com.eelan.musclediary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
fun DietScreen(vm: AppViewModel) {
    var pickOpen by remember { mutableStateOf(false) }
    var pendingTemplate by remember { mutableStateOf<FoodTemplate?>(null) }
    var customOpen by remember { mutableStateOf(false) }

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
            DateHeader(vm)
            Spacer(Modifier.height(8.dp))
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("当日摄入", fontSize = 12.sp, color = TextLo)
                        Text("${fmt0(kcal)} kcal", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Accent)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("蛋白质 ${fmt1(protein)}g", fontSize = 12.sp, color = Protein)
                        Text("碳水 ${fmt1(carb)}g", fontSize = 12.sp, color = CarbC)
                        Text("脂肪 ${fmt1(fat)}g", fontSize = 12.sp, color = FatC)
                    }
                }
            }
            SectionTitle("饮食记录（${entries.size}）")
            if (entries.isEmpty()) {
                EmptyCard("还没有记录，点右下角 + 从模板快速添加")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(entries, key = { it.id }) { f ->
                        EntryRow(
                            f.name,
                            "${fmt0(f.grams)}g · 蛋白${fmt1(f.protein)} 碳水${fmt1(f.carb)} 脂肪${fmt1(f.fat)}",
                            "${fmt0(f.kcal)} kcal",
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
            onDismiss = { pickOpen = false },
        )
    }

    pendingTemplate?.let { t ->
        NumberDialog(
            title = t.name, label = "吃了多少克？", initial = "100",
            onDismiss = { pendingTemplate = null },
            onConfirm = { grams -> vm.addFood(t, grams, vm.selectedDate); pendingTemplate = null },
        )
    }

    if (customOpen) {
        CustomFoodDialog(
            onDismiss = { customOpen = false },
            onConfirm = { name, p, c, f ->
                vm.addCustomFood(name, p, c, f) { saved ->
                    pendingTemplate = saved
                }
                customOpen = false
            },
        )
    }
}

@Composable
private fun EmptyCard(text: String) {
    Text(
        text, fontSize = 13.sp, color = TextLo,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Ink1, RoundedCornerShape(12.dp))
            .padding(14.dp),
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
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
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
                Spacer(Modifier.height(8.dp))
                val filtered = templates.filter { it.name.contains(search, ignoreCase = true) }
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
                                Text(t.name, color = TextHi, fontSize = 14.sp)
                                Text(detail(t), color = TextLo, fontSize = 11.sp)
                            }
                            Text(trailing(t), color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                TextButton(onClick = onCustom) {
                    Text("＋ 新建自定义", color = Accent, fontSize = 13.sp)
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
    onConfirm: (String, Double, Double, Double) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var p by remember { mutableStateOf(initial?.let { fmt1(it.protein) } ?: "") }
    var c by remember { mutableStateOf(initial?.let { fmt1(it.carb) } ?: "") }
    var f by remember { mutableStateOf(initial?.let { fmt1(it.fat) } ?: "") }
    val pd = p.toDoubleOrNull(); val cd = c.toDoubleOrNull(); val fd = f.toDoubleOrNull()
    val valid = name.isNotBlank() && pd != null && cd != null && fd != null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        title = { Text(if (initial == null) "自定义食物（每100g）" else "修改「${initial.name}」（每100g）", color = TextHi, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("名称", color = TextLo) }, singleLine = true, colors = fieldColors())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = p, onValueChange = { p = it },
                        label = { Text("蛋白g", color = TextLo, fontSize = 11.sp) }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), colors = fieldColors())
                    OutlinedTextField(value = c, onValueChange = { c = it },
                        label = { Text("碳水g", color = TextLo, fontSize = 11.sp) }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), colors = fieldColors())
                    OutlinedTextField(value = f, onValueChange = { f = it },
                        label = { Text("脂肪g", color = TextLo, fontSize = 11.sp) }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), colors = fieldColors())
                }
                if (pd != null && cd != null && fd != null) {
                    Text("热量 ≈ ${fmt0(pd * 4 + cd * 4 + fd * 9)} kcal/100g",
                        color = Accent, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onConfirm(name.trim(), pd!!, cd!!, fd!!)
            }) { Text("保存", color = if (valid) Accent else TextLo) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextLo) } },
    )
}
