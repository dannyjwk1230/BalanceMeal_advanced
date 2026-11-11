package com.example.balancemeal_advanced.ui

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.balancemeal_advanced.core.model.NutrientAmounts
import com.example.balancemeal_advanced.ui.home.*
import com.example.balancemeal_advanced.ui.navigation.AppDestination
import com.example.balancemeal_advanced.ui.presentation.NutritionUiState
import com.example.balancemeal_advanced.ui.preview.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Real-density edge pulls exercise the texture stretch path missed by static tall captures. */
@RunWith(AndroidJUnit4::class)
class P09OverscrollTest {
    @get:Rule val rule = createComposeRule()

    @Test(timeout = 120000)
    fun edgePullsKeepChartCleanAndAllFiveScreensScrollable() {
        wakeP09Device()
        var screen by mutableStateOf("home-empty")
        rule.setContent {
            when (screen) {
                "home-empty" -> PreviewFrame(AppDestination.HOME) {
                    val zero = NutrientAmounts(0.0, 0.0, 0.0, 0.0)
                    HomeScreen(HomeUiState(
                        LocalDate.of(2026, 11, 11), loading = false, total = zero,
                        nutrition = NutritionUiState.from(zero), showAddMeal = true,
                        mainComment = "오늘의 첫 식단을 기록하고 피드백을 받아보세요.",
                    ), {}, {}, {})
                }
                "home" -> ReferenceHomePreview()
                "record" -> ReferenceRecordPreview()
                "list" -> FilledMealListPreview()
                "analysis" -> FilledAnalysisPreview()
                else -> ReferenceMyPreview()
            }
        }
        for (name in listOf("home-empty", "home", "record", "list", "analysis", "my")) {
            rule.runOnIdle { screen = name }
            val destination = if (name == "home-empty") "HOME" else name.uppercase()
            val page = rule.onNodeWithTag("destination_$destination")
            page.assertIsDisplayed()
            fun edge(bottom: Boolean) {
                page.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, if (bottom) 100000f else -100000f) }
                rule.waitForIdle()
                repeat(3) {
                    page.performTouchInput {
                        if (bottom) swipeUp(durationMillis = 250) else swipeDown(durationMillis = 250)
                    }
                }
                rule.waitForIdle()
                val range = page.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
                if (bottom && name in listOf("record", "my"))
                    assertTrue("$name must still scroll through its full content", range.maxValue() > 0f)
                assertEquals("$name edge=$bottom", if (bottom) range.maxValue() else 0f, range.value(), 1f)
                capture(rule, "$name-${if (bottom) "bottom" else "top"}", "p09-overscroll")
            }
            edge(false)
            if (name == "home-empty") {
                // This region contains only pale card/ring pixels; the reported dark polygon
                // crossed it. Exclude the center labels and check actual rendered pixels.
                val pixels = rule.onNodeWithContentDescription("영양소 합계 0, 빈 차트")
                    .captureToImage().toPixelMap()
                for (y in pixels.height / 10 until pixels.height / 3)
                    for (x in pixels.width / 20 until pixels.width * 2 / 5) {
                        val color = pixels[x, y]
                        assertTrue("Unexpected dark chart fragment at $x,$y", minOf(color.red, color.green, color.blue) > .6f)
                    }
            }
            edge(true)
            edge(false)
        }
    }
}
