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

    init {
        viewModelScope.launch(Dispatchers.IO) {
            if (dao.foodTemplateCount() == 0) dao.insertFoodTemplates(FoodSeed.items)
            if (dao.exerciseTemplateCount() == 0) dao.insertExerciseTemplates(ExerciseSeed.items)
            profile = dao.getProfile() ?: Profile().also { dao.upsertProfile(it) }
            foodTemplates = dao.foodTemplates()
            exerciseTemplates = dao.exerciseTemplates()
            foodEntries = dao.allFoodEntries()
            exerciseEntries = dao.allExerciseEntries()
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

    fun addFood(t: FoodTemplate, grams: Double, date: LocalDate) {
        val k = grams / 100.0
        val e = FoodEntry(
            date = date.toString(), name = t.name, grams = grams,
            protein = t.protein * k, carb = t.carb * k, fat = t.fat * k,
        )
        viewModelScope.launch(Dispatchers.IO) {
            val id = dao.insertFoodEntry(e)
            withContext(Dispatchers.Main) { foodEntries = foodEntries + e.copy(id = id) }
        }
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
            foodTemplates = dao.foodTemplates()
            exerciseTemplates = dao.exerciseTemplates()
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
            foodTemplates = dao.foodTemplates()
            exerciseTemplates = dao.exerciseTemplates()
            profile = dao.getProfile() ?: profile
        } else summary = "文件中没有可导入的数据"
        withContext(Dispatchers.Main) { onDone(summary) }
    }
}
