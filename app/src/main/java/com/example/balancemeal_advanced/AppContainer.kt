package com.example.balancemeal_advanced

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.example.balancemeal_advanced.core.time.ClockTimeSource
import com.example.balancemeal_advanced.core.time.TimeSource
import com.example.balancemeal_advanced.data.food.CachedFoodRepository
import com.example.balancemeal_advanced.data.local.DataStoreSettingsRepository
import com.example.balancemeal_advanced.data.local.MealDatabase
import com.example.balancemeal_advanced.data.local.RoomMealRepository
import com.example.balancemeal_advanced.data.repository.FoodRepository
import com.example.balancemeal_advanced.data.repository.MealRepository
import com.example.balancemeal_advanced.data.repository.SettingsRepository
import com.example.balancemeal_advanced.data.image.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

private val Context.localSettings by preferencesDataStore(name = "balance_meal_settings")

class BalanceMealApplication : Application(), com.example.balancemeal_advanced.data.repository.MyDependencies {
    val container: AppContainer by lazy { AppContainer(this) }
    override val foods get() = container.foodRepository
    override val meals get() = container.mealRepository
    override val settings get() = container.settingsRepository
    override val images get() = container.imageStorage
    override val time get() = container.timeSource
    override val photos get() = container.photoFiles
    override val camera get() = container.cameraCaptureStore
}

/** Application-owned repositories. Screens get this instance and use their own ViewModels. */
class AppContainer internal constructor(
    context: Context,
    val timeSource: TimeSource = ClockTimeSource(),
) {
    private val appContext = context.applicationContext
    val cameraCaptureStore by lazy { CameraCaptureStore(appContext) }
    val imageStorage: com.example.balancemeal_advanced.data.image.ImageStorage by lazy {
        com.example.balancemeal_advanced.data.image.InternalImageStorage(appContext)
    }
    private val database: MealDatabase by lazy {
        Room.databaseBuilder(appContext, MealDatabase::class.java, "balance_meal.db")
            .addMigrations(MealDatabase.MIGRATION_1_2).build()
    }

    val foodRepository: FoodRepository by lazy {
        CachedFoodRepository(readText = {
            appContext.assets.open("foods_sample.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        })
    }
    private val rawSettings:SettingsRepository by lazy {
        DataStoreSettingsRepository(appContext.localSettings, timeSource)
    }
    val photoFiles:PhotoFiles by lazy { PhotoFiles(imageStorage) { reference ->
        database.meals().photoReferenceCount(reference)>0 || rawSettings.profile.first().photoReference==reference
    }.also { files -> CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
        try {files.sweep()} catch(e:CancellationException) {throw e} catch(e:Exception) {android.util.Log.w("BalanceMealPhotos","Startup photo cleanup deferred",e)}
    } } }
    val mealRepository: MealRepository by lazy { PhotoSafeMeals(RoomMealRepository(database, foodRepository, timeSource),photoFiles) }
    val settingsRepository: SettingsRepository by lazy { PhotoSafeSettings(rawSettings,photoFiles) }
}

