package com.eelan.musclediary.domain

import com.eelan.musclediary.data.PlanItem

/**
 * 训练会话状态机：把编排好的 PlanItem 队列展开为步骤序列（动作组 → 休息 → …）。
 * 每个步骤携带语音触发点，由界面轮询触发播放。
 */
data class WorkoutStep(
    val kind: Kind,
    val item: PlanItem?,     // 动作步骤所属编排
    val setIndex: Int,       // 第几组（0 起）
    val actionIndex: Int,    // 本组是队列里第几个动作（0 起，用于「第N个动作」播报）
    val durationSec: Int,    // 动作组：每组时长（REPS 换算为秒用于进度）；休息/准备：秒
    val reps: Double,        // 动作组：每组数量（REPS 个数 / DISTANCE 公里 / SECONDS 秒）
) {
    enum class Kind { READY, WORK, REST, DONE }
}

object WorkoutSession {

// 每组开始前的准备播报时长（秒）
private const val READY_SEC = 4

    /** 展开步骤队列：每个动作组前插入 READY 准备阶段 */
    fun buildSteps(items: List<PlanItem>): List<WorkoutStep> {
        val steps = mutableListOf<WorkoutStep>()
        items.forEachIndexed { itemIdx, item ->
            repeat(item.sets) { s ->
                steps += WorkoutStep(
                    kind = WorkoutStep.Kind.READY, item = item, setIndex = s,
                    actionIndex = itemIdx, durationSec = READY_SEC, reps = item.reps,
                )
                steps += WorkoutStep(
                    kind = WorkoutStep.Kind.WORK, item = item, setIndex = s,
                    actionIndex = itemIdx, durationSec = setSeconds(item).toInt().coerceAtLeast(1),
                    reps = item.reps,
                )
                val isLastOfItem = s == item.sets - 1
                val isLastOverall = itemIdx == items.lastIndex
                if (!isLastOfItem || !isLastOverall) {
                    steps += WorkoutStep(
                        kind = WorkoutStep.Kind.REST, item = item, setIndex = s,
                        actionIndex = itemIdx, durationSec = item.restSec.coerceAtLeast(5),
                        reps = 0.0,
                    )
                }
            }
        }
        return steps
    }

    /** 单组时长（秒）：REPS 按单次耗时换算；DISTANCE 按配速；SECONDS 即秒 */
    fun setSeconds(item: PlanItem): Double = when (item.qtyType) {
        "REPS" -> item.reps * item.perRepSeconds
        "DISTANCE" -> item.reps * item.paceMinPerKm * 60.0
        else -> item.reps
    }

    /** 单组热量：MET × 体重 × 时长 */
    fun setCalories(item: PlanItem, bodyWeight: Double): Double =
        item.met * bodyWeight * setSeconds(item) / 3600.0

    /** 整个编排的预计消耗（仅动作组，休息不计消耗） */
    fun totalCalories(items: List<PlanItem>, bodyWeight: Double): Double =
        items.sumOf { setCalories(it, bodyWeight) * it.sets }

    /** 预计总时长（秒，含休息） */
    fun totalSeconds(items: List<PlanItem>): Double =
        items.sumOf { setSeconds(it) * it.sets + it.restSec * (it.sets - 1).coerceAtLeast(0) }

    /** 完成某步骤后累计消耗（kcal）：READY/REST 不计消耗 */
    fun caloriesUpTo(steps: List<WorkoutStep>, index: Int, bodyWeight: Double): Double {
        var sum = 0.0
        for (i in 0..index.coerceAtMost(steps.lastIndex)) {
            val s = steps[i]
            if (s.kind == WorkoutStep.Kind.WORK && s.item != null) {
                sum += setCalories(s.item, bodyWeight)
            }
        }
        return sum
    }
}
