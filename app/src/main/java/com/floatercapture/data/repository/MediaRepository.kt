package com.floatercapture.data.repository

import com.floatercapture.data.db.AppDatabase
import com.floatercapture.data.model.MediaItem
import kotlinx.coroutines.flow.Flow

class MediaRepository(
    private val database: AppDatabase = AppDatabase.getInstance()
) {

    private val mediaDao = database.mediaDao

    suspend fun insert(item: MediaItem): String {
        return mediaDao.insert(item)
    }

    suspend fun insertAll(items: List<MediaItem>): List<String> {
        return mediaDao.insertAll(items)
    }

    fun getAll(): Flow<List<MediaItem>> {
        return mediaDao.getAll()
    }

    fun getById(id: String): Flow<MediaItem?> {
        return mediaDao.getById(id)
    }

    fun getByPackage(packageName: String): Flow<List<MediaItem>> {
        return mediaDao.getByPackage(packageName)
    }

    suspend fun delete(id: String) {
        mediaDao.delete(id)
    }

    suspend fun deleteAll() {
        mediaDao.deleteAll()
    }
}
