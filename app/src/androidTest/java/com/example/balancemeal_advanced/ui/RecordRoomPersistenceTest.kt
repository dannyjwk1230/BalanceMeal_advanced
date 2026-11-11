package com.example.balancemeal_advanced.ui

import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.balancemeal_advanced.core.model.*
import com.example.balancemeal_advanced.data.repository.*
import com.example.balancemeal_advanced.ui.record.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RecordRoomPersistenceTest {
    private suspend fun await(vm:RecordViewModel,predicate:(RecordUiState)->Boolean)=withTimeout(10000){vm.state.first(predicate)}
    private suspend fun main(block:()->Unit)=withContext(Dispatchers.Main){block()}
    private suspend fun add(vm:RecordViewModel) {
        main{vm.openPicker()};await(vm){!it.searching && it.choices.isNotEmpty()}
        main{vm.choose(vm.state.value.choices.single {it.food.name=="쌀밥"}.food.code);vm.addAmount("150");vm.addSelected();vm.closePicker()}
    }
    @Test fun confirmedCreateUpdateReopenDeleteAndInjectedFailureUseRealRoom()=runBlocking {
        val f=P07Fixture();val store=ViewModelStore()
        try {
            val vm=RecordViewModel(f.meals,f.foods,f.time,images=f.images,photos=f.photos,camera=f.camera)
            main{store.put("record",vm)};await(vm){!it.loading};add(vm)
            main{vm.requestPersist()};assertNull(f.meals.getMeal(vm.state.value.key.date,MealType.BREAKFAST))
            main{vm.dismissDialog()};assertNull(f.meals.getMeal(vm.state.value.key.date,MealType.BREAKFAST))
            main{vm.requestPersist();vm.confirmPersist();vm.confirmPersist()};val first=await(vm){!it.saving && it.original!=null}.original!!
            assertEquals(1,f.meals.observeOnDate(first.date).first().size)
            main{vm.setAmount(first.items.single().id,"175");vm.requestPersist();vm.confirmPersist()}
            await(vm){!it.saving && !it.isDirty};assertEquals(first.id,f.meals.getMeal(first.id)!!.id)
            f.reopenDatabase();val restored=f.meals.getMeal(first.date,first.mealType)!!
            assertEquals(first.id,restored.id);assertEquals(175.0,restored.items.single().amount,0.0)
            val failing=object:MealRepository by f.meals {override suspend fun updateMeal(record:MealRecord):MealRecord {throw java.io.IOException("Injected failure before durable write")}}
            val failed=RecordViewModel(failing,f.foods,f.time,photos=f.photos)
            main{store.put("failed",failed)};await(failed){!it.loading}
            main{failed.setAmount(restored.items.single().id,"200");failed.requestPersist();failed.confirmPersist()}
            await(failed){!it.saving && it.operationError!=null};assertEquals(restored,f.meals.getMeal(first.id));assertEquals("200",failed.state.value.draft.foods.single().amountInput)
            // Reopen a fresh editor against the reopened DB, then delete the saved meal.
            val deleting=RecordViewModel(f.meals,f.foods,f.time,photos=f.photos)
            main{store.put("deleting",deleting)};await(deleting){!it.loading}
            main{deleting.remove(restored.items.single().id);deleting.requestPersist();deleting.dismissDialog()}
            assertEquals(restored,f.meals.getMeal(first.id));assertEquals(1,f.database.meals().itemCount())
            main{deleting.requestPersist();deleting.confirmPersist()};await(deleting){!it.saving && it.original==null && !it.isDirty}
            assertNull(f.meals.getMeal(first.id));assertEquals(0,f.database.meals().itemCount())
        }finally{main{store.clear()};f.close()}
    }
    @Test fun realFilesRemainUntilAllMealProfileAndDraftReferencesAreGone()=runBlocking {
        val f=P07Fixture()
        try {
            val ticket=f.imageUri();val reference=f.photos.importPhoto(ticket.uri);f.camera.discard(ticket)
            val food=f.foods.getAll().first();val day=f.time.now().toLocalDate()
            val first=f.meals.createMeal(day,MealType.BREAKFAST,listOf(MealItem(food.code,100.0)),reference)
            val second=f.meals.createMeal(day,MealType.LUNCH,listOf(MealItem(food.code,100.0)),reference)
            f.settings.updateProfilePhoto(reference);f.photos.release(reference)
            f.meals.deleteMeal(first.id);f.photos.retire(reference);assertTrue(File(reference).exists())
            f.meals.deleteMeal(second.id);f.photos.retire(reference);assertTrue(File(reference).exists())
            f.photos.retain(reference) // Another open draft still uses it.
            f.settings.updateProfilePhoto(null);f.photos.retire(reference);assertTrue(File(reference).exists())
            f.photos.release(reference);assertFalse(File(reference).exists())
        }finally{f.close()}
    }
    @Test fun fileProviderCameraCopySurvivesSourceDeletionAndDatabaseReopen()=runBlocking {
        val f=P07Fixture()
        try {
            val ticket=f.imageUri();assertTrue(ticket.uri.startsWith("content://com.example.balancemeal_advanced.fileprovider/"))
            val reference=f.photos.importPhoto(ticket.uri)
            val food=f.foods.getAll().first();val record=f.meals.createMeal(f.time.now().toLocalDate(),MealType.BREAKFAST,listOf(MealItem(food.code,100.0)),reference)
            f.photos.release(reference);f.camera.discard(ticket);assertFalse(File(ticket.path).exists())
            f.reopenDatabase();assertEquals(reference,f.meals.getMeal(record.id)!!.photoReference)
            val image=f.images.load(reference);assertEquals(12,image.width);image.recycle()
            val info=f.context.packageManager.getPackageInfo(f.context.packageName,android.content.pm.PackageManager.GET_PERMISSIONS)
            assertFalse(info.requestedPermissions.orEmpty().contains(android.Manifest.permission.CAMERA))
            assertFalse(info.requestedPermissions.orEmpty().contains(android.Manifest.permission.READ_MEDIA_IMAGES))
        }finally{f.close()}
    }
    @Test fun startupSweepRemovesOrphansButKeepsLiveDraftAndExifRotationIsStoredInPixels()=runBlocking {
        val f=P07Fixture()
        try {
            val ticket=f.camera.prepare()
            val bitmap=android.graphics.Bitmap.createBitmap(12,8,android.graphics.Bitmap.Config.ARGB_8888)
            File(ticket.path).outputStream().use {assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,90,it))};bitmap.recycle()
            android.media.ExifInterface(ticket.path).apply {setAttribute(android.media.ExifInterface.TAG_ORIENTATION,android.media.ExifInterface.ORIENTATION_ROTATE_90.toString());saveAttributes()}
            val orphan=f.images.importImage(ticket.uri)
            val live=f.photos.importPhoto(ticket.uri)
            val normalized=f.images.load(live);assertEquals(8,normalized.width);assertEquals(12,normalized.height);normalized.recycle()
            f.photos.sweep();assertFalse(File(orphan).exists());assertTrue(File(live).exists())
            f.photos.release(live);assertFalse(File(live).exists());f.camera.discard(ticket)
        }finally{f.close()}
    }
}
