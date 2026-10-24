package com.eelan.musclediary.domain

import com.eelan.musclediary.data.ExerciseTemplate
import com.eelan.musclediary.data.Profile
import com.eelan.musclediary.data.QtyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** 计算引擎：BMR / 活动系数 / 目标推导 / 运动热量 / 刺激分 */
class CalcTest {

    private val male = Profile(male = true, age = 25, heightCm = 172.0, weightKg = 65.0)
    private val female = Profile(male = false, age = 30, heightCm = 160.0, weightKg = 55.0)

    // —— BMR（Mifflin-St Jeor）——

    @Test fun bmr_male() {
        // 10×65 + 6.25×172 − 5×25 + 5 = 650 + 1075 − 125 + 5 = 1605
        assertEquals(1605.0, Calc.bmr(male), 1e-6)
    }

    @Test fun bmr_female() {
        // 10×55 + 6.25×160 − 5×30 − 161 = 550 + 1000 − 150 − 161 = 1239
        assertEquals(1239.0, Calc.bmr(female), 1e-6)
    }

    // —— 活动系数 ——

    @Test fun exerciseBonus_scalesWithBurn() {
        val bmr = 1600.0
        assertEquals(0.125, Calc.exerciseBonus(200.0, bmr), 1e-9)  // 200/1600
    }

    @Test fun exerciseBonus_cappedAt04() {
        assertTrue(Calc.exerciseBonus(4000.0, 1000.0) == 0.4)
    }

    @Test fun exerciseBonus_zeroBmrGuards() {
        assertEquals(0.0, Calc.exerciseBonus(100.0, 0.0), 1e-9)
    }

    // —— targets ——

    @Test fun targets_bulkBaseGramsPerKg() {
        val t = Calc.targets(male, 0.0)
        assertEquals(130.0, t.baseProtein, 1e-6)  // 2.0 × 65
        assertEquals(325.0, t.baseCarb, 1e-6)     // 5.0 × 65
        assertEquals(65.0, t.baseFat, 1e-6)       // 1.0 × 65
    }

    @Test fun targets_cutBaseGramsPerKg() {
        val t = Calc.targets(female.copy(mode = 1, surplusKcal = -400.0), 0.0)
        assertEquals(82.5, t.baseProtein, 1e-6)   // 1.5 × 55
        assertEquals(110.0, t.baseCarb, 1e-6)     // 2.0 × 55
        assertEquals(44.0, t.baseFat, 1e-6)       // 0.8 × 55
        assertTrue(t.isCut)
    }

    @Test fun targets_tdeeScalesMacrosProportionally() {
        val t = Calc.targets(male, 0.0)
        // 三维目标 = 基础 × 系数，比例不变
        assertEquals(t.protein / t.baseProtein, t.carb / t.baseCarb, 1e-9)
        assertEquals(t.protein / t.baseProtein, t.fat / t.baseFat, 1e-9)
        // 热量守恒：三维目标热量 ≈ tdee
        val kcal = t.protein * 4 + t.carb * 4 + t.fat * 9
        assertTrue(abs(kcal - t.tdee) < 1.0)
    }

    @Test fun targets_activityFactorRange() {
        val sedentary = Calc.targets(male, 0.0)
        val athlete = Calc.targets(male, 5000.0)
        assertEquals(1.2, sedentary.activityFactor, 1e-9)
        assertTrue(athlete.activityFactor <= 1.6)
    }

    // —— 饮水 ——

    @Test fun waterTarget() {
        assertEquals(2275.0, Calc.waterTargetMl(65.0), 1e-6)
    }

    // —— 运动热量 ——

    private fun reps(met: Double = 6.0, perRep: Double = 3.0) =
        ExerciseTemplate(name = "t", qtyType = QtyType.REPS, met = met, perRepSeconds = perRep, primaryMuscle = "chest")

    @Test fun exerciseCalories_reps() {
        // 20 次 × 3 秒 = 60 秒 = 1 分钟；MET 6 × 65kg × (1/60)h = 6.5 kcal
        val (kcal, minutes) = Calc.exerciseCalories(reps(), 20.0, 65.0)
        assertEquals(1.0, minutes, 1e-9)
        assertEquals(6.5, kcal, 1e-9)
    }

    @Test fun exerciseCalories_distanceUsesPace() {
        val run = ExerciseTemplate(name = "r", qtyType = QtyType.DISTANCE, met = 8.0,
            paceMinPerKm = 6.0, primaryMuscle = "quads")
        // 5km × 6min/km = 30 分钟；8 × 65 × 0.5 = 260 kcal
        val (kcal, minutes) = Calc.exerciseCalories(run, 5.0, 65.0)
        assertEquals(30.0, minutes, 1e-9)
        assertEquals(260.0, kcal, 1e-9)
    }

    @Test fun exerciseCalories_seconds() {
        val plank = ExerciseTemplate(name = "p", qtyType = QtyType.SECONDS, met = 4.0, primaryMuscle = "abs")
        // 120 秒 = 2 分钟；4 × 65 × (2/60) = 8.67 kcal
        val (kcal, minutes) = Calc.exerciseCalories(plank, 120.0, 65.0)
        assertEquals(2.0, minutes, 1e-9)
        assertEquals(4.0 * 65.0 * 2.0 / 60.0, kcal, 1e-9)
    }

    // —— 刺激分 ——

    @Test fun muscleScores_primaryFullSecondaryAt04() {
        val t = reps().copy(secondaryMuscles = "triceps,abs")
        val s = Calc.muscleScores(t, 20.0, 0.0)
        assertEquals(20.0, s["chest"]!!, 1e-9)
        assertEquals(8.0, s["triceps"]!!, 1e-9)
        assertEquals(8.0, s["abs"]!!, 1e-9)
    }

    @Test fun muscleScores_dumbbellWeightAmplifies() {
        val t = reps()
        // 附加 20kg：(1 + 20/20) = 2 倍
        val s = Calc.muscleScores(t, 20.0, 20.0)
        assertEquals(40.0, s["chest"]!!, 1e-9)
    }

    @Test fun muscleScores_distanceVolumeTimes10() {
        val run = ExerciseTemplate(name = "r", qtyType = QtyType.DISTANCE, met = 8.0,
            paceMinPerKm = 6.0, primaryMuscle = "quads")
        val s = Calc.muscleScores(run, 3.0, 0.0)
        assertEquals(30.0, s["quads"]!!, 1e-9)
    }

    // —— 档位 ——

    @Test fun intensityThresholds() {
        assertEquals(0, Calc.intensity(0.0))
        assertEquals(1, Calc.intensity(19.9))
        assertEquals(2, Calc.intensity(20.0))
        assertEquals(2, Calc.intensity(49.9))
        assertEquals(3, Calc.intensity(50.0))
    }

    // —— BMI（中国标准）——

    @Test fun bmiAndZones() {
        assertEquals(65.0 / 1.72 / 1.72, Calc.bmi(65.0, 172.0), 1e-9)
        assertEquals(0, Calc.bmiZone(17.0))
        assertEquals(1, Calc.bmiZone(18.5))
        assertEquals(1, Calc.bmiZone(23.9))
        assertEquals(2, Calc.bmiZone(24.0))
        assertEquals(2, Calc.bmiZone(27.9))
        assertEquals(3, Calc.bmiZone(28.0))
        assertEquals(0.0, Calc.bmi(65.0, 0.0), 1e-9) // 身高 0 防除零
    }
}
