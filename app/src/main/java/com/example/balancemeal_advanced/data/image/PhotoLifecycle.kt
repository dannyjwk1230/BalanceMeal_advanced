package com.example.balancemeal_advanced.data.image

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface PhotoLifecycle {
    suspend fun importPhoto(uri: String): String
    suspend fun retain(reference: String)
    suspend fun release(reference: String)
    suspend fun retire(reference: String)
    fun releaseLater(reference: String)
    suspend fun <T> withReference(reference: String?, write: suspend () -> T): T
}

/** One app-owned lock orders photo reference writes, live draft leases, and garbage collection. */
class PhotoFiles(private val images: ImageStorage, private val referenced: suspend (String)->Boolean) : PhotoLifecycle {
    private val mutex=Mutex()
    private val leases=mutableMapOf<String,Int>()
    private val cleanupScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    override suspend fun importPhoto(uri: String): String {
        var imported:String?=null
        try {return mutex.withLock {
            withContext(NonCancellable) {images.importImage(uri).also {imported=it;leases[it]=(leases[it]?:0)+1}}
        }} catch(e:CancellationException) {
            imported?.let {withContext(NonCancellable) {release(it)}}
            throw e
        }
    }
    override suspend fun retain(reference: String) { mutex.withLock { leases[reference]=(leases[reference]?:0)+1 } }
    override suspend fun release(reference: String) { mutex.withLock {
        val count=leases[reference]?:0
        if (count>1) leases[reference]=count-1 else leases.remove(reference)
        if (reference !in leases && !referenced(reference)) images.discard(reference)
    } }
    override suspend fun retire(reference:String) {mutex.withLock {
        if(reference !in leases && !referenced(reference)) images.discard(reference)
    }}
    override fun releaseLater(reference: String) { cleanupScope.launch {
        try {release(reference)} catch (e: CancellationException) {throw e} catch (e: Exception) {
            android.util.Log.w("BalanceMealPhotos","Photo cleanup deferred until next sweep",e)
        }
    } }
    override suspend fun <T> withReference(reference: String?, write: suspend ()->T): T = mutex.withLock {
        // Prevent a writer from publishing a reference that another writer has already retired.
        reference?.let { images.load(it).recycle() }
        write()
    }
    suspend fun sweep() { mutex.withLock {
        for (reference in images.ownedReferences()) {
            if (reference !in leases && !referenced(reference)) images.discard(reference)
        }
    } }
    suspend fun awaitCleanup() {cleanupScope.coroutineContext[Job]?.children?.toList()?.joinAll()}
}
