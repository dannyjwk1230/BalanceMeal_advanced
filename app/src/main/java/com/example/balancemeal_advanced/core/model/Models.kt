package com.example.balancemeal_advanced.core.model

import java.time.LocalDate
import java.util.UUID

enum class FoodUnit(val symbol: String) { G("g"), ML("ml") }

data class NutrientAmounts(
    val carbohydrateG: Double,
    val proteinG: Double,
    val fatG: Double,
    val energyKcal: Double,
) {
    init {
        require(listOf(carbohydrateG, proteinG, fatG, energyKcal).all { it.isFinite() && it >= 0.0 }) {
            "Nutrients must be finite and nonnegative"
        }
    }

    operator fun plus(other: NutrientAmounts) = NutrientAmounts(
        carbohydrateG + other.carbohydrateG, proteinG + other.proteinG,
        fatG + other.fatG, energyKcal + other.energyKcal,
    )

    fun forPortion(amount: Double): NutrientAmounts {
        require(amount.isFinite() && amount > 0.0) { "Portion must be finite and positive" }
        val factor = amount / 100.0
        return NutrientAmounts(carbohydrateG * factor, proteinG * factor, fatG * factor, energyKcal * factor)
    }

    companion object {
        val ZERO = NutrientAmounts(0.0, 0.0, 0.0, 0.0)
    }
}

data class Food(
    val code: String,
    val name: String,
    val unit: FoodUnit,
    val nutrientsPer100: NutrientAmounts,
) {
    init { require(code.isNotBlank() && name.isNotBlank()) }
    val referenceAmount: Double get() = 100.0
}

enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK }

data class MealItem(
    val foodCode: String,
    val amount: Double,
    val id: String = UUID.randomUUID().toString(),
) {
    init {
        require(id.isNotBlank() && foodCode.isNotBlank())
        require(amount.isFinite() && amount > 0.0) { "Portion must be finite and positive" }
    }
}

/** A saved meal, not a partially filled UI draft. Dates are local calendar values. */
data class MealRecord(
    val id: String,
    val date: LocalDate,
    val mealType: MealType,
    val items: List<MealItem>,
    val photoReference: String? = null,
)

data class LocalProfile(
    val nickname: String = "First Account",
    val photoReference: String? = null,
) {
    init {
        require(nickname.isNotBlank())
        require(photoReference == null || photoReference.isNotBlank())
    }
}

enum class Sex { MALE, FEMALE }
enum class ActivityLevel { INACTIVE, LOW_ACTIVE, ACTIVE, VERY_ACTIVE }

data class BodySettings(
    val birthDate: LocalDate,
    val sex: Sex,
    val heightCm: Double,
    val weightKg: Double,
    val activityLevel: ActivityLevel,
    val calculationEnabled: Boolean = true,
) {
    init {
        require(heightCm.isFinite() && heightCm > 0.0)
        require(weightKg.isFinite() && weightKg > 0.0)
    }
}

data class NutrientRange(val lowerG: Double, val upperG: Double) {
    init { require(lowerG.isFinite() && upperG.isFinite() && lowerG >= 0.0 && lowerG <= upperG) }
    fun contains(amountG: Double): Boolean = amountG.isFinite() && amountG >= lowerG && amountG <= upperG
}

