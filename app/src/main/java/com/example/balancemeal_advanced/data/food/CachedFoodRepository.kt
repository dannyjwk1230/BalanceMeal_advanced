package com.example.balancemeal_advanced.data.food

import com.example.balancemeal_advanced.core.model.Food
import com.example.balancemeal_advanced.data.repository.FoodDataException
import com.example.balancemeal_advanced.data.repository.FoodRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Collections

/** Cache only successful reads. Errors propagate, and a later call may retry the source. */
class CachedFoodRepository(
    private val readText: suspend () -> String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FoodRepository {
    private val mutex = Mutex()
    private var catalog: List<Food>? = null

    override suspend fun getAll(): List<Food> = mutex.withLock {
        catalog ?: withContext(ioDispatcher) {
            try {
                Collections.unmodifiableList(FoodCatalogParser.parse(readText())).also { catalog = it }
            } catch (error: CancellationException) {
                throw error
            } catch (error: FoodDataException) {
                throw error
            } catch (error: Exception) {
                throw FoodDataException("Cannot read foods_sample.json", error)
            }
        }
    }

    override suspend fun getByCode(code: String): Food? = getAll().find { it.code == code }

    override suspend fun searchByName(query: String): List<Food> {
        val normalized = query.trim()
        return getAll().filter { it.name.contains(normalized, ignoreCase = true) }
    }
}

