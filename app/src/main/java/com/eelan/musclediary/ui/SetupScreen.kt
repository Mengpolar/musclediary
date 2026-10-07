package com.eelan.musclediary.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eelan.musclediary.AppViewModel
import com.eelan.musclediary.ui.theme.*

/** 首次启动引导：填写性别/年龄/身高/体重，避免用默认值算错目标 */
@Composable
fun SetupScreen(vm: AppViewModel) {
    var male by remember { mutableStateOf(vm.profile.male) }
    var age by remember { mutableStateOf(vm.profile.age.toString()) }
    var height by remember { mutableStateOf(fmt1(vm.profile.heightCm)) }
    var weight by remember { mutableStateOf(fmt1(vm.profile.weightKg)) }

    val ageD = age.toIntOrNull()
    val hD = height.toDoubleOrNull()
    val wD = weight.toDoubleOrNull()
    val valid = ageD != null && ageD in 10..100 && hD != null && hD in 100.0..250.0
            && wD != null && wD in 25.0..300.0

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text("欢迎使用增肌日记", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextHi)
        Spacer(Modifier.height(8.dp))
        Text("先填一下身体数据，热量和营养目标才能算得准", fontSize = 13.sp, color = TextLo)
        Spacer(Modifier.height(32.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("性别", fontSize = 15.sp, color = TextHi, modifier = Modifier.weight(1f))
            FilterChip(
                selected = male, onClick = { male = true }, label = { Text("男") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent.copy(alpha = 0.35f)),
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = !male, onClick = { male = false }, label = { Text("女") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent.copy(alpha = 0.35f)),
            )
        }
        Spacer(Modifier.height(16.dp))

        SetupField("年龄（岁）", age) { age = it }
        SetupField("身高（cm）", height) { height = it }
        SetupField("体重（kg）", weight) { weight = it }

        Spacer(Modifier.height(32.dp))
        Button(
            enabled = valid,
            onClick = {
                vm.updateProfile(
                    vm.profile.copy(
                        male = male, age = ageD!!, heightCm = hD!!, weightKg = wD!!, setupDone = 1,
                    )
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink0),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("开始使用", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(24.dp))
        Text("这些数据之后可以在「我的」页随时修改", fontSize = 11.sp, color = TextLo)
    }
}

@Composable
private fun SetupField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, color = TextLo) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    )
}
