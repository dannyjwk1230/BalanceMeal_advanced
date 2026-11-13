package com.example.balancemeal_advanced.ui.meallist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.balancemeal_advanced.core.model.MealRecord
import com.example.balancemeal_advanced.core.model.NutrientAmounts
import com.example.balancemeal_advanced.core.time.TimeSource
import com.example.balancemeal_advanced.data.repository.FoodRepository
import com.example.balancemeal_advanced.data.repository.MealRepository
import com.example.balancemeal_advanced.ui.presentation.NutritionUiState
import com.example.balancemeal_advanced.ui.presentation.PeriodTab
import com.example.balancemeal_advanced.ui.presentation.PeriodUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest

class MealListViewModel(
    private val meals: MealRepository,
    private val foods: FoodRepository,
    private val timeSource: TimeSource,
) : ViewModel() {
    private val _state = MutableStateFlow(MealListUiState(
        period = PeriodUiState(
            selected = PeriodTab.MONTH,
            rangeLabel = "",
            description = "최근 1개월"
        )
    ))
    val state: StateFlow<MealListUiState> = _state.asStateFlow()

    private var currentStart: LocalDate = timeSource.now().toLocalDate().minusMonths(1)
    private var currentEnd: LocalDate = timeSource.now().toLocalDate()

    private val expandedMeals = mutableSetOf<String>()

    // Store records to easily map to detail view
    private var currentRecords = listOf<MealRecord>()

    init {
        updateDateRange(currentStart, currentEnd)
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            meals.observeBetweenDates(currentStart, currentEnd).collectLatest { records ->
                currentRecords = records
                buildUiState(records)
            }
        }
    }

    private suspend fun buildUiState(records: List<MealRecord>) {
        if (records.isEmpty()) {
            _state.update { it.copy(content = MealListContent.EMPTY, dates = emptyList()) }
            return
        }

        val allFoods = foods.getAll().associateBy { it.code }

        val dates = records.groupBy { it.date }.map { (date, dailyMeals) ->
            val dailyEnergy = dailyMeals.sumOf { meal ->
                meal.items.sumOf { item ->
                    val food = allFoods[item.foodCode]
                    if (food != null) food.nutrientsPer100.forPortion(item.amount).energyKcal.roundToInt() else 0
                }
            }

            val mealRows = dailyMeals.map { meal ->
                var mealEnergy = 0
                var foodSummary = ""
                var protein = 0.0
                var fat = 0.0
                var carbs = 0.0

                meal.items.forEachIndexed { i, item ->
                    val food = allFoods[item.foodCode]
                    if (food != null) {
                        val portion = food.nutrientsPer100.forPortion(item.amount)
                        mealEnergy += portion.energyKcal.roundToInt()
                        protein += portion.proteinG
                        fat += portion.fatG
                        carbs += portion.carbohydrateG
                        if (i == 0) foodSummary = food.name
                    } else if (i == 0) {
                        foodSummary = "알 수 없는 음식"
                    }
                }

                if (meal.items.size > 1) {
                    foodSummary += " 외 ${meal.items.size - 1}개"
                } else if (meal.items.isEmpty()) {
                    foodSummary = "등록된 음식 없음"
                }

                val typeLabel = when(meal.mealType.name) {
                    "BREAKFAST" -> "아침"
                    "LUNCH" -> "점심"
                    "DINNER" -> "저녁"
                    "SNACK" -> "간식"
                    else -> meal.mealType.name
                }

                val nutrientSummary = "탄수화물 ${carbs.roundToInt()}g, 단백질 ${protein.roundToInt()}g, 지방 ${fat.roundToInt()}g"

                MealRowUiState(
                    mealId = meal.id,
                    label = typeLabel,
                    foodSummary = foodSummary,
                    energyLabel = mealEnergy.toString(),
                    nutrientSummary = nutrientSummary,
                    expanded = expandedMeals.contains(meal.id)
                )
            }

            MealDateUiState(
                date = date,
                label = date.format(DateTimeFormatter.ofPattern("M월 d일")),
                summary = "총 기록 ${dailyMeals.size}건",
                energyLabel = dailyEnergy.toString(),
                meals = mealRows
            )
        }

        _state.update { it.copy(content = MealListContent.DATES, dates = dates) }
    }

    private fun updateDateRange(start: LocalDate, end: LocalDate, tab: PeriodTab? = null) {
        currentStart = start
        currentEnd = end

        val formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
        val label = "${start.format(formatter)} ~ ${end.format(formatter)}"
        val description = tab?.label ?: "직접 설정"

        _state.update {
            it.copy(
                period = it.period.copy(
                    selected = tab ?: it.period.selected, // Fallback if custom
                    rangeLabel = label,
                    description = description
                ),
                showDatePicker = false
            )
        }

        observeData()
    }

    fun handleEvent(event: MealListEvent) {
        when (event) {
            is MealListEvent.PeriodSelected -> {
                val today = timeSource.now().toLocalDate()
                val start = when (event.period) {
                    PeriodTab.DAY -> today
                    PeriodTab.WEEK -> today.minusWeeks(1)
                    PeriodTab.MONTH -> today.minusMonths(1)
                }
                updateDateRange(start, today, event.period)
            }
            is MealListEvent.CalendarRequested -> {
                _state.update { it.copy(showDatePicker = true) }
            }
            is MealListEvent.RangeSubmitted -> {
                updateDateRange(event.start, event.end)
            }
            is MealListEvent.MealExpansionRequested -> {
                if (expandedMeals.contains(event.mealId)) {
                    expandedMeals.remove(event.mealId)
                } else {
                    expandedMeals.add(event.mealId)
                }
                // Trigger UI update using current records
                viewModelScope.launch {
                    buildUiState(currentRecords)
                }
            }
            is MealListEvent.MealSelected -> {
                viewModelScope.launch {
                    showMealDetail(event.mealId)
                }
            }
            is MealListEvent.RecordForDateRequested -> {
                // Should navigate to record for this date
            }
        }
    }

    fun dismissDatePicker() {
        _state.update { it.copy(showDatePicker = false) }
    }

    private suspend fun showMealDetail(mealId: String) {
        val record = currentRecords.find { it.id == mealId } ?: return
        val allFoods = foods.getAll().associateBy { it.code }

        var totalEnergy = 0.0
        var totalProtein = 0.0
        var totalFat = 0.0
        var totalCarbs = 0.0

        val items = record.items.map { item ->
            val food = allFoods[item.foodCode]
            if (food != null) {
                val portion = food.nutrientsPer100.forPortion(item.amount)

                totalEnergy += portion.energyKcal
                totalProtein += portion.proteinG
                totalFat += portion.fatG
                totalCarbs += portion.carbohydrateG

                MealDetailItemUiState(
                    id = item.id,
                    name = food.name,
                    amountLabel = "${item.amount.roundToInt()}${food.unit.symbol}",
                    nutrition = NutritionUiState.from(portion)
                )
            } else {
                MealDetailItemUiState(
                    id = item.id,
                    name = "알 수 없는 음식",
                    amountLabel = "${item.amount.roundToInt()}g",
                    nutrition = NutritionUiState.Empty
                )
            }
        }

        val typeLabel = when(record.mealType.name) {
            "BREAKFAST" -> "아침"
            "LUNCH" -> "점심"
            "DINNER" -> "저녁"
            "SNACK" -> "간식"
            else -> record.mealType.name
        }

        val totalNutrients = NutrientAmounts(totalCarbs, totalProtein, totalFat, totalEnergy)
        val detail = MealDetailUiState(
            dateLabel = record.date.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일")),
            mealLabel = typeLabel,
            nutrition = NutritionUiState.from(totalNutrients),
            items = items
        )

        _state.update { it.copy(selectedDetail = detail) }
    }
}
