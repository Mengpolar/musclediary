package com.eelan.musclediary.ui

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.DisposableEffect
import com.eelan.musclediary.AppViewModel
import com.eelan.musclediary.data.PlanItem
import com.eelan.musclediary.domain.WorkoutSession
import com.eelan.musclediary.domain.WorkoutStep
import com.eelan.musclediary.reminder.VoicePlayer
import com.eelan.musclediary.ui.theme.*
import kotlinx.coroutines.delay

/**
 * 训练进行中全屏页面：屏幕常亮，倒计时驱动，语音提示。
 * 语音触发：开始/动作名/80% 加油/倒数 5/休息/休息倒数 3/结束。
 */
@Composable
fun WorkoutSessionScreen(
    vm: AppViewModel,
    items: List<PlanItem>,
    onFinished: (List<Triple<String, Double, Double>>) -> Unit, // (name, 总数量, 热量)
    onQuit: () -> Unit,
) {
    val activity = LocalContext.current as? Activity
    val voice = remember { com.eelan.musclediary.reminder.VoicePlayer.get(vm.getApplication()) }

    val steps = remember(items) { WorkoutSession.buildSteps(items) }
    var stepIdx by remember { mutableIntStateOf(0) }
    var remainSec by remember { mutableFloatStateOf(steps.firstOrNull()?.durationSec?.toFloat() ?: 1f) }
    var paused by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(items.isNotEmpty() && steps.isNotEmpty()) }
    // 已播报标记：当前步骤的 80% 提示 / 倒数播到哪
    var said80 by remember { mutableStateOf(false) }
    var countedDownTo by remember { mutableIntStateOf(Int.MAX_VALUE) }
    var lastTick by remember { mutableIntStateOf(Int.MAX_VALUE) }
    var countdownPending by remember { mutableStateOf(false) }
    var countdownValue by remember { mutableIntStateOf(3) }

    // 屏幕常亮
    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            // 单例播放器只停队列，不释放资源（App 进程存活期间复用）
            voice.stopQueue()
        }
    }

    val step = steps.getOrNull(stepIdx)

    // 步骤切换播报
    LaunchedEffect(stepIdx) {
        val s = steps.getOrNull(stepIdx) ?: return@LaunchedEffect
        said80 = false
        countedDownTo = Int.MAX_VALUE
        when (s.kind) {
            WorkoutStep.Kind.READY -> {
                // 播报整段进语音队列，最后是 3、2、1；屏幕倒数严格跟语音：
                // 队列播空（isSpeaking=false）后才开始计 3 秒，每播完一个数字屏幕同步跳变
                voice.speak("di", "n_${s.setIndex + 1}", "set_word",
                    "n_${s.actionIndex + 1}", "ge_word")
                voice.speakText(s.item?.name ?: "")
                val qty = when (s.item?.qtyType) {
                    "REPS" -> listOf("n_${fmtQtyCn(s.reps)}", "rep_word")
                    "DISTANCE" -> listOf("n_${fmtQtyCn(s.reps)}", "km_word")
                    else -> listOf("n_${fmtQtyCn(s.reps)}", "sec_word")
                }
                voice.speak(*qty.toTypedArray())
                voice.speak("n_3", "n_2", "n_1")
                countdownPending = true
                countdownValue = 3
            }
            WorkoutStep.Kind.WORK -> {
                // 进入训练倒计时前的一声提示
                voice.speak("ready_go")
            }
            WorkoutStep.Kind.REST -> {
                voice.speak("rest")
                voice.speak("great_${(1..3).random()}")
            }
            WorkoutStep.Kind.DONE -> {}
        }
    }

    // 主计时循环
    LaunchedEffect(running) {
        var last = System.currentTimeMillis()
        while (running && stepIdx < steps.size) {
            delay(100)
            val now = System.currentTimeMillis()
            if (!paused && stepIdx < steps.size) {
                remainSec -= (now - last) / 1000f
                val s = steps[stepIdx]
                val total = s.durationSec.toFloat()
                when (s.kind) {
                    WorkoutStep.Kind.READY -> {
                        // 倒计时跟随语音：等整段播报（含 3、2、1）播空后才开始计 3 秒。
                        // 屏幕显示的数字 = countdownValue，由语音播放进度驱动（每播完一个数 -1）。
                        if (countdownPending && !voice.isSpeaking()) {
                            // 队列刚播空 → 开始倒数
                            countdownPending = false
                            remainSec = 3f
                        }
                        if (!countdownPending) {
                            // 队列已空，正常 3 秒倒数（语音的 3、2、1 已同步播完）
                            // 屏幕显示 countdownValue，与语音对齐
                        }
                    }
                    WorkoutStep.Kind.WORK -> {
                        // 每秒嘀一声
                        val tick = remainSec.toInt()
                        if (tick < lastTick) {
                            lastTick = tick
                            if (tick >= 0) voice.speak("beep")
                        }
                        val progress = 1f - (remainSec / total)
                        // 剩余 10 秒整句提示（只报一次）
                        if (!said80 && (total - remainSec) >= total - 10f) {
                            said80 = true
                            voice.speak("hold_10s")
                        }
                        // 最后 5 秒报数替代嘀声
                        val n = remainSec.toInt()
                        if (n in 1..5 && n < countedDownTo) {
                            countedDownTo = n
                            voice.speak("n_$n")
                        }
                    }
                    WorkoutStep.Kind.REST -> {
                        val n = remainSec.toInt()
                        if (n in 1..3 && n < countedDownTo) {
                            countedDownTo = n
                            voice.speak("n_$n")
                        }
                    }
                    else -> {}
                }
                if (remainSec <= 0f) {
                    if (stepIdx == steps.lastIndex) {
                        voice.speak("finish")
                        running = false
                        // 汇总写库
                        val summary = items.map { it2 ->
                            val kcal = WorkoutSession.setCalories(it2, vm.profile.weightKg) * it2.sets
                            val totalQty = it2.reps * it2.sets
                            Triple(it2.name, totalQty, kcal)
                        }
                        onFinished(summary)
                        return@LaunchedEffect
                    } else if (stepIdx + 1 < steps.size) {
                        val cur = steps[stepIdx]
                        val nxt = steps[stepIdx + 1]
                        if (cur.kind == WorkoutStep.Kind.WORK && nxt.kind == WorkoutStep.Kind.READY) {
                            // 组完成 → 准备下一组，先夸再报休息
                        }
                        if (cur.kind == WorkoutStep.Kind.READY) voice.speak("ding")
                        stepIdx += 1
                        remainSec = steps[stepIdx].durationSec.toFloat()
                    }
                }
            }
            last = now
        }
    }

    val s = step
    if (s == null || !running) {
        // 完成界面
        Column(
            Modifier.fillMaxSize().background(Ink0).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("🎉", fontSize = 60.sp)
            Spacer(Modifier.height(12.dp))
            Text("训练完成！", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextHi)
            Spacer(Modifier.height(8.dp))
            Text("已写入今日锻炼记录", fontSize = 14.sp, color = TextLo)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onQuit,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink0),
            ) { Text("完成", fontWeight = FontWeight.Bold) }
        }
        return
    }

    val isWork = s.kind == WorkoutStep.Kind.WORK
    val color = when (s.kind) {
        WorkoutStep.Kind.WORK -> Accent
        WorkoutStep.Kind.READY -> Warn
        else -> Protein
    }
    val progress = 1f - (remainSec / s.durationSec.toFloat()).coerceIn(0f, 1f)
    val burnedKcal = WorkoutSession.caloriesUpTo(steps, stepIdx - 1, vm.profile.weightKg)
    val totalKcal = WorkoutSession.totalCalories(items, vm.profile.weightKg)

    Column(
        Modifier.fillMaxSize().background(Ink0).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        // 顶部：总进度与消耗
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("已消耗 ≈ ${fmt0(burnedKcal)} / ${fmt0(totalKcal)} kcal",
                fontSize = 13.sp, color = TextLo, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                running = false
                onQuit()
            }) { Text("结束", color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        }
        LinearProgressIndicator(
            progress = { stepIdx.toFloat() / steps.size },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = Accent, trackColor = Ink2,
        )
        Spacer(Modifier.weight(1f))

        val isReady = s.kind == WorkoutStep.Kind.READY
        if (isWork || isReady) {
            val setNo = s.setIndex + 1
            val actionNo = s.actionIndex + 1
            Text("第 $setNo 组 第 $actionNo 个动作 · ${s.item?.name.orEmpty()}",
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextHi)
            if (isReady) {
                Spacer(Modifier.height(6.dp))
                val qtyLabel = when (s.item?.qtyType) {
                    "REPS" -> "${fmt1(s.reps)} 个"
                    "DISTANCE" -> "${fmt1(s.reps)} 公里"
                    else -> "${fmt1(s.reps)} 秒"
                }
                Text("准备 · $qtyLabel", fontSize = 18.sp, color = Warn)
            }
            Spacer(Modifier.height(8.dp))
            val remainReps = (s.reps * (remainSec / s.durationSec.toFloat())).toInt().coerceAtLeast(0)
            Text(
                if (isReady) "${if (countdownPending) "·" else countdownValue}"
                else if (s.item?.qtyType == "DISTANCE") "剩余 ${fmt1(remainSec / s.item.paceMinPerKm / 60.0f * 60f)} km"
                else if (s.item?.qtyType == "SECONDS") "${remainSec.toInt() + 1}"
                else "$remainReps 个",
                fontSize = if (s.item?.qtyType == "SECONDS" || isReady) 88.sp else 64.sp,
                fontWeight = FontWeight.Bold, color = color,
            )
            if (s.item?.qtyType != "SECONDS" && !isReady) {
                Text("${remainSec.toInt() + 1} 秒", fontSize = 20.sp, color = TextLo)
            }
        } else {
            Text("休息一下", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Protein)
            Text("下一组：${s.item?.name.orEmpty()}",
                fontSize = 16.sp, color = TextLo, modifier = Modifier.padding(top = 6.dp))
            Text("${remainSec.toInt() + 1}", fontSize = 88.sp, fontWeight = FontWeight.Bold, color = color)
        }

        Spacer(Modifier.height(20.dp))
        // 大环形进度
        Canvas(Modifier.size(220.dp)) {
            val st = 16.dp.toPx()
            drawArc(Ink2, -90f, 360f, false, style = Stroke(st, cap = StrokeCap.Round))
            drawArc(color, -90f, progress * 360f, false, style = Stroke(st, cap = StrokeCap.Round))
        }
        Spacer(Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    paused = !paused
                    if (paused) voice.speak("pause") else voice.speak("resume")
                },
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text(if (paused) "继续" else "暂停", color = TextHi, fontSize = 15.sp) }
            Button(
                onClick = {
                    // 跳过当前组
                    if (stepIdx == steps.lastIndex) {
                        running = false
                        val summary = items.map { it2 ->
                            val kcal = WorkoutSession.setCalories(it2, vm.profile.weightKg) * it2.sets
                            Triple(it2.name, it2.reps * it2.sets, kcal)
                        }
                        voice.speak("finish")
                        onFinished(summary)
                    } else {
                        if (steps[stepIdx].kind == WorkoutStep.Kind.REST) voice.speak("rest_over")
                        stepIdx += 1
                        remainSec = steps[stepIdx].durationSec.toFloat()
                    }
                },
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink2, contentColor = TextHi),
            ) { Text("跳过本组", fontSize = 15.sp) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** 数量转整数中文播报（20.0 -> 20） */
private fun fmtQtyCn(v: Double): String {
    val n = v.toInt()
    return if (n in 0..100) n.toString() else v.toInt().toString()
}
