package com.eelan.musclediary

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eelan.musclediary.data.*
import com.eelan.musclediary.domain.Calc
import com.eelan.musclediary.domain.Muscle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).dao()

    /** 应用更新下载等长任务：不随页面销毁中断，ViewModel 存活期内有效 */
    val appScope = kotlinx.coroutines.CoroutineScope(viewModelScope.coroutineContext + Dispatchers.Main.immediate)

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
            // 旧内置模板没有标签：按名称补上
            val tagByName = FoodSeed.items.associate { it.name to it.tag }
            dao.foodTemplates().filter { !it.isCustom && it.tag.isEmpty() }
                .forEach { old ->
                    tagByName[old.name]?.takeIf { it.isNotEmpty() }?.let { tag ->
                        dao.upsertFoodTemplate(old.copy(tag = tag))
                    }
                }
            // 修复历史数据里的无效肌群 id（如 obliques），避免详情显示英文与刺激分丢失
            dao.exerciseTemplates().forEach { t ->
                val fixed = t.secondaryMuscles.split(',').map { it.trim() }
                    .filter { it.isEmpty() || Muscle.byId.containsKey(it) }
                    .joinToString(",")
                if (fixed != t.secondaryMuscles) dao.upsertExerciseTemplate(t.copy(secondaryMuscles = fixed))
            }
            val fList = dao.foodTemplates().sortedWith(
                compareByDescending<FoodTemplate> { it.lastUsedAt }.thenBy { it.name })
            val xList = dao.exerciseTemplates().sortedWith(
                compareByDescending<ExerciseTemplate> { it.lastUsedAt }.thenBy { it.name })
            val fEntries = dao.allFoodEntries()
            val xEntries = dao.allExerciseEntries()
            val wEntries = dao.allWeightEntries()
            val waEntries = dao.allWaterEntries()
            withContext(Dispatchers.Main) {
                foodTemplates = fList
                exerciseTemplates = xList
                foodEntries = fEntries
                exerciseEntries = xEntries
                weightEntries = wEntries
                waterEntries = waEntries
                loaded = true
            }
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

    fun waterOn(date: LocalDate): Double =
        waterEntries.filter { it.date == date.toString() }.sumOf { it.ml }

    /** 某日期的饮水记录明细（按时间正序） */
    fun waterListOn(date: LocalDate): List<WaterEntry> =
        waterEntries.filter { it.date == date.toString() }.sortedBy { it.createdAt }

    /** 记录一次饮水 */
    fun addWater(ml: Double, date: LocalDate) {
        val e = WaterEntry(date = date.toString(), ml = ml)
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertWaterEntry(e)
            val list = dao.allWaterEntries()
            withContext(Dispatchers.Main) { waterEntries = list }
        }
    }

    /** 删除单条饮水记录 */
    fun deleteWater(e: WaterEntry) {
        viewModelScope.launch(Dispatchers.IO) { dao.deleteWaterEntry(e) }
        waterEntries = waterEntries.filterNot { it.id == e.id }
    }

    /** 修改单条饮水记录的毫升数 */
    fun updateWater(e: WaterEntry, newMl: Double) {
        val upd = e.copy(ml = newMl)
        viewModelScope.launch(Dispatchers.IO) { dao.updateWaterEntry(upd) }
        waterEntries = waterEntries.map { if (it.id == e.id) upd else it }
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

    /** 快速记录一餐：直接录入整餐热量与营养（不经过食物模板），克数记 0 表示按餐录入 */
    fun addMealEntry(mealName: String, kcal: Double, protein: Double, carb: Double, fat: Double, date: LocalDate) {
        val e = FoodEntry(
            date = date.toString(), name = mealName, grams = 0.0,
            protein = protein, carb = carb, fat = fat,
        )
        viewModelScope.launch(Dispatchers.IO) {
            val id = dao.insertFoodEntry(e)
            withContext(Dispatchers.Main) { foodEntries = foodEntries + e.copy(id = id) }
        }
    }

    /** 修改快速记录的餐食热量：把目标热量与现有营养热量的差额按三维热量占比分摊；
     *  三维全 0（只有热量没存进营养）时按 15/50/35 能量占比反推（与自定义食物填 kJ 的口径一致） */
    fun updateMealEntry(e: FoodEntry, newKcal: Double) {
        val curKcal = e.kcal
        val upd = if (curKcal > 0) {
            val k = newKcal / curKcal
            e.copy(protein = e.protein * k, carb = e.carb * k, fat = e.fat * k)
        } else {
            e.copy(protein = newKcal * 0.15 / 4, carb = newKcal * 0.50 / 4, fat = newKcal * 0.35 / 9)
        }
        viewModelScope.launch(Dispatchers.IO) { dao.updateFoodEntry(upd) }
        foodEntries = foodEntries.map { if (it.id == e.id) upd else it }
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

    fun addCustomFood(name: String, protein: Double, carb: Double, fat: Double, tag: String = "",
                      unitName: String = "", unitGrams: Double = 0.0, onReady: (FoodTemplate) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val t = FoodTemplate(name = name, protein = protein, carb = carb, fat = fat, tag = tag,
                isCustom = true, unitName = unitName, unitGrams = unitGrams)
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

    /** 某食物最近一次记录的克数（用于预填输入框） */
    fun lastFoodGrams(template: FoodTemplate): Double? =
        foodEntries.filter { it.name == template.name }
            .maxByOrNull { it.createdAt }?.grams

    /** 某食物最近 3 次记录的克数（去重，新→旧），用于快捷标签 */
    fun recentFoodGrams(template: FoodTemplate): List<Double> {
        val seen = linkedSetOf<Double>()
        foodEntries.filter { it.name == template.name }
            .sortedByDescending { it.createdAt }
            .forEach { e -> seen.add(e.grams) }
        return seen.take(3).toList()
    }

    /** 某动作最近一次记录的数量与重量（用于预填输入框） */
    fun lastExerciseQty(template: ExerciseTemplate): Pair<Double, Double>? =
        exerciseEntries.filter { it.templateId == template.id || it.name == template.name }
            .maxByOrNull { it.createdAt }?.let { it.qty to it.weightKg }

    /** 某动作最近 3 次记录的「数量+附加重量」（去重，新→旧），用于快捷标签 */
    fun recentExerciseQty(template: ExerciseTemplate): List<Pair<Double, Double>> {
        val seen = linkedSetOf<Pair<Double, Double>>()
        exerciseEntries.filter { it.templateId == template.id || it.name == template.name }
            .sortedByDescending { it.createdAt }
            .forEach { e -> seen.add(e.qty to e.weightKg) }
        return seen.take(3).toList()
    }

    /** 某日期往前最近 3 次不同的饮水值（去重，新→旧，跨天），用于快捷标签 */
    fun recentWaterMl(): List<Double> {
        val seen = linkedSetOf<Double>()
        waterEntries.sortedByDescending { it.createdAt }
            .forEach { e -> seen.add(e.ml) }
        return seen.take(3).toList()
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

    /** 备份：导出后回调文件路径（v2 格式：含体重/饮水记录与自定义模板的标签/量词单位，旧版可读的部分向后兼容） */
    fun exportCsv(onDone: (java.io.File) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        val sb = StringBuilder()
        sb.appendLine("# profile")
        sb.appendLine("male,age,heightCm,weightKg,surplus,mode,setupDone,waterReminder,exerciseGoalKcal")
        with(profile) {
            sb.appendLine("$male,$age,$heightCm,$weightKg,$surplusKcal,$mode,$setupDone,$waterReminder,$exerciseGoalKcal")
        }
        sb.appendLine("# food_templates(custom)")
        foodTemplates.filter { it.isCustom }.forEach {
            sb.appendLine("food,${csv(it.name)},${it.protein},${it.carb},${it.fat},${csv(it.tag)},${csv(it.unitName)},${it.unitGrams}")
        }
        sb.appendLine("# exercise_templates(custom)")
        exerciseTemplates.filter { it.isCustom }.forEach {
            sb.appendLine("exercise,${csv(it.name)},${it.qtyType},${it.met},${it.perRepSeconds},${it.paceMinPerKm},${it.primaryMuscle},${csv(it.secondaryMuscles)}")
        }
        sb.appendLine("# food_entries")
        foodEntries.forEach {
            sb.appendLine("fentry,${it.date},${csv(it.name)},${it.grams},${it.protein},${it.carb},${it.fat}")
        }
        sb.appendLine("# exercise_entries")
        exerciseEntries.forEach {
            sb.appendLine("xentry,${it.date},${csv(it.name)},${it.qty},${it.weightKg},${it.qtyTypeOf(exerciseTemplates)},${it.calories}")
        }
        sb.appendLine("# weight_entries")
        weightEntries.forEach {
            sb.appendLine("wentry,${it.date},${it.weightKg}")
        }
        sb.appendLine("# water_entries")
        waterEntries.forEach {
            sb.appendLine("waentry,${it.date},${it.ml}")
        }
        val dir = java.io.File(getApplication<Application>().filesDir, "backup").apply { mkdirs() }
        val f = java.io.File(dir, "musclediary_backup_${LocalDate.now()}.csv")
        f.writeText(sb.toString())
        withContext(Dispatchers.Main) { onDone(f) }
    }

    /** CSV 字段转义：含逗号/引号/换行时加引号 */
    private fun csv(s: String): String =
        if (s.contains(',') || s.contains('"') || s.contains('\n'))
            "\"" + s.replace("\"", "\"\"") + "\""
        else s

    /** 拆分一行 CSV（支持带引号字段） */
    private fun parseCsvLine(line: String): List<String> {
        if (!line.contains('"')) return line.split(',')
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuote = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuote && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                c == '"' -> inQuote = !inQuote
                c == ',' && !inQuote -> { out.add(cur.toString()); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        out.add(cur.toString())
        return out
    }

    private fun ExerciseEntry.qtyTypeOf(templates: List<ExerciseTemplate>): String =
        templates.firstOrNull { it.id == templateId }?.qtyType?.name
            ?: (if (name.contains("跑") || name.contains("步")) "DISTANCE" else "REPS")

    /** 导入备份：解析失败的字段/行跳过，不中断；全程 try/catch，坏文件返回错误提示而非崩溃 */
    fun importCsv(text: String, onDone: (String) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        try {
            var profileLine: List<String>? = null
            val customsF = mutableListOf<FoodTemplate>()
            val customsX = mutableListOf<ExerciseTemplate>()
            val fEntries = mutableListOf<FoodEntry>()
            val xEntries = mutableListOf<Triple<ExerciseEntry, String, String>>() // entry, qtyType, secondary
            val wEntries = mutableListOf<WeightEntry>()
            val waEntries = mutableListOf<WaterEntry>()
            var section = ""
            var skipped = 0
            fun num(s: String?, def: Double = 0.0) = s?.trim()?.toDoubleOrNull() ?: def
            text.lineSequence().forEach { raw ->
                val line = raw.trim()
                when {
                    line.startsWith("#") -> section = line.removePrefix("#").trim()
                    line.isBlank() -> {}
                    section == "profile" -> profileLine = line.split(',')
                    section == "food_templates(custom)" && line.startsWith("food,") -> runCatching {
                        val p = parseCsvLine(line)
                        customsF += FoodTemplate(name = p[1].trim(), protein = num(p[2]),
                            carb = num(p[3]), fat = num(p[4]), isCustom = true,
                            tag = p.getOrElse(5) { "" }.trim(),
                            unitName = p.getOrElse(6) { "" }.trim(),
                            unitGrams = num(p.getOrElse(7) { "0" }))
                    }.onFailure { skipped++ }
                    section == "exercise_templates(custom)" && line.startsWith("exercise,") -> runCatching {
                        val p = parseCsvLine(line)
                        customsX += ExerciseTemplate(name = p[1].trim(), qtyType = QtyType.valueOf(p[2].trim()),
                            met = num(p[3]), perRepSeconds = num(p[4]),
                            paceMinPerKm = num(p[5]), primaryMuscle = p[6].trim(),
                            secondaryMuscles = p.getOrElse(7) { "" }.trim(), isCustom = true)
                    }.onFailure { skipped++ }
                    section == "food_entries" && line.startsWith("fentry,") -> runCatching {
                        val p = parseCsvLine(line)
                        fEntries += FoodEntry(date = p[1].trim(), name = p[2].trim(), grams = num(p[3]),
                            protein = num(p[4]), carb = num(p[5]), fat = num(p[6]))
                    }.onFailure { skipped++ }
                    section == "exercise_entries" && line.startsWith("xentry,") -> runCatching {
                        val p = parseCsvLine(line)
                        xEntries += Triple(ExerciseEntry(date = p[1].trim(), templateId = 0, name = p[2].trim(),
                            qty = num(p[3]), weightKg = num(p[4]), calories = num(p[6]),
                            minutes = 0.0, musclesJson = "{}"), p[5].trim(), "")
                    }.onFailure { skipped++ }
                    section == "weight_entries" && line.startsWith("wentry,") -> runCatching {
                        val p = line.split(',')
                        wEntries += WeightEntry(date = p[1].trim(), weightKg = num(p[2]))
                    }.onFailure { skipped++ }
                    section == "water_entries" && line.startsWith("waentry,") -> runCatching {
                        val p = line.split(',')
                        waEntries += WaterEntry(date = p[1].trim(), ml = num(p[2]))
                    }.onFailure { skipped++ }
                }
            }
            var summary = "导入完成：饮食 ${fEntries.size} 条，锻炼 ${xEntries.size} 条" +
                    (if (wEntries.isNotEmpty() || waEntries.isNotEmpty())
                        "，体重 ${wEntries.size} 条，饮水 ${waEntries.size} 条" else "") +
                    (if (skipped > 0) "（跳过 $skipped 行无法识别的数据）" else "")
            if (fEntries.isNotEmpty() || xEntries.isNotEmpty() || wEntries.isNotEmpty() ||
                waEntries.isNotEmpty() || profileLine != null) {
                // 档案：只覆盖备份里的字段，其余设置（模式/引导/提醒/锻炼目标）保持现状
                profileLine?.let {
                    if (it.size >= 5) {
                        val cur = dao.getProfile() ?: Profile()
                        dao.upsertProfile(cur.copy(
                            male = it[0].trim().toBoolean(),
                            age = it[1].trim().toIntOrNull() ?: cur.age,
                            heightCm = num(it[2], cur.heightCm),
                            weightKg = num(it[3], cur.weightKg),
                            surplusKcal = num(it[4], cur.surplusKcal),
                            // v2 备份才有的列：旧 5 列 CSV 缺省时保持现状
                            mode = it.getOrNull(5)?.trim()?.toIntOrNull()?.coerceIn(0, 1) ?: cur.mode,
                            setupDone = it.getOrNull(6)?.trim()?.toIntOrNull() ?: cur.setupDone,
                            waterReminder = it.getOrNull(7)?.trim()?.toIntOrNull()?.coerceIn(0, 1) ?: cur.waterReminder,
                            exerciseGoalKcal = num(it.getOrNull(8), cur.exerciseGoalKcal),
                        ))
                    }
                }
                // 体重按日期唯一（REPLACE 同日取后值）；饮水/饮食/锻炼按「覆盖」语义清空重灌
                if (wEntries.isNotEmpty()) dao.insertWeightEntries(wEntries)
                if (waEntries.isNotEmpty()) {
                    dao.clearWaterEntries()
                    dao.insertWaterEntries(waEntries)
                }
                dao.clearFoodEntries(); dao.clearExerciseEntries()
                dao.insertFoodEntries(fEntries)
                dao.insertExerciseEntries(xEntries.map { it.first })
                dao.clearCustomFoodTemplates(); dao.insertFoodTemplates(customsF)
                dao.clearCustomExerciseTemplates(); dao.insertExerciseTemplates(customsX)
                foodEntries = dao.allFoodEntries()
                exerciseEntries = dao.allExerciseEntries()
                weightEntries = dao.allWeightEntries()
                waterEntries = dao.allWaterEntries()
                foodTemplates = dao.foodTemplates().sortedWith(
                    compareByDescending<FoodTemplate> { it.lastUsedAt }.thenBy { it.name })
                exerciseTemplates = dao.exerciseTemplates().sortedWith(
                    compareByDescending<ExerciseTemplate> { it.lastUsedAt }.thenBy { it.name })
                profile = dao.getProfile() ?: profile
            } else summary = "文件中没有可导入的数据"
            withContext(Dispatchers.Main) { onDone(summary) }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onDone("导入失败：文件格式无法识别") }
        }
    }
}
