package com.example.balancemeal_advanced.core.validation

import com.example.balancemeal_advanced.core.model.BodySettings
import com.example.balancemeal_advanced.core.model.MealRecord
import java.time.LocalDate
import java.time.Period

object SaveValidation {
    fun meal(record: MealRecord, today: LocalDate) {
        require(record.id.isNotBlank()) { "Meal ID is required" }
        require(!record.date.isAfter(today)) { "Future meal date is not allowed" }
        require(record.items.isNotEmpty()) { "A saved meal needs at least one item" }
        require(record.items.map { it.id }.distinct().size == record.items.size) { "Meal item IDs must be unique" }
        require(record.photoReference == null || record.photoReference.isNotBlank())
    }

    fun body(settings: BodySettings, today: LocalDate) {
        require(!settings.birthDate.isAfter(today)) { "Birth date cannot be in the future" }
        require(Period.between(settings.birthDate, today).years >= 19) { "Body settings require age 19 or older" }
        // Validate even when invoked by a non-UI caller.
        require(settings.heightCm.isFinite() && settings.heightCm > 0.0)
        require(settings.weightKg.isFinite() && settings.weightKg > 0.0)
    }
}

