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
            // 同步语言偏好到全局（切换语言后所有 L() 取词自动刷新）
            Lang.set(profile.lang)
            // 老版本升级：盈余数值对齐新模式区间（增肌 200~300 / 减脂 −300~−500），并落库避免每次启动重算
            if (profile.surplusKcal > 300) {
                profile = profile.copy(surplusKcal = 250.0)
                dao.upsertProfile(profile)
            }
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
        Lang.set(p.lang)
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
            withContext(Dispatchers.Main) { weightEntries = list; profile = updated; Lang.set(updated.lang) }
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
        // kcal 是计算属性（p*4+c*4+f*9）：只填热量时三维全 0 会让 kcal 归零——
        // 按 15/50/35 能量占比反推三维，保证热量真实落库（与 updateMealEntry 口径一致）
        val (p, c, f) = if (protein == 0.0 && carb == 0.0 && fat == 0.0 && kcal > 0) {
            Triple(kcal * 0.15 / 4, kcal * 0.50 / 4, kcal * 0.35 / 9)
        } else Triple(protein, carb, fat)
        val e = FoodEntry(
            date = date.toString(), name = mealName, grams = 0.0,
            protein = p, carb = c, fat = f,
        )
        viewModelScope.launch(Dispatchers.IO) {
            val id = dao.insertFoodEntry(e)
            withContext(Dispatchers.Main) { foodEntries = foodEntries + e.copy(id = id) }
        }
    }

    /** 修改快速记录的餐食：直接写入用户编辑后的三维营养（kcal 为计算属性，随三维更新） */
    fun updateMealEntry(e: FoodEntry, newProtein: Double, newCarb: Double, newFat: Double) {
        val upd = e.copy(protein = newProtein, carb = newCarb, fat = newFat)
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

    /** 撤销删除：按原 id 重插并保持时间顺序（主页删除带 Snackbar 撤销） */
    fun restoreFood(e: FoodEntry) {
        viewModelScope.launch(Dispatchers.IO) { dao.insertFoodEntry(e) }
        foodEntries = (foodEntries + e).sortedBy { it.createdAt }
    }

    /** 某食物最近 3 次记录的克数（去重，新→旧），用于快捷标签与预填 */
    fun recentFoodGrams(template: FoodTemplate): List<Double> {
        val seen = linkedSetOf<Double>()
        foodEntries.filter { it.name == template.name }
            .sortedByDescending { it.createdAt }
            .forEach { e -> seen.add(e.grams) }
        return seen.take(3).toList()
    }

    /** 某动作最近 3 次记录的「数量+附加重量」（去重，新→旧），用于快捷标签与预填 */
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

    fun deleteExercise(e: com.eelan.musclediary.data.ExerciseEntry) {
        viewModelScope.launch(Dispatchers.IO) { dao.deleteExerciseEntry(e) }
        exerciseEntries = exerciseEntries.filterNot { it.id == e.id }
    }

    /** 撤销删除：按原 id 重插并保持时间顺序（主页删除带 Snackbar 撤销） */
    fun restoreExercise(e: com.eelan.musclediary.data.ExerciseEntry) {
        viewModelScope.launch(Dispatchers.IO) { dao.insertExerciseEntry(e) }
        exerciseEntries = (exerciseEntries + e).sortedBy { it.createdAt }
    }

    /** 备份：导出后回调文件路径（v3 格式：v2 基础上补锻炼时长/肌肉刺激分、饮水时间戳与语言偏好，新增列在行尾，旧版可读） */
    fun exportCsv(onDone: (java.io.File) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        val sb = StringBuilder()
        sb.appendLine("# profile")
        sb.appendLine("male,age,heightCm,weightKg,surplus,mode,setupDone,waterReminder,exerciseGoalKcal,lang")
        with(profile) {
            sb.appendLine("$male,$age,$heightCm,$weightKg,$surplusKcal,$mode,$setupDone,$waterReminder,$exerciseGoalKcal,$lang")
        }
        sb.appendLine("# food_templates(custom)")
        foodTemplates.filter { it.isCustom }.forEach {
            sb.appendLine("food,${CsvCodec.escape(CsvCodec.safe(it.name))},${it.protein},${it.carb},${it.fat},${CsvCodec.escape(it.tag)},${CsvCodec.escape(it.unitName)},${it.unitGrams}")
        }
        sb.appendLine("# exercise_templates(custom)")
        exerciseTemplates.filter { it.isCustom }.forEach {
            sb.appendLine("exercise,${CsvCodec.escape(CsvCodec.safe(it.name))},${it.qtyType},${it.met},${it.perRepSeconds},${it.paceMinPerKm},${it.primaryMuscle},${CsvCodec.escape(it.secondaryMuscles)}")
        }
        sb.appendLine("# food_entries")
        foodEntries.forEach {
            sb.appendLine("fentry,${it.date},${CsvCodec.escape(CsvCodec.safe(it.name))},${it.grams},${it.protein},${it.carb},${it.fat}")
        }
        sb.appendLine("# exercise_entries")
        exerciseEntries.forEach {
            sb.appendLine("xentry,${it.date},${CsvCodec.escape(CsvCodec.safe(it.name))},${it.qty},${it.weightKg},${it.qtyTypeOf(exerciseTemplates)},${it.calories},${it.minutes},${CsvCodec.escape(it.musclesJson)}")
        }
        sb.appendLine("# weight_entries")
        weightEntries.forEach {
            sb.appendLine("wentry,${it.date},${it.weightKg}")
        }
        sb.appendLine("# water_entries")
        waterEntries.forEach {
            sb.appendLine("waentry,${it.date},${it.ml},${it.createdAt}")
        }
        val dir = java.io.File(getApplication<Application>().filesDir, "backup").apply { mkdirs() }
        val f = java.io.File(dir, "musclediary_backup_${LocalDate.now()}.csv")
        f.writeText(sb.toString())
        withContext(Dispatchers.Main) { onDone(f) }
    }

    /** CSV 字段转义与公式注入防护见 [CsvCodec]（纯函数，带单元测试） */

    /** 拆分一行 CSV（支持带引号字段） */
    private fun parseCsvLine(line: String): List<String> = CsvCodec.parseLine(line)

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
            // 备份里实际解析出有效数据的 section 才按「覆盖」语义清空重灌，防止缺 section 的旧备份抹掉现有记录
            var sawFood = false; var sawExercise = false
            var sawCustomF = false; var sawCustomX = false
            fun num(s: String?, def: Double = 0.0) = s?.trim()?.toDoubleOrNull() ?: def
            // 日期必须是合法 yyyy-MM-dd：坏日期入库会让记录页 LocalDate.parse 崩溃
            fun dateOk(s: String?): Boolean = s?.trim()?.let { runCatching { LocalDate.parse(it) }.isSuccess } == true
            text.lineSequence().forEach { raw ->
                val line = raw.trim()
                when {
                    line.startsWith("#") -> section = line.removePrefix("#").trim()
                    line.isBlank() -> {}
                    section == "profile" -> profileLine = line.split(',')
                    section == "food_templates(custom)" && line.startsWith("food,") -> runCatching {
                        val p = parseCsvLine(line)
                        customsF += FoodTemplate(name = CsvCodec.unSafe(p[1].trim()), protein = num(p[2]),
                            carb = num(p[3]), fat = num(p[4]), isCustom = true,
                            tag = p.getOrElse(5) { "" }.trim(),
                            unitName = p.getOrElse(6) { "" }.trim(),
                            unitGrams = num(p.getOrElse(7) { "0" }))
                        sawCustomF = true
                    }.onFailure { skipped++ }
                    section == "exercise_templates(custom)" && line.startsWith("exercise,") -> runCatching {
                        val p = parseCsvLine(line)
                        customsX += ExerciseTemplate(name = CsvCodec.unSafe(p[1].trim()), qtyType = QtyType.valueOf(p[2].trim()),
                            met = num(p[3]), perRepSeconds = num(p[4]),
                            paceMinPerKm = num(p[5]), primaryMuscle = p[6].trim(),
                            secondaryMuscles = p.getOrElse(7) { "" }.trim(), isCustom = true)
                        sawCustomX = true
                    }.onFailure { skipped++ }
                    section == "food_entries" && line.startsWith("fentry,") -> runCatching {
                        val p = parseCsvLine(line)
                        val d = p[1].trim()
                        if (!dateOk(d)) { skipped++; return@runCatching }
                        fEntries += FoodEntry(date = d, name = CsvCodec.unSafe(p[2].trim()), grams = num(p[3]),
                            protein = num(p[4]), carb = num(p[5]), fat = num(p[6]))
                        sawFood = true
                    }.onFailure { skipped++ }
                    section == "exercise_entries" && line.startsWith("xentry,") -> runCatching {
                        val p = parseCsvLine(line)
                        val d = p[1].trim()
                        if (!dateOk(d)) { skipped++; return@runCatching }
                        xEntries += Triple(ExerciseEntry(date = d, templateId = 0, name = CsvCodec.unSafe(p[2].trim()),
                            qty = num(p[3]), weightKg = num(p[4]), calories = num(p[6]),
                            minutes = num(p.getOrElse(7) { "0" }),
                            musclesJson = p.getOrNull(8)?.takeIf { it.isNotBlank() } ?: "{}"), p[5].trim(), "")
                        sawExercise = true
                    }.onFailure { skipped++ }
                    section == "weight_entries" && line.startsWith("wentry,") -> runCatching {
                        val p = line.split(',')
                        val d = p[1].trim()
                        if (!dateOk(d)) { skipped++; return@runCatching }
                        wEntries += WeightEntry(date = d, weightKg = num(p[2]))
                    }.onFailure { skipped++ }
                    section == "water_entries" && line.startsWith("waentry,") -> runCatching {
                        val p = line.split(',')
                        val d = p[1].trim()
                        if (!dateOk(d)) { skipped++; return@runCatching }
                        waEntries += WaterEntry(date = d, ml = num(p[2]),
                            createdAt = p.getOrNull(3)?.trim()?.toLongOrNull() ?: System.currentTimeMillis())
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
                            lang = it.getOrNull(9)?.trim()?.toIntOrNull()?.coerceIn(0, 1) ?: cur.lang,
                        ))
                    }
                }
                // 体重按日期唯一（REPLACE 同日取后值）；其余 section 仅在备份中存在有效数据时按「覆盖」清空重灌
                if (wEntries.isNotEmpty()) dao.insertWeightEntries(wEntries)
                if (waEntries.isNotEmpty()) {
                    dao.clearWaterEntries()
                    dao.insertWaterEntries(waEntries)
                }
                if (sawFood) { dao.clearFoodEntries(); dao.insertFoodEntries(fEntries) }
                if (sawExercise) { dao.clearExerciseEntries(); dao.insertExerciseEntries(xEntries.map { it.first }) }
                if (sawCustomF) { dao.clearCustomFoodTemplates(); dao.insertFoodTemplates(customsF) }
                if (sawCustomX) { dao.clearCustomExerciseTemplates(); dao.insertExerciseTemplates(customsX) }
                foodEntries = dao.allFoodEntries()
                exerciseEntries = dao.allExerciseEntries()
                weightEntries = dao.allWeightEntries()
                waterEntries = dao.allWaterEntries()
                foodTemplates = dao.foodTemplates().sortedWith(
                    compareByDescending<FoodTemplate> { it.lastUsedAt }.thenBy { it.name })
                exerciseTemplates = dao.exerciseTemplates().sortedWith(
                    compareByDescending<ExerciseTemplate> { it.lastUsedAt }.thenBy { it.name })
                profile = dao.getProfile() ?: profile
                Lang.set(profile.lang)
            } else summary = "文件中没有可导入的数据"
            withContext(Dispatchers.Main) { onDone(summary) }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onDone("导入失败：文件格式无法识别") }
        }
    }
}
