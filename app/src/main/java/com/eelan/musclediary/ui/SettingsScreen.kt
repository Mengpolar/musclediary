package com.eelan.musclediary.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.eelan.musclediary.L
import com.eelan.musclediary.update.Updater
import com.eelan.musclediary.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

/** 设置页：身体档案 + 增肌盈余 + 语言 + 模板管理 + 数据备份 + 在线更新（官方 / 代理） */
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
    // 更新源：跟随最近一次检查所用源下载，默认官方
    var updateSource by remember { mutableStateOf(Updater.SRC_OFFICIAL) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            vm.exportCsv { f ->
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    f.inputStream().use { it.copyTo(out) }
                }
                Toast.makeText(ctx, L("已导出到所选位置", "Exported to selected location"), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            com.eelan.musclediary.reminder.WaterReminder.scheduleNext(ctx)
            Toast.makeText(ctx, L("提醒已开启", "Reminders enabled"), Toast.LENGTH_SHORT).show()
        } else {
            // 未授权则关闭功能开关
            vm.updateProfile(vm.profile.copy(waterReminder = 0))
            Toast.makeText(ctx, L("未授予通知权限，提醒保持关闭",
                "Notification permission denied; reminders stay off"), Toast.LENGTH_LONG).show()
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
            } ?: Toast.makeText(ctx, L("无法读取文件", "Cannot read file"), Toast.LENGTH_SHORT).show()
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
        SectionTitle(L("目标模式", "Goal Mode"))
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L("训练阶段", "Phase"), fontSize = 14.sp, color = TextHi, modifier = Modifier.weight(1f))
                FilterChip(
                    selected = p.mode == 0, onClick = {
                        vm.updateProfile(p.copy(mode = 0, surplusKcal = p.surplusKcal.coerceIn(200.0, 300.0)))
                    },
                    label = { Text(L("增肌期", "Bulk")) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent.copy(alpha = 0.35f)),
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = p.mode == 1, onClick = {
                        vm.updateProfile(p.copy(mode = 1, surplusKcal = p.surplusKcal.coerceIn(-500.0, -300.0)))
                    },
                    label = { Text(L("减脂期", "Cut")) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent.copy(alpha = 0.35f)),
                )
            }
            Spacer(Modifier.height(4.dp))
            if (p.mode == 0) {
                Text(L("增肌期：蛋白质 2.0g/kg · 碳水 5.0g/kg · 脂肪 1.0g/kg，热量盈余 200~300 大卡",
                    "Bulk: protein 2.0g/kg · carbs 5.0g/kg · fat 1.0g/kg, surplus 200~300 kcal"),
                    fontSize = 12.sp, color = TextLo)
            } else {
                Text(L("减脂期：蛋白质 1.5g/kg · 碳水 2.0g/kg · 脂肪 0.8g/kg，热量赤字 300~500 大卡",
                    "Cut: protein 1.5g/kg · carbs 2.0g/kg · fat 0.8g/kg, deficit 300~500 kcal"),
                    fontSize = 12.sp, color = TextLo)
            }
            Text(L("多余/不足热量按蛋白、碳水、脂肪的比例等比分摊",
                "Surplus/deficit is split across protein/carb/fat by their calorie ratio"),
                fontSize = 12.sp, color = TextLo)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (p.mode == 0) L("增肌盈余", "Bulk surplus") else L("减脂赤字", "Cut deficit"),
                        fontSize = 14.sp, color = TextHi)
                    Text(
                        if (p.mode == 0) L("每天多吃多少热量", "Extra calories per day")
                        else L("每天少吃多少热量（赤字为负数）", "Calories to cut per day (negative)"),
                        fontSize = 12.sp, color = TextLo,
                    )
                }
                SmallButton("−") {
                    val range = if (p.mode == 0) 200.0..300.0 else -500.0..-300.0
                    val v = (p.surplusKcal - 50).coerceIn(range.start, range.endInclusive)
                    vm.updateProfile(p.copy(surplusKcal = v))
                }
                Text(
                    if (p.surplusKcal < 0) "−${fmt0(-p.surplusKcal)}" else "+${fmt0(p.surplusKcal)}",
                    fontSize = 14.sp, color = Accent,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp),
                )
                SmallButton("+") {
                    val range = if (p.mode == 0) 200.0..300.0 else -500.0..-300.0
                    val v = (p.surplusKcal + 50).coerceIn(range.start, range.endInclusive)
                    vm.updateProfile(p.copy(surplusKcal = v))
                }
            }
        }

        SectionTitle(L("身体档案", "Body Profile"))
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L("性别", "Sex"), fontSize = 14.sp, color = TextHi, modifier = Modifier.weight(1f))
                FilterChip(
                    selected = p.male, onClick = { vm.updateProfile(p.copy(male = true)) },
                    label = { Text(L("男", "Male")) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent.copy(alpha = 0.35f)),
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = !p.male, onClick = { vm.updateProfile(p.copy(male = false)) },
                    label = { Text(L("女", "Female")) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent.copy(alpha = 0.35f)),
                )
            }
            SettingRow(L("年龄", "Age"), "${p.age} ${L("岁", "yrs")}") { editAge = true }
            SettingRow(L("身高", "Height"), fmt1(p.heightCm) + " cm") { editHeight = true }
            SettingRow(L("体重", "Weight"), fmt1(p.weightKg) + " kg") { editWeight = true }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(L("每日锻炼目标", "Exercise Goal"), fontSize = 14.sp, color = TextHi)
                    Text(L("今日页锻炼消耗环的目标值", "Target of the exercise ring on Today"),
                        fontSize = 12.sp, color = TextLo)
                }
                SmallButton("−") {
                    if (p.exerciseGoalKcal > 100) vm.updateProfile(p.copy(exerciseGoalKcal = p.exerciseGoalKcal - 50))
                }
                Text("${fmt0(p.exerciseGoalKcal)}", fontSize = 14.sp, color = Accent,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
                SmallButton("+") {
                    if (p.exerciseGoalKcal < 1000) vm.updateProfile(p.copy(exerciseGoalKcal = p.exerciseGoalKcal + 50))
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(L("饮水目标", "Water Goal"), fontSize = 14.sp, color = TextHi)
                    Text(L("每日饮水目标 = 体重 × 35ml（固定系数）", "Daily water goal = weight × 35 ml (fixed)"),
                        fontSize = 12.sp, color = TextLo)
                }
                Text("${fmt0(com.eelan.musclediary.domain.Calc.waterTargetMl(p.weightKg))} ml",
                    fontSize = 14.sp, color = Accent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(L("喝水提醒", "Water Reminder"), fontSize = 14.sp, color = TextHi)
                    Text(
                        if (p.waterReminder == 1) L("分时段检查饮水量，落后时通知（11/15/19/22点）",
                            "Checks intake by time slot, notifies when behind (11/15/19/22)")
                        else L("已关闭", "Off"),
                        fontSize = 12.sp, color = TextLo,
                    )
                }
                Switch(
                    checked = p.waterReminder == 1,
                    onCheckedChange = { on ->
                        if (on) {
                            if (com.eelan.musclediary.reminder.WaterReminder.hasPermission(ctx)) {
                                vm.updateProfile(p.copy(waterReminder = 1))
                                com.eelan.musclediary.reminder.WaterReminder.scheduleNext(ctx)
                            } else {
                                // 主动申请通知权限，授予后再开启
                                notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            vm.updateProfile(p.copy(waterReminder = 0))
                            com.eelan.musclediary.reminder.WaterReminder.cancel(ctx)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Accent,
                        uncheckedTrackColor = Ink3,
                    ),
                )
            }
        }

        SectionTitle(L("语言", "Language"))
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(L("界面语言", "App language"), fontSize = 14.sp, color = TextHi)
                    Text(L("切换后立即生效", "Applies immediately"), fontSize = 12.sp, color = TextLo)
                }
                FilterChip(
                    selected = p.lang == 0, onClick = { vm.updateProfile(p.copy(lang = 0)) },
                    label = { Text("中文") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent.copy(alpha = 0.35f)),
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = p.lang == 1, onClick = { vm.updateProfile(p.copy(lang = 1)) },
                    label = { Text("English") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent.copy(alpha = 0.35f)),
                )
            }
        }

        SectionTitle(L("模板管理", "Templates"))
        CardBox {
            SettingRow(L("食物模板", "Food templates"), "${vm.foodTemplates.size} ${L("个", "")}") { onManageFood() }
            SettingRow(L("锻炼动作模板", "Exercise templates"), "${vm.exerciseTemplates.size} ${L("个", "")}") { onManageExercise() }
            Spacer(Modifier.height(4.dp))
            Text(L("可修改、删除内置与自定义模板，也可补回被删的内置模板",
                "Edit or delete built-in & custom templates, restore deleted built-ins"),
                fontSize = 12.sp, color = TextLo)
        }

        SectionTitle(L("数据备份（本地 CSV）", "Backup (Local CSV)"))
        CardBox {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportLauncher.launch("musclediary_backup.csv") },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink0)) {
                    Text(L("导出", "Export"), fontSize = 13.sp)
                }
                OutlinedButton(onClick = {
                    importLauncher.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "*/*"))
                }) { Text(L("导入（覆盖现有记录）", "Import (overwrites)"), fontSize = 13.sp, color = Accent) }
            }
            Spacer(Modifier.height(6.dp))
            Text(L("包含：档案、自定义模板、全部饮食与锻炼记录。内置模板不在备份内。",
                "Includes: profile, custom templates, all food & exercise records. Built-in templates excluded."),
                fontSize = 12.sp, color = TextLo)
        }

        SectionTitle(L("关于与更新", "About & Update"))
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(L("当前版本", "Current version"), fontSize = 14.sp, color = TextHi)
                    Text("v${BuildConfig.VERSION_NAME} · GitHub: ${Updater.REPO}",
                        fontSize = 12.sp, color = TextLo)
                }
                Button(
                    onClick = {
                        updateState = L("正在检查更新…", "Checking for updates…")
                        scope.launch {
                            runCatching { Updater.check(BuildConfig.VERSION_NAME, updateSource) }
                                .onSuccess { info ->
                                    if (info == null) updateState = L("已是最新版本", "Already up to date")
                                    else { updateInfo = info; updateState = null }
                                }
                                .onFailure { updateState = L("检查失败：", "Check failed: ") + (it.message ?: "") }
                        }
                    },
                    enabled = !downloading,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink0),
                ) { Text(L("检查更新", "Check updates"), fontSize = 13.sp) }
            }
            // 更新源：官方直连 / gh-proxy 代理加速（国内直连 GitHub 慢或失败时选用）
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L("更新源", "Update source"), fontSize = 12.sp, color = TextLo, modifier = Modifier.weight(1f))
                FilterChip(
                    selected = updateSource == Updater.SRC_OFFICIAL,
                    onClick = { updateSource = Updater.SRC_OFFICIAL },
                    label = { Text(L("官方", "Official"), fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent.copy(alpha = 0.35f)),
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = updateSource == Updater.SRC_PROXY,
                    onClick = { updateSource = Updater.SRC_PROXY },
                    label = { Text(L("代理", "Proxy"), fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent.copy(alpha = 0.35f)),
                )
            }
            Text(
                if (updateSource == Updater.SRC_PROXY)
                    L("经 gh-proxy.com 加速下载 APK 与版本查询", "APK & release queries via gh-proxy.com")
                else
                    L("直连 GitHub Releases（可能较慢）", "Direct GitHub Releases (may be slow)"),
                fontSize = 11.sp, color = TextLo,
            )
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
                Text(
                    L("下载中", "Downloading") + " $progress% · " +
                            "${String.format("%.1f", (updateInfo?.sizeBytes ?: 0) * progress / 100.0 / 1048576.0)}" +
                            " / ${Updater.formatSize(updateInfo?.sizeBytes ?: 0)}",
                    fontSize = 12.sp, color = TextLo,
                )
            }
        }
    }

    if (editAge) NumberDialog(L("修改年龄", "Edit Age"), L("年龄", "Age"), "${vm.profile.age}",
        onDismiss = { editAge = false },
        onConfirm = { vm.updateProfile(vm.profile.copy(age = it.toInt())); editAge = false })
    if (editHeight) NumberDialog(L("修改身高", "Edit Height"), L("厘米", "cm"), fmt1(vm.profile.heightCm),
        onDismiss = { editHeight = false },
        onConfirm = { vm.updateProfile(vm.profile.copy(heightCm = it)); editHeight = false })
    if (editWeight) NumberDialog(L("修改体重", "Edit Weight"), L("公斤", "kg"), fmt1(vm.profile.weightKg),
        onDismiss = { editWeight = false },
        onConfirm = { vm.updateProfile(vm.profile.copy(weightKg = it)); editWeight = false })

    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { },
            containerColor = Ink2,
            title = { Text(L("发现新版本 v", "New version v") + info.version, color = TextHi) },
            text = {
                Column {
                    Text(L("新版本 v", "New version v") + "${info.version} · " +
                            L("包大小 ", "size ") + Updater.formatSize(info.sizeBytes),
                        fontSize = 14.sp, color = TextHi, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(info.notes.ifBlank { L("无更新说明", "No release notes") }, fontSize = 13.sp, color = TextLo,
                        modifier = Modifier.heightIn(max = 200.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    downloading = true; progress = 0
                    // 下载是长任务：用 ViewModel 作用域，切页不中断
                    vm.appScope.launch {
                        runCatching {
                            Updater.downloadApk(ctx, info.apkUrl, { progress = it }, updateSource)
                        }.onSuccess { apk ->
                            // 校验下载包与 Release 资产摘要一致，防止下载损坏或被篡改
                            if (!Updater.verifySha256(apk, info.sha256)) {
                                apk.delete()
                                downloading = false
                                updateState = L("安装包校验失败，已取消。请稍后重试",
                                    "Checksum verification failed, cancelled. Try again later")
                                return@launch
                            }
                            downloading = false
                            updateInfo = null
                            Updater.install(ctx, apk)
                        }.onFailure {
                            downloading = false
                            updateState = L("下载失败：", "Download failed: ") + (it.message ?: "")
                        }
                    }
                }) { Text(L("下载并安装", "Download & install"), color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { updateInfo = null }) { Text(L("以后再说", "Later"), color = TextLo) }
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
        Spacer(Modifier.width(10.dp))
        Text(
            L("修改", "Edit"), fontSize = 12.sp, color = TextLo,
            modifier = Modifier
                .border(1.dp, Ink3, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick)
            .background(Ink3, androidx.compose.foundation.shape.RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 18.sp, color = TextHi, fontWeight = FontWeight.Bold)
    }
}
