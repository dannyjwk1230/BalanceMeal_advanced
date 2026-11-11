package com.example.balancemeal_advanced.core.nutrition

import com.example.balancemeal_advanced.core.model.Food
import com.example.balancemeal_advanced.core.model.MealItem
import com.example.balancemeal_advanced.core.model.NutrientAmounts

object MealNutrition {
    /** Does not deduplicate food codes: separate items of the same food both contribute. */
    fun total(items: List<MealItem>, foodsByCode: Map<String, Food>): NutrientAmounts =
        items.fold(NutrientAmounts.ZERO) { total, item ->
            val food = requireNotNull(foodsByCode[item.foodCode]) { "Unknown food code: " + item.foodCode }
            total + food.nutrientsPer100.forPortion(item.amount)
        }
}

