package com.example.balancemeal_advanced.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.example.balancemeal_advanced.core.model.*
import com.example.balancemeal_advanced.core.time.TimeSource
import com.example.balancemeal_advanced.core.validation.SaveValidation
import com.example.balancemeal_advanced.data.repository.SettingsRepository
import com.example.balancemeal_advanced.data.repository.StoredDataException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class DataStoreSettingsRepository(
    private val store: DataStore<Preferences>,
    private val timeSource: TimeSource,
) : SettingsRepository {
    override val profile: Flow<LocalProfile> = store.data.map { preferences ->
        decodeStored { LocalProfile(preferences[NICKNAME] ?: "First Account", preferences[PHOTO]) }
    }
    override val bodySettings: Flow<BodySettings?> = store.data.map { preferences ->
        decodeStored {
            val present = BODY_KEYS.count { preferences.contains(it) }
            when (present) {
                0 -> null
                BODY_KEYS.size -> BodySettings(
                    LocalDate.parse(checkNotNull(preferences[BIRTH])),
                    Sex.valueOf(checkNotNull(preferences[SEX])),
                    checkNotNull(preferences[HEIGHT]),
                    checkNotNull(preferences[WEIGHT]),
                    ActivityLevel.valueOf(checkNotNull(preferences[ACTIVITY])),
                    calculationEnabled = preferences[CALCULATION_ENABLED] ?: true,
                )
                else -> throw StoredDataException("Partially stored body settings")
            }
        }
    }

    override suspend fun saveProfile(profile: LocalProfile) {
        require(profile.nickname.isNotBlank())
        store.edit {
            it[NICKNAME] = profile.nickname
            if (profile.photoReference == null) it.remove(PHOTO) else it[PHOTO] = profile.photoReference
        }
    }

    override suspend fun updateNickname(nickname: String) {
        require(nickname.isNotBlank()) { "Nickname must not be blank" }
        store.edit { it[NICKNAME] = nickname }
    }

    override suspend fun updateProfilePhoto(photoReference: String?) {
        require(photoReference == null || photoReference.isNotBlank())
        store.edit {
            if (photoReference == null) it.remove(PHOTO) else it[PHOTO] = photoReference
        }
    }

    override suspend fun saveBodySettings(settings: BodySettings) {
        store.edit {
            SaveValidation.body(settings, timeSource.now().toLocalDate())
            it[BIRTH] = settings.birthDate.toString()
            it[SEX] = settings.sex.name
            it[HEIGHT] = settings.heightCm
            it[WEIGHT] = settings.weightKg
            it[ACTIVITY] = settings.activityLevel.name
            it[CALCULATION_ENABLED] = settings.calculationEnabled
        }
    }

    private inline fun <T> decodeStored(block: () -> T): T = try {
        block()
    } catch (error: StoredDataException) {
        throw error
    } catch (error: Exception) {
        throw StoredDataException("Invalid local settings", error)
    }

    private companion object {
        val NICKNAME = stringPreferencesKey("profile.nickname")
        val PHOTO = stringPreferencesKey("profile.photoReference")
        val BIRTH = stringPreferencesKey("body.birthDate")
        val SEX = stringPreferencesKey("body.sex")
        val HEIGHT = doublePreferencesKey("body.heightCm")
        val WEIGHT = doublePreferencesKey("body.weightKg")
        val ACTIVITY = stringPreferencesKey("body.activityLevel")
        val CALCULATION_ENABLED = booleanPreferencesKey("body.calculationEnabled")
        val BODY_KEYS = listOf(BIRTH, SEX, HEIGHT, WEIGHT, ACTIVITY)
    }
}

