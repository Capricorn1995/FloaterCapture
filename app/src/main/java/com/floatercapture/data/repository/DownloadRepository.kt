package com.floatercapture.data.repository

import com.floatercapture.data.db.AppDatabase
import com.floatercapture.data.model.DownloadState
import com.floatercapture.data.model.DownloadTask
import kotlinx.coroutines.flow.Flow

class DownloadRepository(
    private val database: AppDatabase = AppDatabase.getInstance()
) {

    private val downloadDao = database.downloadDao

    suspend fun insert(task: DownloadTask): String {
        return downloadDao.insert(task)
    }

    suspend fun update(task: DownloadTask) {
        downloadDao.update(task)
    }

    fun getAll(): Flow<List<DownloadTask>> {
        return downloadDao.getAll()
    }

    fun getById(id: String): Flow<DownloadTask?> {
        return downloadDao.getById(id)
    }

    fun getByState(state: DownloadState): Flow<List<DownloadTask>> {
        return downloadDao.getByState(state)
    }

    suspend fun delete(id: String) {
        downloadDao.delete(id)
    }

    suspend fun deleteAll() {
        downloadDao.deleteAll()
    }
}
