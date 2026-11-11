package com.example.balancemeal_advanced.ui

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.balancemeal_advanced.ui.components.DatePickerMode
import com.example.balancemeal_advanced.ui.components.WheelDatePicker
import com.example.balancemeal_advanced.ui.theme.BalanceMeal_advancedTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class WheelDatePickerTest {
    @get:Rule val rule = createComposeRule()
    private val today = LocalDate.of(2026, 11, 11)
    private var result: String? = null
    private var calls = 0

    private fun show(mode: DatePickerMode, initial: LocalDate) {
        rule.setContent {
            var visible by remember { mutableStateOf(true) }
            BalanceMeal_advancedTheme {
                if (visible) WheelDatePicker(
                    mode = mode, initialDate = initial, today = today,
                    onSelect = { result = it; calls++ },
                    onDismissRequest = { visible = false },
                )
            }
        }
        rule.onNodeWithTag("date_picker_confirm").assertExists()
    }
    private fun select(tag: String, value: Int) {
        rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(value.toFloat())) }
        rule.waitForIdle()
    }

    @Test fun confirmsInitialDateAsIsoExactlyOnceAndCloses() {
        show(DatePickerMode.BIRTH, LocalDate.of(2022, 3, 23))
        rule.onNodeWithTag("date_picker_confirm").assertTextEquals("선택").performClick()
        rule.waitForIdle()
        assertEquals("2022-03-23", result)
        assertEquals(1, calls)
        rule.onNodeWithTag("date_picker_confirm").assertDoesNotExist()
    }

    @Test fun changingMonthAndYearClampsLeapDay() {
        show(DatePickerMode.BIRTH, LocalDate.of(2000, 1, 31))
        select("date_wheel_month", 2)
        rule.onNodeWithTag("date_wheel_day").assert(hasStateDescription("29일"))
        select("date_wheel_year", 2001)
        rule.onNodeWithTag("date_wheel_day").assert(hasStateDescription("28일"))
        rule.onNodeWithTag("date_picker_confirm").assertTextEquals("선택").performClick()
        rule.waitForIdle()
        assertEquals("2001-02-28", result)
    }

    @Test fun mealYearRangeAndFutureDateBoundsAreEnforced() {
        show(DatePickerMode.MEAL, today)
        select("date_wheel_year", 1930)
        rule.onNodeWithTag("date_wheel_year").assert(hasStateDescription("2025년"))
        select("date_wheel_month", 12)
        select("date_wheel_day", 31)
        rule.onNodeWithTag("date_wheel_day").assert(hasStateDescription("31일"))
        select("date_wheel_year", 2026)
        rule.onNodeWithTag("date_wheel_year").assert(hasStateDescription("2026년"))
        rule.onNodeWithTag("date_picker_confirm").assertTextEquals("선택").performClick()
        rule.waitForIdle()
        assertEquals(today.toString(), result)
    }

    @Test fun touchScrollChangesSelectedMonthBeforeConfirmation() {
        show(DatePickerMode.BIRTH, LocalDate.of(2022, 3, 23))
        val before = rule.onNodeWithTag("date_wheel_month").fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        rule.onNodeWithTag("date_wheel_month").performTouchInput { swipeUp(durationMillis = 600) }
        rule.waitForIdle()
        val after = rule.onNodeWithTag("date_wheel_month").fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertNotEquals(before, after)
        assertNull(result)
        rule.onNodeWithTag("date_picker_confirm").assertIsEnabled().performClick()
        rule.waitForIdle()
        assertNotEquals("2022-03-23", result)
        assertEquals(1, calls)
        LocalDate.parse(result)
    }
}
