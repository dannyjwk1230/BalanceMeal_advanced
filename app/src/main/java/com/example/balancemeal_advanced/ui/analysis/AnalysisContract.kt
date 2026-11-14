package com.example.balancemeal_advanced.ui.analysis

import com.example.balancemeal_advanced.ui.presentation.NutritionUiState
import com.example.balancemeal_advanced.ui.presentation.PeriodTab
import com.example.balancemeal_advanced.ui.presentation.PeriodUiState
import java.time.LocalDate

data class AnalysisMealUiState(
    val label: String,
    val nutrition: NutritionUiState = NutritionUiState.Initial,
    val recordCountLabel: String = "—",
)
data class AnalysisUiState(
    val period: PeriodUiState = PeriodUiState(),
    val average: NutritionUiState = NutritionUiState.Initial,
    val averageBasis: String = "기간을 선택해 영양 밸런스를 확인해보세요.",
    val commentTitle: String = "영양 밸런스",
    val commentBody: String = "하루 평균 섭취량과 영양소 구성을 확인해보세요.",
    val showBodySettingsCta: Boolean = false,
    val periodEnergyLabel: String = "—",
    val meals: List<AnalysisMealUiState> = listOf("조식", "중식", "석식", "간식·음료").map { AnalysisMealUiState(it) },
    val bodyNotice: String = "현재 저장된 신체 정보를 기준으로 평가합니다. 신체 정보가 변경되면 목표 섭취량과 분석 코멘트가 달라질 수 있습니다.",
    val showDatePicker: Boolean = false,
)
sealed interface AnalysisEvent {
    data class PeriodSelected(val period: PeriodTab) : AnalysisEvent
    data object CalendarRequested : AnalysisEvent
    data class RangeSubmitted(val start: LocalDate, val end: LocalDate) : AnalysisEvent
    data object BodySettingsRequested : AnalysisEvent
}

