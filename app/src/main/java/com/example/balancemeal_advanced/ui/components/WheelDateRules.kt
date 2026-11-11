package com.example.balancemeal_advanced.ui.components

import java.time.LocalDate
import java.time.YearMonth

enum class DatePickerMode { MEAL, BIRTH }

internal object WheelDateRules {
    fun years(mode: DatePickerMode, today: LocalDate): IntRange =
        (if (mode == DatePickerMode.MEAL) today.year - 1 else 1930)..today.year

    fun clamp(date: LocalDate, mode: DatePickerMode, today: LocalDate): LocalDate =
        date.coerceIn(LocalDate.of(years(mode, today).first, 1, 1), today)

    fun change(date: LocalDate, mode: DatePickerMode, today: LocalDate,
        year: Int = date.year, month: Int = date.monthValue, day: Int = date.dayOfMonth): LocalDate {
        val validYear = year.coerceIn(years(mode, today))
        val validMonth = month.coerceIn(1, 12)
        val validDay = day.coerceIn(1, YearMonth.of(validYear, validMonth).lengthOfMonth())
        return clamp(LocalDate.of(validYear, validMonth, validDay), mode, today)
    }

    fun months(date: LocalDate, today: LocalDate): IntRange =
        1..if (date.year == today.year) today.monthValue else 12

    fun days(date: LocalDate, today: LocalDate): IntRange =
        1..if (YearMonth.from(date) == YearMonth.from(today)) today.dayOfMonth else date.lengthOfMonth()
}
