package com.example.balancemeal_advanced.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.balancemeal_advanced.core.model.*
import com.example.balancemeal_advanced.data.image.InternalImageStorage
import com.example.balancemeal_advanced.data.repository.SettingsRepository
import com.example.balancemeal_advanced.ui.my.ProfileViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProfileImagePersistenceTest {
    @Test fun ownedImageSurvivesSourceRemovalAndNewStorageAndFailedSelectionKeepsPhoto() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=File.createTempFile("p05-photo", ".png",context.cacheDir)
        val bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
        source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        bitmap.recycle()
        val storage=InternalImageStorage(context)
        val reference=storage.importImage(Uri.fromFile(source).toString())
        source.delete()
        val restored=InternalImageStorage(context).load(reference)
        assertEquals(8,restored.width);restored.recycle()
        val repo=object:SettingsRepository {
            override val profile=MutableStateFlow(LocalProfile())
            override val bodySettings=MutableStateFlow<BodySettings?>(null)
            override suspend fun updateNickname(nickname:String) { profile.value=profile.value.copy(nickname=nickname) }
            override suspend fun updateProfilePhoto(photoReference:String?) { profile.value=profile.value.copy(photoReference=photoReference) }
            override suspend fun saveBodySettings(settings:BodySettings) { bodySettings.value=settings }
        }
        val sourceAgain=File.createTempFile("p05-photo", ".jpg",context.cacheDir)
        File(reference).copyTo(sourceAgain,overwrite=true)
        val vm=ProfileViewModel(repo,storage)
        val store=ViewModelStore().apply { put("profile",vm) }
        var imported:String?=null
        try {
            withTimeout(5000) { vm.state.first { !it.loading } }
            vm.selectPhoto(Uri.fromFile(sourceAgain).toString())
            val saved=withTimeout(5000) { vm.state.first { !it.savingPhoto && it.profile.photoReference!=null } }
            imported=saved.profile.photoReference
            assertNotNull(saved.bitmap)
            vm.selectPhoto(null)
            assertEquals(imported,repo.profile.value.photoReference)
            vm.selectPhoto("content://invalid.balance.meal/missing")
            withTimeout(5000) { vm.state.first { !it.savingPhoto && it.error!=null } }
            assertEquals(imported,repo.profile.value.photoReference)
        } finally {
            store.clear();storage.discard(reference);imported?.let { storage.discard(it) };sourceAgain.delete()
        }
    }
}
