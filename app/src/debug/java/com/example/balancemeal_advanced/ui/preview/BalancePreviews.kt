package com.example.balancemeal_advanced.ui.preview

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.balancemeal_advanced.core.model.NutrientAmounts
import com.example.balancemeal_advanced.ui.AppNavigation
import com.example.balancemeal_advanced.ui.AppTopBar
import com.example.balancemeal_advanced.ui.analysis.*
import com.example.balancemeal_advanced.ui.components.*
import com.example.balancemeal_advanced.ui.meallist.*
import com.example.balancemeal_advanced.ui.navigation.AppDestination
import com.example.balancemeal_advanced.ui.presentation.*
import com.example.balancemeal_advanced.ui.theme.BalanceMeal_advancedTheme
import java.time.LocalDate

// DEBUG/PREVIEW ONLY. No FoodRepository, MealRecord, storage writes, or runtime NavHost references.
private fun nutrition(c: Double, p: Double, f: Double, kcal: Double) =
    NutritionUiState.from(NutrientAmounts(c, p, f, kcal))
private fun period() = PeriodUiState(
    PeriodTab.WEEK, "2025.05.08 ~ 2025.05.14", "종료일을 입력해주세요",
)

@Composable
internal fun PreviewFrame(destination: AppDestination, content: @Composable () -> Unit) {
    BalanceMeal_advancedTheme {
      BoxWithConstraints {
        val rail=AppWindowClass.fromWidth(maxWidth)!=AppWindowClass.COMPACT
        CompositionLocalProvider(LocalAppWindowWidth provides maxWidth) {
        Scaffold(
            topBar = { AppTopBar({}) },
            bottomBar = { if(!rail)AppNavigation(destination, false) {} },
        ) { padding -> Row(Modifier.fillMaxSize().padding(padding).balanceBackground()) {
            if(rail)AppNavigation(destination,true) {}
            Box(Modifier.weight(1f).fillMaxHeight()) {content()}
        } }
        }
      }
    }
}

@Preview(name = "목록 - 표시 전용 초기 상태", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
fun InitialMealListPreview() = PreviewFrame(AppDestination.LIST) { MealListScreen(MealListUiState(), {}) }

@Preview(name = "목록 - 날짜·빈 날짜·펼친 끼니", widthDp = 390, heightDp = 1415, showBackground = true)
@Composable
fun FilledMealListPreview() {
    val state = MealListUiState(
        period = period().copy(description = "최근 7일 · 선택 범위"),
        content = MealListContent.DATES,
        dates = listOf(
            MealDateUiState(
                LocalDate.of(2025, 5, 12), "2025년 5월 12일 (월)", "3끼 기록", "1,820",
                listOf(MealRowUiState("preview-1", "중식","닭가슴살 샐러드 & 현미밥", "750", "탄 82g · 단 48g · 지 21g", true)),
            ),
            MealDateUiState(LocalDate.of(2025, 5, 13), "2025년 5월 13일 (화)", "0끼 기록", "0"),
            MealDateUiState(
                LocalDate.of(2025, 5, 14), "2025년 5월 14일 (수)", "오늘 · 3끼 기록", "1,450",
                listOf(
                    MealRowUiState("preview-2", "조식","아보카도 통밀 토스트 & 스크램블", "450", "탄 34g · 단 22g · 지 16g", true),
                    MealRowUiState("preview-3", "중식","소고기 야채 비빔밥", "680", "탄 78g · 단 36g · 지 18g", true),
                    MealRowUiState("preview-4", "간식·음료","아메리카노 & 삶은 달걀", "320", "탄 10g · 단 8g · 지 4g"),
                ),
            ),
        ),
    )
    PreviewFrame(AppDestination.LIST) { MealListScreen(state, {}) }
}

@Preview(name = "목록 - 전체 빈 상태", widthDp = 390, heightDp = 844)
@Composable
fun EmptyMealListPreview() = PreviewFrame(AppDestination.LIST) {
    MealListScreen(MealListUiState(period = period(), content = MealListContent.EMPTY), {})
}

@Preview(name = "분석 - 표시 전용 초기 상태", widthDp = 390, heightDp = 844)
@Composable
fun InitialAnalysisPreview() = PreviewFrame(AppDestination.ANALYSIS) { AnalysisScreen(AnalysisUiState(), {}) }

@Preview(name = "분석 A - 원형·평균·기간·네 끼니", widthDp = 390, heightDp = 1729, showBackground = true)
@Composable
fun FilledAnalysisPreview() {
    val state = AnalysisUiState(
        period = period(), average = nutrition(180.0, 70.0, 50.0, 1450.0),
        averageBasis = "선택 기간 7일 중 4일 기록 기준 하루 평균",
        commentTitle = "칼로리가 목표보다 부족해요.",
        commentBody = "탄수화물 섭취에 신경 써보세요.",
        periodEnergyLabel = "5,800",
        meals = listOf(
            AnalysisMealUiState("조식", nutrition(180.0, 50.0, 35.0, 1400.0), "4"),
            AnalysisMealUiState("중식", nutrition(300.0, 120.0, 80.0, 2400.0), "4"),
            AnalysisMealUiState("석식", nutrition(150.0, 80.0, 60.0, 1300.0), "3"),
            AnalysisMealUiState("간식·음료", nutrition(90.0, 30.0, 25.0, 700.0), "3"),
        ),
    )
    PreviewFrame(AppDestination.ANALYSIS) { AnalysisScreen(state, {}) }
}

@Preview(name = "분석 - 미기록·빈 원형", widthDp = 390, heightDp = 844)
@Composable
fun EmptyAnalysisPreview() = PreviewFrame(AppDestination.ANALYSIS) {
    AnalysisScreen(AnalysisUiState(
        period = period(), average = NutritionUiState.Empty, averageBasis = "기록된 날짜 없음",
        commentTitle = "선택한 기간에 기록된 식단이 없습니다.",
        commentBody = "신체 정보를 설정하고 식단을 기록해보세요.",
        showBodySettingsCta = true, periodEnergyLabel = "0",
        meals = listOf("조식", "중식", "석식", "간식·음료").map { AnalysisMealUiState(it, NutritionUiState.Empty, "0") },
    ), {})
}

@Preview(name = "공통 - 기간 표시·달력 진입 버튼", widthDp = 390, heightDp = 220)
@Composable
fun PeriodControlPreview() = BalanceMeal_advancedTheme { PeriodSelector(period(), {}, {}) }

@Preview(name = "상세 - 확보된 cc552 화면 참고 전용", widthDp = 390, heightDp = 1700)
@Composable
fun DetailReferencePreview() = PreviewFrame(AppDestination.LIST) {
    MealDetailReference(MealDetailUiState(
        "2025년 5월 14일 (수)", "조식",
        nutrition(38.0, 20.3, 19.1, 467.0),
        listOf(
            MealDetailItemUiState("preview-detail-1", "통밀 토스트", "80g", nutrition(30.0, 7.0, 2.0, 210.0)),
            MealDetailItemUiState("preview-detail-2", "스크램블 에그", "100g", nutrition(1.0, 12.0, 10.0, 150.0)),
            MealDetailItemUiState("preview-detail-3", "아보카도 슬라이스", "40g", nutrition(3.0, 1.0, 7.0, 90.0)),
            MealDetailItemUiState("preview-detail-4", "블루베리", "30g", nutrition(4.0, .3, .1, 17.0)),
        ),
    ))
}

@Preview(name = "공통 - 확인 모달", widthDp = 390, heightDp = 844)
@Composable
fun ConfirmationPreview() = PreviewFrame(AppDestination.RECORD) {
    CommonConfirmationDialog(
        "작성 중인 식단이 있습니다",
        "저장하지 않은 내용이 있습니다. 이동하면 작성 중인 내용이 삭제됩니다.",
        "이동하기", {}, {}, "계속 작성",
    )
}

