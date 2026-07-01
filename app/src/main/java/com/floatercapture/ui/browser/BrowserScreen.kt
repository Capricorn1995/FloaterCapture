package com.floatercapture.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.floatercapture.data.model.MediaItem
import com.floatercapture.data.model.MediaType
import com.floatercapture.data.repository.MediaRepository
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onNavigateToResources: () -> Unit
) {
    var currentUrl by remember { mutableStateOf("https://www.baidu.com") }
    var inputUrl by remember { mutableStateOf("") }
    var pageTitle by remember { mutableStateOf("FloaterCapture 浏览器") }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            Column {
                // URL 栏
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputUrl.ifEmpty { currentUrl },
                        onValueChange = { inputUrl = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("输入网址...") },
                        leadingIcon = {
                            if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Language, null)
                        },
                        trailingIcon = {
                            Row {
                                if (inputUrl.isNotEmpty()) {
                                    IconButton(onClick = { inputUrl = "" }) {
                                        Icon(Icons.Default.Close, "清除", Modifier.size(20.dp))
                                    }
                                }
                                IconButton(onClick = {
                                    var url = inputUrl.ifEmpty { currentUrl }
                                    if (!url.startsWith("http")) url = "https://$url"
                                    currentUrl = url; inputUrl = ""
                                    webView?.loadUrl(url)
                                }) {
                                    Icon(Icons.Default.Search, "前往", Modifier.size(20.dp))
                                }
                            }
                        },
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                }
                // 导航栏
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { webView?.goBack() }, enabled = canGoBack) {
                        Icon(Icons.Default.ArrowBack, "后退", tint = if (canGoBack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    }
                    IconButton(onClick = { webView?.goForward() }, enabled = canGoForward) {
                        Icon(Icons.Default.ArrowForward, "前进", tint = if (canGoForward) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    }
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                    IconButton(onClick = onNavigateToResources) {
                        Icon(Icons.Default.PhotoLibrary, "资源")
                    }
                    IconButton(onClick = { webView?.loadUrl(currentUrl) }) {
                        Icon(Icons.Default.Home, "首页")
                    }
                }
            }
        }
    ) { innerPadding ->
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    @SuppressLint("SetJavaScriptEnabled")
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.allowFileAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.cacheMode = WebSettings.LOAD_DEFAULT

                    // 安装资源拦截器
                    val interceptor = ResourceInterceptor(context)
                    webViewClient = BrowserClient(
                        onPageStarted = { url, title ->
                            currentUrl = url
                            pageTitle = title ?: url
                            isLoading = true
                        },
                        onPageFinished = {
                            isLoading = false
                        },
                        interceptor = interceptor
                    )
                    webChromeClient = WebChromeClient()

                    webView = this
                    loadUrl(currentUrl)
                }
            },
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        )
    }
}

/**
 * WebViewClient — 在 shouldInterceptRequest 中拦截资源
 */
class BrowserClient(
    private val onPageStarted: (String, String?) -> Unit,
    private val onPageFinished: () -> Unit,
    private val interceptor: ResourceInterceptor
) : WebViewClient() {

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted(url, view.title)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        onPageFinished()
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url.toString()
        // 异步处理资源
        interceptor.handleResource(url, request)
        return null // 不阻止加载，正常返回
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        return false // 在 WebView 内打开所有链接
    }
}

/**
 * 资源拦截器 — 记录所有加载的资源 URL 并存入数据库
 */
class ResourceInterceptor(private val context: android.content.Context) {

    private val seenUrls = ConcurrentHashMap.newKeySet<String>()
    private val repo = MediaRepository()

    // 资源分类规则
    private val imageExts = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico", "heic", "avif")
    private val videoExts = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m3u8", "ts")
    private val audioExts = setOf("mp3", "wav", "aac", "ogg", "flac", "m4a", "opus")
    private val docExts = setOf("pdf", "zip", "rar", "7z", "apk", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "tar", "gz", "bz2")
    private val fontExts = setOf("ttf", "woff", "woff2", "eot", "otf")
    private val scriptExts = setOf("js", "css", "json", "xml")

    private val skipDomains = setOf("google-analytics.com", "googletagmanager.com", "doubleclick.net",
        "facebook.com/tr", "analytics", "tracking", "pixel", "beacon", "stat")

    fun handleResource(url: String, request: WebResourceRequest) {
        if (url.isBlank()) return

        // 过滤域名（追踪/统计）
        val lower = url.lowercase()
        for (skip in skipDomains) {
            if (lower.contains(skip)) return
        }

        // 分类资源
        val ext = url.substringAfterLast('.').substringBefore('?').substringBefore('#').lowercase()
        val type = when {
            ext in imageExts -> MediaType.IMAGE
            ext in videoExts -> MediaType.VIDEO
            ext in audioExts -> MediaType.AUDIO
            ext in docExts -> MediaType.DOCUMENT
            ext in scriptExts -> MediaType.TEXT
            // 路径特征
            lower.contains("/image") || lower.contains("/img") || lower.contains("/photo") || lower.contains("/thumb") -> MediaType.IMAGE
            lower.contains("/video") || lower.contains("/media") -> MediaType.VIDEO
            lower.contains("/audio") || lower.contains("/music") || lower.contains("/sound") -> MediaType.AUDIO
            lower.contains("/download") || lower.contains("/file") -> MediaType.DOCUMENT
            else -> null // 跳过不确定的
        } ?: return

        // 去重
        if (!seenUrls.add(url)) return

        // 生成缩略 URL（图片直接用自身，其他用图标）
        val thumbUrl = if (type == MediaType.IMAGE) url else ""

        val item = MediaItem(
            id = UUID.randomUUID().toString(),
            type = type,
            url = url,
            sourcePackage = "browser",
            sourceAppName = "浏览器",
            description = "${type.displayName}: ${ext.uppercase()}",
            mimeType = when (type) {
                MediaType.IMAGE -> "image/$ext"
                MediaType.VIDEO -> "video/$ext"
                MediaType.AUDIO -> "audio/$ext"
                MediaType.DOCUMENT -> "application/octet-stream"
                MediaType.TEXT -> "text/$ext"
                else -> "*/*"
            },
            thumbnailUrl = thumbUrl,
            timestamp = System.currentTimeMillis()
        )

        kotlinx.coroutines.MainScope().launch {
            try {
                repo.insert(item)
            } catch (e: Exception) {
                // 忽略
            }
        }
    }
}
