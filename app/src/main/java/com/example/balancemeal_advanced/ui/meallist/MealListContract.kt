package com.example.balancemeal_advanced.ui.meallist

import com.example.balancemeal_advanced.ui.presentation.NutritionUiState
import com.example.balancemeal_advanced.ui.presentation.PeriodTab
import com.example.balancemeal_advanced.ui.presentation.PeriodUiState
import java.time.LocalDate

enum class MealListContent { INITIAL, EMPTY, DATES }

data class MealRowUiState(
    val mealId: String,
    val label: String,
    val foodSummary: String,
    val energyLabel: String,
    val nutrientSummary: String,
    val expanded: Boolean = false,
)
data class MealDateUiState(
    val date: LocalDate,
    val label: String,
    val summary: String,
    val energyLabel: String,
    val meals: List<MealRowUiState> = emptyList(),
)
data class MealListUiState(
    val period: PeriodUiState = PeriodUiState(),
    val content: MealListContent = MealListContent.INITIAL,
    val dates: List<MealDateUiState> = emptyList(),
    val selectedDetail: MealDetailUiState? = null,
    val showDatePicker: Boolean = false,
)
sealed interface MealListEvent {
    data class PeriodSelected(val period: PeriodTab) : MealListEvent
    data object CalendarRequested : MealListEvent
    data class RangeSubmitted(val start: LocalDate, val end: LocalDate) : MealListEvent
    data class MealExpansionRequested(val mealId: String) : MealListEvent
    data class MealSelected(val mealId: String) : MealListEvent
    data class RecordForDateRequested(val date: LocalDate) : MealListEvent
}

// Visual contract for the existing Stitch detail reference; no detail destination is connected.
data class MealDetailUiState(
    val dateLabel: String,
    val mealLabel: String,
    val nutrition: NutritionUiState,
    val items: List<MealDetailItemUiState>,
)
data class MealDetailItemUiState(
    val id: String, val name: String, val amountLabel: String, val nutrition: NutritionUiState,
)

