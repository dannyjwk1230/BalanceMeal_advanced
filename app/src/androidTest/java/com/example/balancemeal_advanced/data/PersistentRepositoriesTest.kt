package com.example.balancemeal_advanced.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.balancemeal_advanced.core.model.*
import com.example.balancemeal_advanced.core.nutrition.MealNutrition
import com.example.balancemeal_advanced.core.time.TimeSource
import com.example.balancemeal_advanced.data.food.CachedFoodRepository
import com.example.balancemeal_advanced.data.local.*
import com.example.balancemeal_advanced.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PersistentRepositoriesTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val now = LocalDateTime.of(2026, 11, 10, 20, 0)
    private val clock = TimeSource { now }
    private val date = now.toLocalDate()
    private fun foods() = CachedFoodRepository(readText = {
        context.assets.open("foods_sample.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
    })
    private fun database(name: String) = Room.databaseBuilder(context, MealDatabase::class.java, name).build()
    private suspend fun rice(repository: FoodRepository) = repository.getAll().single { it.name == "쌀밥" }

    @Test fun mealsSurviveReopenUpdateKeepsIdAndDeleteCascades() = runBlocking {
        val name = "p03-meal-" + UUID.randomUUID() + ".db"
        var db = database(name)
        try {
            val foods = foods()
            val food = rice(foods)
            var repository: MealRepository = RoomMealRepository(db, foods, clock)
            val items = listOf(MealItem(food.code, 100.0), MealItem(food.code, 50.0))
            val saved = repository.createMeal(date, MealType.LUNCH,items, "photos/one.jpg")
            assertEquals(2, db.meals().itemCount())
            assertEquals(saved, withTimeout(5000) { repository.observeMeal(date, MealType.LUNCH).first() })
            db.close()
            db = database(name)
            repository = RoomMealRepository(db, foods, clock)
            val reopened = checkNotNull(repository.getMeal(saved.id))
            assertEquals(saved, reopened)
            assertNotEquals(reopened.items[0].id, reopened.items[1].id)
            assertEquals(249.0, MealNutrition.total(reopened.items, foods.getAll().associateBy { it.code }).energyKcal, 1e-10)
            val changed = repository.updateMeal(reopened.copy(items = listOf(items[1])))
            assertEquals(saved.id, changed.id)
            assertEquals(changed, repository.getMeal(date, MealType.LUNCH))
            assertEquals(1, db.meals().itemCount())
            assertTrue(repository.deleteMeal(saved.id))
            assertEquals(0, db.meals().itemCount())
            assertNull(repository.getMeal(saved.id))
            assertFalse(repository.deleteMeal(saved.id))
            assertTrue(withTimeout(5000) { repository.observeOnDate(date).first() }.isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun competingCreatesHaveOnlyOneWinnerForDateAndType() = runBlocking {
        val name = "p03-race-" + UUID.randomUUID() + ".db"
        val db = database(name)
        try {
            val foods = foods()
            val code = rice(foods).code
            val repository = RoomMealRepository(db, foods, clock)
            val results = coroutineScope {
                List(2) { async(Dispatchers.Default) {
                    try {
                        repository.createMeal(date, MealType.BREAKFAST,listOf(MealItem(code, 100.0)))
                        true
                    } catch (_: MealConflictException) { false }
                } }.awaitAll()
            }
            assertEquals(1, results.count { it })
            assertEquals(1, withTimeout(5000) { repository.observeOnDate(date).first() }.size)
            assertEquals(1, db.meals().itemCount())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun failedReplacementRollsBackParentAndChildrenAndCannotMoveKey() = runBlocking {
        val name = "p03-rollback-" + UUID.randomUUID() + ".db"
        val db = database(name)
        try {
            val foods = foods()
            val code = rice(foods).code
            val repository = RoomMealRepository(db, foods, clock)
            val first = repository.createMeal(date, MealType.BREAKFAST,listOf(MealItem(code, 100.0)))
            val second = repository.createMeal(date, MealType.LUNCH,listOf(MealItem(code, 50.0)))
            try {
                repository.updateMeal(first.copy(items = second.items))
                fail("Conflicting item ID must fail")
            } catch (_: MealConflictException) { }
            assertEquals(first, repository.getMeal(first.id))
            assertEquals(second, repository.getMeal(second.id))
            assertEquals(2, db.meals().itemCount())
            try {
                repository.updateMeal(first.copy(date = date.minusDays(1)))
                fail("Meal key must not move")
            } catch (_: IllegalArgumentException) { }
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun invalidMealWritesDoNotCreateRows() = runBlocking {
        val name = "p03-invalid-" + UUID.randomUUID() + ".db"
        val db = database(name)
        try {
            val foods = foods()
            val repository = RoomMealRepository(db, foods, clock)
            val item = MealItem(rice(foods).code, 1.0)
            val invalidItems = listOf(emptyList(), listOf(item, item), listOf(MealItem("unknown", 1.0)))
            for (items in invalidItems) {
                try { repository.createMeal(date, MealType.SNACK,items); fail() }
                catch (_: IllegalArgumentException) { }
            }
            try {
                repository.createMeal(date.plusDays(1), MealType.SNACK,listOf(item))
                fail()
            } catch (_: IllegalArgumentException) { }
            assertTrue(repository.observeOnDate(date).first().isEmpty())
            assertEquals(0, db.meals().itemCount())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun calculationFlagDefaultsOnForLegacyDataAndPersistsWithoutChangingOtherFields() = runBlocking {
        val file=File(context.cacheDir,"calculation-flag-"+UUID.randomUUID()+".preferences_pb")
        var scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        fun store()=PreferenceDataStoreFactory.create(scope=scope,produceFile={file})
        try {
            val legacyStore=store()
            legacyStore.updateData { prefs ->
                prefs.toMutablePreferences().apply {
                    this[androidx.datastore.preferences.core.stringPreferencesKey("body.birthDate")]="1990-02-03"
                    this[androidx.datastore.preferences.core.stringPreferencesKey("body.sex")]="MALE"
                    this[androidx.datastore.preferences.core.doublePreferencesKey("body.heightCm")]=180.5
                    this[androidx.datastore.preferences.core.doublePreferencesKey("body.weightKg")]=75.25
                    this[androidx.datastore.preferences.core.stringPreferencesKey("body.activityLevel")]="LOW_ACTIVE"
                }
            }
            val repository=DataStoreSettingsRepository(legacyStore,clock)
            val original=checkNotNull(repository.bodySettings.first())
            assertTrue(original.calculationEnabled)
            repository.updateNickname("Preserved")
            repository.saveBodySettings(original.copy(calculationEnabled=false))
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
            val reopened=DataStoreSettingsRepository(store(),clock)
            assertEquals(original.copy(calculationEnabled=false),reopened.bodySettings.first())
            assertEquals("Preserved",reopened.profile.first().nickname)
            reopened.saveBodySettings(original)
            assertTrue(reopened.bodySettings.first()!!.calculationEnabled)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }

    @Test fun settingsSurviveReopenAndInvalidBodyPreservesAllFiveFields() = runBlocking {
        val file = File(context.cacheDir, "p03-settings-" + UUID.randomUUID() + ".preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        fun open() = DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }), clock,
        )
        var repository: SettingsRepository = open()
        try {
            assertEquals(LocalProfile(), repository.profile.first())
            assertNull(repository.bodySettings.first())
            val settings = BodySettings(LocalDate.of(1990, 2, 3), Sex.MALE, 180.5, 75.25, ActivityLevel.LOW_ACTIVE)
            repository.updateNickname("한글 이름")
            repository.updateProfilePhoto("photos/profile.jpg")
            repository.saveBodySettings(settings)
            val profile = repository.profile.first()
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            repository = open()
            assertEquals(profile, repository.profile.first())
            assertEquals(settings, repository.bodySettings.first())
            try {
                repository.saveBodySettings(settings.copy(birthDate = LocalDate.of(2010, 1, 1)))
                fail()
            } catch (_: IllegalArgumentException) { }
            assertEquals(settings, repository.bodySettings.first())
            repository.updateProfilePhoto(null)
            assertEquals("한글 이름", repository.profile.first().nickname)
            assertNull(repository.profile.first().photoReference)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }
}

