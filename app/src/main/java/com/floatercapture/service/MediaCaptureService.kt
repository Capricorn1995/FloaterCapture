package com.floatercapture.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.floatercapture.data.model.AppRule
import com.floatercapture.data.model.MediaItem
import com.floatercapture.data.model.MediaType
import com.floatercapture.data.repository.MediaRepository
import com.floatercapture.util.AppRulesLoader
import com.floatercapture.util.FileHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 核心无障碍服务 - 监听其他App界面并提取媒体内容
 *
 * 重要发现：现代 App（小红书、抖音、视频号等）的真实媒体 URL
 * 几乎从不暴露在 AccessibilityNodeInfo 的任何公开属性中。
 * 它们被存储在自定义 View 的私有字段里。
 *
 * 因此本实现采用"**节点驱动**"策略：
 * 1. 仍然尝试从节点属性中提取 URL（如果运气好可以拿到）
 * 2. 对所有 ImageView/PhotoView/VideoView 类型的节点都创建 MediaItem，
 *    记录其位置信息、viewId 哈希等元数据，URL 字段填入
 *    `node://` 协议的合成占位
 * 3. 用户可在「媒体预览」中使用截屏裁切功能实际保存图片
 */
class MediaCaptureService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mediaRepository = MediaRepository()

    private var currentPackageName: String = ""
    private var currentAppRule: AppRule? = null

    // 已抓取 URL/节点 ID 集合（去重）
    private val seenKeys = ConcurrentHashMap.newKeySet<String>()

    companion object {
        private const val TAG = "MediaCapture"
        private const val SELF_PACKAGE = "com.floatercapture"

        // 占位 URL 前缀
        private const val NODE_URI_PREFIX = "node://"

        // 需要抓取的节点类名后缀
        private val IMAGE_CLASS_HINTS = listOf(
            "ImageView", "ImageView_", "Image",
            "PhotoView", "PinchImageView", "Photo",
            "Picture", "Banner", "Cover", "Avatar",
            "Gif", "AnimatedGif", "MMNeat", "SnsImage",
            "AsyncMask", "NetworkImage", "URLImage",
            "SimpleDraweeView", "FrescoImage", "IgImage"
        )

        private val VIDEO_CLASS_HINTS = listOf(
            "VideoView", "TextureView", "SurfaceView",
            "PlayerView", "MediaPlayer", "IjkVideo",
            "ExoPlayer", "GeckoPlayer", "Video", "Player"
        )

        /**
         * 检查无障碍服务是否已启用
         */
        fun isServiceEnabled(context: android.content.Context): Boolean {
            val serviceName = "${context.packageName}/.service.MediaCaptureService"
            val enabledServices = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.contains(serviceName) ||
                   enabledServices.contains("com.floatercapture/.service.MediaCaptureService") ||
                   enabledServices.contains("com.floatercapture")
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "无障碍服务已连接")
        seenKeys.clear()

        // 注册重置广播
        val resetFilter = android.content.IntentFilter(FloatingWindowService.ACTION_RESET_CAPTURE)
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
            .registerReceiver(resetReceiver, resetFilter)
    }

    private val resetReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == FloatingWindowService.ACTION_RESET_CAPTURE) {
                seenKeys.clear()
                Log.d(TAG, "已重置去重集合，下次扫描将重新发现所有节点")
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    val packageName = event.packageName?.toString() ?: return

                    if (packageName == SELF_PACKAGE) return

                    if (packageName != currentPackageName) {
                        currentPackageName = packageName
                        currentAppRule = AppRulesLoader.getRule(packageName)
                        Log.d(TAG, "切换到 App: $packageName (${currentAppRule?.appName ?: "未适配"})")
                        // 切换 App 时清空分享链接缓存
                        ShareLinkCaptureService.reset()
                    }

                    // 1. 节点驱动嗅探（现有逻辑）
                    scanCurrentWindow(packageName)

                    // 2. 分享链接嗅探（新）
                    ShareLinkCaptureService.scanForShareLinks(
                        event,
                        rootInActiveWindow,
                        packageName
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理事件失败", e)
        }
    }

    override fun onInterrupt() {
        currentPackageName = ""
        currentAppRule = null
    }

    override fun onDestroy() {
        try {
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
                .unregisterReceiver(resetReceiver)
        } catch (e: Exception) {}
        serviceScope.cancel()
        super.onDestroy()
    }

    // ==================== 窗口扫描 ====================

    private fun scanCurrentWindow(packageName: String) {
        val rootNode = rootInActiveWindow ?: return
        try {
            traverseNode(rootNode, packageName)
        } catch (e: Exception) {
            Log.e(TAG, "扫描失败", e)
        } finally {
            try {
                rootNode.recycle()
            } catch (e: Exception) {}
        }
    }

    private fun traverseNode(node: AccessibilityNodeInfo, packageName: String) {
        try {
            processNode(node, packageName)

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    traverseNode(child, packageName)
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {}
    }

    // ==================== 节点处理 ====================

    /**
     * 处理单个节点：先尝试提取 URL，失败则根据节点类名判断是否为媒体节点并创建占位
     */
    private fun processNode(node: AccessibilityNodeInfo, packageName: String) {
        val className = node.className?.toString() ?: ""
        if (className.isEmpty()) return

        // 1. 优先尝试从节点属性中提取真实 URL
        if (tryExtractUrlFromAttributes(node, packageName)) return

        // 2. 如果节点是图片/视频类型，生成占位 MediaItem
        val type = classifyNode(className) ?: return

        // 跳过不可见节点
        if (!isNodeVisible(node)) return

        // 用节点 viewId + 位置 + 类名做去重 key
        val key = buildNodeKey(node, className, type)
        if (!seenKeys.add(key)) return

        // 合成占位 URL
        val nodeId = node.viewIdResourceName ?: ""
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val posInfo = "${rect.left},${rect.top},${rect.width()}x${rect.height()}"
        val syntheticUrl = "${NODE_URI_PREFIX}${packageName}/${className}/${nodeId.hashCode()}/${UUID.randomUUID()}"

        val description = buildString {
            append(type.displayName)
            if (nodeId.isNotEmpty()) append(" · ").append(nodeId.substringAfterLast('/'))
            append(" · ").append(posInfo)
        }

        createMediaItem(
            type = type,
            url = syntheticUrl,
            packageName = packageName,
            description = description,
            nodeBounds = rect
        )
    }

    private fun tryExtractUrlFromAttributes(
        node: AccessibilityNodeInfo,
        packageName: String
    ): Boolean {
        // 拼接所有可读属性
        val contentDesc = node.contentDescription?.toString().orEmpty()
        val text = node.text?.toString().orEmpty()
        val viewId = node.viewIdResourceName.orEmpty()
        val decodedId = if (viewId.isNotEmpty()) {
            try {
                java.net.URLDecoder.decode(viewId, "UTF-8")
            } catch (e: Exception) { viewId }
        } else ""

        val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            node.hintText?.toString().orEmpty()
        } else ""
        val pane = node.paneTitle?.toString().orEmpty()
        val tooltip = node.tooltipText?.toString().orEmpty()

        val combined = listOf(contentDesc, text, decodedId, hint, pane, tooltip)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        if (combined.isBlank()) return false

        // 收集子节点内容
        val sb = StringBuilder(combined)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val cd = child.contentDescription?.toString().orEmpty()
            val ct = child.text?.toString().orEmpty()
            if (cd.isNotBlank()) sb.append(' ').append(cd)
            if (ct.isNotBlank()) sb.append(' ').append(ct)
        }

        val url = extractFirstUrl(sb.toString()) ?: return false
        if (!isAcceptableMediaUrl(url)) return false

        val key = "url:${url.hashCode()}"
        if (!seenKeys.add(key)) return false

        val type = inferMediaTypeFromUrl(url, node)
        createMediaItem(
            type = type,
            url = url,
            packageName = packageName,
            description = (contentDesc.ifBlank { text }).take(200),
            nodeBounds = null
        )
        return true
    }

    // ==================== URL 提取正则 ====================

    private fun extractFirstUrl(content: String): String? {
        if (content.isBlank()) return null

        val httpPattern = Regex(
            """https?://[^\s<>"'`\\)\]};,]+""",
            RegexOption.IGNORE_CASE
        )
        httpPattern.find(content)?.let { return cleanUrl(it.value) }

        val encodedPattern = Regex(
            """https?%3A%2F%2F[^\s<>"'`\\)\]};,]+""",
            RegexOption.IGNORE_CASE
        )
        encodedPattern.find(content)?.let {
            return try { cleanUrl(java.net.URLDecoder.decode(it.value, "UTF-8")) } catch (e: Exception) { null }
        }

        val dataPattern = Regex(
            """data:(image|video|audio)/[a-zA-Z0-9.+-]+;base64,[A-Za-z0-9+/=]{64,}""",
            RegexOption.IGNORE_CASE
        )
        dataPattern.find(content)?.let { return it.value }

        val customProto = Regex(
            """(file|content|asset|cdn|res|drawable)://[^\s<>"'`\\)\]};,]+""",
            RegexOption.IGNORE_CASE
        )
        customProto.find(content)?.let { return it.value }

        return null
    }

    private fun cleanUrl(url: String): String {
        var cleaned = url
        while (cleaned.isNotEmpty() && cleaned.last() in ".,;:!?)\"'>]}") {
            cleaned = cleaned.dropLast(1)
        }
        return cleaned
    }

    private fun isAcceptableMediaUrl(url: String): Boolean {
        if (url.isBlank() || url.length < 10) return false

        if (url.startsWith("data:")) return true
        if (url.startsWith("file://") || url.startsWith("content://") ||
            url.startsWith("asset://") || url.startsWith("android.resource://")) return true

        if (url.startsWith("http://") || url.startsWith("https://")) {
            val ext = FileHelper.getFileExtension(url)
            val mediaExts = setOf(
                "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "heif", "avif", "tiff", "tif",
                "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m3u8", "ts", "m4v",
                "mp3", "wav", "aac", "ogg", "flac", "m4a"
            )
            return ext in mediaExts
        }

        return false
    }

    private fun inferMediaTypeFromUrl(url: String, node: AccessibilityNodeInfo): MediaType {
        val ext = FileHelper.getFileExtension(url)
        when (ext) {
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m3u8", "ts", "m4v" -> return MediaType.VIDEO
            "mp3", "wav", "aac", "ogg", "flac", "m4a" -> return MediaType.OTHER
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "heif", "avif", "tiff" -> return MediaType.IMAGE
        }
        val className = node.className?.toString() ?: ""
        return when {
            className.contains("Video", ignoreCase = true) ||
            className.contains("Player", ignoreCase = true) -> MediaType.VIDEO
            else -> MediaType.OTHER
        }
    }

    // ==================== 节点分类 ====================

    /**
     * 判断节点类名是否暗示是媒体节点
     * @return 媒体类型；null 表示不是
     */
    private fun classifyNode(className: String): MediaType? {
        val lower = className.lowercase()
        // 排除系统 ViewGroup
        if (lower.startsWith("android.widget.linearlayout") ||
            lower.startsWith("android.widget.framelayout") ||
            lower.startsWith("android.widget.relativelayout") ||
            lower.startsWith("androidx.constraintlayout") ||
            lower.startsWith("android.widget.gridlayout") ||
            lower.startsWith("androidx.coordinatorlayout") ||
            lower.startsWith("android.widget.scrollview") ||
            lower.startsWith("androidx.recyclerview") ||
            lower.startsWith("androidx.viewpager") ||
            lower.startsWith("androidx.viewpager2")) {
            return null
        }

        // 视频类优先（防止 PlayerView 包含 Image 关键字）
        for (hint in VIDEO_CLASS_HINTS) {
            if (className.contains(hint, ignoreCase = true)) return MediaType.VIDEO
        }
        // 显式 VideoView 单独再判断
        if (className.contains("VideoView", ignoreCase = true)) return MediaType.VIDEO

        // 图片类
        for (hint in IMAGE_CLASS_HINTS) {
            if (className.contains(hint, ignoreCase = true)) return MediaType.IMAGE
        }

        return null
    }

    /**
     * 检查节点是否在屏幕上可见（bounds 有效且有大小）
     */
    private fun isNodeVisible(node: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() < 10 || rect.height() < 10) return false
        if (rect.right <= rect.left || rect.bottom <= rect.top) return false
        return true
    }

    private fun buildNodeKey(node: AccessibilityNodeInfo, className: String, type: MediaType): String {
        val viewId = node.viewIdResourceName ?: ""
        val rect = Rect()
        node.getBoundsInScreen(rect)
        // 用 viewId + 位置 + 类名作为去重 key
        return "node:$viewId:${rect.left},${rect.top},${rect.right},${rect.bottom}:$className"
    }

    // ==================== MediaItem 创建 ====================

    private fun createMediaItem(
        type: MediaType,
        url: String,
        packageName: String,
        description: String,
        nodeBounds: Rect?
    ) {
        val appName = currentAppRule?.appName ?: getFriendlyAppName(packageName)
        val boundsStr = nodeBounds?.let { "${it.left},${it.top},${it.right},${it.bottom}" } ?: ""
        val mediaItem = MediaItem(
            id = UUID.randomUUID().toString(),
            type = type,
            url = url,
            sourcePackage = packageName,
            sourceAppName = appName,
            description = description.take(200),
            mimeType = if (url.startsWith(NODE_URI_PREFIX)) "image/*" else FileHelper.getMimeType(url),
            timestamp = System.currentTimeMillis(),
            nodeBounds = boundsStr
        )

        serviceScope.launch {
            try {
                val id = mediaRepository.insert(mediaItem)
                if (url.startsWith(NODE_URI_PREFIX)) {
                    Log.d(TAG, "已捕获[$type]: ${description.take(60)}")
                } else {
                    Log.d(TAG, "已捕获[$type URL]: $url")
                }
                val intent = Intent(FloatingWindowService.ACTION_MEDIA_FOUND).apply {
                    putExtra(FloatingWindowService.EXTRA_MEDIA_TYPE, type.name)
                    putExtra(FloatingWindowService.EXTRA_MEDIA_ID, id)
                }
                LocalBroadcastManager.getInstance(this@MediaCaptureService).sendBroadcast(intent)
            } catch (e: Exception) {
                Log.e(TAG, "保存媒体失败", e)
            }
        }
    }

    private fun getFriendlyAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}
