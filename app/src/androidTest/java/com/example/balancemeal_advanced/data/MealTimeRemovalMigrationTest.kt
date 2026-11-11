package com.example.balancemeal_advanced.data

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.balancemeal_advanced.data.local.MealDatabase
import com.example.balancemeal_advanced.data.local.MealEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MealTimeRemovalMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun legacyDatabase(name: String, populated: Boolean) {
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE meal_records (id TEXT NOT NULL, date TEXT NOT NULL, mealType TEXT NOT NULL, time TEXT NOT NULL, photoReference TEXT, PRIMARY KEY(id))")
            db.execSQL("CREATE UNIQUE INDEX index_meal_records_date_mealType ON meal_records(date, mealType)")
            db.execSQL("CREATE TABLE meal_items (id TEXT NOT NULL, mealId TEXT NOT NULL, foodCode TEXT NOT NULL, amount REAL NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(mealId) REFERENCES meal_records(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX index_meal_items_mealId ON meal_items(mealId)")
            db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT INTO room_master_table VALUES(42, '7292eb54e1b315a8773f484961582441')")
            if (populated) {
                db.execSQL("INSERT INTO meal_records VALUES('first','2026-11-10','BREAKFAST','08:30','photos/shared.jpg')")
                db.execSQL("INSERT INTO meal_records VALUES('second','2026-11-10','LUNCH','12:45',NULL)")
                db.execSQL("INSERT INTO meal_items VALUES('a','first','rice',150.0,0)")
                db.execSQL("INSERT INTO meal_items VALUES('b','first','rice',50.5,1)")
                db.execSQL("INSERT INTO meal_items VALUES('c','second','milk',200.0,0)")
            }
            db.version = 1
        }
    }

    @Test fun migrationPreservesMealsItemsOrderPhotosAndConstraints() = runBlocking {
        val name = "time-removal-" + UUID.randomUUID() + ".db"
        legacyDatabase(name, true)
        var db = Room.databaseBuilder(context, MealDatabase::class.java, name)
            .addMigrations(MealDatabase.MIGRATION_1_2).build()
        try {
            val first = checkNotNull(db.meals().getMeal("first"))
            assertEquals("2026-11-10", first.meal.date)
            assertEquals("BREAKFAST", first.meal.mealType)
            assertEquals("photos/shared.jpg", first.meal.photoReference)
            assertEquals(listOf("a", "b"), first.items.sortedBy { it.position }.map { it.id })
            assertEquals(listOf(150.0, 50.5), first.items.sortedBy { it.position }.map { it.amount })
            assertNull(db.meals().getMeal("second")!!.meal.photoReference)
            assertEquals(3, db.meals().itemCount())
            db.openHelper.writableDatabase.query("PRAGMA table_info(meal_records)").use { cursor ->
                val columns = mutableListOf<String>()
                while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
                assertEquals(listOf("id", "date", "mealType", "photoReference"), columns)
            }
            db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
            db.close()
            db = Room.databaseBuilder(context, MealDatabase::class.java, name)
                .addMigrations(MealDatabase.MIGRATION_1_2).build()
            assertEquals(first, db.meals().getMeal("first"))
            try {
                db.meals().insertMeal(MealEntity("duplicate", "2026-11-10", "BREAKFAST", null))
                fail("Date and meal type must remain unique")
            } catch (_: SQLiteConstraintException) { }
            db.meals().deleteMeal("first")
            assertEquals(1, db.meals().itemCount())
            assertNotNull(db.meals().getMeal("second"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun emptyLegacyDatabaseCanMigrateAndAcceptTimeFreeMeals() = runBlocking {
        val name = "empty-time-removal-" + UUID.randomUUID() + ".db"
        legacyDatabase(name, false)
        val db = Room.databaseBuilder(context, MealDatabase::class.java, name)
            .addMigrations(MealDatabase.MIGRATION_1_2).build()
        try {
            assertEquals(0, db.meals().itemCount())
            db.meals().insertMeal(MealEntity("new", "2026-11-11", "DINNER", null))
            assertEquals("DINNER", db.meals().getMeal("new")!!.meal.mealType)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
