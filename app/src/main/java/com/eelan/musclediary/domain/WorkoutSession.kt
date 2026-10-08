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
    val durationSec: Int,    // 动作组：每组时长（REPS 换算为秒用于进度）；休息：秒
    val reps: Double,        // 动作组：每组数量（REPS 个数 / DISTANCE 公里 / SECONDS 秒）
) {
    enum class Kind { WORK, REST, DONE }
}

object WorkoutSession {

    /** 展开步骤队列 */
    fun buildSteps(items: List<PlanItem>): List<WorkoutStep> {
        val steps = mutableListOf<WorkoutStep>()
        items.forEach { item ->
            repeat(item.sets) { s ->
                steps += WorkoutStep(
                    kind = WorkoutStep.Kind.WORK, item = item, setIndex = s,
                    durationSec = setSeconds(item).toInt().coerceAtLeast(1),
                    reps = item.reps,
                )
                val isLastOfItem = s == item.sets - 1
                val isLastOverall = item === items.last()
                if (!isLastOfItem || !isLastOverall) {
                    steps += WorkoutStep(
                        kind = WorkoutStep.Kind.REST, item = item, setIndex = s,
                        durationSec = item.restSec.coerceAtLeast(5),
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

    /** 完成某步骤后累计消耗（kcal） */
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
