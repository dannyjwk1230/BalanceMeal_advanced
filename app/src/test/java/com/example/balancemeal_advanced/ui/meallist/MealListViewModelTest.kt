package com.example.balancemeal_advanced.ui.meallist

import androidx.lifecycle.ViewModelStore
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
class MealListViewModelTest {
    private class Meals : MealRepository {
        val records = MutableStateFlow<List<MealRecord>>(emptyList())
        override fun observeOnDate(date: LocalDate) = flow { emitAll(records.map { it.filter { r -> r.date == date } }) }
        override fun observeBetweenDates(start: LocalDate, end: LocalDate) = flow { emitAll(records.map { it.filter { r -> !r.date.isBefore(start) && !r.date.isAfter(end) }.sortedWith(compareByDescending<MealRecord> { rec -> rec.date }.thenBy { rec -> rec.mealType.ordinal }) }) }
        override fun observeMeal(date: LocalDate, mealType: MealType) = flowOf<MealRecord?>(null)
        override suspend fun getMeal(date: LocalDate, mealType: MealType) = records.value.firstOrNull { it.date == date && it.mealType == mealType }
        override suspend fun getMeal(id: String) = records.value.firstOrNull { it.id == id }
        override suspend fun createMeal(date: LocalDate, mealType: MealType, items: List<MealItem>, photoReference: String?) = MealRecord("id_${System.currentTimeMillis()}", date, mealType, items).also { records.value += it }
        override suspend fun updateMeal(record: MealRecord) = record.also { records.value = records.value.filterNot { it.id == record.id } + record }
        override suspend fun deleteMeal(id: String): Boolean { records.value = records.value.filterNot { it.id == id }; return true }
    }

    private fun scenario(block: suspend TestScope.(MealListViewModel, Meals, TimeSource) -> Unit) = runTest {
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
        val vm = MealListViewModel(meals, foods, time)
        try { block(vm, meals, time) } finally { runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun initialStateLoadsOneMonthAndHandlesEmpty() = scenario { vm, _, _ ->
        val sub = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
        runCurrent()
        assertEquals(PeriodTab.MONTH, vm.state.value.period.selected)
        assertEquals(MealListContent.EMPTY, vm.state.value.content)
        assertTrue(vm.state.value.dates.isEmpty())
        sub.cancel()
    }

    @Test fun loadsMealRecordsAndComputesDailyEnergyAccurately() = scenario { vm, meals, time ->
        val sub = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
        val today = time.now().toLocalDate()
        // Apple 200g (100kcal) + Chicken 100g (150kcal) -> 250 kcal
        meals.createMeal(today, MealType.LUNCH, listOf(MealItem("apple", 200.0), MealItem("chicken", 100.0)))
        runCurrent()
        assertEquals(MealListContent.DATES, vm.state.value.content)
        val dateUi = vm.state.value.dates.first()
        assertEquals("250", dateUi.energyLabel)
        assertEquals(1, dateUi.meals.size)
        val mealUi = dateUi.meals.first()
        assertEquals("점심", mealUi.label)
        assertEquals("Apple 외 1개", mealUi.foodSummary)
        assertEquals("250", mealUi.energyLabel)

        // 200g apple -> C: 20
        // 100g chicken -> P: 30, F: 5
        assertTrue(mealUi.nutrientSummary.contains("탄수화물 20g"))
        assertTrue(mealUi.nutrientSummary.contains("단백질 30g"))
        assertTrue(mealUi.nutrientSummary.contains("지방 5g"))
        sub.cancel()
    }

    @Test fun changesPeriodToWeek() = scenario { vm, meals, time ->
        val sub = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
        val today = time.now().toLocalDate()
        meals.createMeal(today.minusDays(10), MealType.BREAKFAST, listOf(MealItem("apple", 100.0)))
        meals.createMeal(today.minusDays(2), MealType.LUNCH, listOf(MealItem("chicken", 100.0)))
        runCurrent()
        assertEquals(2, vm.state.value.dates.size)

        vm.handleEvent(MealListEvent.PeriodSelected(PeriodTab.WEEK))
        runCurrent()
        // Only 2 days ago meal should be visible in WEEK tab
        assertEquals(1, vm.state.value.dates.size)
        sub.cancel()
    }

    @Test fun toggleExpansionWorks() = scenario { vm, meals, time ->
        val sub = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
        val today = time.now().toLocalDate()
        val record = meals.createMeal(today, MealType.LUNCH, listOf(MealItem("apple", 100.0)))
        runCurrent()
        assertFalse(vm.state.value.dates.first().meals.first().expanded)

        vm.handleEvent(MealListEvent.MealExpansionRequested(record.id))
        runCurrent()
        assertTrue(vm.state.value.dates.first().meals.first().expanded)

        vm.handleEvent(MealListEvent.MealExpansionRequested(record.id))
        runCurrent()
        assertFalse(vm.state.value.dates.first().meals.first().expanded)
        sub.cancel()
    }

    @Test fun mealSelectionLoadsDetailCorrectly() = scenario { vm, meals, time ->
        val sub = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
        val today = time.now().toLocalDate()
        val record = meals.createMeal(today, MealType.DINNER, listOf(MealItem("chicken", 200.0)))
        runCurrent()
        assertNull(vm.state.value.selectedDetail)

        vm.handleEvent(MealListEvent.MealSelected(record.id))
        runCurrent()
        val detail = vm.state.value.selectedDetail
        assertNotNull(detail)
        assertEquals("저녁", detail!!.mealLabel)
        assertEquals("300", detail.nutrition.energyKcal)
        assertEquals(1, detail.items.size)
        val item = detail.items.first()
        assertEquals("Chicken", item.name)
        assertEquals("200g", item.amountLabel)
        sub.cancel()
    }

    @Test fun handlesUnknownFoodGracefully() = scenario { vm, meals, time ->
        val sub = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
        val record = meals.createMeal(time.now().toLocalDate(), MealType.SNACK, listOf(MealItem("unknown_food", 100.0)))
        runCurrent()
        val mealUi = vm.state.value.dates.first().meals.first()
        assertEquals("알 수 없는 음식", mealUi.foodSummary) // Missing food yields unknown status
        assertEquals("0", mealUi.energyLabel)

        vm.handleEvent(MealListEvent.MealSelected(record.id))
        runCurrent()
        val detailItem = vm.state.value.selectedDetail!!.items.first()
        assertEquals("알 수 없는 음식", detailItem.name)
        sub.cancel()
    }

    @Test fun calendarRequestedShowsDatePicker() = scenario { vm, _, _ ->
        val sub = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
        runCurrent()
        assertFalse(vm.state.value.showDatePicker)
        vm.handleEvent(MealListEvent.CalendarRequested)
        runCurrent()
        assertTrue(vm.state.value.showDatePicker)
        vm.dismissDatePicker()
        runCurrent()
        assertFalse(vm.state.value.showDatePicker)
        sub.cancel()
    }
}