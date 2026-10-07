package com.eelan.musclediary

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eelan.musclediary.data.*
import com.eelan.musclediary.domain.Calc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).dao()

    var selectedDate by mutableStateOf(LocalDate.now())
        private set

    var profile by mutableStateOf(Profile())
        private set

    var loaded by mutableStateOf(false)
        private set

    var foodTemplates by mutableStateOf<List<FoodTemplate>>(emptyList())
        private set
    var exerciseTemplates by mutableStateOf<List<ExerciseTemplate>>(emptyList())
        private set
    var foodEntries by mutableStateOf<List<FoodEntry>>(emptyList())
        private set
    var exerciseEntries by mutableStateOf<List<ExerciseEntry>>(emptyList())
        private set
    var weightEntries by mutableStateOf<List<WeightEntry>>(emptyList())
        private set
    var waterEntries by mutableStateOf<List<WaterEntry>>(emptyList())
        private set

    init {
        viewModelScope.launch(Dispatchers.IO) {
            if (dao.foodTemplateCount() == 0) dao.insertFoodTemplates(FoodSeed.items)
            if (dao.exerciseTemplateCount() == 0) dao.insertExerciseTemplates(ExerciseSeed.items)
            profile = dao.getProfile() ?: Profile().also { dao.upsertProfile(it) }
            // 老版本升级：盈余数值对齐新模式区间（增肌 200~300 / 减脂 −300~−500）
            if (profile.surplusKcal > 300) profile = profile.copy(surplusKcal = 250.0)
            // 内置食物数据源升级：重建内置模板（自定义模板与历史记录不受影响）
            if (profile.seedVersion < FoodSeed.version) {
                dao.clearBuiltinFoodTemplates()
                dao.insertFoodTemplates(FoodSeed.items)
                profile = profile.copy(seedVersion = FoodSeed.version)
                dao.upsertProfile(profile)
            }
            foodTemplates = dao.foodTemplates().sortedWith(
                compareByDescending<FoodTemplate> { it.lastUsedAt }.thenBy { it.name })
            exerciseTemplates = dao.exerciseTemplates().sortedWith(
                compareByDescending<ExerciseTemplate> { it.lastUsedAt }.thenBy { it.name })
            foodEntries = dao.allFoodEntries()
            exerciseEntries = dao.allExerciseEntries()
            weightEntries = dao.allWeightEntries()
            waterEntries = dao.allWaterEntries()
            withContext(Dispatchers.Main) { loaded = true }
        }
    }

    fun selectDate(d: LocalDate) { selectedDate = d }

    fun foodOn(date: LocalDate) = foodEntries.filter { it.date == date.toString() }
    fun exerciseOn(date: LocalDate) = exerciseEntries.filter { it.date == date.toString() }
    fun exerciseKcalOn(date: LocalDate) = exerciseOn(date).sumOf { it.calories }

    fun updateProfile(p: Profile) {
        profile = p
        viewModelScope.launch(Dispatchers.IO) { dao.upsertProfile(p) }
    }

    /** 记录今日体重，并同步到身体档案用于目标计算 */
    fun addWeight(weightKg: Double) {
        val today = LocalDate.now().toString()
        val e = WeightEntry(date = today, weightKg = weightKg)
        val updated = profile.copy(weightKg = weightKg)
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertWeightEntry(e)
            dao.upsertProfile(updated)
            val list = dao.allWeightEntries()
            withContext(Dispatchers.Main) { weightEntries = list; profile = updated }
        }
    }

    fun deleteWeight(e: WeightEntry) {
        viewModelScope.launch(Dispatchers.IO) { dao.deleteWeightEntry(e) }
        weightEntries = weightEntries.filterNot { it.id == e.id }
    }

    /** 修改体重记录；若记录的是今天，同步更新身体档案 */
    fun updateWeight(e: WeightEntry, newKg: Double) {
        val upd = e.copy(weightKg = newKg)
        viewModelScope.launch(Dispatchers.IO) { dao.insertWeightEntry(upd) }
        weightEntries = weightEntries.map { if (it.id == e.id) upd else it }
        if (e.date == LocalDate.now().toString() && profile.weightKg != newKg) {
            updateProfile(profile.copy(weightKg = newKg))
        }
    }

    fun waterOn(date: LocalDate): WaterEntry? =
        waterEntries.firstOrNull { it.date == date.toString() }

    /** 记录饮水：在指定日期的累计值上追加 */
    fun addWater(ml: Double, date: LocalDate) {
        val key = date.toString()
        val cur = waterEntries.firstOrNull { it.date == key }
        val e = (cur ?: WaterEntry(date = key, ml = 0.0)).copy(ml = (cur?.ml ?: 0.0) + ml)
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertWaterEntry(e)
            waterEntries = dao.allWaterEntries()
        }
    }

    /** 直接设定指定日期的饮水总量 */
    fun setWaterTotal(ml: Double, date: LocalDate) {
        val key = date.toString()
        val cur = waterEntries.firstOrNull { it.date == key }
        val e = (cur ?: WaterEntry(date = key, ml = 0.0)).copy(ml = ml)
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertWaterEntry(e)
            waterEntries = dao.allWaterEntries()
        }
    }

    fun clearWater(date: LocalDate) {
        val key = date.toString()
        waterEntries.firstOrNull { it.date == key }?.let { e ->
            viewModelScope.launch(Dispatchers.IO) {
                dao.deleteWaterEntry(e)
                waterEntries = dao.allWaterEntries()
            }
        }
    }

    /** 修改饮食记录克数：营养按克数比例重算 */
    fun updateFoodEntry(e: FoodEntry, newGrams: Double) {
        if (e.grams <= 0) return
        val k = newGrams / e.grams
        val upd = e.copy(grams = newGrams, protein = e.protein * k, carb = e.carb * k, fat = e.fat * k)
        viewModelScope.launch(Dispatchers.IO) { dao.updateFoodEntry(upd) }
        foodEntries = foodEntries.map { if (it.id == e.id) upd else it }
    }

    /** 修改锻炼记录数量：热量与刺激分按比例重算 */
    fun updateExerciseEntry(e: ExerciseEntry, newQty: Double) {
        if (e.qty <= 0) return
        val k = newQty / e.qty
        val upd = e.copy(
            qty = newQty,
            calories = e.calories * k,
            minutes = e.minutes * k,
            musclesJson = run {
                val obj = org.json.JSONObject(e.musclesJson)
                val out = org.json.JSONObject()
                obj.keys().forEach { key -> out.put(key, obj.optDouble(key, 0.0) * k) }
                out.toString()
            },
        )
        viewModelScope.launch(Dispatchers.IO) { dao.updateExerciseEntry(upd) }
        exerciseEntries = exerciseEntries.map { if (it.id == e.id) upd else it }
    }

    fun addFood(t: FoodTemplate, grams: Double, date: LocalDate) {
        val k = grams / 100.0
        val e = FoodEntry(
            date = date.toString(), name = t.name, grams = grams,
            protein = t.protein * k, carb = t.carb * k, fat = t.fat * k,
        )
        bumpFoodUsage(t)
        viewModelScope.launch(Dispatchers.IO) {
            val id = dao.insertFoodEntry(e)
            withContext(Dispatchers.Main) { foodEntries = foodEntries + e.copy(id = id) }
        }
    }

    /** 使用模板后记录时间并置顶排序 */
    private fun bumpFoodUsage(t: FoodTemplate) {
        val bumped = t.copy(lastUsedAt = System.currentTimeMillis())
        viewModelScope.launch(Dispatchers.IO) { dao.upsertFoodTemplate(bumped) }
        foodTemplates = (foodTemplates.filterNot { it.id == bumped.id } + bumped)
            .sortedWith(compareByDescending<FoodTemplate> { it.lastUsedAt }.thenBy { it.name })
    }

    private fun bumpExerciseUsage(t: ExerciseTemplate) {
        val bumped = t.copy(lastUsedAt = System.currentTimeMillis())
        viewModelScope.launch(Dispatchers.IO) { dao.upsertExerciseTemplate(bumped) }
        exerciseTemplates = (exerciseTemplates.filterNot { it.id == bumped.id } + bumped)
            .sortedWith(compareByDescending<ExerciseTemplate> { it.lastUsedAt }.thenBy { it.name })
    }

    fun addCustomFood(name: String, protein: Double, carb: Double, fat: Double, onReady: (FoodTemplate) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val t = FoodTemplate(name = name, protein = protein, carb = carb, fat = fat, isCustom = true)
            val id = dao.insertFoodTemplate(t)
            val saved = t.copy(id = id)
            withContext(Dispatchers.Main) {
                foodTemplates = (foodTemplates + saved).sortedBy { it.name }
                onReady(saved)
            }
        }
    }

    fun deleteFood(e: FoodEntry) {
        viewModelScope.launch(Dispatchers.IO) { dao.deleteFoodEntry(e) }
        foodEntries = foodEntries.filterNot { it.id == e.id }
    }

    fun saveFoodTemplate(t: FoodTemplate) {
        viewModelScope.launch(Dispatchers.IO) { dao.upsertFoodTemplate(t) }
        foodTemplates = (foodTemplates.filterNot { it.id == t.id } + t).sortedBy { it.name }
    }

    fun deleteFoodTemplate(t: FoodTemplate) {
        viewModelScope.launch(Dispatchers.IO) { dao.deleteFoodTemplate(t) }
        foodTemplates = foodTemplates.filterNot { it.id == t.id }
    }

    fun saveExerciseTemplate(t: ExerciseTemplate) {
        viewModelScope.launch(Dispatchers.IO) { dao.upsertExerciseTemplate(t) }
        exerciseTemplates = (exerciseTemplates.filterNot { it.id == t.id } + t).sortedBy { it.name }
    }

    fun deleteExerciseTemplate(t: ExerciseTemplate) {
        viewModelScope.launch(Dispatchers.IO) { dao.deleteExerciseTemplate(t) }
        exerciseTemplates = exerciseTemplates.filterNot { it.id == t.id }
    }

    /** 重新补回被删除的内置模板（按名称去重） */
    fun restoreBuiltins(onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val fNames = dao.foodTemplates().map { it.name }.toSet()
            val addF = FoodSeed.items.filter { it.name !in fNames }
            if (addF.isNotEmpty()) dao.insertFoodTemplates(addF)
            val xNames = dao.exerciseTemplates().map { it.name }.toSet()
            val addX = ExerciseSeed.items.filter { it.name !in xNames }
            if (addX.isNotEmpty()) dao.insertExerciseTemplates(addX)
            foodTemplates = dao.foodTemplates().sortedWith(
                compareByDescending<FoodTemplate> { it.lastUsedAt }.thenBy { it.name })
            exerciseTemplates = dao.exerciseTemplates().sortedWith(
                compareByDescending<ExerciseTemplate> { it.lastUsedAt }.thenBy { it.name })
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun addExercise(t: ExerciseTemplate, qty: Double, extraWeight: Double, date: LocalDate) {
        val (kcal, minutes) = Calc.exerciseCalories(t, qty, profile.weightKg)
        val scores = Calc.muscleScores(t, qty, extraWeight)
        val json = org.json.JSONObject().apply { scores.forEach { (k, v) -> put(k, v) } }.toString()
        val e = ExerciseEntry(
            date = date.toString(), templateId = t.id, name = t.name,
            qty = qty, weightKg = extraWeight, calories = kcal, minutes = minutes, musclesJson = json,
        )
        bumpExerciseUsage(t)
        viewModelScope.launch(Dispatchers.IO) {
            val id = dao.insertExerciseEntry(e)
            withContext(Dispatchers.Main) { exerciseEntries = exerciseEntries + e.copy(id = id) }
        }
    }

    fun addCustomExercise(t: ExerciseTemplate, onReady: (ExerciseTemplate) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = dao.insertExerciseTemplate(t)
            val saved = t.copy(id = id)
            withContext(Dispatchers.Main) {
                exerciseTemplates = (exerciseTemplates + saved).sortedBy { it.name }
                onReady(saved)
            }
        }
    }

    fun deleteExercise(e: ExerciseEntry) {
        viewModelScope.launch(Dispatchers.IO) { dao.deleteExerciseEntry(e) }
        exerciseEntries = exerciseEntries.filterNot { it.id == e.id }
    }

    /** 备份：导出后回调文件路径 */
    fun exportCsv(onDone: (java.io.File) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        val sb = StringBuilder()
        sb.appendLine("# profile")
        sb.appendLine("male,age,heightCm,weightKg,surplus")
        with(profile) { sb.appendLine("$male,$age,$heightCm,$weightKg,$surplusKcal") }
        sb.appendLine("# food_templates(custom)")
        foodTemplates.filter { it.isCustom }.forEach {
            sb.appendLine("food,${it.name},${it.protein},${it.carb},${it.fat}")
        }
        sb.appendLine("# exercise_templates(custom)")
        exerciseTemplates.filter { it.isCustom }.forEach {
            sb.appendLine("exercise,${it.name},${it.qtyType},${it.met},${it.perRepSeconds},${it.paceMinPerKm},${it.primaryMuscle},${it.secondaryMuscles}")
        }
        sb.appendLine("# food_entries")
        foodEntries.forEach {
            sb.appendLine("fentry,${it.date},${it.name},${it.grams},${it.protein},${it.carb},${it.fat}")
        }
        sb.appendLine("# exercise_entries")
        exerciseEntries.forEach {
            sb.appendLine("xentry,${it.date},${it.name},${it.qty},${it.weightKg},${it.qtyTypeOf(exerciseTemplates)},${it.calories}")
        }
        val dir = java.io.File(getApplication<Application>().filesDir, "backup").apply { mkdirs() }
        val f = java.io.File(dir, "musclediary_backup_${LocalDate.now()}.csv")
        f.writeText(sb.toString())
        withContext(Dispatchers.Main) { onDone(f) }
    }

    private fun ExerciseEntry.qtyTypeOf(templates: List<ExerciseTemplate>): String =
        templates.firstOrNull { it.id == templateId }?.qtyType?.name
            ?: (if (name.contains("跑") || name.contains("步")) "DISTANCE" else "REPS")

    fun importCsv(text: String, onDone: (String) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        var profileLine: List<String>? = null
        val customsF = mutableListOf<FoodTemplate>()
        val customsX = mutableListOf<ExerciseTemplate>()
        val fEntries = mutableListOf<FoodEntry>()
        val xEntries = mutableListOf<Triple<ExerciseEntry, String, String>>() // entry, qtyType, secondary
        var section = ""
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#") -> section = line.removePrefix("#").trim()
                line.isBlank() -> {}
                section == "profile" -> profileLine = line.split(',')
                section == "food_templates(custom)" && line.startsWith("food,") -> {
                    val p = line.split(',')
                    customsF += FoodTemplate(name = p[1], protein = p[2].toDouble(),
                        carb = p[3].toDouble(), fat = p[4].toDouble(), isCustom = true)
                }
                section == "exercise_templates(custom)" && line.startsWith("exercise,") -> {
                    val p = line.split(',')
                    customsX += ExerciseTemplate(name = p[1], qtyType = QtyType.valueOf(p[2]),
                        met = p[3].toDouble(), perRepSeconds = p[4].toDouble(),
                        paceMinPerKm = p[5].toDouble(), primaryMuscle = p[6],
                        secondaryMuscles = p.getOrElse(7) { "" }, isCustom = true)
                }
                section == "food_entries" && line.startsWith("fentry,") -> {
                    val p = line.split(',')
                    fEntries += FoodEntry(date = p[1], name = p[2], grams = p[3].toDouble(),
                        protein = p[4].toDouble(), carb = p[5].toDouble(), fat = p[6].toDouble())
                }
                section == "exercise_entries" && line.startsWith("xentry,") -> {
                    val p = line.split(',')
                    xEntries += Triple(ExerciseEntry(date = p[1], templateId = 0, name = p[2],
                        qty = p[3].toDouble(), weightKg = p[4].toDouble(), calories = p[6].toDouble(),
                        minutes = 0.0, musclesJson = "{}"), p[5], "")
                }
            }
        }
        var summary = "导入完成：饮食 ${fEntries.size} 条，锻炼 ${xEntries.size} 条"
        if (fEntries.isNotEmpty() || xEntries.isNotEmpty() || profileLine != null) {
            dao.clearFoodEntries(); dao.clearExerciseEntries()
            dao.insertFoodEntries(fEntries)
            profileLine?.let {
                if (it.size >= 5) dao.upsertProfile(Profile(male = it[0].toBoolean(), age = it[1].toInt(),
                    heightCm = it[2].toDouble(), weightKg = it[3].toDouble(), surplusKcal = it[4].toDouble()))
            }
            dao.clearCustomFoodTemplates(); dao.insertFoodTemplates(customsF)
            dao.clearCustomExerciseTemplates(); dao.insertExerciseTemplates(customsX)
            foodEntries = dao.allFoodEntries()
            exerciseEntries = dao.allExerciseEntries()
            foodTemplates = dao.foodTemplates().sortedWith(
                compareByDescending<FoodTemplate> { it.lastUsedAt }.thenBy { it.name })
            exerciseTemplates = dao.exerciseTemplates().sortedWith(
                compareByDescending<ExerciseTemplate> { it.lastUsedAt }.thenBy { it.name })
            profile = dao.getProfile() ?: profile
        } else summary = "文件中没有可导入的数据"
        withContext(Dispatchers.Main) { onDone(summary) }
    }
}
