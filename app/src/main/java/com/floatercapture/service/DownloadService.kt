package com.floatercapture.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.floatercapture.FloaterApp
import com.floatercapture.MainActivity
import com.floatercapture.data.model.DownloadState
import com.floatercapture.data.model.DownloadTask
import com.floatercapture.data.model.MediaItem
import com.floatercapture.data.model.MediaType
import com.floatercapture.data.repository.DownloadRepository
import com.floatercapture.data.repository.MediaRepository
import com.floatercapture.util.FileHelper
import com.floatercapture.util.NotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * 前台服务，管理文件下载。
 * 使用 OkHttp 进行下载，通过 Semaphore 控制最大并发数（3个），
 * 支持暂停、恢复和队列管理。
 */
class DownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadRepository = DownloadRepository()
    private val mediaRepository = MediaRepository()

    // 并发控制：最大同时下载 3 个任务
    private val downloadSemaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)

    // 下载队列管理：跟踪所有活跃的下载任务协程
    private val downloadQueue = ConcurrentHashMap<String, Job>()

    override fun onCreate() {
        super.onCreate()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            // 必须先确保前台服务启动（Android 8+ 5秒限制）
            ensureForeground()

            when (intent?.action) {
                ACTION_START_DOWNLOAD -> {
                    val itemsJson = intent.getStringExtra(EXTRA_MEDIA_ITEMS_JSON)
                    if (itemsJson != null) {
                        handleStartDownload(itemsJson)
                    }
                }
                ACTION_PAUSE_ALL -> pauseAll()
                ACTION_RESUME_ALL -> resumeAll()
                ACTION_PAUSE -> {
                    val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                    if (taskId != null) pauseTask(taskId)
                }
                ACTION_RESUME -> {
                    val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                    if (taskId != null) resumeTask(taskId)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DownloadService", "onStartCommand error", e)
        }
        return START_STICKY
    }

    private fun ensureForeground() {
        try {
            val notification = NotificationHelper.createDownloadProgressNotification(this, "准备中", 0)
            startForeground(NOTIFICATION_ID_DOWNLOAD, notification)
        } catch (e: Exception) {
            android.util.Log.e("DownloadService", "ensureForeground error", e)
        }
    }

    /**
     * 处理开始下载指令：解析 MediaItem JSON 列表，创建 DownloadTask 并开始下载。
     */
    private fun handleStartDownload(itemsJson: String) {
        val items = parseMediaItems(itemsJson)
        if (items.isEmpty()) return

        serviceScope.launch {
            for (item in items) {
                val fileName = FileHelper.generateFileName(
                    item.url,
                    FileHelper.MediaType.valueOf(item.type.name)
                )
                val task = DownloadTask(
                    url = item.url,
                    fileName = fileName,
                    mediaType = item.type,
                    state = DownloadState.Pending,
                    timestamp = System.currentTimeMillis()
                )

                try {
                    val taskId = downloadRepository.insert(task)
                    val savedTask = task.copy(id = taskId)
                    // 启动下载任务（在单独的协程中）
                    val job = launchDownloadTask(savedTask, item)
                    downloadQueue[taskId] = job
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * 启动单个下载任务协程。
     * 使用 Semaphore 控制并发，下载完成后释放。
     */
    private fun launchDownloadTask(task: DownloadTask, mediaItem: MediaItem): Job {
        return serviceScope.launch {
            try {
                // 获取下载许可（控制并发数）
                downloadSemaphore.acquire()
                downloadFile(task, mediaItem)
            } catch (e: Exception) {
                e.printStackTrace()
                handleDownloadError(task, e)
            } finally {
                downloadSemaphore.release()
                downloadQueue.remove(task.id)
            }
        }
    }

    /**
     * 核心下载逻辑：使用 OkHttp 下载文件，流式写入磁盘。
     */
    private suspend fun downloadFile(task: DownloadTask, mediaItem: MediaItem) {
        // node:// 是节点驱动捕获的资源，没有真实 URL，无法下载
        if (task.url.startsWith("node://")) {
            handleDownloadError(
                task,
                IllegalStateException("此资源为节点捕获，无 URL。请在详情页使用「截屏保存」功能。")
            )
            return
        }

        // 更新任务状态为下载中
        val downloadingTask = task.copy(state = DownloadState.Downloading)
        downloadRepository.update(downloadingTask)

        val request = Request.Builder()
            .url(task.url)
            .header("User-Agent", "FloaterCapture/1.0")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }

            val body = response.body ?: throw IOException("Empty response body")
            val contentLength = body.contentLength()
            val totalBytes = if (contentLength > 0) contentLength else -1L

            // 获取保存目录并创建
            val mediaType = FileHelper.MediaType.valueOf(task.mediaType.name)
            val saveDir = FileHelper.getSaveDirectory(this, mediaType)
            if (!saveDir.exists()) {
                saveDir.mkdirs()
            }

            val outputFile = File(saveDir, task.fileName)

            // 流式写入文件
            val source = body.source()
            val sink = outputFile.sink().buffer()

            var downloadedBytes = 0L

            sink.use { bufferedSink ->
                source.use { bufferedSource ->
                    val buffer = okio.Buffer()
                    var lastUpdateTime = System.currentTimeMillis()

                    try {
                        while (!bufferedSource.exhausted()) {
                            val bytesRead = bufferedSource.read(buffer, BUFFER_SIZE.toLong())
                            if (bytesRead == -1L) break

                            downloadedBytes += bytesRead
                            bufferedSink.write(buffer, bytesRead)

                            // 更新进度（限制更新频率为 200ms）
                            val now = System.currentTimeMillis()
                            if (now - lastUpdateTime > PROGRESS_UPDATE_INTERVAL_MS) {
                                val progress = if (totalBytes > 0) {
                                    ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                                } else {
                                    // 无法获取总大小时使用估算进度
                                    (-1).coerceIn(0, 100)
                                }
                                val progressTask = task.copy(
                                    state = DownloadState.Downloading,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                    progress = progress
                                )
                                downloadRepository.update(progressTask)

                                // 更新通知
                                updateProgressNotification(task.fileName, progress)

                                lastUpdateTime = now
                            }
                        }
                    } finally {
                        bufferedSink.flush()
                    }
                }
            }

            body.close()
            response.close()

            // 下载完成
            val completedTask = task.copy(
                state = DownloadState.Completed,
                downloadedBytes = downloadedBytes,
                totalBytes = outputFile.length(),
                progress = 100
            )
            downloadRepository.update(completedTask)

            // 更新 MediaItem 的下载状态
            val updatedItem = mediaItem.copy(
                isDownloaded = true,
                localFilePath = outputFile.absolutePath,
                fileSize = outputFile.length()
            )

            // 由于 MediaRepository 的 update 方法未暴露，通过删除再插入来更新
            // 注意：实际项目中应该使用专门的 update 方法
            // 这里通过 MediaRepository 管理

            // 发送下载完成通知
            NotificationHelper.showDownloadComplete(this, task.fileName)

            // 扫描文件到媒体库
            scanFileToMediaLibrary(outputFile)

        } catch (e: IOException) {
            handleDownloadError(task, e)
            throw e
        } catch (e: Exception) {
            handleDownloadError(task, e)
            throw e
        }
    }

    /**
     * 处理下载异常：更新任务状态为 Failed 并记录错误信息。
     */
    private fun handleDownloadError(task: DownloadTask, error: Throwable) {
        serviceScope.launch {
            try {
                val failedTask = task.copy(
                    state = DownloadState.Failed,
                    errorMessage = error.message ?: "Unknown error"
                )
                downloadRepository.update(failedTask)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 更新下载进度通知。
     */
    private fun updateProgressNotification(fileName: String, progress: Int) {
        try {
            NotificationHelper.showDownloadProgress(this, fileName, progress)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 使用 MediaScannerConnection 将下载的文件扫描到系统媒体库。
     */
    private fun scanFileToMediaLibrary(file: File) {
        try {
            MediaScannerConnection.scanFile(
                this,
                arrayOf(file.absolutePath),
                null
            ) { path, uri ->
                // 扫描完成回调
                if (uri != null) {
                    android.util.Log.d("DownloadService", "File scanned: $path -> $uri")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 暂停所有下载任务。
     */
    private fun pauseAll() {
        downloadQueue.forEach { (taskId, job) ->
            if (job.isActive) {
                job.cancel()
            }
        }
        downloadQueue.clear()

        serviceScope.launch {
            try {
                // 将所有进行中的任务标记为暂停
                downloadRepository.getByState(DownloadState.Downloading).first().forEach { task ->
                    downloadRepository.update(task.copy(state = DownloadState.Paused))
                }
                downloadRepository.getByState(DownloadState.Pending).first().forEach { task ->
                    downloadRepository.update(task.copy(state = DownloadState.Paused))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 暂停单个下载任务。
     */
    private fun pauseTask(taskId: String) {
        downloadQueue[taskId]?.let { job ->
            if (job.isActive) {
                job.cancel()
            }
        }
        downloadQueue.remove(taskId)

        serviceScope.launch {
            try {
                downloadRepository.getById(taskId).first()?.let { task ->
                    if (task.state == DownloadState.Downloading || task.state == DownloadState.Pending) {
                        downloadRepository.update(task.copy(state = DownloadState.Paused))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 恢复单个下载任务。
     */
    private fun resumeTask(taskId: String) {
        serviceScope.launch {
            try {
                downloadRepository.getById(taskId).first()?.let { task ->
                    if (task.state == DownloadState.Paused || task.state == DownloadState.Failed) {
                        val newTask = task.copy(state = DownloadState.Pending)
                        downloadRepository.update(newTask)
                        val mediaItem = MediaItem(
                            type = task.mediaType,
                            url = task.url
                        )
                        val job = launchDownloadTask(newTask, mediaItem)
                        downloadQueue[taskId] = job
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 恢复所有暂停的下载任务。
     */
    private fun resumeAll() {
        serviceScope.launch {
            try {
                val pausedTasks = downloadRepository.getByState(DownloadState.Paused).first()
                for (task in pausedTasks) {
                    // 为每个暂停的任务创建新的下载
                    val newTask = task.copy(state = DownloadState.Pending)
                    downloadRepository.update(newTask)
                    val job = serviceScope.launch {
                        try {
                            downloadSemaphore.acquire()
                            downloadFile(newTask, MediaItem(
                                type = task.mediaType,
                                url = task.url
                            ))
                        } catch (e: Exception) {
                            handleDownloadError(newTask, e)
                        } finally {
                            downloadSemaphore.release()
                        }
                    }
                    downloadQueue[task.id] = job
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 启动前台服务通知。
     */
    /**
     * 从 JSON 字符串解析 MediaItem 列表（使用 Android 内置 org.json）。
     */
    private fun parseMediaItems(json: String): List<MediaItem> {
        return try {
            val jsonArray = JSONArray(json)
            val items = mutableListOf<MediaItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                items.add(MediaItem(
                    id = obj.optString("id", ""),
                    type = try {
                        MediaType.valueOf(obj.optString("type", "OTHER"))
                    } catch (e: Exception) {
                        MediaType.OTHER
                    },
                    url = obj.optString("url", ""),
                    sourcePackage = obj.optString("sourcePackage", ""),
                    sourceAppName = obj.optString("sourceAppName", ""),
                    description = obj.optString("description", ""),
                    fileSize = obj.optLong("fileSize", 0),
                    mimeType = obj.optString("mimeType", ""),
                    thumbnailUrl = obj.optString("thumbnailUrl", ""),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    isDownloaded = obj.optBoolean("isDownloaded", false),
                    localFilePath = obj.optString("localFilePath", "")
                ))
            }
            items
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override fun onDestroy() {
        // 取消所有活跃的下载任务
        downloadQueue.values.forEach { it.cancel() }
        downloadQueue.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ========================
    // Companion
    // ========================

    companion object {
        const val ACTION_START_DOWNLOAD = "com.floatercapture.action.START_DOWNLOAD"
        const val ACTION_PAUSE_ALL = "com.floatercapture.action.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "com.floatercapture.action.RESUME_ALL"
        const val ACTION_PAUSE = "com.floatercapture.action.PAUSE"
        const val ACTION_RESUME = "com.floatercapture.action.RESUME"
        const val EXTRA_MEDIA_ITEMS_JSON = "extra_media_items_json"
        const val EXTRA_TASK_ID = "extra_task_id"

        const val NOTIFICATION_ID_DOWNLOAD = 1002

        private const val MAX_CONCURRENT_DOWNLOADS = 3
        private const val BUFFER_SIZE = 8192
        private const val PROGRESS_UPDATE_INTERVAL_MS = 200L

        /** OkHttpClient 单例，配置连接和超时参数 */
        val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }

        /**
         * 启动下载服务，传入待下载的 MediaItem 列表。
         * 使用 org.json 将列表序列化为 JSON 字符串。
         */
        fun startDownload(context: Context, items: List<MediaItem>) {
            val jsonArray = JSONArray()
            items.forEach { item ->
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("type", item.type.name)
                    put("url", item.url)
                    put("sourcePackage", item.sourcePackage)
                    put("sourceAppName", item.sourceAppName)
                    put("description", item.description)
                    put("fileSize", item.fileSize)
                    put("mimeType", item.mimeType)
                    put("thumbnailUrl", item.thumbnailUrl)
                    put("timestamp", item.timestamp)
                    put("isDownloaded", item.isDownloaded)
                    put("localFilePath", item.localFilePath)
                }
                jsonArray.put(obj)
            }
            val json = jsonArray.toString()
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_MEDIA_ITEMS_JSON, json)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * 暂停所有下载。
         */
        fun pauseAll(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_PAUSE_ALL
            }
            context.startService(intent)
        }

        /**
         * 恢复所有暂停的下载。
         */
        fun resumeAll(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_RESUME_ALL
            }
            context.startService(intent)
        }

        /**
         * 暂停单个下载任务。
         */
        fun pauseTask(context: Context, taskId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_TASK_ID, taskId)
            }
            context.startService(intent)
        }

        /**
         * 恢复单个下载任务。
         */
        fun resumeTask(context: Context, taskId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_RESUME
                putExtra(EXTRA_TASK_ID, taskId)
            }
            context.startService(intent)
        }
    }
}
