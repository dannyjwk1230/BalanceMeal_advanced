package com.example.balancemeal_advanced.ui.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.balancemeal_advanced.core.model.MealRecord
import com.example.balancemeal_advanced.core.model.MealType
import com.example.balancemeal_advanced.core.model.NutrientAmounts
import com.example.balancemeal_advanced.core.time.TimeSource
import com.example.balancemeal_advanced.data.repository.FoodRepository
import com.example.balancemeal_advanced.data.repository.MealRepository
import com.example.balancemeal_advanced.data.repository.SettingsRepository
import com.example.balancemeal_advanced.ui.presentation.NutritionUiState
import com.example.balancemeal_advanced.ui.presentation.PeriodTab
import com.example.balancemeal_advanced.ui.presentation.PeriodUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AnalysisViewModel(
    private val meals: MealRepository,
    private val foods: FoodRepository,
    private val settings: SettingsRepository,
    private val timeSource: TimeSource,
) : ViewModel() {
    private val _state = MutableStateFlow(
        AnalysisUiState(
            period = PeriodUiState(
                selected = PeriodTab.MONTH,
                rangeLabel = "",
                description = "종료일을 입력해주세요"
            )
        )
    )
    val state: StateFlow<AnalysisUiState> = _state.asStateFlow()

    private var currentStart: LocalDate = timeSource.now().toLocalDate().minusMonths(1)
    var currentEnd: LocalDate = timeSource.now().toLocalDate()
        private set

    init {
        updateDateRange(currentStart, currentEnd)
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            settings.bodySettings.collectLatest { bodySettings ->
                val hasBodySettings = bodySettings != null
                _state.update { it.copy(showBodySettingsCta = !hasBodySettings) }
            }
        }

        viewModelScope.launch {
            meals.observeBetweenDates(currentStart, currentEnd).collectLatest { records ->
                buildUiState(records)
            }
        }
    }

    private suspend fun buildUiState(records: List<MealRecord>) {
        if (records.isEmpty()) {
            _state.update {
                it.copy(
                    average = NutritionUiState.Empty,
                    averageBasis = "기록된 식단이 없습니다.",
                    periodEnergyLabel = "0",
                    commentTitle = "기록이 부족합니다",
                    commentBody = "선택한 기간 동안 기록된 식단이 없습니다. 식단을 기록하고 영양 밸런스를 확인해보세요.",
                    meals = listOf("조식", "중식", "석식", "간식·음료").map { label ->
                        AnalysisMealUiState(label, NutritionUiState.Empty, "0")
                    }
                )
            }
            return
        }

        val allFoods = foods.getAll().associateBy { it.code }
        val daysWithRecords = records.map { it.date }.distinct().size

        var totalEnergy = 0.0
        var totalCarbs = 0.0
        var totalProtein = 0.0
        var totalFat = 0.0

        val mealTypeTotals = mutableMapOf<MealType, NutrientAmounts>()
        val mealTypeCounts = mutableMapOf<MealType, Int>()

        records.forEach { record ->
            var recordEnergy = 0.0
            var recordCarbs = 0.0
            var recordProtein = 0.0
            var recordFat = 0.0

            record.items.forEach { item ->
                val food = allFoods[item.foodCode]
                if (food != null) {
                    val portion = food.nutrientsPer100.forPortion(item.amount)
                    recordEnergy += portion.energyKcal
                    recordCarbs += portion.carbohydrateG
                    recordProtein += portion.proteinG
                    recordFat += portion.fatG
                }
            }

            totalEnergy += recordEnergy
            totalCarbs += recordCarbs
            totalProtein += recordProtein
            totalFat += recordFat

            val typeAmount = NutrientAmounts(recordCarbs, recordProtein, recordFat, recordEnergy)
            mealTypeTotals[record.mealType] = (mealTypeTotals[record.mealType] ?: NutrientAmounts.ZERO) + typeAmount
            mealTypeCounts[record.mealType] = (mealTypeCounts[record.mealType] ?: 0) + 1
        }

        val avgAmounts = NutrientAmounts(
            totalCarbs / daysWithRecords,
            totalProtein / daysWithRecords,
            totalFat / daysWithRecords,
            totalEnergy / daysWithRecords
        )

        val formatter = DecimalFormat("#,###")

        val meals = MealType.entries.map { type ->
            val label = when (type) {
                MealType.BREAKFAST -> "조식"
                MealType.LUNCH -> "중식"
                MealType.DINNER -> "석식"
                MealType.SNACK -> "간식·음료"
            }
            val count = mealTypeCounts[type] ?: 0
            val typeTotals = mealTypeTotals[type] ?: NutrientAmounts.ZERO
            AnalysisMealUiState(
                label = label,
                nutrition = NutritionUiState.from(typeTotals),
                recordCountLabel = count.toString()
            )
        }

        _state.update {
            it.copy(
                average = NutritionUiState.from(avgAmounts),
                averageBasis = "총 ${daysWithRecords}일간의 기록 기준",
                periodEnergyLabel = formatter.format(totalEnergy.toInt()),
                commentTitle = "잘 기록하고 계시네요!",
                commentBody = "선택한 기간 동안 총 ${daysWithRecords}일의 식단이 기록되었습니다. 하루 평균 약 ${formatter.format(avgAmounts.energyKcal.toInt())}kcal를 섭취하고 있습니다.",
                meals = meals
            )
        }
    }

    private fun updateDateRange(start: LocalDate, end: LocalDate, tab: PeriodTab? = null) {
        currentStart = start
        currentEnd = end

        val formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
        val label = "${start.format(formatter)} ~ ${end.format(formatter)}"
        val description = "종료일을 입력해주세요"

        _state.update {
            it.copy(
                period = it.period.copy(
                    selected = tab ?: it.period.selected,
                    rangeLabel = label,
                    description = description
                ),
                showDatePicker = false
            )
        }

        observeData()
    }

    fun handleEvent(event: AnalysisEvent) {
        when (event) {
            is AnalysisEvent.PeriodSelected -> {
                val today = timeSource.now().toLocalDate()
                val start = when (event.period) {
                    PeriodTab.DAY -> today
                    PeriodTab.WEEK -> today.minusWeeks(1)
                    PeriodTab.MONTH -> today.minusMonths(1)
                }
                updateDateRange(start, today, event.period)
            }
            is AnalysisEvent.CalendarRequested -> {
                _state.update { it.copy(showDatePicker = true) }
            }
            is AnalysisEvent.RangeSubmitted -> {
                if (event.start == event.end) {
                    val endDate = event.end
                    val (calculatedStart, tab) = when (_state.value.period.selected) {
                        PeriodTab.DAY -> endDate to PeriodTab.DAY
                        PeriodTab.WEEK -> endDate.minusWeeks(1) to PeriodTab.WEEK
                        PeriodTab.MONTH -> endDate.minusMonths(1) to PeriodTab.MONTH
                    }
                    updateDateRange(calculatedStart, endDate, tab)
                } else {
                    updateDateRange(event.start, event.end)
                }
            }
            is AnalysisEvent.BodySettingsRequested -> {
                // To be intercepted in UI to trigger navigation
            }
        }
    }

    fun dismissDatePicker() {
        _state.update { it.copy(showDatePicker = false) }
    }
}
