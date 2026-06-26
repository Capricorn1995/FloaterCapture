package com.floatercapture.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
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

/**
 * 核心无障碍服务 - 监听其他App界面并提取媒体内容
 *
 * 监听TYPE_WINDOW_STATE_CHANGED和TYPE_WINDOW_CONTENT_CHANGED事件，
 * 遍历当前窗口的节点树，识别ImageView、VideoView和WebView节点，
 * 从中提取图片/视频/音频等媒体URL，创建MediaItem并存入仓库。
 */
class MediaCaptureService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mediaRepository = MediaRepository()

    private var currentPackageName: String = ""
    private var currentAppRule: AppRule? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 服务已连接，开始监听
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    val packageName = event.packageName?.toString() ?: return
                    // 更新当前包名和规则
                    if (packageName != currentPackageName) {
                        currentPackageName = packageName
                        currentAppRule = AppRulesLoader.getRule(packageName)
                    }
                    scanCurrentWindow(packageName)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * 递归遍历节点树，识别并提取媒体节点
     */
    private fun traverseNode(node: AccessibilityNodeInfo, packageName: String) {
        try {
            val className = node.className?.toString() ?: ""

            when {
                isImageView(className, node) -> extractImageInfo(node, packageName)
                isVideoView(className, node) -> extractVideoInfo(node, packageName)
                isWebView(className, node) -> extractWebViewContent(node, packageName)
                else -> extractUrlFromNode(node, packageName)
            }

            // 递归遍历子节点
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    traverseNode(child, packageName)
                } finally {
                    child.recycle()
                }
            }
        } catch (e: Exception) {
            // 节点可能已被回收，忽略错误
        }
    }

    // ==================== 节点类型识别 ====================

    /**
     * 判断节点是否为ImageView类型
     * 使用AppRulesLoader获取当前包名的规则进行匹配
     */
    private fun isImageView(className: String, node: AccessibilityNodeInfo): Boolean {
        // 通用 ImageView 类名检查
        if (className.contains("ImageView", ignoreCase = true) ||
            className.contains("PhotoView", ignoreCase = true) ||
            className.contains("NetworkImageView", ignoreCase = true)
        ) {
            return true
        }
        // 使用App规则匹配
        currentAppRule?.let { rule ->
            if (rule.imageViewClasses.any {
                    className.contains(it, ignoreCase = true) || it.contains(className, ignoreCase = true)
                }
            ) {
                return true
            }
        }
        return false
    }

    /**
     * 判断节点是否为VideoView类型
     */
    private fun isVideoView(className: String, node: AccessibilityNodeInfo): Boolean {
        // 通用 VideoView 类名检查
        if (className.contains("VideoView", ignoreCase = true) ||
            className.contains("PlayerView", ignoreCase = true) ||
            className.contains("TextureView", ignoreCase = true) ||
            className.contains("SurfaceView", ignoreCase = true) ||
            className.contains("ExoPlayerView", ignoreCase = true)
        ) {
            return true
        }
        // 使用App规则匹配
        currentAppRule?.let { rule ->
            if (rule.videoViewClasses.any {
                    className.contains(it, ignoreCase = true) || it.contains(className, ignoreCase = true)
                }
            ) {
                return true
            }
        }
        return false
    }

    /**
     * 判断节点是否为WebView类型
     */
    private fun isWebView(className: String, node: AccessibilityNodeInfo): Boolean {
        if (className.contains("WebView", ignoreCase = true)) {
            return true
        }
        // 使用App规则匹配
        currentAppRule?.let { rule ->
            if (rule.webViewClasses.any {
                    className.contains(it, ignoreCase = true) || it.contains(className, ignoreCase = true)
                }
            ) {
                return true
            }
        }
        return false
    }

    // ==================== 信息提取 ====================

    /**
     * 从ImageView节点提取图片信息
     */
    private fun extractImageInfo(node: AccessibilityNodeInfo, packageName: String) {
        val contentDescription = node.contentDescription?.toString() ?: ""
        val viewIdResourceName = node.viewIdResourceName ?: ""
        val text = node.text?.toString() ?: ""

        // 尝试从多个来源提取URL
        val extractedUrl = extractUrlFromContent(contentDescription)
            ?: extractUrlFromContent(text)
            ?: extractUrlFromResourceName(viewIdResourceName)
            ?: return // 未找到有效URL则跳过

        // 验证URL是否为有效的媒体URL
        if (!FileHelper.isValidMediaUrl(extractedUrl)) return

        createMediaItem(
            type = MediaType.IMAGE,
            url = extractedUrl,
            packageName = packageName,
            description = contentDescription.ifEmpty { text }
        )
    }

    /**
     * 从VideoView节点提取视频信息
     */
    private fun extractVideoInfo(node: AccessibilityNodeInfo, packageName: String) {
        val contentDescription = node.contentDescription?.toString() ?: ""
        val text = node.text?.toString() ?: ""

        val extractedUrl = extractUrlFromContent(contentDescription)
            ?: extractUrlFromContent(text)
            ?: return

        if (!FileHelper.isValidMediaUrl(extractedUrl)) return

        createMediaItem(
            type = MediaType.VIDEO,
            url = extractedUrl,
            packageName = packageName,
            description = contentDescription.ifEmpty { text }
        )
    }

    /**
     * 从WebView节点提取内容
     * 尝试获取WebView的URL，并遍历其子节点查找媒体
     */
    private fun extractWebViewContent(node: AccessibilityNodeInfo, packageName: String) {
        val contentDescription = node.contentDescription?.toString() ?: ""
        val webUrl = extractUrlFromContent(contentDescription)
            ?: extractUrlFromContent(node.text?.toString() ?: "")

        // 如果找到WebView的URL，标记为文档类型
        if (webUrl != null && webUrl.isNotEmpty()) {
            createMediaItem(
                type = MediaType.OTHER,
                url = webUrl,
                packageName = packageName,
                description = "WebView: $webUrl"
            )
        }

        // 递归检查WebView子节点中的媒体内容
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val className = child.className?.toString() ?: ""
                if (isImageView(className, child)) {
                    extractImageInfo(child, packageName)
                } else if (isVideoView(className, child)) {
                    extractVideoInfo(child, packageName)
                }
            } finally {
                child.recycle()
            }
        }
    }

    /**
     * 从节点文本和contentDescription中提取URL
     * 使用正则匹配http/https链接
     */
    private fun extractUrlFromNode(node: AccessibilityNodeInfo, packageName: String) {
        val text = node.text?.toString() ?: ""
        val contentDescription = node.contentDescription?.toString() ?: ""

        val url = extractUrlFromContent(text)
            ?: extractUrlFromContent(contentDescription)
            ?: return

        if (!FileHelper.isValidMediaUrl(url)) return

        createMediaItem(
            type = MediaType.OTHER,
            url = url,
            packageName = packageName,
            description = contentDescription.ifEmpty { text }
        )
    }

    /**
     * 从内容字符串中提取URL
     */
    private fun extractUrlFromContent(content: String): String? {
        if (content.isBlank()) return null

        val urlPattern = Regex(
            """https?://[^\s<>"']+""",
            RegexOption.IGNORE_CASE
        )
        return urlPattern.find(content)?.value
    }

    /**
     * 从viewIdResourceName中尝试提取URL相关信息
     */
    private fun extractUrlFromResourceName(resourceName: String): String? {
        if (resourceName.isBlank()) return null

        // 某些App会将URL编码在resource ID中
        val urlPattern = Regex(
            """https?%3A%2F%2F[^\s<>"']+""",
            RegexOption.IGNORE_CASE
        )
        val match = urlPattern.find(resourceName)?.value ?: return null
        return try {
            java.net.URLDecoder.decode(match, "UTF-8")
        } catch (e: Exception) {
            null
        }
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
        val appName = currentAppRule?.appName ?: packageName
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
                // 通过LocalBroadcast发送媒体发现广播
                val intent = Intent(FloatingWindowService.ACTION_MEDIA_FOUND).apply {
                    putExtra(FloatingWindowService.EXTRA_MEDIA_TYPE, type.name)
                    putExtra(FloatingWindowService.EXTRA_MEDIA_ID, id)
                }
                LocalBroadcastManager.getInstance(this@MediaCaptureService)
                    .sendBroadcast(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
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
}
