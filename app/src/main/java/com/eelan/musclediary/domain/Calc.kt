package com.eelan.musclediary.domain

import com.eelan.musclediary.data.ExerciseTemplate
import com.eelan.musclediary.data.Profile
import com.eelan.musclediary.data.QtyType
import kotlin.math.min

data class Targets(
    val bmr: Double,
    val bonus: Double,        // 运动带来的活动系数增量
    val activityFactor: Double,
    val surplus: Double,      // 增肌盈余
    val tdee: Double,         // 热量目标 = BMR×系数 + 盈余
    val protein: Double,      // g
    val carb: Double,         // g
    val fat: Double,          // g
)

/**
 * 计算引擎（公式在 App 内点击任意目标数值即可看到带真实数字的完整推导）：
 * - BMR：Mifflin-St Jeor，男 10w+6.25h-5a+5 / 女 -161
 * - 活动系数：1.2（久坐基础）+ 当日运动消耗/BMR，上限 1.6
 * - 热量目标 = BMR × 活动系数 + 增肌盈余（默认 400）
 * - 蛋白质 = 2.0 g/kg；脂肪 = 25% 热量 ÷ 9（下限 0.8 g/kg）；碳水 = 剩余热量 ÷ 4（自然落在 4~6g/kg）
 */
object Calc {

    fun bmr(p: Profile): Double =
        10 * p.weightKg + 6.25 * p.heightCm - 5 * p.age + (if (p.male) 5.0 else -161.0)

    fun exerciseBonus(exerciseKcal: Double, bmr: Double): Double =
        if (bmr <= 0) 0.0 else min(0.4, exerciseKcal / bmr)

    fun targets(p: Profile, exerciseKcal: Double): Targets {
        val b = bmr(p)
        val bonus = exerciseBonus(exerciseKcal, b)
        val af = 1.2 + bonus
        val tdee = b * af + p.surplusKcal
        val protein = 2.0 * p.weightKg
        // 脂肪：25% 热量，但不低于 0.8 g/kg（激素合成需要）
        val fat = maxOf(tdee * 0.25 / 9, 0.8 * p.weightKg)
        val carb = (tdee - protein * 4 - fat * 9) / 4
        return Targets(b, bonus, af, p.surplusKcal, tdee, protein, carb, fat)
    }

    /** 运动热量：MET × 体重(kg) × 时长(h)；返回 (热量, 分钟) */
    fun exerciseCalories(t: ExerciseTemplate, qty: Double, bodyWeight: Double): Pair<Double, Double> {
        val minutes = when (t.qtyType) {
            QtyType.REPS -> qty * t.perRepSeconds / 60.0
            QtyType.DISTANCE -> qty * t.paceMinPerKm
            QtyType.SECONDS -> qty / 60.0
        }
        return t.met * bodyWeight * minutes / 60.0 to minutes
    }

    /**
     * 单次运动对各肌群的刺激分。
     * 基础量：次数 / 公里×10 / 分钟；主刺激 ×1.0，副刺激 ×0.4；哑铃重量按 (1 + kg/20) 放大。
     */
    fun muscleScores(t: ExerciseTemplate, qty: Double, extraWeightKg: Double): Map<String, Double> {
        val volume = when (t.qtyType) {
            QtyType.REPS -> qty
            QtyType.DISTANCE -> qty * 10
            QtyType.SECONDS -> qty / 60.0
        } * (1.0 + extraWeightKg / 20.0)
        val map = LinkedHashMap<String, Double>()
        map[t.primaryMuscle] = (map[t.primaryMuscle] ?: 0.0) + volume
        t.secondaryMuscles.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            .forEach { id -> map[id] = (map[id] ?: 0.0) + volume * 0.4 }
        return map
    }

    /** 刺激分 → 强度档位：0 无 / 1 轻度 / 2 中度 / 3 高强度 */
    fun intensity(score: Double): Int = when {
        score <= 0.0 -> 0
        score < 20.0 -> 1
        score < 50.0 -> 2
        else -> 3
    }

    fun unitLabel(t: ExerciseTemplate): String = when (t.qtyType) {
        QtyType.REPS -> "个"
        QtyType.DISTANCE -> "公里"
        QtyType.SECONDS -> "秒"
    }
}
