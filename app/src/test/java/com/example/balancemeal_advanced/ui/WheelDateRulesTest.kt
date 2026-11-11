package com.example.balancemeal_advanced.ui

import com.example.balancemeal_advanced.ui.components.DatePickerMode
import com.example.balancemeal_advanced.ui.components.WheelDateRules
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WheelDateRulesTest {
    private val today = LocalDate.of(2026, 11, 11)

    @Test fun modesUseRequiredYearRanges() {
        assertEquals(2025..2026, WheelDateRules.years(DatePickerMode.MEAL, today))
        assertEquals(1930..2026, WheelDateRules.years(DatePickerMode.BIRTH, today))
    }
    @Test fun initialDatesClampToSupportedDates() {
        assertEquals(LocalDate.of(2025, 1, 1), WheelDateRules.clamp(LocalDate.of(1990, 6, 2), DatePickerMode.MEAL, today))
        assertEquals(LocalDate.of(1930, 1, 1), WheelDateRules.clamp(LocalDate.of(1900, 6, 2), DatePickerMode.BIRTH, today))
        assertEquals(today, WheelDateRules.clamp(today.plusDays(1), DatePickerMode.MEAL, today))
        assertEquals(today, WheelDateRules.clamp(today.plusYears(1), DatePickerMode.BIRTH, today))
    }
    @Test fun shortMonthsClampDayWithoutOverflowingIntoNextMonth() {
        assertEquals(LocalDate.of(2025, 2, 28), WheelDateRules.change(LocalDate.of(2025, 1, 31), DatePickerMode.MEAL, today, month = 2))
        assertEquals(LocalDate.of(2025, 4, 30), WheelDateRules.change(LocalDate.of(2025, 3, 31), DatePickerMode.MEAL, today, month = 4))
    }
    @Test fun leapDayChangesSafelyAcrossYears() {
        val leap = LocalDate.of(2000, 2, 29)
        assertEquals(LocalDate.of(2001, 2, 28), WheelDateRules.change(leap, DatePickerMode.BIRTH, today, year = 2001))
        assertEquals(LocalDate.of(2004, 2, 29), WheelDateRules.change(leap, DatePickerMode.BIRTH, today, year = 2004))
        assertEquals(29, WheelDateRules.days(leap, today).last)
    }
    @Test fun currentYearAndMonthCannotOfferFutureDates() {
        assertEquals(1..11, WheelDateRules.months(today, today))
        assertEquals(1..11, WheelDateRules.days(today, today))
        assertEquals(1..12, WheelDateRules.months(today.minusYears(1), today))
        assertEquals(today, WheelDateRules.change(LocalDate.of(2025, 12, 31), DatePickerMode.MEAL, today, year = 2026))
    }
}
