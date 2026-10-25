package com.eelan.musclediary.data

import com.eelan.musclediary.domain.Muscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内置模板数据源质量测试：结构约束（重名/肌群 id/MET/量词单位/英文名覆盖）
 * 与关键数值回归（熟重基准、勘误项防回退）。
 */
class SeedTest {

    // —— 结构约束：食物 ——

    @Test
    fun foodNamesUnique() {
        assertEquals(FoodSeed.items.size, FoodSeed.items.map { it.name }.toSet().size)
    }

    @Test
    fun foodNameEnCompleteAndUnique() {
        FoodSeed.items.forEach { assertTrue("${it.name} 缺英文名", it.nameEn.isNotBlank()) }
        assertEquals(FoodSeed.items.size, FoodSeed.items.map { it.nameEn }.toSet().size)
    }

    @Test
    fun foodMacrosInRange() {
        FoodSeed.items.forEach {
            assertTrue("${it.name} 蛋白越界 ${it.protein}", it.protein in 0.0..60.0)
            assertTrue("${it.name} 碳水越界 ${it.carb}", it.carb in 0.0..90.0)
            assertTrue("${it.name} 脂肪越界 ${it.fat}", it.fat in 0.0..100.0)
            assertTrue("${it.name} 热量越界 ${it.kcal}", it.kcal in 0.0..950.0)
            assertTrue("${it.name} 热量为 0", it.kcal > 0.0)
        }
    }

    @Test
    fun foodUnitGramsValid() {
        FoodSeed.items.filter { it.unitName.isNotBlank() }.forEach {
            assertTrue("${it.name} 配了量词但 unitGrams 非法 ${it.unitGrams}", it.unitGrams > 0)
        }
    }

    // —— 关键数值回归（v4 勘误防回退）——

    @Test
    fun eggFatCorrected() {
        assertEquals(9.5, byName("鸡蛋").fat, 0.01) // 原 11.3 偏高（USDA 生全蛋 9.5）
    }

    @Test
    fun porkTenderloinLean() {
        assertTrue("猪里脊是最瘦部位，熟重脂肪应 <6g", byName("猪里脊").fat < 6.0) // 原 14.0
    }

    @Test
    fun fishCookedBasis() {
        assertEquals(25.4, byName("三文鱼").protein, 0.01) // 熟重基准（生 20.4）
        assertEquals(22.8, byName("鳕鱼").protein, 0.01)
        assertEquals(26.0, byName("牛肉馅").protein, 0.01)
    }

    @Test
    fun beerAlcoholFoldedIntoCarb() {
        val b = byName("啤酒")
        assertEquals(43.0, b.kcal, 2.0) // 酒精热量折算进碳水列后总热量≈43/100ml
    }

    @Test
    fun ramenRemoved() {
        assertNull("拉面为干面饼基准且与兰州拉面重复，应已删除", byNameOrNull("拉面"))
        assertNotNull(byName("兰州拉面"))
    }

    @Test
    fun cottageCheeseRenamed() {
        assertNull(byNameOrNull("干酪"))
        assertNotNull(byName("茅屋奶酪"))
    }

    private fun byName(n: String) = FoodSeed.items.first { it.name == n }
    private fun byNameOrNull(n: String) = FoodSeed.items.firstOrNull { it.name == n }

    // —— 结构约束：锻炼 ——

    @Test
    fun exerciseNamesUnique() {
        assertEquals(ExerciseSeed.items.size, ExerciseSeed.items.map { it.name }.toSet().size)
    }

    @Test
    fun exerciseNameEnCompleteAndUnique() {
        ExerciseSeed.items.forEach { assertTrue("${it.name} 缺英文名", it.nameEn.isNotBlank()) }
        assertEquals(ExerciseSeed.items.size, ExerciseSeed.items.map { it.nameEn }.toSet().size)
    }

    @Test
    fun muscleIdsValid() {
        ExerciseSeed.items.forEach { t ->
            assertTrue("${t.name} 主肌群非法 ${t.primaryMuscle}", Muscle.byId.containsKey(t.primaryMuscle))
            t.secondaryMuscles.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                assertTrue("${t.name} 副肌群非法 $it", Muscle.byId.containsKey(it))
            }
        }
    }

    @Test
    fun metInRange() {
        ExerciseSeed.items.forEach { assertTrue("${tName(it)} MET 越界 ${it.met}", it.met in 2.5..13.0) }
    }

    @Test
    fun repsHavePerRepSeconds() {
        ExerciseSeed.items.filter { it.qtyType == QtyType.REPS }.forEach {
            assertTrue("${tName(it)} 单次耗时越界 ${it.perRepSeconds}", it.perRepSeconds in 1.5..8.0)
        }
    }

    @Test
    fun distanceHavePace() {
        ExerciseSeed.items.filter { it.qtyType == QtyType.DISTANCE }.forEach {
            assertTrue("${tName(it)} 配速越界 ${it.paceMinPerKm}", it.paceMinPerKm in 2.0..20.0)
        }
    }

    // —— 关键数值回归（v2 勘误防回退）——

    @Test
    fun cyclingMetMatchesDefaultPace() {
        val c = ExerciseSeed.items.first { it.name == "骑行" }
        assertEquals(6.0, c.met, 0.01) // 默认配速 4:00/km（15 km/h）≈6 MET，原 7.5 高估约 25%
    }

    @Test
    fun rearDeltFlySecondaryFixed() {
        val t = ExerciseSeed.items.first { it.name == "哑铃俯身飞鸟" }
        assertEquals("traps", t.secondaryMuscles) // 后束/上背，原误标 lats
    }

    private fun tName(t: ExerciseTemplate) = t.name
}
