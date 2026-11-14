package com.example.balancemeal_advanced.ui.analysis

import com.example.balancemeal_advanced.core.model.*
import com.example.balancemeal_advanced.core.time.TimeSource
import com.example.balancemeal_advanced.data.repository.*
import com.example.balancemeal_advanced.ui.presentation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisViewModelTest {
    private class Meals : MealRepository {
        val records = MutableStateFlow<List<MealRecord>>(emptyList())
        override fun observeOnDate(date: LocalDate) = flow { emitAll(records.map { it.filter { r -> r.date == date } }) }
        override fun observeBetweenDates(start: LocalDate, end: LocalDate) = flow { emitAll(records.map { it.filter { r -> !r.date.isBefore(start) && !r.date.isAfter(end) } }) }
        override fun observeMeal(date: LocalDate, mealType: MealType) = flowOf<MealRecord?>(null)
        override suspend fun getMeal(date: LocalDate, mealType: MealType) = records.value.firstOrNull { it.date == date && it.mealType == mealType }
        override suspend fun getMeal(id: String) = records.value.firstOrNull { it.id == id }
        override suspend fun createMeal(date: LocalDate, mealType: MealType, items: List<MealItem>, photoReference: String?) = MealRecord("id_${System.currentTimeMillis()}", date, mealType, items).also { records.value += it }
        override suspend fun updateMeal(record: MealRecord) = record.also { records.value = records.value.filterNot { it.id == record.id } + record }
        override suspend fun deleteMeal(id: String): Boolean { records.value = records.value.filterNot { it.id == id }; return true }
    }

    private class Settings : SettingsRepository {
        override val profile = flowOf(LocalProfile())
        override val bodySettings = MutableStateFlow<BodySettings?>(null)
        override suspend fun updateNickname(nickname: String) {}
        override suspend fun updateProfilePhoto(photoReference: String?) {}
        override suspend fun saveBodySettings(settings: BodySettings) { bodySettings.value = settings }
    }

    private fun scenario(block: suspend TestScope.(AnalysisViewModel, Meals, Settings, TimeSource) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val now = LocalDateTime.of(2026, 11, 11, 12, 0)
        val time = TimeSource { now }
        val apple = Food("apple", "Apple", FoodUnit.G, NutrientAmounts(10.0, 0.0, 0.0, 50.0))
        val chicken = Food("chicken", "Chicken", FoodUnit.G, NutrientAmounts(0.0, 30.0, 5.0, 150.0))
        val foods = object : FoodRepository {
            override suspend fun getAll() = listOf(apple, chicken)
            override suspend fun getByCode(code: String) = getAll().firstOrNull { it.code == code }
            override suspend fun searchByName(query: String) = getAll().filter { it.name.contains(query) }
        }
        val meals = Meals()
        val settings = Settings()
        val vm = AnalysisViewModel(meals, foods, settings, time)
        try { block(vm, meals, settings, time) } finally { runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun handlesEmptyStateCorrectly() = scenario { vm, _, _, _ ->
        val sub = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
        runCurrent()
        assertEquals(PeriodTab.MONTH, vm.state.value.period.selected)
        assertEquals("기록된 식단이 없습니다.", vm.state.value.averageBasis)
        assertEquals("기록이 부족합니다", vm.state.value.commentTitle)
        assertTrue(vm.state.value.showBodySettingsCta) // No settings configured yet
        sub.cancel()
    }

    @Test fun calculatesAveragesAcrossMultipleDays() = scenario { vm, meals, _, time ->
        val sub = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
        val today = time.now().toLocalDate()
        // Day 1: 300 kcal (chicken 200g)
        meals.createMeal(today, MealType.LUNCH, listOf(MealItem("chicken", 200.0)))
        // Day 2: 100 kcal (apple 200g)
        meals.createMeal(today.minusDays(1), MealType.BREAKFAST, listOf(MealItem("apple", 200.0)))
        runCurrent()

        // Total = 400 kcal, Days = 2 => Avg = 200 kcal
        assertEquals("400", vm.state.value.periodEnergyLabel)
        assertEquals("총 2일간의 기록 기준", vm.state.value.averageBasis)
        assertEquals("200", vm.state.value.average.energyKcal)
        assertTrue(vm.state.value.commentBody.contains("하루 평균 약 200kcal"))
        sub.cancel()
    }

    @Test fun calculatesMealTypeBreakdown() = scenario { vm, meals, _, time ->
        val sub = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
        val today = time.now().toLocalDate()
        // 2 breakfasts over the month
        meals.createMeal(today, MealType.BREAKFAST, listOf(MealItem("apple", 100.0))) // 50 kcal
        meals.createMeal(today.minusDays(5), MealType.BREAKFAST, listOf(MealItem("apple", 200.0))) // 100 kcal
        // 1 lunch
        meals.createMeal(today, MealType.LUNCH, listOf(MealItem("chicken", 100.0))) // 150 kcal

        runCurrent()
        val breakfastStat = vm.state.value.meals.find { it.label == "조식" }!!
        val lunchStat = vm.state.value.meals.find { it.label == "중식" }!!
        val dinnerStat = vm.state.value.meals.find { it.label == "석식" }!!

        assertEquals("2", breakfastStat.recordCountLabel)
        assertEquals("150", breakfastStat.nutrition.energyKcal)

        assertEquals("1", lunchStat.recordCountLabel)
        assertEquals("150", lunchStat.nutrition.energyKcal)

        assertEquals("0", dinnerStat.recordCountLabel)
        assertEquals("0", dinnerStat.nutrition.energyKcal)
        sub.cancel()
    }

    @Test fun bodySettingsCtaHidesWhenSettingsExist() = scenario { vm, _, settings, _ ->
        val sub = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
        runCurrent()
        assertTrue(vm.state.value.showBodySettingsCta)

        settings.saveBodySettings(BodySettings(LocalDate.of(1990, 1, 1), Sex.MALE, 175.0, 70.0, ActivityLevel.ACTIVE))
        runCurrent()
        assertFalse(vm.state.value.showBodySettingsCta)
        sub.cancel()
    }

    @Test fun changesPeriodAndRefiltersRecords() = scenario { vm, meals, _, time ->
        val sub = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
        val today = time.now().toLocalDate()
        meals.createMeal(today.minusDays(20), MealType.LUNCH, listOf(MealItem("chicken", 100.0))) // Outside of WEEK range
        meals.createMeal(today, MealType.DINNER, listOf(MealItem("chicken", 100.0))) // Inside WEEK range
        runCurrent()

        assertEquals("총 2일간의 기록 기준", vm.state.value.averageBasis)

        vm.handleEvent(AnalysisEvent.PeriodSelected(PeriodTab.WEEK))
        runCurrent()

        assertEquals("총 1일간의 기록 기준", vm.state.value.averageBasis)
        sub.cancel()
    }

    @Test fun calendarRequestedShowsDatePicker() = scenario { vm, _, _, _ ->
        val sub = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
        runCurrent()
        assertFalse(vm.state.value.showDatePicker)
        vm.handleEvent(AnalysisEvent.CalendarRequested)
        runCurrent()
        assertTrue(vm.state.value.showDatePicker)
        sub.cancel()
    }
}