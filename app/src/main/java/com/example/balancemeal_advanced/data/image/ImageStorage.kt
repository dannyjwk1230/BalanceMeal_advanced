package com.example.balancemeal_advanced.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** App-owned images. Import finishes before a durable reference is published to a repository. */
interface ImageStorage {
    suspend fun importImage(uri: String): String
    suspend fun load(reference: String): Bitmap
    suspend fun discard(reference: String)
    suspend fun ownedReferences(): List<String> = emptyList()
}
class InternalImageStorage(context: Context,subdirectory:String="images") : ImageStorage {
    private val app = context.applicationContext
    init {require(subdirectory.matches(Regex("[A-Za-z0-9_-]+")))}
    private val directory = File(app.filesDir, subdirectory)
    private fun owned(reference: String): File {
        val file = File(reference).canonicalFile
        require(file.parentFile == directory.canonicalFile)
        return file
    }
    override suspend fun importImage(uri: String): String = withContext(Dispatchers.IO) {
        check(directory.exists() || directory.mkdirs())
        val source = Uri.parse(uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        app.contentResolver.openInputStream(source).use { requireNotNull(it); BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
        val decoded = app.contentResolver.openInputStream(source).use {
            requireNotNull(it)
            requireNotNull(BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }))
        }
        val orientation=runCatching {app.contentResolver.openInputStream(source).use {ExifInterface(checkNotNull(it)).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)}}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix=Matrix().apply {when(orientation){
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL->setScale(-1f,1f)
            ExifInterface.ORIENTATION_ROTATE_180->setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL->setScale(1f,-1f)
            ExifInterface.ORIENTATION_TRANSPOSE->{setRotate(90f);postScale(-1f,1f)}
            ExifInterface.ORIENTATION_ROTATE_90->setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE->{setRotate(-90f);postScale(-1f,1f)}
            ExifInterface.ORIENTATION_ROTATE_270->setRotate(270f)
        }}
        val bitmap=if(matrix.isIdentity) decoded else Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true)
        if(bitmap!==decoded)decoded.recycle()
        val target = File(directory, UUID.randomUUID().toString()+".jpg")
        val temporary = File(directory, target.name+".tmp")
        try {
            temporary.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
            check(temporary.renameTo(target))
            target.absolutePath
        } finally { bitmap.recycle(); temporary.delete() }
    }
    override suspend fun load(reference: String): Bitmap = withContext(Dispatchers.IO) {
        requireNotNull(BitmapFactory.decodeFile(owned(reference).absolutePath)) { "Image cannot be read" }
    }
    override suspend fun discard(reference: String) { withContext(Dispatchers.IO) {val file=owned(reference);check(!file.exists() || file.delete());Unit} }
    override suspend fun ownedReferences():List<String> = withContext(Dispatchers.IO) {
        if(!directory.exists()) emptyList() else checkNotNull(directory.listFiles()).filter {it.isFile && (it.extension=="jpg" || it.name.endsWith(".jpg.tmp"))}.map {it.absolutePath}
    }
}
