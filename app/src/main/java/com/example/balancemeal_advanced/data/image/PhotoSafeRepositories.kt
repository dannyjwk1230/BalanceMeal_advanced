package com.example.balancemeal_advanced.data.image

import com.example.balancemeal_advanced.data.repository.*
import com.example.balancemeal_advanced.core.model.*
import java.time.LocalDate

class PhotoSafeMeals(private val source:MealRepository,private val photos:PhotoLifecycle):MealRepository by source {
    override suspend fun createMeal(date:LocalDate,mealType:MealType,items:List<MealItem>,photoReference:String?):MealRecord =
        photos.withReference(photoReference) {source.createMeal(date,mealType,items,photoReference)}
    override suspend fun updateMeal(record:MealRecord):MealRecord = photos.withReference(record.photoReference) {source.updateMeal(record)}
    override suspend fun deleteMeal(id:String):Boolean = photos.withReference(null) {source.deleteMeal(id)}
}
class PhotoSafeSettings(private val source:SettingsRepository,private val photos:PhotoLifecycle):SettingsRepository by source {
    override suspend fun updateProfilePhoto(photoReference:String?) = photos.withReference(photoReference) {source.updateProfilePhoto(photoReference)}
}
