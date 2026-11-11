package com.example.balancemeal_advanced.core.nutrition

import com.example.balancemeal_advanced.core.model.NutrientAmounts

data class MacronutrientDistribution(
    val carbohydrate: Double, val protein: Double, val fat: Double,
) {
    val isEmpty: Boolean get() = carbohydrate == 0.0 && protein == 0.0 && fat == 0.0

    companion object {
        val EMPTY = MacronutrientDistribution(0.0, 0.0, 0.0)
        fun from(amounts: NutrientAmounts): MacronutrientDistribution {
            // Scaling first preserves the mass ratios even if C+P+F would overflow.
            val scale = maxOf(amounts.carbohydrateG, amounts.proteinG, amounts.fatG)
            if (scale == 0.0) return EMPTY
            val c = amounts.carbohydrateG / scale
            val p = amounts.proteinG / scale
            val f = amounts.fatG / scale
            val total = c + p + f
            return MacronutrientDistribution(c / total, p / total, f / total)
        }
    }
}

