package com.example.balancemeal_advanced.ui.analysis

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.balancemeal_advanced.core.model.NutrientAmounts
import com.example.balancemeal_advanced.ui.presentation.NutritionUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalysisScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersAverageNutritionAndMealTypeBreakdown() {
        val state = AnalysisUiState(
            averageBasis = "총 10일간의 기록 기준",
            average = NutritionUiState.from(NutrientAmounts(150.0, 50.0, 30.0, 1200.0)),
            commentTitle = "영양 밸런스 완벽",
            commentBody = "탄수화물, 단백질, 지방 비율이 좋습니다.",
            periodEnergyLabel = "12,000",
            meals = listOf(
                AnalysisMealUiState("조식", NutritionUiState.from(NutrientAmounts(50.0, 20.0, 10.0, 400.0)), "10")
            ),
            showBodySettingsCta = false
        )

        composeTestRule.setContent {
            AnalysisScreen(state = state, onEvent = {})
        }

        composeTestRule.onNodeWithText("총 10일간의 기록 기준").assertIsDisplayed()
        composeTestRule.onNodeWithText("영양 밸런스 완벽").assertIsDisplayed()
        composeTestRule.onNodeWithText("기간 총 섭취 칼로리").assertIsDisplayed()
        composeTestRule.onNodeWithText("12,000", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("조식 · 400 kcal").assertIsDisplayed()
        composeTestRule.onNodeWithText("기간 합계 · 기록 10회").assertIsDisplayed()
        // No Body CTA should be present
        composeTestRule.onNodeWithText("신체 정보 입력하기").assertDoesNotExist()
    }

    @Test
    fun bodySettingsCtaClickFiresEvent() {
        val state = AnalysisUiState(
            showBodySettingsCta = true
        )
        var capturedEvent: AnalysisEvent? = null

        composeTestRule.setContent {
            AnalysisScreen(state = state, onEvent = { capturedEvent = it })
        }

        composeTestRule.onNodeWithText("신체 정보 입력하기").performClick()

        assert(capturedEvent is AnalysisEvent.BodySettingsRequested)
    }
}