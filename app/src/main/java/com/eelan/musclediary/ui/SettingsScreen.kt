package com.eelan.musclediary.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eelan.musclediary.AppViewModel
import com.eelan.musclediary.BuildConfig
import com.eelan.musclediary.update.Updater
import com.eelan.musclediary.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

/** 设置页：身体档案 + 增肌盈余 + 模板管理 + 数据备份 + 在线更新 */
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onManageFood: () -> Unit = {},
    onManageExercise: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var editAge by remember { mutableStateOf(false) }
    var editHeight by remember { mutableStateOf(false) }
    var editWeight by remember { mutableStateOf(false) }

    var updateState by remember { mutableStateOf<String?>(null) }
    var updateInfo by remember { mutableStateOf<Updater.UpdateInfo?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            vm.exportCsv { f ->
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    f.inputStream().use { it.copyTo(out) }
                }
                Toast.makeText(ctx, "已导出到所选位置", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()?.let { text ->
                vm.importCsv(text) { msg -> Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
            } ?: Toast.makeText(ctx, "无法读取文件", Toast.LENGTH_SHORT).show()
        }
    }

    val p = vm.profile
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 80.dp)
    ) {
        SectionTitle("体重记录")
        WeightCard(vm)

        SectionTitle("目标模式")
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("训练阶段", fontSize = 14.sp, color = TextHi, modifier = Modifier.weight(1f))
                FilterChip(
                    selected = p.mode == 0, onClick = {
                        vm.updateProfile(p.copy(mode = 0, surplusKcal = p.surplusKcal.coerceIn(200.0, 300.0)))
                    },
                    label = { Text("增肌期") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent.copy(alpha = 0.35f)),
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = p.mode == 1, onClick = {
                        vm.updateProfile(p.copy(mode = 1, surplusKcal = p.surplusKcal.coerceIn(-500.0, -300.0)))
                    },
                    label = { Text("减脂期") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent.copy(alpha = 0.35f)),
                )
            }
            Spacer(Modifier.height(4.dp))
            if (p.mode == 0) {
                Text("增肌期：蛋白质 2.0g/kg · 碳水 5.0g/kg · 脂肪 1.0g/kg，热量盈余 200~300 大卡",
                    fontSize = 11.sp, color = TextLo)
            } else {
                Text("减脂期：蛋白质 1.5g/kg · 碳水 2.0g/kg · 脂肪 0.8g/kg，热量赤字 300~500 大卡",
                    fontSize = 11.sp, color = TextLo)
            }
            Text("三维基础目标之外多出/不足的热量，按三者的热量比例等比分摊（即进度条上的「热量所需」刻度）",
                fontSize = 11.sp, color = TextLo)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (p.mode == 0) "增肌盈余" else "减脂赤字", fontSize = 14.sp, color = TextHi)
                    Text(
                        if (p.mode == 0) "每天多吃多少热量" else "每天少吃多少热量（赤字为负数）",
                        fontSize = 11.sp, color = TextLo,
                    )
                }
                SmallButton("−") {
                    val range = if (p.mode == 0) 200.0..300.0 else -500.0..-300.0
                    val v = (p.surplusKcal - 50).coerceIn(range.start, range.endInclusive)
                    vm.updateProfile(p.copy(surplusKcal = v))
                }
                Text(
                    if (p.surplusKcal < 0) "−${fmt0(-p.surplusKcal)}" else "+${fmt0(p.surplusKcal)}",
                    fontSize = 15.sp, color = Accent,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp),
                )
                SmallButton("+") {
                    val range = if (p.mode == 0) 200.0..300.0 else -500.0..-300.0
                    val v = (p.surplusKcal + 50).coerceIn(range.start, range.endInclusive)
                    vm.updateProfile(p.copy(surplusKcal = v))
                }
            }
        }

        SectionTitle("身体档案")
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("性别", fontSize = 14.sp, color = TextHi, modifier = Modifier.weight(1f))
                FilterChip(
                    selected = p.male, onClick = { vm.updateProfile(p.copy(male = true)) },
                    label = { Text("男") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent.copy(alpha = 0.35f)),
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = !p.male, onClick = { vm.updateProfile(p.copy(male = false)) },
                    label = { Text("女") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent.copy(alpha = 0.35f)),
                )
            }
            SettingRow("年龄", "${p.age} 岁") { editAge = true }
            SettingRow("身高", fmt1(p.heightCm) + " cm") { editHeight = true }
            SettingRow("体重", fmt1(p.weightKg) + " kg") { editWeight = true }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("饮水目标", fontSize = 14.sp, color = TextHi)
                    Text("每日饮水目标 = 体重 × 35ml（固定系数）", fontSize = 11.sp, color = TextLo)
                }
                Text("${fmt0(com.eelan.musclediary.domain.Calc.waterTargetMl(p.weightKg))} ml",
                    fontSize = 15.sp, color = Accent, fontWeight = FontWeight.Bold)
            }
        }

        SectionTitle("模板管理")
        CardBox {
            SettingRow("食物模板", "${vm.foodTemplates.size} 个") { onManageFood() }
            SettingRow("锻炼动作模板", "${vm.exerciseTemplates.size} 个") { onManageExercise() }
            Spacer(Modifier.height(4.dp))
            Text("可修改、删除内置与自定义模板，也可补回被删的内置模板",
                fontSize = 11.sp, color = TextLo)
        }

        SectionTitle("数据备份（本地 CSV）")
        CardBox {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportLauncher.launch("musclediary_backup.csv") },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink0)) {
                    Text("导出", fontSize = 13.sp)
                }
                OutlinedButton(onClick = {
                    importLauncher.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "*/*"))
                }) { Text("导入（覆盖现有记录）", fontSize = 13.sp, color = Accent) }
            }
            Spacer(Modifier.height(6.dp))
            Text("包含：档案、自定义模板、全部饮食与锻炼记录。内置模板不在备份内。",
                fontSize = 11.sp, color = TextLo)
        }

        SectionTitle("关于与更新")
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("当前版本", fontSize = 14.sp, color = TextHi)
                    Text("v${BuildConfig.VERSION_NAME} · GitHub: ${Updater.REPO}",
                        fontSize = 11.sp, color = TextLo)
                }
                Button(
                    onClick = {
                        updateState = "正在检查更新…"
                        scope.launch {
                            runCatching { Updater.check(BuildConfig.VERSION_NAME) }
                                .onSuccess { info ->
                                    if (info == null) updateState = "已是最新版本"
                                    else { updateInfo = info; updateState = null }
                                }
                                .onFailure { updateState = "检查失败：${it.message}" }
                        }
                    },
                    enabled = !downloading,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink0),
                ) { Text("检查更新", fontSize = 13.sp) }
            }
            updateState?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontSize = 12.sp, color = Warn)
            }
            if (downloading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Accent, trackColor = Ink2,
                )
                Text("下载中 $progress%", fontSize = 12.sp, color = TextLo)
            }
        }
    }

    if (editAge) NumberDialog("修改年龄", "年龄", "${vm.profile.age}",
        onDismiss = { editAge = false },
        onConfirm = { vm.updateProfile(vm.profile.copy(age = it.toInt())); editAge = false })
    if (editHeight) NumberDialog("修改身高", "厘米", fmt1(vm.profile.heightCm),
        onDismiss = { editHeight = false },
        onConfirm = { vm.updateProfile(vm.profile.copy(heightCm = it)); editHeight = false })
    if (editWeight) NumberDialog("修改体重", "公斤", fmt1(vm.profile.weightKg),
        onDismiss = { editWeight = false },
        onConfirm = { vm.updateProfile(vm.profile.copy(weightKg = it)); editWeight = false })

    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { },
            containerColor = Ink2,
            title = { Text("发现新版本 v${info.version}", color = TextHi) },
            text = {
                Column {
                    Text(info.notes.ifBlank { "无更新说明" }, fontSize = 13.sp, color = TextLo,
                        modifier = Modifier.heightIn(max = 200.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    downloading = true; progress = 0
                    scope.launch {
                        runCatching {
                            Updater.downloadApk(ctx, info.apkUrl) { progress = it }
                        }.onSuccess { apk ->
                            downloading = false
                            updateInfo = null
                            Updater.install(ctx, apk)
                        }.onFailure {
                            downloading = false
                            updateState = "下载失败：${it.message}"
                        }
                    }
                }) { Text("下载并安装", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { updateInfo = null }) { Text("以后再说", color = TextLo) }
            },
        )
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(label, fontSize = 14.sp, color = TextHi, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, color = Accent)
        Spacer(Modifier.width(8.dp))
        Text("改", fontSize = 12.sp, color = TextLo,
            modifier = Modifier
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                .background(Ink3)
                .padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    Text(
        text, fontSize = 16.sp, color = TextHi,
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(Ink3, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 2.dp),
    )
}
