package com.example.balancemeal_advanced.ui

import androidx.lifecycle.ViewModelStore
import com.example.balancemeal_advanced.core.model.*
import com.example.balancemeal_advanced.core.time.TimeSource
import com.example.balancemeal_advanced.data.repository.*
import com.example.balancemeal_advanced.ui.home.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private class Meals:MealRepository {
        val records=MutableStateFlow<List<MealRecord>>(emptyList());var active=0;var fail=false
        override fun observeOnDate(date:LocalDate)=flow {active++;try {if(fail)error("injected read failure");emitAll(records.map {it.filter {it.date==date}})} finally {active--}}
        override fun observeBetweenDates(start: LocalDate, end: LocalDate) = flow { emitAll(records.map { it.filter { r -> !r.date.isBefore(start) && !r.date.isAfter(end) } }) }
        override fun observeMeal(date:LocalDate,mealType:MealType)=flowOf<MealRecord?>(null)
        override suspend fun getMeal(date:LocalDate,mealType:MealType)=records.value.firstOrNull {it.date==date && it.mealType==mealType}
        override suspend fun getMeal(id:String)=records.value.firstOrNull {it.id==id}
        override suspend fun createMeal(date:LocalDate,mealType:MealType,items:List<MealItem>,photoReference:String?)=MealRecord(mealType.name,date,mealType,items).also {records.value+=it}
        override suspend fun updateMeal(record:MealRecord)=record.also {records.value=records.value.filterNot {it.id==record.id}+record}
        override suspend fun deleteMeal(id:String):Boolean {records.value=records.value.filterNot {it.id==id};return true}
    }
    private class Settings:SettingsRepository {
        override val profile=flowOf(LocalProfile());override val bodySettings=MutableStateFlow<BodySettings?>(null)
        var writes=0;var failWrite=false
        override suspend fun updateNickname(nickname:String)=error("Home is read-only")
        override suspend fun updateProfilePhoto(photoReference:String?)=error("Home is read-only")
        override suspend fun saveBodySettings(settings:BodySettings) {if(failWrite)error("injected write failure");writes++;bodySettings.value=settings}
    }
    private fun scenario(block:suspend TestScope.(HomeViewModel,Meals,Settings,(LocalDateTime)->Unit)->Unit)=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var now=LocalDateTime.of(2026,11,11,20,30)
        val food=Food("fixture","test-only",FoodUnit.G,NutrientAmounts(50.0,30.0,20.0,450.0))
        val foods=object:FoodRepository {override suspend fun getAll()=listOf(food);override suspend fun getByCode(code:String)=food;override suspend fun searchByName(query:String)=listOf(food)}
        val meals=Meals();val settings=Settings();val vm=HomeViewModel(meals,settings,foods,TimeSource {now})
        val store=ViewModelStore().apply {put("home",vm)}
        try {block(vm,meals,settings,{now=it})} finally {store.clear();runCurrent();Dispatchers.resetMain()}
    }
    @Test fun observesConfirmedCreateUpdateDeleteAndBodyWithoutChangingIntake()=scenario {vm,meals,settings,_ ->
        val subscription=backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)){vm.state.collect()};runCurrent()
        assertFalse(vm.state.value.hasRecords)
        val day=vm.state.value.date
        val saved=meals.createMeal(day,MealType.SNACK,listOf(MealItem("fixture",100.0)),null);runCurrent()
        assertEquals(450.0,vm.state.value.total!!.energyKcal,0.0);assertTrue(vm.state.value.needsBody)
        settings.saveBodySettings(BodySettings(day.minusYears(30),Sex.MALE,175.0,70.0,ActivityLevel.LOW_ACTIVE));runCurrent()
        assertFalse(vm.state.value.needsBody);assertEquals(2660.48,vm.state.value.targets!!.energyKcal,1e-8)
        assertEquals(450.0,vm.state.value.total!!.energyKcal,0.0)
        meals.updateMeal(saved.copy(items=listOf(saved.items.single().copy(amount=50.0))));runCurrent();assertEquals(225.0,vm.state.value.total!!.energyKcal,0.0)
        meals.deleteMeal(saved.id);runCurrent();assertEquals(0.0,vm.state.value.total!!.energyKcal,0.0);assertEquals(HomeRules.FIRST_MEAL,vm.state.value.mainComment)
        subscription.cancel();runCurrent();assertEquals(0,meals.active)
    }
    @Test fun midnightGreetingBoundariesAndResumeReadCurrentLocalClock()=scenario {vm,meals,_,setNow ->
        setNow(LocalDateTime.of(2026,11,11,23,59,59))
        var subscription=backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)){vm.state.collect()};runCurrent()
        meals.createMeal(vm.state.value.date,MealType.BREAKFAST,listOf(MealItem("fixture",100.0)),null);runCurrent();assertTrue(vm.state.value.hasRecords)
        setNow(LocalDateTime.of(2026,11,12,0,0));advanceTimeBy(1000);runCurrent()
        assertEquals(LocalDate.of(2026,11,12),vm.state.value.date);assertFalse(vm.state.value.hasRecords)
        for(t in listOf("05:59","06:00","10:59","11:00","16:59","17:00")) {
            setNow(LocalDate.of(2026,11,12).atTime(LocalTime.parse(t)));advanceTimeBy(60_000);runCurrent()
            assertEquals(HomeRules.greeting(LocalTime.parse(t)),vm.state.value.subComment)
        }
        subscription.cancel();runCurrent();assertEquals(0,meals.active)
        setNow(LocalDateTime.of(2026,11,13,7,0));subscription=backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)){vm.state.collect()};runCurrent()
        assertEquals(LocalDate.of(2026,11,13),vm.state.value.date);assertEquals(HomeRules.greeting(LocalTime.of(7,0)),vm.state.value.subComment)
        subscription.cancel()
    }
    @Test fun directActivationPreservesBodyAndIntakeAndBlocksDuplicateTaps()=scenario {vm,meals,settings,_ ->
        val body=BodySettings(LocalDate.of(1996,11,11),Sex.MALE,175.0,70.0,ActivityLevel.LOW_ACTIVE,calculationEnabled=false)
        settings.bodySettings.value=body
        val subscription=backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)){vm.state.collect()};runCurrent()
        meals.createMeal(vm.state.value.date,MealType.SNACK,listOf(MealItem("fixture",100.0)),null);runCurrent()
        val intake=vm.state.value.total
        vm.enableBodyCalculation();vm.enableBodyCalculation();runCurrent()
        assertEquals(body.copy(calculationEnabled=true),settings.bodySettings.value)
        assertEquals(1,settings.writes)
        assertEquals(intake,vm.state.value.total)
        assertNotNull(vm.state.value.targets)
        assertFalse(vm.state.value.activatingBody)
        subscription.cancel()
    }
    @Test fun failedActivationPreservesDisabledStateAndCanRetry()=scenario {vm,_,settings,_ ->
        val body=BodySettings(LocalDate.of(1996,11,11),Sex.MALE,175.0,70.0,ActivityLevel.LOW_ACTIVE,calculationEnabled=false)
        settings.bodySettings.value=body;settings.failWrite=true
        val subscription=backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)){vm.state.collect()};runCurrent()
        vm.enableBodyCalculation();runCurrent()
        assertEquals(body,settings.bodySettings.value)
        assertTrue(vm.state.value.calculationDisabled)
        assertNotNull(vm.state.value.activationError)
        assertFalse(vm.state.value.activatingBody)
        settings.failWrite=false;vm.enableBodyCalculation();runCurrent()
        assertNull(vm.state.value.activationError)
        assertNotNull(vm.state.value.targets)
        subscription.cancel()
    }
    @Test fun absentBodyIsNotInventedByActivation()=scenario {vm,_,settings,_ ->
        vm.enableBodyCalculation();runCurrent()
        assertNull(settings.bodySettings.value)
        assertEquals(0,settings.writes)
    }
    @Test fun readFailureIsNotEmptySuccessAndRetryRestoresObservation()=scenario {vm,meals,_,_ ->
        meals.fail=true
        val subscription=backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)){vm.state.collect()};runCurrent()
        assertNotNull(vm.state.value.error);assertNull(vm.state.value.total);assertFalse(vm.state.value.nutrition.isKnown)
        meals.fail=false;vm.retry();runCurrent();assertNull(vm.state.value.error);assertEquals(0.0,vm.state.value.total!!.energyKcal,0.0)
        subscription.cancel()
    }
}
