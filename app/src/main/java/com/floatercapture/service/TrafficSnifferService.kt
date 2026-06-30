package com.floatercapture.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.floatercapture.FloaterApp
import com.floatercapture.data.model.MediaItem
import com.floatercapture.data.model.MediaType
import com.floatercapture.data.repository.MediaRepository
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class TrafficSnifferService : VpnService() {

    companion object {
        const val TAG = "TrafficSniffer"
        const val ACTION_START = "com.floatercapture.action.VPN_START"
        const val ACTION_STOP = "com.floatercapture.action.VPN_STOP"

        private val discoveredUrls = ConcurrentHashMap.newKeySet<String>()
        private var isRunning = false

        fun isRunning(): Boolean = isRunning

        fun start(context: android.content.Context) {
            val intent = Intent(context, TrafficSnifferService::class.java).apply {
                action = ACTION_START
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: android.content.Context) {
            val intent = Intent(context, TrafficSnifferService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun requestVpnPermission(context: android.content.Context): Intent? {
            return VpnService.prepare(context)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var mediaRepository: MediaRepository
    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile private var processing = false

    // 媒体文件扩展名和 MIME 类型
    private val imageExts = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "heif", "avif", "tiff")
    private val videoExts = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v")

    override fun onCreate() {
        super.onCreate()
        mediaRepository = MediaRepository()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
            else -> startVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) return
        try {
            val builder = Builder()
                .setSession("FloaterCapture")
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .setMtu(1500)
                .setBlocking(true)

            vpnInterface = builder.establish() ?: throw Exception("VPN failed")
            isRunning = true
            Log.d(TAG, "VPN started")

            startForeground(3001, NotificationCompat.Builder(this, FloaterApp.CHANNEL_SERVICE)
                .setContentTitle("FloaterCapture")
                .setContentText("Traffic sniffing active")
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setOngoing(true)
                .setContentIntent(PendingIntent.getActivity(this, 0,
                    packageManager.getLaunchIntentForPackage(packageName),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                .build())

            serviceScope.launch { processPackets() }
        } catch (e: Exception) {
            Log.e(TAG, "VPN start error", e)
            isRunning = false
            stopSelf()
        }
    }

    private suspend fun processPackets() {
        val fd = vpnInterface ?: return
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(65535)
        processing = true

        // 保存目录
        val saveDir = File(getExternalFilesDir(null), "FloaterCapture/Sniffed")
        if (!saveDir.exists()) saveDir.mkdirs()

        try {
            while (processing) {
                val len = input.read(buf)
                if (len <= 0) continue

                val packet = buf.copyOf(len)
                val proto = (packet[9].toInt() and 0xFF)
                if (proto != 6) { output.write(packet); continue } // TCP only

                val ipHdr = (packet[0].toInt() and 0x0F) * 4
                val tcpHdr = ((packet[ipHdr + 12].toInt() and 0xF0) shr 4) * 4
                val dataOff = ipHdr + tcpHdr
                if (len <= dataOff) { output.write(packet); continue }

                val payload = packet.copyOfRange(dataOff, len)

                // 尝试从 payload 中提取 HTTP 响应并保存媒体数据
                trySaveHttpMedia(payload, saveDir)

                output.write(packet) // 直通
            }
        } catch (e: Exception) {
            Log.e(TAG, "Packet error", e)
        }
    }

    /**
     * 核心：从 TCP payload 中提取 HTTP 响应，如果 Content-Type 是图片/视频，直接保存文件
     */
    private fun trySaveHttpMedia(payload: ByteArray, saveDir: File) {
        try {
            val maxLen = payload.size.coerceAtMost(16384)
            val data = String(payload, 0, maxLen, Charsets.UTF_8)

            // 只处理 HTTP 响应
            if (!data.startsWith("HTTP/")) return

            // 提取 Content-Type
            val ctMatch = Regex("Content-Type:\\s*([^\\r\\n]+)", RegexOption.IGNORE_CASE).find(data)
            val contentType = ctMatch?.groupValues?.get(1)?.trim()?.lowercase() ?: return

            val isImage = contentType.startsWith("image/") && contentType != "image/svg+xml"
            val isVideo = contentType.startsWith("video/")

            if (!isImage && !isVideo) return

            // 找到 HTTP body 起始位置（\r\n\r\n）
            val bodyStart = findHttpBodyStart(payload)
            if (bodyStart < 0 || bodyStart >= payload.size) return

            val mediaData = payload.copyOfRange(bodyStart, payload.size)
            if (mediaData.size < 100) return // 太小忽略

            // 生成文件名
            val ext = when {
                contentType.contains("jpeg") || contentType.contains("jpg") -> "jpg"
                contentType.contains("png") -> "png"
                contentType.contains("gif") -> "gif"
                contentType.contains("webp") -> "webp"
                contentType.contains("mp4") -> "mp4"
                contentType.contains("webm") -> "webm"
                contentType.contains("avi") -> "avi"
                else -> "dat"
            }

            val ts = SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(Date())
            val fileName = "sniffed_${ts}.${ext}"
            val file = File(saveDir, fileName)

            // 保存文件
            file.writeBytes(mediaData)

            val type = if (isImage) MediaType.IMAGE else MediaType.VIDEO

            Log.d(TAG, "Saved: $fileName (${mediaData.size} bytes, $contentType)")

            // 创建 MediaItem 并通知
            val item = MediaItem(
                id = UUID.randomUUID().toString(),
                type = type,
                url = file.absolutePath,
                sourcePackage = "network",
                sourceAppName = "流量抓包",
                description = "VPN: $fileName (${formatSize(mediaData.size.toLong())})",
                mimeType = contentType,
                fileSize = mediaData.size.toLong(),
                isDownloaded = true,
                localFilePath = file.absolutePath,
                timestamp = System.currentTimeMillis()
            )

            serviceScope.launch {
                try {
                    val id = mediaRepository.insert(item)
                    val intent = Intent(FloatingWindowService.ACTION_MEDIA_FOUND).apply {
                        putExtra(FloatingWindowService.EXTRA_MEDIA_TYPE, type.name)
                        putExtra(FloatingWindowService.EXTRA_MEDIA_ID, id)
                    }
                    LocalBroadcastManager.getInstance(this@TrafficSnifferService)
                        .sendBroadcast(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Insert error", e)
                }
            }
        } catch (e: Exception) {
            // ignore parse errors
        }
    }

    private fun findHttpBodyStart(data: ByteArray): Int {
        for (i in 0 until data.size - 3) {
            if (data[i] == 0x0D.toByte() && data[i+1] == 0x0A.toByte() &&
                data[i+2] == 0x0D.toByte() && data[i+3] == 0x0A.toByte()) {
                return i + 4
            }
        }
        return -1
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${"%.1f".format(bytes.toDouble() / 1024 / 1024)} MB"
        }
    }

    private fun stopVpn() {
        isRunning = false
        processing = false
        serviceScope.cancel()
        try { vpnInterface?.close() } catch (e: Exception) {}
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
