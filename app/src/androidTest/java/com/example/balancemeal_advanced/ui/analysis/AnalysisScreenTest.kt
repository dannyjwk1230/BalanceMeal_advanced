package com.example.balancemeal_advanced.ui.analysis

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalysisScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

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