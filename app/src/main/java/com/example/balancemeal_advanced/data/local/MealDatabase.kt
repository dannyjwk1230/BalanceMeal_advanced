package com.example.balancemeal_advanced.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "meal_records", indices = [Index(value = ["date", "mealType"], unique = true)])
internal data class MealEntity(
    @PrimaryKey val id: String,
    val date: String,
    val mealType: String,
    val photoReference: String?,
)

@Entity(
    tableName = "meal_items",
    foreignKeys = [ForeignKey(
        entity = MealEntity::class, parentColumns = ["id"], childColumns = ["mealId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("mealId")],
)
internal data class MealItemEntity(
    @PrimaryKey val id: String,
    val mealId: String,
    val foodCode: String,
    val amount: Double,
    val position: Int,
)

internal data class MealWithItems(
    @Embedded val meal: MealEntity,
    @Relation(parentColumn = "id", entityColumn = "mealId") val items: List<MealItemEntity>,
)

@Dao
internal interface MealDao {
    @Transaction
    @Query("SELECT * FROM meal_records WHERE date = :date")
    fun observeOnDate(date: String): Flow<List<MealWithItems>>

    @Transaction
    @Query("SELECT * FROM meal_records WHERE date = :date AND mealType = :type")
    fun observeMeal(date: String, type: String): Flow<MealWithItems?>

    @Transaction
    @Query("SELECT * FROM meal_records WHERE date = :date AND mealType = :type")
    suspend fun getMeal(date: String, type: String): MealWithItems?

    @Transaction
    @Query("SELECT * FROM meal_records WHERE id = :id")
    suspend fun getMeal(id: String): MealWithItems?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMeal(meal: MealEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun updateMeal(meal: MealEntity): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItems(items: List<MealItemEntity>)

    @Query("DELETE FROM meal_items WHERE mealId = :mealId")
    suspend fun deleteItems(mealId: String)

    @Query("DELETE FROM meal_records WHERE id = :id")
    suspend fun deleteMeal(id: String): Int

    @Query("SELECT COUNT(*) FROM meal_items")
    suspend fun itemCount(): Int
    @Query("SELECT COUNT(*) FROM meal_records WHERE photoReference = :reference")
    suspend fun photoReferenceCount(reference:String):Int
}

@Database(entities = [MealEntity::class, MealItemEntity::class], version = 2, exportSchema = true)
internal abstract class MealDatabase : RoomDatabase() {
    abstract fun meals(): MealDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Back up child rows before rebuilding the parent: dropping it can cascade.
                db.execSQL("CREATE TEMP TABLE meal_items_backup_v1 AS SELECT id, mealId, foodCode, amount, position FROM meal_items")
                db.execSQL("CREATE TABLE meal_records_new (id TEXT NOT NULL, date TEXT NOT NULL, mealType TEXT NOT NULL, photoReference TEXT, PRIMARY KEY(id))")
                db.execSQL("INSERT INTO meal_records_new (id, date, mealType, photoReference) SELECT id, date, mealType, photoReference FROM meal_records")
                db.execSQL("DROP TABLE meal_items")
                db.execSQL("DROP TABLE meal_records")
                db.execSQL("ALTER TABLE meal_records_new RENAME TO meal_records")
                db.execSQL("CREATE UNIQUE INDEX index_meal_records_date_mealType ON meal_records (date, mealType)")
                db.execSQL("CREATE TABLE meal_items (id TEXT NOT NULL, mealId TEXT NOT NULL, foodCode TEXT NOT NULL, amount REAL NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(mealId) REFERENCES meal_records(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX index_meal_items_mealId ON meal_items (mealId)")
                db.execSQL("INSERT INTO meal_items (id, mealId, foodCode, amount, position) SELECT id, mealId, foodCode, amount, position FROM meal_items_backup_v1")
                db.execSQL("DROP TABLE meal_items_backup_v1")
            }
        }
    }
}

