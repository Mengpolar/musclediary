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
    val mode: Int = 0, // 0 增肌期 / 1 减脂期
    val exerciseGoalKcal: Double = 300.0,
    val setupDone: Int = 0, // 0 未完成首次引导
)

/** 每日体重记录（每天最多一条，不必天天记） */
@Entity(tableName = "weight_entries", indices = [Index(value = ["date"], unique = true)])
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // yyyy-MM-dd
    val weightKg: Double,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 每日饮水量（当天累计，一条记录） */
@Entity(tableName = "water_entries", indices = [Index(value = ["date"], unique = true)])
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
    val protein: Double,
    val carb: Double,
    val fat: Double,
    val isCustom: Boolean = false,
    val lastUsedAt: Long = 0,
) {
    val kcal: Double get() = protein * 4 + carb * 4 + fat * 9
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
    val qtyType: QtyType,
    val met: Double,
    val perRepSeconds: Double = 0.0,  // REPS 类型：单次耗时（秒）
    val paceMinPerKm: Double = 0.0,   // DISTANCE 类型：配速（分钟/公里）
    val primaryMuscle: String,        // Muscle.id
    val secondaryMuscles: String = "",// 逗号分隔的 Muscle.id
    val isCustom: Boolean = false,
    val lastUsedAt: Long = 0,
)

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

    @Insert
    suspend fun insertFoodEntries(list: List<FoodEntry>)

    @Insert
    suspend fun insertExerciseEntries(list: List<ExerciseEntry>)

    @Query("DELETE FROM food_templates WHERE isCustom = 1")
    suspend fun clearCustomFoodTemplates()

    @Query("DELETE FROM exercise_templates WHERE isCustom = 1")
    suspend fun clearCustomExerciseTemplates()

    @Insert
    suspend fun insertFoodTemplatesGetIds(list: List<FoodTemplate>): List<Long>

    @Insert
    suspend fun insertExerciseTemplatesGetIds(list: List<ExerciseTemplate>): List<Long>

    @Query("SELECT * FROM weight_entries ORDER BY date")
    suspend fun allWeightEntries(): List<WeightEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeightEntry(e: WeightEntry): Long

    @Delete
    suspend fun deleteWeightEntry(e: WeightEntry)

    @Query("DELETE FROM food_templates WHERE isCustom = 0")
    suspend fun clearBuiltinFoodTemplates()

    @Query("SELECT * FROM water_entries ORDER BY date")
    suspend fun allWaterEntries(): List<WaterEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaterEntry(e: WaterEntry): Long

    @Delete
    suspend fun deleteWaterEntry(e: WaterEntry)

    @Update
    suspend fun updateFoodEntry(e: FoodEntry)

    @Update
    suspend fun updateExerciseEntry(e: ExerciseEntry)
}

@Database(
    entities = [Profile::class, FoodTemplate::class, FoodEntry::class,
        ExerciseTemplate::class, ExerciseEntry::class, WeightEntry::class, WaterEntry::class],
    version = 4,
    exportSchema = false,
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

        fun get(ctx: Context): AppDatabase =
            inst ?: synchronized(this) {
                inst ?: Room.databaseBuilder(
                    ctx.applicationContext, AppDatabase::class.java, "musclediary.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { inst = it }
            }
    }
}
