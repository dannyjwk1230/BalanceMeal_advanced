package com.example.balancemeal_advanced.data.repository

import com.example.balancemeal_advanced.core.time.TimeSource
import com.example.balancemeal_advanced.data.image.ImageStorage

/** Implemented by the existing Application; no additional container or repository instance. */
interface MyDependencies {
    val foods: FoodRepository
    val meals: MealRepository
    val settings: SettingsRepository
    val images: ImageStorage
    val time: TimeSource
    val photos: com.example.balancemeal_advanced.data.image.PhotoLifecycle
    val camera: com.example.balancemeal_advanced.data.image.CameraCaptureStore
}
