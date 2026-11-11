package com.example.balancemeal_advanced.ui.my

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.balancemeal_advanced.core.model.LocalProfile
import com.example.balancemeal_advanced.data.image.ImageStorage
import com.example.balancemeal_advanced.data.image.PhotoLifecycle
import com.example.balancemeal_advanced.data.repository.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ProfileUiState(val profile: LocalProfile = LocalProfile(), val bitmap: Bitmap? = null, val loading: Boolean = true, val savingPhoto: Boolean = false, val error: String? = null, val draftPhoto: String? = null, val draftBitmap: Bitmap? = null)
class ProfileViewModel(private val repository: SettingsRepository, private val images: ImageStorage, private val photos: PhotoLifecycle? = null) : ViewModel() {
    private val mutable = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = mutable.asStateFlow()
    init { viewModelScope.launch {
        repository.profile.catch { e -> if (e is CancellationException) throw e; mutable.update { it.copy(error = "프로필을 읽지 못했습니다.") } }
            .collectLatest { profile ->
                try {
                    val bitmap = profile.photoReference?.let { images.load(it) }
                    mutable.update { it.copy(profile = profile, bitmap = bitmap, loading = false) }
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) { mutable.update { it.copy(profile = profile, bitmap = null, loading = false, error = "저장된 사진을 읽지 못했습니다.") } }
            }
    } }
    private suspend fun release(path: String) {
        withContext(NonCancellable) { runCatching { if (photos != null) photos.release(path) else images.discard(path) } }
    }
    fun selectPhoto(uri: String?) {
        if (uri == null || state.value.loading || state.value.savingPhoto) return
        mutable.update { it.copy(savingPhoto = true, error = null) }
        viewModelScope.launch {
            var imported: String? = null
            try {
                val path = if (photos != null) photos.importPhoto(uri) else images.importImage(uri)
                imported = path
                val bitmap = images.load(path)
                val previous = state.value.draftPhoto
                mutable.update { it.copy(draftPhoto = path, draftBitmap = bitmap) }
                imported = null
                previous?.let { release(it) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { mutable.update { it.copy(error = "사진을 불러오지 못했습니다. 다시 선택해주세요.") }
            } finally {
                imported?.let { release(it) }
                mutable.update { it.copy(savingPhoto = false) }
            }
        }
    }
    suspend fun saveProfile(nickname: String) {
        check(!state.value.savingPhoto)
        val current = state.value
        val path = current.draftPhoto
        repository.saveProfile(current.profile.copy(nickname = nickname, photoReference = path ?: current.profile.photoReference))
        mutable.update { it.copy(profile = it.profile.copy(nickname = nickname, photoReference = path ?: it.profile.photoReference),
            bitmap = it.draftBitmap ?: it.bitmap, draftPhoto = null, draftBitmap = null, error = null) }
        if (path != null) {
            withContext(NonCancellable) {
                runCatching { photos?.release(path); current.profile.photoReference?.let { photos?.retire(it) } }
            }
        }
    }
    fun discardPhoto() {
        val path = state.value.draftPhoto
        mutable.update { it.copy(draftPhoto = null, draftBitmap = null, error = null) }
        path?.let { viewModelScope.launch { release(it) } }
    }
}
