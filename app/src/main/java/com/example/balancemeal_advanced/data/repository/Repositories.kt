package com.example.balancemeal_advanced.data.repository

import com.example.balancemeal_advanced.core.model.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface FoodRepository {
    suspend fun getAll(): List<Food>
    suspend fun getByCode(code: String): Food?
    suspend fun searchByName(query: String): List<Food>

    companion object {
        const val SOURCE_TEXT = "영양 정보 출처: 식품의약품안전처"
    }
}

class FoodDataException(message: String, cause: Throwable? = null) : Exception(message, cause)
class MealConflictException(message: String, cause: Throwable? = null) : Exception(message, cause)
class MealNotFoundException(id: String) : Exception("Meal not found: " + id)
class StoredDataException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface MealRepository {
    fun observeOnDate(date: LocalDate): Flow<List<MealRecord>>
    fun observeMeal(date: LocalDate, mealType: MealType): Flow<MealRecord?>
    suspend fun getMeal(date: LocalDate, mealType: MealType): MealRecord?
    suspend fun getMeal(id: String): MealRecord?
    suspend fun createMeal(
        date: LocalDate,
        mealType: MealType,
        items: List<MealItem>,
        photoReference: String? = null,
    ): MealRecord
    suspend fun updateMeal(record: MealRecord): MealRecord
    suspend fun deleteMeal(id: String): Boolean
}

interface SettingsRepository {
    val profile: Flow<LocalProfile>
    val bodySettings: Flow<BodySettings?>
    suspend fun saveProfile(profile: LocalProfile) {
        updateNickname(profile.nickname)
        updateProfilePhoto(profile.photoReference)
    }
    suspend fun updateNickname(nickname: String)
    suspend fun updateProfilePhoto(photoReference: String?)
    suspend fun saveBodySettings(settings: BodySettings)
}

