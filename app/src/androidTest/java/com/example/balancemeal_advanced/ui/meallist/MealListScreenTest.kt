package com.example.balancemeal_advanced.ui.meallist

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.balancemeal_advanced.ui.presentation.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class MealListScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersEmptyStateCorrectly() {
        val state = MealListUiState(
            period = PeriodUiState(selected = PeriodTab.MONTH, rangeLabel = "2026.10.11 ~ 2026.11.11"),
            content = MealListContent.EMPTY
        )
        composeTestRule.setContent {
            MealListScreen(state = state, onEvent = {})
        }

        composeTestRule.onNodeWithText("식단 목록").assertExists()
        composeTestRule.onNodeWithText("2026.10.11 ~ 2026.11.11").assertExists()
        composeTestRule.onNodeWithText("선택한 기간에 기록된 식단이 없습니다.").assertExists()
    }

    @Test
    fun rendersDatesAndExpandsMeal() {
        val mealRow = MealRowUiState(
            mealId = "meal_1",
            label = "점심",
            foodSummary = "닭가슴살 샐러드",
            energyLabel = "350",
            nutrientSummary = "탄수화물 10g, 단백질 30g, 지방 5g",
            expanded = false
        )
        val dateUi = MealDateUiState(
            date = LocalDate.of(2026, 11, 11),
            label = "11월 11일",
            summary = "총 기록 1건",
            energyLabel = "350",
            meals = listOf(mealRow)
        )
        var capturedEvent: MealListEvent? = null

        composeTestRule.setContent {
            MealListScreen(
                state = MealListUiState(
                    content = MealListContent.DATES,
                    dates = listOf(dateUi)
                ),
                onEvent = { capturedEvent = it }
            )
        }

        composeTestRule.onNodeWithText("11월 11일").assertExists()
        composeTestRule.onNodeWithText("점심").assertExists()
        composeTestRule.onNodeWithText("350 kcal", substring = true).assertExists()

        // Trigger expansion
        composeTestRule.onNodeWithText("점심").performClick()

        assert(capturedEvent is MealListEvent.MealExpansionRequested)
        assert((capturedEvent as MealListEvent.MealExpansionRequested).mealId == "meal_1")
    }
}