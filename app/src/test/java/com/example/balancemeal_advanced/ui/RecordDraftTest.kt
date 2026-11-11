package com.example.balancemeal_advanced.ui

import androidx.lifecycle.ViewModelStore
import com.example.balancemeal_advanced.core.model.*
import com.example.balancemeal_advanced.core.time.TimeSource
import com.example.balancemeal_advanced.data.food.CachedFoodRepository
import com.example.balancemeal_advanced.data.repository.*
import com.example.balancemeal_advanced.ui.record.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
class RecordDraftTest {
    private val day=LocalDate.of(2026,11,10)
    private val key=RecordKey(day,MealType.BREAKFAST)
    private class Meals : MealRepository {
        val records=mutableMapOf<RecordKey,MealRecord>()
        var slow:RecordKey?=null
        var writes=0
        override suspend fun getMeal(date:LocalDate,mealType:MealType):MealRecord? {
            val key=RecordKey(date,mealType)
            if (key==slow) withContext(NonCancellable) { delay(1000) }
            return records[key]
        }
        override suspend fun getMeal(id:String)=records.values.firstOrNull {it.id==id}
        override fun observeOnDate(date:LocalDate)=flowOf(records.values.filter {it.date==date})
        override fun observeMeal(date:LocalDate,mealType:MealType)=flowOf(records[RecordKey(date,mealType)])
        override suspend fun createMeal(date:LocalDate,mealType:MealType,items:List<MealItem>,photoReference:String?):MealRecord { writes++;error("P06 must never save") }
        override suspend fun updateMeal(record:MealRecord):MealRecord { writes++;error("P06 must never update") }
        override suspend fun deleteMeal(id:String):Boolean { writes++;error("P06 must never delete") }
    }
    private fun scenario(existing:Boolean=false,test:suspend TestScope.(RecordViewModel,Meals)->Unit)=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val foods=CachedFoodRepository(readText={ checkNotNull(javaClass.classLoader!!.getResourceAsStream("foods_sample.json")).bufferedReader().use {it.readText()} },ioDispatcher=StandardTestDispatcher(testScheduler))
        val meals=Meals()
        if (existing) meals.records[key]=MealRecord("saved",day,MealType.BREAKFAST,listOf(MealItem(foods.getAll().single {it.name=="쌀밥"}.code,150.0,"original-row")),"saved-photo")
        val vm=RecordViewModel(meals,foods,TimeSource {day.atTime(23,59)})
        val store=ViewModelStore().apply {put("record",vm)}
        try {runCurrent();test(vm,meals);assertEquals(0,meals.writes)} finally {store.clear();Dispatchers.resetMain()}
    }
    private fun add(vm:RecordViewModel,name:String,amount:String) {
        vm.choose(vm.state.value.choices.single {it.food.name==name}.food.code);vm.addAmount(amount);vm.addSelected()
    }
    @Test fun duplicateRowsHaveUniqueIdsAndDeleteIndependently()=scenario {vm,_ ->
        vm.openPicker();runCurrent();assertEquals(100,vm.state.value.choices.size)
        add(vm,"쌀밥","150");add(vm,"쌀밥","150")
        val rows=vm.state.value.rows
        assertEquals(2,rows.size);assertNotEquals(rows[0].itemId,rows[1].itemId)
        assertEquals(498.0,vm.state.value.total!!.energyKcal,1e-10)
        vm.remove(rows[0].itemId);assertEquals(rows[1].itemId,vm.state.value.rows.single().itemId)
        assertEquals(249.0,vm.state.value.total!!.energyKcal,1e-10)
    }
    @Test fun gramsAndMillilitresUseOriginalEnergyAndDecimalAmounts()=scenario {vm,_ ->
        vm.openPicker();runCurrent();add(vm,"쌀밥","150");add(vm,"커피_아메리카노","250")
        assertEquals(listOf("g","ml"),vm.state.value.rows.map {it.unit})
        val total=vm.state.value.total!!
        assertEquals(56.87,total.carbohydrateG,1e-10);assertEquals(5.615,total.proteinG,1e-10)
        assertEquals(.805,total.fatG,1e-10);assertEquals(256.5,total.energyKcal,1e-10)
        vm.setAmount(vm.state.value.rows.last().itemId,"250.5")
        assertEquals(249.0+7.515,vm.state.value.total!!.energyKcal,1e-10)
    }
    @Test fun incompleteAndInvalidAmountsDoNotMasqueradeAsTotals()=scenario {vm,_ ->
        vm.openPicker();runCurrent();add(vm,"쌀밥","150")
        val id=vm.state.value.rows.single().itemId
        for (bad in listOf("",".","1.","0","-1","NaN","Infinity","1e309")) {
            vm.setAmount(id,bad);assertNotNull(vm.state.value.rows.single().error);assertNull(vm.state.value.total);assertFalse(vm.state.value.inputValid)
        }
        vm.setAmount(id,".5");assertNotNull(vm.state.value.total)
    }
    @Test fun editsNeverMutateStoredRecordAndRestoringValuesClearsDirty()=scenario(true) {vm,meals ->
        val saved=meals.records[key]!!
        assertFalse(vm.state.value.isDirty);assertEquals(RecordAction.UPDATE,vm.state.value.action)
        vm.setAmount("original-row","175");assertTrue(vm.state.value.isDirty)
        vm.setAmount("original-row","150");assertFalse(vm.state.value.isDirty)
        vm.remove("original-row");assertEquals(RecordAction.DELETE,vm.state.value.action)
        assertEquals(saved,meals.records[key]);assertEquals(1,saved.items.size)
    }
    @Test fun foodCanSaveWithoutTimeAndFutureDatesAreRejected()=scenario {vm,_ ->
        assertFalse(vm.state.value.isDirty)
        val photoOnly=RecordDraft(photoReference="internal-photo")
        assertEquals(RecordAction.SAVE,recordAction(null,photoOnly));assertFalse(RecordUiState(key,draft=photoOnly).persistenceEnabled)
        vm.openPicker();runCurrent();add(vm,"쌀밥","80")
        assertTrue(vm.state.value.inputValid);assertTrue(vm.state.value.persistenceEnabled)
        vm.requestSelection(RecordSelection.Date(day.plusDays(1)));vm.confirmDiscard();runCurrent()
        vm.openPicker();runCurrent();add(vm,"쌀밥","80")
        assertNotNull(vm.state.value.dateError);assertFalse(vm.state.value.persistenceEnabled)
    }
    @Test fun slowOldLoadCannotOverwriteNewDateAndMealAndDateResetsBreakfast()=scenario {vm,meals ->
        meals.slow=key.copy(type=MealType.LUNCH)
        vm.requestSelection(RecordSelection.Meal(MealType.LUNCH));runCurrent()
        val other=day.minusDays(1)
        vm.requestSelection(RecordSelection.Date(other));runCurrent()
        vm.requestSelection(RecordSelection.Meal(MealType.DINNER));advanceUntilIdle()
        assertEquals(RecordKey(other,MealType.DINNER),vm.state.value.key)
        assertFalse(vm.state.value.loading);assertNull(vm.state.value.original)
        vm.requestSelection(RecordSelection.Date(day));runCurrent()
        assertEquals(key,vm.state.value.key)
    }
    @Test fun searchUsesFoodRepositoryAndDraftSurvivesSameViewModelStore()=scenario {vm,_ ->
        vm.openPicker();runCurrent();vm.search("아메리카노");runCurrent()
        assertEquals(1,vm.state.value.choices.size);assertTrue(vm.state.value.choices.single().referenceLabel.startsWith("100ml"))
        add(vm,"커피_아메리카노","250.5")
        val retained=ViewModelStore().apply {put("record",vm)}
        assertSame(vm,retained["record"])
        assertEquals("250.5",vm.state.value.draft.foods.single().amountInput)
        // A real Activity recreation is covered separately by the device test, not claimed here.
    }
}
