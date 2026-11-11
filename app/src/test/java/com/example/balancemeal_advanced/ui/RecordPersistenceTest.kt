package com.example.balancemeal_advanced.ui

import androidx.lifecycle.ViewModelStore
import com.example.balancemeal_advanced.core.model.*
import com.example.balancemeal_advanced.core.time.TimeSource
import com.example.balancemeal_advanced.core.validation.SaveValidation
import com.example.balancemeal_advanced.data.food.CachedFoodRepository
import com.example.balancemeal_advanced.data.image.PhotoLifecycle
import com.example.balancemeal_advanced.data.repository.*
import com.example.balancemeal_advanced.ui.record.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class RecordPersistenceTest {
    private val day=LocalDate.of(2026,11,11)
    private class Meals:MealRepository {
        val records=mutableMapOf<RecordKey,MealRecord>()
        var attempts=0;var fail=false;var gate:CompletableDeferred<Unit>?=null
        override suspend fun getMeal(date:LocalDate,mealType:MealType)=records[RecordKey(date,mealType)]
        override suspend fun getMeal(id:String)=records.values.firstOrNull {it.id==id}
        override fun observeOnDate(date:LocalDate)=flowOf(records.values.filter {it.date==date})
        override fun observeMeal(date:LocalDate,mealType:MealType)=flowOf(records[RecordKey(date,mealType)])
        private suspend fun writing(){attempts++;gate?.await();check(!fail){"Injected storage failure"}}
        override suspend fun createMeal(date:LocalDate,mealType:MealType,items:List<MealItem>,photoReference:String?):MealRecord {
            writing();val key=RecordKey(date,mealType)
            if(key in records)throw MealConflictException("duplicate key")
            return MealRecord(UUID.randomUUID().toString(),date,mealType,items.toList(),photoReference).also {records[key]=it}
        }
        override suspend fun updateMeal(record:MealRecord):MealRecord {writing();check(records.values.any {it.id==record.id});records[RecordKey(record.date,record.mealType)]=record;return record}
        override suspend fun deleteMeal(id:String):Boolean {writing();val key=records.entries.firstOrNull {it.value.id==id}?.key ?:return false;records.remove(key);return true}
    }
    private class Photos(private val meals:Meals):PhotoLifecycle {
        val leases=mutableMapOf<String,Int>();val deleted=mutableSetOf<String>();var next=0
        var profile:String?=null
        var importGate:CompletableDeferred<Unit>?=null
        private fun collect(ref:String){if((leases[ref]?:0)==0 && profile!=ref && meals.records.values.none {it.photoReference==ref}) deleted+=ref}
        override suspend fun importPhoto(uri:String):String {check(uri!="denied");return "photo-${++next}".also {retain(it);importGate?.await()}}
        override suspend fun retain(reference:String){leases[reference]=(leases[reference]?:0)+1}
        override suspend fun release(reference:String){leases[reference]=maxOf(0,(leases[reference]?:0)-1);collect(reference)}
        override suspend fun retire(reference:String){collect(reference)}
        override fun releaseLater(reference:String){leases[reference]=maxOf(0,(leases[reference]?:0)-1);collect(reference)}
        override suspend fun <T> withReference(reference:String?,write:suspend()->T):T=write()
    }
    private fun scenario(existing:Boolean=false,test:suspend TestScope.(RecordViewModel,Meals,Photos,()->Unit)->Unit)=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val foods=CachedFoodRepository(readText={javaClass.classLoader!!.getResourceAsStream("foods_sample.json")!!.bufferedReader().use {it.readText()}},ioDispatcher=StandardTestDispatcher(testScheduler))
        val meals=Meals();val photos=Photos(meals)
        var now=day.atTime(23,0)
        if(existing) meals.records[RecordKey(day,MealType.BREAKFAST)]=MealRecord("existing",day,MealType.BREAKFAST,listOf(MealItem(foods.getAll().single {it.name=="쌀밥"}.code,150.0,"old-row")),"old-photo")
        val vm=RecordViewModel(meals,foods,TimeSource{now},photos=photos)
        val store=ViewModelStore().apply{put("record",vm)}
        try{runCurrent();test(vm,meals,photos,{now=day.minusDays(1).atTime(23,0)})}finally{store.clear();Dispatchers.resetMain()}
    }
    private suspend fun TestScope.add(vm:RecordViewModel,amount:String="150") {
        vm.openPicker();runCurrent();vm.choose(vm.state.value.choices.single {it.food.name=="쌀밥"}.food.code);vm.addAmount(amount);vm.addSelected();vm.closePicker()
    }
    @Test fun confirmationCancelCreateUpdateKeepsIdsAndResetsBaseline()=scenario {vm,meals,_,_ ->
        add(vm);vm.requestPersist();assertTrue(meals.records.isEmpty());vm.dismissDialog();assertTrue(vm.state.value.isDirty)
        vm.requestPersist();vm.confirmPersist();vm.confirmPersist();runCurrent()
        val saved=meals.records.values.single();assertEquals(1,meals.attempts);assertFalse(vm.state.value.isDirty);assertEquals(saved,vm.state.value.original)
        vm.setAmount(saved.items.single().id,"175");vm.requestPersist();vm.confirmPersist();runCurrent()
        val updated=meals.records.values.single();assertEquals(saved.id,updated.id);assertEquals(saved.items.single().id,updated.items.single().id);assertEquals(175.0,updated.items.single().amount,0.0)
    }
    @Test fun invalidInputsAndFinalClockRevalidationNeverWrite()=scenario {vm,meals,_,rewind ->
        add(vm)
        for(value in listOf("0","-2","NaN","Infinity",".")) {vm.setAmount(vm.state.value.rows.single().itemId,value);vm.requestPersist();assertNull(vm.state.value.dialog)}
        vm.setAmount(vm.state.value.rows.single().itemId,"150");vm.requestPersist();rewind();vm.confirmPersist();runCurrent()
        assertEquals(0,meals.attempts);assertTrue(vm.state.value.isDirty);assertNotNull(vm.state.value.operationError);assertNull(vm.state.value.message)
    }
    @Test fun finalRowDeleteAndCancelKeepPersistenceRules()=scenario(true) {vm,meals,photos,_ ->
        vm.remove("old-row");assertEquals(1,meals.records.values.single().items.size)
        assertEquals(RecordAction.DELETE,vm.state.value.action);assertTrue(vm.state.value.persistenceEnabled)
        vm.requestPersist();vm.dismissDialog();assertEquals(1,meals.records.size);assertFalse("old-photo" in photos.deleted)
        vm.requestPersist();vm.confirmPersist();runCurrent()
        assertTrue(meals.records.isEmpty());assertNull(vm.state.value.original);assertFalse(vm.state.value.isDirty);assertTrue("old-photo" in photos.deleted)
    }
    @Test fun injectedWriteFailurePreservesDraftOriginalAndAllowsRetry()=scenario(true) {vm,meals,_,_ ->
        val saved=meals.records.values.single();vm.setAmount("old-row","200");meals.fail=true
        vm.requestPersist();vm.confirmPersist();runCurrent()
        assertEquals(saved,meals.records.values.single());assertEquals(saved,vm.state.value.original);assertEquals("200",vm.state.value.draft.foods.single().amountInput)
        assertNotNull(vm.state.value.operationError);assertTrue(vm.state.value.isDirty)
        meals.fail=false;vm.setAmount("old-row","175");vm.requestPersist();vm.confirmPersist();runCurrent();assertEquals(175.0,meals.records.values.single().items.single().amount,0.0)
    }
    @Test fun savingBlocksDoubleTapEditsAndNavigation()=scenario {vm,meals,_,_ ->
        add(vm);meals.gate=CompletableDeferred();vm.requestPersist();vm.confirmPersist();runCurrent()
        assertTrue(vm.state.value.saving);vm.requestPersist();vm.confirmPersist();vm.setAmount(vm.state.value.rows.single().itemId,"175");var allowed=true;vm.guardLeave {allowed=it};assertFalse(allowed)
        meals.gate!!.complete(Unit);runCurrent();assertEquals(1,meals.attempts);assertEquals(150.0,meals.records.values.single().items.single().amount,0.0)
    }
    @Test fun duplicateKeyConflictDoesNotOverwriteOtherSavedRecord()=scenario {vm,meals,_,_ ->
        add(vm);val other=MealRecord("other",day,MealType.BREAKFAST,listOf(MealItem(vm.state.value.draft.foods.single().food.code,80.0)))
        meals.records[RecordKey(day,MealType.BREAKFAST)]=other
        vm.requestPersist();vm.confirmPersist();runCurrent()
        assertEquals(other,meals.records.values.single());assertTrue(vm.state.value.isDirty);assertNull(vm.state.value.message)
    }
    @Test fun dirtyGuardKeepsFirstDestinationAndCancelledCallbacksNeverNavigate()=scenario(true) {vm,meals,_,_ ->
        var clean=false;vm.guardLeave {clean=it};assertTrue(clean)
        vm.setAmount(vm.state.value.rows.single().itemId,"175");var first:Boolean?=null;var second:Boolean?=null
        vm.guardLeave {first=it};vm.guardLeave {second=it};assertNull(first);assertEquals(false,second)
        vm.dismissDialog();assertEquals(false,first);vm.confirmDiscard();runCurrent();assertEquals("175",vm.state.value.draft.foods.single().amountInput)
        vm.setAmount("old-row","150");assertFalse(vm.state.value.isDirty)
        vm.remove("old-row");vm.requestSelection(RecordSelection.Meal(MealType.LUNCH));assertEquals(MealType.BREAKFAST,vm.state.value.key.type)
        vm.confirmDiscard();runCurrent();assertEquals(MealType.LUNCH,vm.state.value.key.type);assertEquals(1,meals.records.size)
    }
    @Test fun photoOnlyDisabledCancelDeniedReplacementAndDiscardAreSafe()=scenario {vm,meals,photos,_ ->
        vm.beginGallery();vm.galleryResult("one");runCurrent();val first=vm.state.value.draft.photoReference!!
        assertFalse(vm.state.value.persistenceEnabled);assertTrue(vm.state.value.isDirty)
        vm.beginGallery();vm.galleryResult(null);runCurrent();assertEquals(first,vm.state.value.draft.photoReference)
        vm.beginGallery();vm.photoLaunchFailed();runCurrent();assertEquals(first,vm.state.value.draft.photoReference)
        vm.beginGallery();vm.galleryResult("denied");runCurrent();assertEquals(first,vm.state.value.draft.photoReference)
        vm.beginGallery();vm.galleryResult("two");runCurrent();assertTrue(first in photos.deleted)
        val second=vm.state.value.draft.photoReference!!;vm.guardLeave {};vm.confirmDiscard();runCurrent();assertTrue(second in photos.deleted);assertFalse(vm.state.value.isDirty);assertTrue(meals.records.isEmpty())
    }
    @Test fun savedPhotoSurvivesDraftReplacementUntilCommitAndProfileReferenceProtectsIt()=scenario(true) {vm,meals,photos,_ ->
        vm.beginGallery();vm.galleryResult("new");runCurrent();assertFalse("old-photo" in photos.deleted)
        vm.guardLeave {};vm.confirmDiscard();runCurrent();assertEquals("old-photo",vm.state.value.draft.photoReference)
        vm.beginGallery();vm.galleryResult("next");runCurrent();photos.profile="old-photo"
        vm.requestPersist();vm.confirmPersist();runCurrent();assertEquals(vm.state.value.draft.photoReference,meals.records.values.single().photoReference);assertFalse("old-photo" in photos.deleted)
    }
    @Test fun destroyedEditorReleasesAFileWhoseImportFinishesAfterClear()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val foods=CachedFoodRepository(readText={javaClass.classLoader!!.getResourceAsStream("foods_sample.json")!!.bufferedReader().use {it.readText()}},ioDispatcher=StandardTestDispatcher(testScheduler))
        val meals=Meals();val photos=Photos(meals).apply{importGate=CompletableDeferred()}
        val vm=RecordViewModel(meals,foods,TimeSource {day.atTime(23,0)},photos=photos)
        val store=ViewModelStore().apply{put("record",vm)}
        try{
            runCurrent();vm.beginGallery();vm.galleryResult("one");runCurrent();assertTrue(vm.state.value.photoBusy)
            store.clear();photos.importGate!!.complete(Unit);runCurrent()
            assertTrue("photo-1" in photos.deleted);assertTrue(meals.records.isEmpty())
        }finally{store.clear();Dispatchers.resetMain()}
    }
    @Test fun configurationDisposalCancelsOldNavigationWithoutDiscardingDraft()=scenario(true) {vm,meals,_,_ ->
        vm.setAmount(vm.state.value.rows.single().itemId,"175");var allowed:Boolean?=null
        vm.guardLeave {allowed=it};vm.cancelPendingNavigation();vm.confirmDiscard();runCurrent()
        assertEquals(false,allowed);assertEquals("175",vm.state.value.draft.foods.single().amountInput);assertTrue(vm.state.value.isDirty)
        assertEquals(150.0,meals.records.values.single().items.single().amount,0.0)
    }
}
