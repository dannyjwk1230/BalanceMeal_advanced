package com.example.balancemeal_advanced.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.example.balancemeal_advanced.core.model.*
import com.example.balancemeal_advanced.core.time.TimeSource
import com.example.balancemeal_advanced.core.validation.SaveValidation
import com.example.balancemeal_advanced.data.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

internal class RoomMealRepository(
    private val database: MealDatabase,
    private val foods: FoodRepository,
    private val timeSource: TimeSource,
) : MealRepository {
    private val dao = database.meals()

    override fun observeOnDate(date: LocalDate): Flow<List<MealRecord>> =
        dao.observeOnDate(date.toString()).map { rows ->
            rows.map { it.toDomain() }.sortedBy { it.mealType.ordinal }
        }

    override fun observeBetweenDates(start: LocalDate, end: LocalDate): Flow<List<MealRecord>> =
        dao.observeBetweenDates(start.toString(), end.toString()).map { rows ->
            rows.map { it.toDomain() }.sortedWith(compareByDescending<MealRecord> { it.date }.thenBy { it.mealType.ordinal })
        }

    override fun observeMeal(date: LocalDate, mealType: MealType): Flow<MealRecord?> =
        dao.observeMeal(date.toString(), mealType.name).map { it?.toDomain() }

    override suspend fun getMeal(date: LocalDate, mealType: MealType): MealRecord? =
        dao.getMeal(date.toString(), mealType.name)?.toDomain()

    override suspend fun getMeal(id: String): MealRecord? = dao.getMeal(id)?.toDomain()

    override suspend fun createMeal(
        date: LocalDate,
        mealType: MealType,
        items: List<MealItem>,
        photoReference: String?,
    ): MealRecord {
        val record = MealRecord(UUID.randomUUID().toString(), date, mealType, items.toList(), photoReference)
        validate(record)
        try {
            database.withTransaction {
                SaveValidation.meal(record, timeSource.now().toLocalDate())
                if (dao.getMeal(date.toString(), mealType.name) != null) {
                    throw MealConflictException("Date and meal type already exist")
                }
                dao.insertMeal(record.toEntity())
                dao.insertItems(record.itemEntities())
            }
        } catch (error: SQLiteConstraintException) {
            throw MealConflictException("Meal key or item ID already exists", error)
        }
        return record
    }

    override suspend fun updateMeal(record: MealRecord): MealRecord {
        val snapshot = record.copy(items = record.items.toList())
        validate(snapshot)
        try {
            database.withTransaction {
                SaveValidation.meal(snapshot, timeSource.now().toLocalDate())
                val previous = dao.getMeal(snapshot.id)?.meal ?: throw MealNotFoundException(snapshot.id)
                require(previous.date == snapshot.date.toString() && previous.mealType == snapshot.mealType.name) {
                    "Updating a meal cannot move its date or meal type"
                }
                check(dao.updateMeal(snapshot.toEntity()) == 1)
                dao.deleteItems(snapshot.id)
                dao.insertItems(snapshot.itemEntities())
            }
        } catch (error: SQLiteConstraintException) {
            throw MealConflictException("Item ID already belongs to another saved meal", error)
        }
        return snapshot
    }

    override suspend fun deleteMeal(id: String): Boolean =
        database.withTransaction { dao.deleteMeal(id) > 0 }

    private suspend fun validate(record: MealRecord) {
        val catalog = foods.getAll().associateBy { it.code }
        require(record.items.all { catalog.containsKey(it.foodCode) }) { "Unknown food code" }
        // Recheck current local date after potentially slow asset IO, immediately before the transaction.
        SaveValidation.meal(record, timeSource.now().toLocalDate())
    }
}

private fun MealRecord.toEntity() = MealEntity(id, date.toString(), mealType.name, photoReference)
private fun MealRecord.itemEntities() = items.mapIndexed { index, item ->
    MealItemEntity(item.id, id, item.foodCode, item.amount, index)
}

private fun MealWithItems.toDomain(): MealRecord = try {
    require(items.isNotEmpty()) { "Saved meal has no items" }
    MealRecord(
        meal.id, LocalDate.parse(meal.date), MealType.valueOf(meal.mealType),
        items.sortedBy { it.position }.map { MealItem(it.foodCode, it.amount, it.id) }, meal.photoReference,
    ).also {
        require(it.id.isNotBlank())
        require(it.photoReference == null || it.photoReference.isNotBlank())
    }
} catch (error: Exception) {
    throw StoredDataException("Invalid saved meal: " + meal.id, error)
}

