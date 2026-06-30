package com.floatercapture.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.floatercapture.FloaterApp
import com.floatercapture.data.model.MediaItem
import com.floatercapture.data.model.MediaType
import com.floatercapture.data.repository.MediaRepository
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TrafficSnifferService : VpnService() {

    companion object {
        const val TAG = "TrafficSniffer"
        const val VPN_MTU = 1500
        const val VPN_ADDRESS = "10.0.0.2"
        const val VPN_ROUTE = "0.0.0.0"
        const val VPN_DNS = "8.8.8.8"
        const val NOTIFICATION_ID = 3001
        const val MAX_PACKET_SIZE = 65535

        const val ACTION_START = "com.floatercapture.action.VPN_START"
        const val ACTION_STOP = "com.floatercapture.action.VPN_STOP"

        val CDN_PATTERNS = listOf(
            "cdn", "cos", "oss", "img", "image", "video", "media", "pic", "photo",
            "xhscdn", "sns-img", "sns-avatar", "pstatp", "douyincdn", "bytedns", "snssdk",
            "sinaimg", "sinajs", "zhimg", "wxqcloud", "mmbiz", "qpic",
            "hdslb", "bilivideo", "yximgs", "kwimgs", "kscdn",
            "alicdn", "taobaocdn", "tbcache", "360buyimg", "jdcdn",
            "cdninstagram", "fbcdn", "twimg", "tdcdn", "ytimg", "googlevideo"
        )

        val MEDIA_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "heif", "avif",
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m3u8", "ts", "m4v",
            "mp3", "wav", "aac", "ogg", "flac", "m4a"
        )

        private val discoveredUrls = ConcurrentHashMap.newKeySet<String>()
        private var isRunning = false

        fun isRunning(): Boolean = isRunning

        fun resetDiscoveredUrls() {
            discoveredUrls.clear()
        }

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
                .setSession("FloaterCapture Traffic Sniffer")
                .addAddress(VPN_ADDRESS, 32)
                .addRoute(VPN_ROUTE, 0)
                .addDnsServer(VPN_DNS)
                .setMtu(VPN_MTU)
                .setBlocking(true)

            vpnInterface = builder.establish()
                ?: throw IllegalStateException("VPN establishment failed")

            isRunning = true
            Log.d(TAG, "VPN started")

            startForeground(NOTIFICATION_ID, createNotification())

            serviceScope.launch { processPackets() }
        } catch (e: Exception) {
            Log.e(TAG, "VPN start failed", e)
            isRunning = false
            stopSelf()
        }
    }

    private suspend fun processPackets() {
        val vpnFd = vpnInterface ?: return
        val input = FileInputStream(vpnFd.fileDescriptor)
        val output = FileOutputStream(vpnFd.fileDescriptor)
        val buffer = ByteArray(MAX_PACKET_SIZE)

        try {
            processing = true
            while (processing) {
                val length = input.read(buffer)
                if (length <= 0) continue

                val packet = buffer.copyOf(length)
                val protocol = (packet[9].toInt() and 0xFF)

                if (protocol != 6) {
                    output.write(packet)
                    continue
                }

                val ipHeaderLen = (packet[0].toInt() and 0x0F) * 4
                val tcpHeaderLen = ((packet[ipHeaderLen + 12].toInt() and 0xF0) shr 4) * 4
                val dataOffset = ipHeaderLen + tcpHeaderLen

                if (length <= dataOffset) {
                    output.write(packet)
                    continue
                }

                val payload = packet.copyOfRange(dataOffset, length)
                extractHttpInfo(payload)
                output.write(packet)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Packet processing error", e)
        }
    }

    private fun extractHttpInfo(payload: ByteArray) {
        try {
            val maxLen = payload.size.coerceAtMost(8192)
            val data = String(payload, 0, maxLen, Charsets.UTF_8)

            if (!data.startsWith("GET ") && !data.startsWith("POST ")) return

            val hostMatch = Regex("Host:\\s*([^\\r\\n]+)", RegexOption.IGNORE_CASE).find(data) ?: return
            val host = hostMatch.groupValues[1].trim()

            val pathMatch = Regex("(GET|POST)\\s+([^\\s]+)\\s+HTTP").find(data) ?: return
            val path = pathMatch.groupValues[2]

            val fullUrl = "http://$host$path"

            if (!isLikelyMediaUrl(fullUrl, host)) return
            if (!discoveredUrls.add(fullUrl)) return

            val type = inferType(fullUrl)

            Log.d(TAG, "sniffed: $fullUrl")

            val mediaItem = MediaItem(
                id = UUID.randomUUID().toString(),
                type = type,
                url = fullUrl,
                sourcePackage = "network",
                sourceAppName = "network",
                description = "VPN: ${fullUrl.take(80)}",
                mimeType = inferMimeType(fullUrl),
                timestamp = System.currentTimeMillis()
            )

            serviceScope.launch {
                try {
                    val id = mediaRepository.insert(mediaItem)
                    val intent = Intent(FloatingWindowService.ACTION_MEDIA_FOUND).apply {
                        putExtra(FloatingWindowService.EXTRA_MEDIA_TYPE, type.name)
                        putExtra(FloatingWindowService.EXTRA_MEDIA_ID, id)
                    }
                    LocalBroadcastManager.getInstance(this@TrafficSnifferService)
                        .sendBroadcast(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Save failed", e)
                }
            }
        } catch (e: Exception) {
            // ignore parse errors
        }
    }

    private fun isLikelyMediaUrl(url: String, host: String): Boolean {
        val ext = url.substringAfterLast('.', "").substringBefore('?').substringBefore('#').lowercase()
        if (ext in MEDIA_EXTENSIONS) return true

        val hostLower = host.lowercase()
        for (pattern in CDN_PATTERNS) {
            if (hostLower.contains(pattern)) return true
        }

        val path = url.lowercase()
        for (keyword in listOf("/img/", "/image/", "/video/", "/photo/", "/media/",
            "/pic/", "/upload/", "/thumbnail/", "/avatar/", "/cover/")) {
            if (path.contains(keyword)) return true
        }

        return false
    }

    private fun inferType(url: String): MediaType {
        val ext = url.substringAfterLast('.', "").substringBefore('?').substringBefore('#').lowercase()
        return when (ext) {
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m3u8", "ts", "m4v" -> MediaType.VIDEO
            "mp3", "wav", "aac", "ogg", "flac", "m4a" -> MediaType.OTHER
            else -> MediaType.IMAGE
        }
    }

    private fun inferMimeType(url: String): String {
        val ext = url.substringAfterLast('.', "").substringBefore('?').substringBefore('#').lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "m3u8" -> "application/x-mpegURL"
            else -> "application/octet-stream"
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, FloaterApp.CHANNEL_SERVICE)
            .setContentTitle("FloaterCapture traffic sniffer")
            .setContentText("Monitoring network media")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun stopVpn() {
        isRunning = false
        processing = false
        serviceScope.cancel()
        try { vpnInterface?.close() } catch (e: Exception) {}
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "VPN stopped")
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
