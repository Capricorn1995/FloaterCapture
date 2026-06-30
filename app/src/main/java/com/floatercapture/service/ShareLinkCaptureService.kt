package com.floatercapture.service

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.floatercapture.data.model.MediaItem
import com.floatercapture.data.model.MediaType
import com.floatercapture.data.repository.MediaRepository
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object ShareLinkCaptureService {

    private const val TAG = "ShareLinkCapture"

    private val seenUrls = ConcurrentHashMap.newKeySet<String>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var mediaRepository: MediaRepository? = null

    fun init(repo: MediaRepository) {
        mediaRepository = repo
    }

    fun reset() {
        seenUrls.clear()
    }

    fun destroy() {
        scope.cancel()
    }

    fun scanForShareLinks(
        event: AccessibilityEvent?,
        rootNode: AccessibilityNodeInfo?,
        packageName: String
    ) {
        if (event == null || rootNode == null) return

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        try {
            scanNodeForShareUrl(rootNode, packageName)
        } catch (e: Exception) {
            Log.e(TAG, "scan failed", e)
        }
    }

    private fun scanNodeForShareUrl(node: AccessibilityNodeInfo, packageName: String) {
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val combined = "$text $contentDesc"

        val urlPattern = Regex("https?://[^\\s<>\"'`\\)\\]};,]+", RegexOption.IGNORE_CASE)
        val matches = urlPattern.findAll(combined)

        for (match in matches) {
            val url = cleanUrl(match.value)
            if (url.isBlank() || url.length < 15) continue
            if (!seenUrls.add(url)) continue

            Log.d(TAG, "found share link: $url")

            val repo = mediaRepository ?: continue

            val mediaItem = MediaItem(
                id = UUID.randomUUID().toString(),
                type = MediaType.OTHER,
                url = url,
                sourcePackage = packageName,
                sourceAppName = getAppName(packageName),
                description = "share: ${url.take(80)}",
                mimeType = "text/html",
                timestamp = System.currentTimeMillis()
            )

            scope.launch {
                try {
                    repo.insert(mediaItem)
                    tryResolveMediaFromUrl(url, packageName, repo)
                } catch (e: Exception) {
                    Log.e(TAG, "save link failed", e)
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                scanNodeForShareUrl(child, packageName)
            } catch (e: Exception) {}
        }
    }

    private suspend fun tryResolveMediaFromUrl(
        url: String,
        sourcePackage: String,
        repo: MediaRepository
    ) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent",
                    "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return
            response.close()

            val extractedUrls = extractMediaFromHtml(body)

            for ((index, mediaUrl) in extractedUrls.withIndex()) {
                if (mediaUrl.isBlank()) continue
                if (!seenUrls.add(mediaUrl)) continue

                val type = when {
                    mediaUrl.contains(".mp4") || mediaUrl.contains("video") -> MediaType.VIDEO
                    else -> MediaType.IMAGE
                }

                val mediaItem = MediaItem(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    url = mediaUrl,
                    sourcePackage = sourcePackage,
                    sourceAppName = getAppName(sourcePackage),
                    description = "resolved #${index + 1}",
                    mimeType = if (type == MediaType.VIDEO) "video/mp4" else "image/jpeg",
                    timestamp = System.currentTimeMillis()
                )

                repo.insert(mediaItem)
                Log.d(TAG, "resolved media[$type]: $mediaUrl")
            }
        } catch (e: Exception) {
            Log.w(TAG, "resolve failed: ${e.message}")
        }
    }

    private fun extractMediaFromHtml(html: String): List<String> {
        val results = mutableListOf<String>()

        val imgPattern = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        imgPattern.findAll(html).forEach { match ->
            val src = match.groupValues[1]
            if (src.startsWith("http") && isMediaUrl(src)) results.add(src)
        }

        val videoPattern = Regex("<video[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        videoPattern.findAll(html).forEach { match ->
            val src = match.groupValues[1]
            if (src.startsWith("http") && isMediaUrl(src)) results.add(src)
        }

        val ogPattern = Regex("<meta[^>]+property=[\"']og:(image|video)[\"'][^>]+content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        ogPattern.findAll(html).forEach { match ->
            results.add(match.groupValues[2])
        }

        val twitterPattern = Regex("<meta[^>]+name=[\"']twitter:image[\"'][^>]+content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        twitterPattern.findAll(html).forEach { match ->
            results.add(match.groupValues[1])
        }

        val mediaUrlPatterns = listOf(
            Regex("https?://[^\\s<>\"']+\\.(jpg|jpeg|png|gif|webp|mp4|mov)(\\?[^\\s<>\"']*)?\$", RegexOption.IGNORE_CASE),
            Regex("https?://[^\\s<>\"']+(video|image|photo|media)/[^\\s<>\"']+", RegexOption.IGNORE_CASE),
            Regex("https?://p\\d+-[^\\s<>\"']+\\.(jpg|jpeg|png|webp|heic)", RegexOption.IGNORE_CASE),
            Regex("https?://[^\\s<>\"']+(cos|cdn|oss|img)[^\\s<>\"']+\\.(jpg|jpeg|png|webp|mp4|m3u8)", RegexOption.IGNORE_CASE)
        )
        for (pattern in mediaUrlPatterns) {
            pattern.findAll(html).forEach { match -> results.add(match.value) }
        }

        return results.distinct()
    }

    private fun isMediaUrl(url: String): Boolean {
        val ext = url.substringAfterLast('.', "").substringBefore('?').substringBefore('#').lowercase()
        return ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg",
            "mp4", "mkv", "avi", "mov", "webm", "m3u8", "mp3", "wav", "aac")
    }

    private fun cleanUrl(url: String): String {
        var cleaned = url
        while (cleaned.isNotEmpty() && cleaned.last() in ".,;:!?)\"'>]}") {
            cleaned = cleaned.dropLast(1)
        }
        return cleaned
    }

    private fun getAppName(packageName: String): String {
        return try {
            com.floatercapture.util.AppRulesLoader.getRule(packageName)?.appName ?: packageName
        } catch (e: Exception) {
            packageName
        }
    }
}
