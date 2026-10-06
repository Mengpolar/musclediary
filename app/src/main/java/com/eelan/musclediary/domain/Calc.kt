package com.eelan.musclediary.domain

import com.eelan.musclediary.data.ExerciseTemplate
import com.eelan.musclediary.data.Profile
import com.eelan.musclediary.data.QtyType
import kotlin.math.min

data class Targets(
    val bmr: Double,
    val bonus: Double,        // 运动带来的活动系数增量
    val activityFactor: Double,
    val surplus: Double,      // 增肌盈余（正）/ 减脂赤字（负）
    val tdee: Double,         // 热量目标 = BMR×系数 ± 盈余/赤字
    // 每公斤固定基础目标（「目标所需」刻度）
    val baseProtein: Double,
    val baseCarb: Double,
    val baseFat: Double,
    val scale: Double,        // 热量等比分摊系数（「热量所需」= base × scale）
    // 最终目标（热量所需）
    val protein: Double,      // g
    val carb: Double,         // g
    val fat: Double,          // g
) {
    val isCut: Boolean get() = surplus < 0
}

/**
 * 计算引擎（公式在 App 内点击任意目标数值即可看到带真实数字的完整推导）：
 * - BMR：Mifflin-St Jeor，男 10w+6.25h-5a+5 / 女 -161
 * - 活动系数：1.2（久坐基础）+ 当日运动消耗/BMR，上限 1.6
 * - 热量目标 = BMR × 活动系数 + 盈余/赤字（增肌期 +200~300，减脂期 −300~−500）
 * - 三维基础目标（每公斤体重）：增肌期 蛋白2g 碳水5g 脂肪1g；减脂期 蛋白1.5g 碳水2g 脂肪0.8g
 * - 热量目标与三维基础的热量差，按三维基础热量比例等比分摊：最终目标 = 基础 × (热量目标 ÷ 基础热量)
 */
object Calc {

    fun bmr(p: Profile): Double =
        10 * p.weightKg + 6.25 * p.heightCm - 5 * p.age + (if (p.male) 5.0 else -161.0)

    fun exerciseBonus(exerciseKcal: Double, bmr: Double): Double =
        if (bmr <= 0) 0.0 else min(0.4, exerciseKcal / bmr)

    /** 每日饮水目标 ml：体重 × 35 */
    fun waterTargetMl(weightKg: Double): Double = weightKg * 35.0

    fun targets(p: Profile, exerciseKcal: Double): Targets {
        val b = bmr(p)
        val bonus = exerciseBonus(exerciseKcal, b)
        val af = 1.2 + bonus
        val tdee = b * af + p.surplusKcal
        val cut = p.mode == 1
        val baseP = (if (cut) 1.5 else 2.0) * p.weightKg
        val baseC = (if (cut) 2.0 else 5.0) * p.weightKg
        val baseF = (if (cut) 0.8 else 1.0) * p.weightKg
        val baseKcal = baseP * 4 + baseC * 4 + baseF * 9
        val k = if (baseKcal > 0) tdee / baseKcal else 1.0
        return Targets(
            bmr = b, bonus = bonus, activityFactor = af, surplus = p.surplusKcal, tdee = tdee,
            baseProtein = baseP, baseCarb = baseC, baseFat = baseF, scale = k,
            protein = baseP * k, carb = baseC * k, fat = baseF * k,
        )
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

    /** BMI 与区间：0 偏瘦 / 1 正常 / 2 超重 / 3 肥胖（中国标准） */
    fun bmi(weightKg: Double, heightCm: Double): Double =
        if (heightCm > 0) weightKg / ((heightCm / 100.0) * (heightCm / 100.0)) else 0.0

    fun bmiZone(bmi: Double): Int = when {
        bmi < 18.5 -> 0
        bmi < 24.0 -> 1
        bmi < 28.0 -> 2
        else -> 3
    }

    fun unitLabel(t: ExerciseTemplate): String = when (t.qtyType) {
        QtyType.REPS -> "个"
        QtyType.DISTANCE -> "公里"
        QtyType.SECONDS -> "秒"
    }
}
