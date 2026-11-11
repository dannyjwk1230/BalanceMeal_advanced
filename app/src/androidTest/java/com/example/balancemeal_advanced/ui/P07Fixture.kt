package com.example.balancemeal_advanced.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.balancemeal_advanced.core.model.*
import com.example.balancemeal_advanced.core.time.TimeSource
import com.example.balancemeal_advanced.data.food.CachedFoodRepository
import com.example.balancemeal_advanced.data.image.*
import com.example.balancemeal_advanced.data.local.*
import com.example.balancemeal_advanced.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.LocalDateTime
import java.util.UUID

/** Real Room/DataStore/files with isolated names: never writes the runtime account's meals/profile. */
internal class P07Fixture(
    override val time:TimeSource=TimeSource {LocalDateTime.of(2026,11,11,20,30)},
    private val id:String="p07-"+UUID.randomUUID(),
    val context:Context=InstrumentationRegistry.getInstrumentation().targetContext,
) : MyDependencies,AutoCloseable {
    private val databaseName="$id.db"
    private val preferences=File(context.cacheDir,"$id.preferences_pb")
    private val settingsJob=SupervisorJob()
    var database=Room.databaseBuilder(context,MealDatabase::class.java,databaseName).build()
    override val foods=CachedFoodRepository(readText={context.assets.open("foods_sample.json").bufferedReader().use {it.readText()}})
    private val rawSettings=DataStoreSettingsRepository(PreferenceDataStoreFactory.create(scope=CoroutineScope(settingsJob+Dispatchers.IO),produceFile={preferences}),time)
    override val images=InternalImageStorage(context,id)
    override val photos=PhotoFiles(images) {reference ->database.meals().photoReferenceCount(reference)>0 || rawSettings.profile.first().photoReference==reference}
    override var meals:MealRepository=PhotoSafeMeals(RoomMealRepository(database,foods,time),photos)
    override val settings:SettingsRepository=PhotoSafeSettings(rawSettings,photos)
    override val camera=CameraCaptureStore(context)
    fun reopenDatabase() {
        database.close();database=Room.databaseBuilder(context,MealDatabase::class.java,databaseName).build()
        meals=PhotoSafeMeals(RoomMealRepository(database,foods,time),photos)
    }
    suspend fun imageUri():CameraTicket {
        val ticket=camera.prepare();writeImage(Uri.parse(ticket.uri));return ticket
    }
    fun writeImage(uri:Uri) {
        val bitmap=Bitmap.createBitmap(12,12,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.GREEN)
        context.contentResolver.openOutputStream(uri).use {checkNotNull(it);check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
        bitmap.recycle()
    }
    override fun close() {runBlocking {
        // Finish test-owned cleanup while its reference sources are still open.
        photos.awaitCleanup()
        images.ownedReferences().forEach {images.discard(it)}
        settingsJob.cancelAndJoin();database.close();context.deleteDatabase(databaseName);preferences.delete()
    }}
    /** Two-process P10 check only: close handles, retaining this fixture's durable files. */
    fun closeKeepingFiles() {runBlocking {
        photos.awaitCleanup();settingsJob.cancelAndJoin();database.close()
    }}
}
