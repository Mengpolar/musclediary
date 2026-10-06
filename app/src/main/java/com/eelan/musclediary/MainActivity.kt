package com.eelan.musclediary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eelan.musclediary.ui.*
import com.eelan.musclediary.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MuscleDiaryTheme {
                val vm: AppViewModel = viewModel()
                if (!vm.loaded) {
                    Box(
                        Modifier.fillMaxSize().background(Ink0),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) { CircularProgressIndicator(color = Accent) }
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
        Tab("日历", Icons.Default.CalendarMonth),
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
                    0 -> TodayScreen(vm, onGoDiet = { tab = 1 }, onGoWorkout = { tab = 2 })
                    1 -> DietScreen(vm)
                    2 -> WorkoutScreen(vm)
                    3 -> CalendarScreen(vm)
                    4 -> when (manage) {
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
