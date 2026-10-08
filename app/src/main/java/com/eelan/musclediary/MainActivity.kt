package com.eelan.musclediary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eelan.musclediary.ui.*
import com.eelan.musclediary.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                com.eelan.musclediary.reminder.WaterReminder.scheduleNext(applicationContext)
            }
            // 未授权：提醒功能保持关闭（设置页可重新开启）
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 饮水提醒：已开启且未授权时主动申请权限；安排下一个时段检查
        val reminder = com.eelan.musclediary.reminder.WaterReminder
        lifecycleScope.launch {
            val dao = com.eelan.musclediary.data.AppDatabase.get(applicationContext).dao()
            val p = dao.getProfile()
            if (p?.waterReminder == 1) {
                if (com.eelan.musclediary.reminder.WaterReminder.hasPermission(applicationContext)) {
                    com.eelan.musclediary.reminder.WaterReminder.scheduleNext(applicationContext)
                } else if (android.os.Build.VERSION.SDK_INT >= 33) {
                    requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        setContent {
            MuscleDiaryTheme {
                val vm: AppViewModel = viewModel()
                if (!vm.loaded) {
                    Box(
                        Modifier.fillMaxSize().background(Ink0),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) { CircularProgressIndicator(color = Accent) }
                } else if (vm.profile.setupDone == 0) {
                    com.eelan.musclediary.ui.SetupScreen(vm)
                } else {
                    AppRoot(vm)
                }
            }
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

@Composable
fun AppRoot(vm: AppViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var manage by remember { mutableIntStateOf(-1) } // -1 无；0 食物模板管理；1 锻炼模板管理
    val tabs = listOf(
        Tab("今日", Icons.Default.Home),
        Tab("饮食", Icons.Default.Restaurant),
        Tab("锻炼", Icons.Default.FitnessCenter),
        Tab("我的", Icons.Default.Settings),
    )

    Scaffold(
        containerColor = Ink0,
        bottomBar = {
            NavigationBar(containerColor = Ink1) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i; manage = -1 },
                        icon = { Icon(t.icon, t.label, tint = if (tab == i) Accent else TextLo) },
                        label = { Text(t.label, color = if (tab == i) Accent else TextLo, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Ink2,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .background(Ink0)
        ) {
            Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
                when (tab) {
                    0 -> TodayScreen(vm, onGoWorkout = { tab = 2 })
                    1 -> DietScreen(vm)
                    2 -> WorkoutScreen(vm)
                    3 -> when (manage) {
                        0 -> ManageTemplatesScreen(vm, 0, onClose = { manage = -1 })
                        1 -> ManageTemplatesScreen(vm, 1, onClose = { manage = -1 })
                        else -> SettingsScreen(
                            vm,
                            onManageFood = { manage = 0 },
                            onManageExercise = { manage = 1 },
                        )
                    }
                }
            }
        }
    }
}
