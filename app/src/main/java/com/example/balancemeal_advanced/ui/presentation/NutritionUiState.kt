package com.example.balancemeal_advanced.ui.presentation

import com.example.balancemeal_advanced.core.model.NutrientAmounts
import com.example.balancemeal_advanced.core.nutrition.MacronutrientDistribution
import java.util.Locale

data class NutrientMetricUiState(
    val name: String, val grams: String, val percent: String, val fraction: Float,
)

/** Build outside Screen composables; contains presentation values, not period aggregation. */
@ConsistentCopyVisibility
data class NutritionUiState private constructor(
    val nutrients: List<NutrientMetricUiState>,
    val energyKcal: String,
    val totalGrams: String,
    val isEmpty: Boolean,
    val isKnown: Boolean,
) {
    companion object {
        val Initial = from(null)
        val Empty = from(NutrientAmounts.ZERO)
        fun from(amounts: NutrientAmounts?): NutritionUiState {
            val ratios = amounts?.let(MacronutrientDistribution::from) ?: MacronutrientDistribution.EMPTY
            val values = listOf(amounts?.carbohydrateG, amounts?.proteinG, amounts?.fatG)
            val fractions = listOf(ratios.carbohydrate, ratios.protein, ratios.fat)
            return NutritionUiState(
                listOf("탄수화물", "단백질", "지방").mapIndexed { i, name ->
                    NutrientMetricUiState(name, decimal(values[i]), if (amounts == null) "—" else decimal(fractions[i] * 100.0), fractions[i].toFloat())
                },
                amounts?.let { String.format(Locale.KOREA, "%.0f", it.energyKcal) } ?: "—",
                decimal(amounts?.let { it.carbohydrateG + it.proteinG + it.fatG }),
                ratios.isEmpty, amounts != null,
            )
        }
        private fun decimal(value: Double?): String =
            if (value == null || !value.isFinite()) "—" else String.format(Locale.KOREA, "%.1f", value)
    }
}

