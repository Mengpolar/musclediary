package com.eelan.musclediary.data

import androidx.room.*
import android.content.Context

@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey val id: Int = 1,
    val male: Boolean = true,
    val age: Int = 25,
    val heightCm: Double = 172.0,
    val weightKg: Double = 65.0,
    val surplusKcal: Double = 250.0,
    val seedVersion: Int = 1,
    val exerciseSeedVersion: Int = 1,
    val mode: Int = 0, // 0 增肌期 / 1 减脂期
    val exerciseGoalKcal: Double = 300.0,
    val setupDone: Int = 0, // 0 未完成首次引导
    val waterReminder: Int = 1, // 0 关 / 1 开（需系统通知权限）
    val voiceId: String = "default", // 旧训练模式遗留字段，已废弃
    val lang: Int = 0, // 界面语言：0 中文 / 1 English
)

/** 每日体重记录（每天最多一条，不必天天记） */
@Entity(tableName = "weight_entries", indices = [Index(value = ["date"], unique = true)])
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // yyyy-MM-dd
    val weightKg: Double,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 每次饮水的记录（按条记录，当天累计） */
@Entity(tableName = "water_entries")
data class WaterEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // yyyy-MM-dd
    val ml: Double,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 营养数值均为每 100g 含量 */
@Entity(tableName = "food_templates")
data class FoodTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nameEn: String = "", // 英文名（内置模板数据源），空 = 无英文名，英文界面回退中文名
    val protein: Double,
    val carb: Double,
    val fat: Double,
    val isCustom: Boolean = false,
    val lastUsedAt: Long = 0,
    val tag: String = "", // 标签：高质量蛋白 / 优质碳水 / 健康脂肪 / 果蔬纤维 等，空为无标签
    val unitName: String = "", // 量词单位名：如「个」「盒」「碗」，空 = 按克
    val unitGrams: Double = 0.0, // 1 个单位等于多少 g（牛奶等液体为 ml）
    val userEdited: Boolean = false, // 用户手动改过（仅对内置模板有意义）：数据源升级重播种时保留，不覆盖
) {
    val kcal: Double get() = protein * 4 + carb * 4 + fat * 9
    val dispName: String get() = if (com.eelan.musclediary.Lang.isEn && nameEn.isNotEmpty()) nameEn else name
}

/** 营养数值为当日进食总量 */
@Entity(tableName = "food_entries")
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // yyyy-MM-dd
    val name: String,
    val grams: Double,
    val protein: Double,
    val carb: Double,
    val fat: Double,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val kcal: Double get() = protein * 4 + carb * 4 + fat * 9
}

enum class QtyType { REPS, DISTANCE, SECONDS }

@Entity(tableName = "exercise_templates")
data class ExerciseTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nameEn: String = "", // 英文名（内置模板数据源），空 = 无英文名，英文界面回退中文名
    val qtyType: QtyType,
    val met: Double,
    val perRepSeconds: Double = 0.0,  // REPS 类型：单次耗时（秒）
    val paceMinPerKm: Double = 0.0,   // DISTANCE 类型：配速（分钟/公里）
    val primaryMuscle: String,        // Muscle.id
    val secondaryMuscles: String = "",// 逗号分隔的 Muscle.id
    val isCustom: Boolean = false,
    val lastUsedAt: Long = 0,
    val voiceKey: String = "",        // 旧训练模式遗留字段，已废弃
    val userEdited: Boolean = false,  // 用户手动改过（仅对内置模板有意义）：数据源升级重播种时保留，不覆盖
) {
    val dispName: String get() = if (com.eelan.musclediary.Lang.isEn && nameEn.isNotEmpty()) nameEn else name
}

/** 历史条目名是录入时语言的快照：回查模板时中英文名都要认（语言切换后仍能匹配） */
fun FoodTemplate.matchesEntryName(n: String): Boolean =
    n == name || (nameEn.isNotEmpty() && n == nameEn)

fun ExerciseTemplate.matchesEntryName(n: String): Boolean =
    n == name || (nameEn.isNotEmpty() && n == nameEn)

@Entity(tableName = "exercise_entries")
data class ExerciseEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val templateId: Long,
    val name: String,
    val qty: Double,
    val weightKg: Double = 0.0, // 哑铃类附加重量
    val calories: Double,
    val minutes: Double,
    val musclesJson: String, // {"chest":12.5,...} 当次各肌群刺激分
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface AppDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun getProfile(): Profile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(p: Profile)

    @Query("SELECT COUNT(*) FROM food_templates")
    suspend fun foodTemplateCount(): Int

    @Query("SELECT COUNT(*) FROM exercise_templates")
    suspend fun exerciseTemplateCount(): Int

    @Insert
    suspend fun insertFoodTemplates(list: List<FoodTemplate>)

    @Insert
    suspend fun insertExerciseTemplates(list: List<ExerciseTemplate>)

    @Query("SELECT * FROM food_templates ORDER BY isCustom, name")
    suspend fun foodTemplates(): List<FoodTemplate>

    @Query("SELECT * FROM exercise_templates ORDER BY isCustom, name")
    suspend fun exerciseTemplates(): List<ExerciseTemplate>

    @Insert
    suspend fun insertFoodTemplate(t: FoodTemplate): Long

    @Insert
    suspend fun insertExerciseTemplate(t: ExerciseTemplate): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFoodTemplate(t: FoodTemplate)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExerciseTemplate(t: ExerciseTemplate)

    @Delete
    suspend fun deleteFoodTemplate(t: FoodTemplate)

    @Delete
    suspend fun deleteExerciseTemplate(t: ExerciseTemplate)

    @Query("SELECT * FROM food_entries ORDER BY createdAt")
    suspend fun allFoodEntries(): List<FoodEntry>

    @Query("SELECT * FROM exercise_entries ORDER BY createdAt")
    suspend fun allExerciseEntries(): List<ExerciseEntry>

    @Insert
    suspend fun insertFoodEntry(e: FoodEntry): Long

    @Insert
    suspend fun insertExerciseEntry(e: ExerciseEntry): Long

    @Delete
    suspend fun deleteFoodEntry(e: FoodEntry)

    @Delete
    suspend fun deleteExerciseEntry(e: ExerciseEntry)

    @Query("DELETE FROM food_entries")
    suspend fun clearFoodEntries()

    @Query("DELETE FROM exercise_entries")
    suspend fun clearExerciseEntries()

    @Query("DELETE FROM water_entries")
    suspend fun clearWaterEntries()

    @Insert
    suspend fun insertFoodEntries(list: List<FoodEntry>)

    @Insert
    suspend fun insertExerciseEntries(list: List<ExerciseEntry>)

    @Query("DELETE FROM food_templates WHERE isCustom = 1")
    suspend fun clearCustomFoodTemplates()

    @Query("DELETE FROM exercise_templates WHERE isCustom = 1")
    suspend fun clearCustomExerciseTemplates()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeightEntries(list: List<WeightEntry>)

    @Insert
    suspend fun insertWaterEntries(list: List<WaterEntry>)

    @Query("SELECT * FROM weight_entries ORDER BY date")
    suspend fun allWeightEntries(): List<WeightEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeightEntry(e: WeightEntry): Long

    @Delete
    suspend fun deleteWeightEntry(e: WeightEntry)

    @Query("DELETE FROM food_templates WHERE isCustom = 0")
    suspend fun clearBuiltinFoodTemplates()

    @Query("DELETE FROM exercise_templates WHERE isCustom = 0")
    suspend fun clearBuiltinExerciseTemplates()

    @Query("SELECT * FROM water_entries ORDER BY date, createdAt")
    suspend fun allWaterEntries(): List<WaterEntry>

    @Insert
    suspend fun insertWaterEntry(e: WaterEntry): Long

    @Query("SELECT * FROM water_entries WHERE date = :date ORDER BY createdAt")
    suspend fun waterEntriesOn(date: String): List<WaterEntry>

    @Delete
    suspend fun deleteWaterEntry(e: WaterEntry)

    @Update
    suspend fun updateFoodEntry(e: FoodEntry)

    @Update
    suspend fun updateExerciseEntry(e: ExerciseEntry)

    @Update
    suspend fun updateWaterEntry(e: WaterEntry)
}

@Database(
    entities = [Profile::class, FoodTemplate::class, FoodEntry::class,
        ExerciseTemplate::class, ExerciseEntry::class, WeightEntry::class, WaterEntry::class],
    version = 10,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var inst: AppDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `weight_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`date` TEXT NOT NULL, `weightKg` REAL NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_weight_entries_date` ON `weight_entries` (`date`)")
                db.execSQL("ALTER TABLE `profile` ADD COLUMN `seedVersion` INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `water_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`date` TEXT NOT NULL, `ml` REAL NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_water_entries_date` ON `water_entries` (`date`)")
                db.execSQL("ALTER TABLE `profile` ADD COLUMN `mode` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `profile` ADD COLUMN `exerciseGoalKcal` REAL NOT NULL DEFAULT 300")
                db.execSQL("ALTER TABLE `profile` ADD COLUMN `setupDone` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `food_templates` ADD COLUMN `lastUsedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `exercise_templates` ADD COLUMN `lastUsedAt` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `food_templates` ADD COLUMN `tag` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `profile` ADD COLUMN `waterReminder` INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workout_plans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, `itemsJson` TEXT NOT NULL, " +
                            "`lastUsedAt` INTEGER NOT NULL DEFAULT 0, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE `exercise_templates` ADD COLUMN `voiceKey` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `profile` ADD COLUMN `voiceId` TEXT NOT NULL DEFAULT 'default'")
            }
        }

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 饮水改按条记录：去掉 date 唯一约束，重建表
                db.execSQL("DROP TABLE IF EXISTS `water_entries_new`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `water_entries_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`date` TEXT NOT NULL, `ml` REAL NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `water_entries_new` (`date`, `ml`, `createdAt`) " +
                            "SELECT `date`, `ml`, `createdAt` FROM `water_entries`"
                )
                db.execSQL("DROP TABLE `water_entries`")
                db.execSQL("ALTER TABLE `water_entries_new` RENAME TO `water_entries`")
                // 移除训练模式：计划表不再使用
                db.execSQL("DROP TABLE IF EXISTS `workout_plans`")
                // 食物模板：量词单位（1个=多少g，液体为ml）
                db.execSQL("ALTER TABLE `food_templates` ADD COLUMN `unitName` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `food_templates` ADD COLUMN `unitGrams` REAL NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 界面语言偏好（0 中文 / 1 English）
                db.execSQL("ALTER TABLE `profile` ADD COLUMN `lang` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 模板英文名（内置模板随数据源升级重播种补全，自定义模板默认空回退中文名）
                db.execSQL("ALTER TABLE `food_templates` ADD COLUMN `nameEn` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercise_templates` ADD COLUMN `nameEn` TEXT NOT NULL DEFAULT ''")
                // 锻炼数据源版本：与食物 seedVersion 同机制，老用户可收到动作模板修正
                db.execSQL("ALTER TABLE `profile` ADD COLUMN `exerciseSeedVersion` INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 标记用户手动编辑过的内置模板：数据源升级重播种时保留这些行不被官方数据覆盖
                db.execSQL("ALTER TABLE `food_templates` ADD COLUMN `userEdited` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `exercise_templates` ADD COLUMN `userEdited` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(ctx: Context): AppDatabase =
            inst ?: synchronized(this) {
                inst ?: Room.databaseBuilder(
                    ctx.applicationContext, AppDatabase::class.java, "musclediary.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                    MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .build().also { inst = it }
            }
    }
}
