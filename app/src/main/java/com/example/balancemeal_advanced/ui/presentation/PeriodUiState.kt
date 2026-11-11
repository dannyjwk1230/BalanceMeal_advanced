package com.example.balancemeal_advanced.ui.presentation

enum class PeriodTab(val label: String) { DAY("일 (1일)"), WEEK("주 (7일)"), MONTH("월 (30일)") }

data class PeriodUiState(
    val selected: PeriodTab = PeriodTab.WEEK,
    val rangeLabel: String = "기간을 선택해주세요",
    val description: String = "일 · 주 · 월",
)

