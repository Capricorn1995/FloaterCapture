package com.floatercapture.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
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
 * 实现策略：
 * 1. 监听 TYPE_WINDOW_STATE_CHANGED 和 TYPE_WINDOW_CONTENT_CHANGED 事件
 * 2. 遍历根节点树，识别图片/视频/媒体节点
 * 3. 从节点的多个属性中提取 URL（contentDescription / text / viewIdResourceName / 节点层级 / AccessibilityNodeInfo extras）
 * 4. 使用 URL 集合去重，避免同一资源被多次添加
 * 5. 过滤自身应用界面
 */
class MediaCaptureService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mediaRepository = MediaRepository()

    private var currentPackageName: String = ""
    private var currentAppRule: AppRule? = null

    // 已抓取 URL 集合（去重），key 是 URL 的 hash
    private val seenUrls = ConcurrentHashMap.newKeySet<String>()

    companion object {
        private const val TAG = "MediaCapture"
        private const val SELF_PACKAGE = "com.floatercapture"

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
        // 服务连接时清空去重集合
        seenUrls.clear()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    val packageName = event.packageName?.toString() ?: return

                    // 跳过自身应用
                    if (packageName == SELF_PACKAGE) return

                    // 更新当前包名和规则
                    if (packageName != currentPackageName) {
                        currentPackageName = packageName
                        currentAppRule = AppRulesLoader.getRule(packageName)
                        Log.d(TAG, "切换到 App: $packageName (${currentAppRule?.appName ?: "未适配"})")
                    }
                    scanCurrentWindow(packageName)
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
        serviceScope.cancel()
        super.onDestroy()
    }

    // ==================== 窗口扫描 ====================

    /**
     * 扫描当前窗口，从根节点开始递归遍历
     */
    private fun scanCurrentWindow(packageName: String) {
        val rootNode = rootInActiveWindow ?: return
        try {
            traverseNode(rootNode, packageName)
        } catch (e: Exception) {
            Log.e(TAG, "扫描失败", e)
        } finally {
            try {
                rootNode.recycle()
            } catch (e: Exception) {
                // 忽略回收错误
            }
        }
    }

    /**
     * 递归遍历节点树，识别并提取媒体节点
     */
    private fun traverseNode(node: AccessibilityNodeInfo, packageName: String) {
        try {
            // 1. 尝试从节点的多个属性中提取 URL
            extractUrlFromAnyAttribute(node, packageName)

            // 2. 递归遍历子节点
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    traverseNode(child, packageName)
                } catch (e: Exception) {
                    // 节点可能已被回收，忽略错误
                }
            }
        } catch (e: Exception) {
            // 节点可能已被回收，忽略错误
        }
    }

    // ==================== URL 提取（多策略）====================

    /**
     * 从节点的任意可用属性中提取 URL
     * 这是核心方法 - 多种提取策略：
     * 1. contentDescription 中的 URL
     * 2. text 中的 URL
     * 3. viewIdResourceName 中的 URL（解码后）
     * 4. 节点树向上层级中的 URL（很多 App 把 URL 放在父节点的 contentDescription）
     * 5. 通过子节点递归查找
     */
    private fun extractUrlFromAnyAttribute(node: AccessibilityNodeInfo, packageName: String) {
        val className = node.className?.toString() ?: ""

        // 跳过系统级和布局类节点
        if (shouldSkipNode(className, node)) return

        // 策略 1: contentDescription
        val contentDesc = node.contentDescription?.toString() ?: ""
        extractUrlFromContent(contentDesc, node, packageName)?.let { return }

        // 策略 2: text
        val text = node.text?.toString() ?: ""
        extractUrlFromContent(text, node, packageName)?.let { return }

        // 策略 3: viewIdResourceName（URL 可能被 URL-encoded 嵌入）
        val viewId = node.viewIdResourceName ?: ""
        if (viewId.isNotEmpty()) {
            val decoded = try {
                java.net.URLDecoder.decode(viewId, "UTF-8")
            } catch (e: Exception) {
                viewId
            }
            extractUrlFromContent(decoded, node, packageName)?.let { return }
        }

        // 策略 4: 检查节点的所有可用属性
        // 一些自定义 View 会把 URL 存到节点的 hintText / paneTitle 等
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            node.hintText?.toString()?.let { hint ->
                extractUrlFromContent(hint, node, packageName)?.let { return }
            }
        }
        node.paneTitle?.toString()?.let { pane ->
            extractUrlFromContent(pane, node, packageName)?.let { return }
        }
        node.tooltipText?.toString()?.let { tooltip ->
            extractUrlFromContent(tooltip, node, packageName)?.let { return }
        }

        // 策略 5: 遍历子节点查找 URL（适用于子 ImageView 包含 URL 的情况）
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val childDesc = child.contentDescription?.toString() ?: ""
            val childText = child.text?.toString() ?: ""
            val combined = "$childDesc $childText"
            extractUrlFromContent(combined, node, packageName)?.let { return }
        }
    }

    /**
     * 提取节点中的第一个有效 URL 并创建 MediaItem
     * @return 成功提取返回 true，否则 null
     */
    private fun extractUrlFromContent(
        content: String,
        contextNode: AccessibilityNodeInfo,
        packageName: String
    ): Boolean? {
        if (content.isBlank()) return null

        val url = extractFirstUrl(content) ?: return null
        return processUrl(url, contextNode, packageName)
    }

    /**
     * 从字符串中提取第一个 URL
     * 支持多种 URL 格式：
     * - http:// / https:// 直接 URL
     * - URL-encoded 格式
     * - 自定义协议
     */
    private fun extractFirstUrl(content: String): String? {
        if (content.isBlank()) return null

        // 策略 1: 直接的 http/https URL
        val httpPattern = Regex(
            """https?://[^\s<>"'\u0020\u00A0]+""",
            RegexOption.IGNORE_CASE
        )
        httpPattern.find(content)?.let { match ->
            return cleanUrl(match.value)
        }

        // 策略 2: URL-encoded 格式
        val encodedPattern = Regex(
            """https?%3A%2F%2F[^\s<>"'\u0020\u00A0]+""",
            RegexOption.IGNORE_CASE
        )
        encodedPattern.find(content)?.let { match ->
            return try {
                cleanUrl(java.net.URLDecoder.decode(match.value, "UTF-8"))
            } catch (e: Exception) {
                null
            }
        }

        // 策略 3: 自定义协议（如 cdn://, file://, content://, data:image, data:video）
        val dataUriPattern = Regex(
            """data:(image|video|audio)/[a-z]+;base64,[A-Za-z0-9+/=]+""",
            RegexOption.IGNORE_CASE
        )
        dataUriPattern.find(content)?.let { match ->
            return match.value
        }

        // 策略 4: cdn/file/content 等协议
        val customProtocolPattern = Regex(
            """(file|content|asset|cdn|res|drawable)://[^\s<>"'\u0020\u00A0]+""",
            RegexOption.IGNORE_CASE
        )
        customProtocolPattern.find(content)?.let { match ->
            return match.value
        }

        return null
    }

    /**
     * 清理 URL：去除尾部标点符号
     */
    private fun cleanUrl(url: String): String {
        var cleaned = url
        // 去除尾部常见标点
        while (cleaned.isNotEmpty() && cleaned.last() in ".,;:!?)\"'>]}") {
            cleaned = cleaned.dropLast(1)
        }
        return cleaned
    }

    /**
     * 处理提取到的 URL：验证、去重、创建 MediaItem
     */
    private fun processUrl(
        url: String,
        contextNode: AccessibilityNodeInfo,
        packageName: String
    ): Boolean {
        // 验证 URL 有效性
        if (!isAcceptableMediaUrl(url)) return false

        // 去重（使用 URL 本身做 key）
        val urlKey = url.hashCode().toString()
        if (!seenUrls.add(urlKey)) return false  // 重复 URL

        // 判断媒体类型
        val type = inferMediaType(url, contextNode)

        // 提取描述
        val description = contextNode.contentDescription?.toString()
            ?: contextNode.text?.toString()
            ?: ""

        createMediaItem(
            type = type,
            url = url,
            packageName = packageName,
            description = description
        )
        return true
    }

    /**
     * 验证 URL 是否为可接受的媒体 URL
     */
    private fun isAcceptableMediaUrl(url: String): Boolean {
        if (url.isBlank() || url.length < 10) return false

        // 直接的 data URI 接受
        if (url.startsWith("data:image/") || url.startsWith("data:video/") || url.startsWith("data:audio/")) {
            return true
        }

        // 自定义协议接受（用于 file://, content://, asset:// 等）
        if (url.startsWith("file://") || url.startsWith("content://") ||
            url.startsWith("asset://") || url.startsWith("android.resource://")) {
            return true
        }

        // http/https URL 需要验证扩展名
        if (url.startsWith("http://") || url.startsWith("https://")) {
            val ext = FileHelper.getFileExtension(url)
            val mediaExts = setOf(
                "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "heif",
                "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m3u8", "ts",
                "mp3", "wav", "aac", "ogg", "flac", "m4a",
                // 一些 App 用 jpeg、webp、avif 等变体
                "avif", "tiff", "tif"
            )
            return ext in mediaExts
        }

        return false
    }

    /**
     * 推断媒体类型
     */
    private fun inferMediaType(url: String, node: AccessibilityNodeInfo): MediaType {
        // 1. 根据 URL 扩展名
        val ext = FileHelper.getFileExtension(url)
        when (ext) {
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m3u8", "ts" -> return MediaType.VIDEO
            "mp3", "wav", "aac", "ogg", "flac", "m4a" -> return MediaType.OTHER
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "heif", "avif", "tiff" -> return MediaType.IMAGE
        }

        // 2. 根据节点类名
        val className = node.className?.toString() ?: ""
        when {
            className.contains("VideoView", ignoreCase = true) -> return MediaType.VIDEO
            className.contains("Video", ignoreCase = true) -> return MediaType.VIDEO
            className.contains("Player", ignoreCase = true) -> return MediaType.VIDEO
            className.contains("Image", ignoreCase = true) -> return MediaType.IMAGE
            className.contains("Photo", ignoreCase = true) -> return MediaType.IMAGE
        }

        return MediaType.OTHER
    }

    /**
     * 判断节点是否应该跳过（不处理）
     */
    private fun shouldSkipNode(className: String, node: AccessibilityNodeInfo): Boolean {
        // 跳过无内容的纯布局节点
        if (className.isEmpty()) return true

        // 跳过常见的纯布局类（它们通常不包含 URL）
        val layoutClasses = setOf(
            "android.widget.FrameLayout",
            "android.widget.LinearLayout",
            "android.widget.RelativeLayout",
            "androidx.constraintlayout.widget.ConstraintLayout",
            "android.widget.GridLayout",
            "androidx.coordinatorlayout.widget.CoordinatorLayout"
        )
        // 但这些布局如果有子节点需要继续遍历，所以不直接 return true
        // 暂时不跳过，让递归继续

        return false
    }

    // ==================== MediaItem创建 ====================

    /**
     * 创建MediaItem并添加到仓库，发送LocalBroadcast通知
     */
    private fun createMediaItem(
        type: MediaType,
        url: String,
        packageName: String,
        description: String
    ) {
        val appName = currentAppRule?.appName ?: getFriendlyAppName(packageName)
        val mediaItem = MediaItem(
            id = UUID.randomUUID().toString(),
            type = type,
            url = url,
            sourcePackage = packageName,
            sourceAppName = appName,
            description = description.take(200),
            mimeType = FileHelper.getMimeType(url),
            timestamp = System.currentTimeMillis()
        )

        serviceScope.launch {
            try {
                val id = mediaRepository.insert(mediaItem)
                Log.d(TAG, "已捕获: [$type] $url")
                // 通过LocalBroadcast发送媒体发现广播
                val intent = Intent(FloatingWindowService.ACTION_MEDIA_FOUND).apply {
                    putExtra(FloatingWindowService.EXTRA_MEDIA_TYPE, type.name)
                    putExtra(FloatingWindowService.EXTRA_MEDIA_ID, id)
                }
                LocalBroadcastManager.getInstance(this@MediaCaptureService)
                    .sendBroadcast(intent)
            } catch (e: Exception) {
                Log.e(TAG, "保存媒体失败", e)
            }
        }
    }

    /**
     * 获取友好的应用名（未知应用时使用包名）
     */
    private fun getFriendlyAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}
