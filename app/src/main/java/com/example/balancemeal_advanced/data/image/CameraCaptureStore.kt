package com.example.balancemeal_advanced.data.image

import android.content.Context
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class CameraTicket(val path:String,val uri:String)
class CameraCaptureStore(context:Context) {
    private val app=context.applicationContext
    private val directory=File(app.cacheDir,"captures")
    suspend fun prepare():CameraTicket = withContext(Dispatchers.IO) {
        check(directory.exists() || directory.mkdirs())
        directory.listFiles()?.filter {System.currentTimeMillis()-it.lastModified()>24*60*60*1000L}?.forEach {it.delete()}
        val file=File.createTempFile("meal-",".jpg",directory)
        try {CameraTicket(file.absolutePath,FileProvider.getUriForFile(app,app.packageName+".fileprovider",file).toString())}
        catch(e:Exception){file.delete();throw e}
    }
    suspend fun discard(ticket:CameraTicket) { withContext(Dispatchers.IO) {
        val file=File(ticket.path).canonicalFile
        require(file.parentFile==directory.canonicalFile)
        check(!file.exists() || file.delete())
    } }
}
