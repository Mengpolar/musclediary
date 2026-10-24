package com.eelan.musclediary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
                // 授权后立刻补一次当前时段检查，避免错过本轮
                lifecycleScope.launch {
                    runCatching { com.eelan.musclediary.reminder.WaterReminder.checkNow(applicationContext) }
                }
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
                    // 已过时段若落后，打开 App 时补提醒
                    runCatching { reminder.checkNow(applicationContext) }
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

@Composable
fun AppRoot(vm: AppViewModel) {
    // rememberSaveable：旋转/深色模式切换等 Activity 重建后保持所在页与层级
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var manage by rememberSaveable { mutableIntStateOf(-1) } // -1 无；0 食物模板管理；1 锻炼模板管理
    var records by rememberSaveable { mutableIntStateOf(-1) } // -1 无；0 体重；1 饮水；2 饮食；3 锻炼 记录历史页
    val tabs = listOf(L("今日", "Today"), L("饮食", "Diet"), L("锻炼", "Workout"), L("我的", "Me"))

    Scaffold(
        containerColor = Ink0,
        bottomBar = {
            // 悬浮胶囊导航：去 Material 底栏，选中色块即灯光
            PillNavBar(
                tabs = tabs,
                selected = tab,
                onSelect = { i -> tab = i; manage = -1; records = -1 },
            )
        },
    ) { pad ->
        Box(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .background(Ink0)
        ) {
            // 页面切换过渡：按导航方向水平滑动——向右切（序号增大）新页从右侧滑入，向左切从左侧滑入
            val stateKey = if (records >= 0) "records$records" else "tab$tab$manage"
            val prevKey = remember { mutableStateOf(stateKey) }
            // 与上一状态比较：records 页视为更深一层（向右），返回视为向左
            val forward = stateKey != prevKey.value && (
                stateKey.startsWith("records") ||
                    (stateKey.startsWith("tab") && prevKey.value.startsWith("tab") &&
                        stateKey.removePrefix("tab").take(1).toIntOrNull()
                            .let { it != null && prevKey.value.removePrefix("tab").take(1).toIntOrNull().let { p -> p != null && it > p } })
                )
            SideEffect { prevKey.value = stateKey }
            AnimatedContent(
                targetState = stateKey,
                transitionSpec = {
                    val enterX = if (forward) 1 else -1
                    (fadeIn(tween(200)) + slideInHorizontally(tween(220)) { enterX * it / 3 })
                        .togetherWith(fadeOut(tween(90)))
                },
                label = "pageTransition",
            ) { key ->
                Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
                    when {
                        key.startsWith("records") -> RecordsScreen(vm, records) { records = -1 }
                        else -> when (tab) {
                            0 -> TodayScreen(vm, onGoWorkout = { tab = 2 }, onOpenWaterRecords = { records = 1 })
                            1 -> DietScreen(vm, onOpenRecords = { records = 2 })
                            2 -> WorkoutScreen(vm, onOpenWeightRecords = { records = 0 }, onOpenRecords = { records = 3 })
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
    }
}
