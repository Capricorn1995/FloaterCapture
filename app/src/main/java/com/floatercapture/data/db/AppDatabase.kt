package com.floatercapture.data.db

import com.floatercapture.data.model.DownloadState
import com.floatercapture.data.model.DownloadTask
import com.floatercapture.data.model.MediaItem
import com.floatercapture.data.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface MediaDao {
    suspend fun insert(item: MediaItem): String
    suspend fun insertAll(items: List<MediaItem>): List<String>
    suspend fun update(item: MediaItem)
    fun getAll(): Flow<List<MediaItem>>
    fun getById(id: String): Flow<MediaItem?>
    fun getByPackage(packageName: String): Flow<List<MediaItem>>
    suspend fun delete(id: String)
    suspend fun deleteAll()
}

interface DownloadDao {
    suspend fun insert(task: DownloadTask): String
    suspend fun update(task: DownloadTask)
    fun getAll(): Flow<List<DownloadTask>>
    fun getById(id: String): Flow<DownloadTask?>
    fun getByState(state: DownloadState): Flow<List<DownloadTask>>
    suspend fun delete(id: String)
    suspend fun deleteAll()
}

class AppDatabase private constructor() {

    val mediaDao: MediaDao = InMemoryMediaDao()
    val downloadDao: DownloadDao = InMemoryDownloadDao()

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppDatabase().also { INSTANCE = it }
            }
        }
    }
}

private class InMemoryMediaDao : MediaDao {

    private val storage = ConcurrentHashMap<String, MediaItem>()
    private val allItemsFlow = MutableStateFlow<List<MediaItem>>(emptyList())

    private fun refreshFlow() {
        allItemsFlow.value = storage.values.toList()
    }

    override suspend fun insert(item: MediaItem): String {
        val id = item.id.ifEmpty { UUID.randomUUID().toString() }
        val savedItem = item.copy(id = id)
        storage[id] = savedItem
        refreshFlow()
        return id
    }

    override suspend fun insertAll(items: List<MediaItem>): List<String> {
        val ids = items.map { item ->
            val id = item.id.ifEmpty { UUID.randomUUID().toString() }
            storage[id] = item.copy(id = id)
            id
        }
        refreshFlow()
        return ids
    }

    override suspend fun update(item: MediaItem) {
        if (item.id.isNotEmpty() && storage.containsKey(item.id)) {
            storage[item.id] = item
            refreshFlow()
        }
    }

    override fun getAll(): Flow<List<MediaItem>> {
        return allItemsFlow.asStateFlow()
    }

    override fun getById(id: String): Flow<MediaItem?> {
        return allItemsFlow.map { items -> items.find { it.id == id } }
    }

    override fun getByPackage(packageName: String): Flow<List<MediaItem>> {
        return allItemsFlow.map { items ->
            items.filter { it.sourcePackage == packageName }
        }
    }

    override suspend fun delete(id: String) {
        storage.remove(id)
        refreshFlow()
    }

    override suspend fun deleteAll() {
        storage.clear()
        refreshFlow()
    }
}

private class InMemoryDownloadDao : DownloadDao {

    private val storage = ConcurrentHashMap<String, DownloadTask>()
    private val allTasksFlow = MutableStateFlow<List<DownloadTask>>(emptyList())

    private fun refreshFlow() {
        allTasksFlow.value = storage.values.toList()
    }

    override suspend fun insert(task: DownloadTask): String {
        val id = task.id.ifEmpty { UUID.randomUUID().toString() }
        val savedTask = task.copy(id = id)
        storage[id] = savedTask
        refreshFlow()
        return id
    }

    override suspend fun update(task: DownloadTask) {
        if (task.id.isNotEmpty() && storage.containsKey(task.id)) {
            storage[task.id] = task
            refreshFlow()
        }
    }

    override fun getAll(): Flow<List<DownloadTask>> {
        return allTasksFlow.asStateFlow()
    }

    override fun getById(id: String): Flow<DownloadTask?> {
        return allTasksFlow.map { tasks -> tasks.find { it.id == id } }
    }

    override fun getByState(state: DownloadState): Flow<List<DownloadTask>> {
        return allTasksFlow.map { tasks ->
            tasks.filter { it.state == state }
        }
    }

    override suspend fun delete(id: String) {
        storage.remove(id)
        refreshFlow()
    }

    override suspend fun deleteAll() {
        storage.clear()
        refreshFlow()
    }
}
