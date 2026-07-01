package com.floatercapture.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import com.floatercapture.data.model.MediaItem
import com.floatercapture.data.model.MediaType
import com.floatercapture.ui.theme.ErrorRed
import com.floatercapture.ui.theme.WarningOrange
import com.floatercapture.data.repository.MediaRepository
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// 书签数据模型
data class Bookmark(val title: String, val url: String, val timestamp: Long = System.currentTimeMillis())
data class HistoryEntry(val title: String, val url: String, val timestamp: Long = System.currentTimeMillis())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(onNavigateToResources: () -> Unit) {
    var currentUrl by remember { mutableStateOf("https://www.baidu.com") }
    var inputUrl by remember { mutableStateOf("") }
    var inputFocused by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("FloaterCapture") }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }

    // 书签
    var bookmarks by remember { mutableStateOf(loadBookmarks()) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showAddBookmark by remember { mutableStateOf(false) }
    var newBookmarkTitle by remember { mutableStateOf("") }
    var newBookmarkUrl by remember { mutableStateOf("") }

    // 历史
    var history by remember { mutableStateOf(loadHistory()) }
    var showHistory by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    Scaffold(
        topBar = {
            Column {
                // URL 地址栏
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(currentUrl, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                                    val url = inputUrl.ifBlank { currentUrl }.let {
                                        if (!it.startsWith("http")) "https://$it" else it
                                    }
                                    currentUrl = url; inputUrl = ""; inputFocused = false
                                    webView?.loadUrl(url)
                                }) {
                                    Icon(Icons.Default.ArrowForward, "前往", Modifier.size(20.dp))
                                }
                            }
                        },
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                }
                // 加载进度条
                if (isLoading && progress in 1..99) {
                    LinearProgressIndicator(
                        progress = progress / 100f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // 导航按钮栏
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { webView?.goBack() }, enabled = canGoBack) {
                        Icon(Icons.Default.ArrowBack, "后退",
                            tint = if (canGoBack) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    }
                    IconButton(onClick = { webView?.goForward() }, enabled = canGoForward) {
                        Icon(Icons.Default.ArrowForward, "前进",
                            tint = if (canGoForward) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    }
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                    IconButton(onClick = {
                        newBookmarkTitle = pageTitle
                        newBookmarkUrl = currentUrl
                        showAddBookmark = true
                    }) {
                        Icon(Icons.Default.Star, "收藏")
                    }
                    IconButton(onClick = { showBookmarks = true }) {
                        Icon(Icons.Default.Bookmarks, "书签")
                    }
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Default.History, "历史")
                    }
                    IconButton(onClick = onNavigateToResources) {
                        BadgedBox(badge = {}) {
                            Icon(Icons.Default.PhotoLibrary, "资源")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        AndroidView(
            factory = { c ->
                WebView(c).apply {
                    @SuppressLint("SetJavaScriptEnabled")
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.allowFileAccess = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.setSupportMultipleWindows(false)

                    val interceptor = ResourceInterceptor(c)
                    webViewClient = BrowserClient(
                        onPageStarted = { url, title ->
                            currentUrl = url
                            pageTitle = title ?: url
                            isLoading = true
                            inputUrl = ""
                            // 添加到历史
                            history = addHistory(history, pageTitle, url)
                        },
                        onPageFinished = { isLoading = false },
                        onProgress = { p -> progress = p },
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

    // ===== 书签对话框 =====
    if (showBookmarks) {
        AlertDialog(
            onDismissRequest = { showBookmarks = false },
            title = { Text("书签", fontWeight = FontWeight.Bold) },
            text = {
                if (bookmarks.isEmpty()) {
                    Text("暂无书签，浏览网页时点击 ⭐ 收藏", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(modifier = Modifier.height(400.dp)) {
                        items(bookmarks.sortedByDescending { it.timestamp }) { bm ->
                            ListItem(
                                headlineContent = { Text(bm.title.ifBlank { bm.url }, maxLines = 1) },
                                supportingContent = { Text(bm.url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) },
                                leadingContent = { Icon(Icons.Default.Star, null, tint = WarningOrange) },
                                modifier = Modifier.clickable {
                                    currentUrl = bm.url; inputUrl = ""
                                    webView?.loadUrl(bm.url)
                                    showBookmarks = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    bookmarks = emptyList()
                    saveBookmarks(bookmarks)
                    showBookmarks = false
                }) { Text("清空全部", color = ErrorRed) }
            },
            dismissButton = { TextButton(onClick = { showBookmarks = false }) { Text("关闭") } }
        )
    }

    // ===== 添加书签对话框 =====
    if (showAddBookmark) {
        AlertDialog(
            onDismissRequest = { showAddBookmark = false },
            title = { Text("添加书签") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newBookmarkTitle,
                        onValueChange = { newBookmarkTitle = it },
                        label = { Text("标题") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newBookmarkUrl,
                        onValueChange = { newBookmarkUrl = it },
                        label = { Text("网址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    bookmarks = addBookmark(bookmarks, newBookmarkTitle, newBookmarkUrl)
                    saveBookmarks(bookmarks)
                    showAddBookmark = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showAddBookmark = false }) { Text("取消") } }
        )
    }

    // ===== 历史对话框 =====
    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("浏览历史", fontWeight = FontWeight.Bold) },
            text = {
                if (history.isEmpty()) {
                    Text("暂无历史记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(modifier = Modifier.height(400.dp)) {
                        items(history.sortedByDescending { it.timestamp }) { entry ->
                            ListItem(
                                headlineContent = { Text(entry.title.ifBlank { entry.url }, maxLines = 1) },
                                supportingContent = { Text(entry.url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) },
                                leadingContent = { Icon(Icons.Default.History, null) },
                                modifier = Modifier.clickable {
                                    currentUrl = entry.url; inputUrl = ""
                                    webView?.loadUrl(entry.url)
                                    showHistory = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    history = emptyList()
                    saveHistory(history)
                    showHistory = false
                }) { Text("清空全部", color = ErrorRed) }
            },
            dismissButton = { TextButton(onClick = { showHistory = false }) { Text("关闭") } }
        )
    }
}

// ==================== 书签/历史 持久化 ====================

private fun loadBookmarks(): List<Bookmark> {
    val prefs = com.floatercapture.FloaterApp.appContext
        .getSharedPreferences("browser_prefs", android.content.Context.MODE_PRIVATE)
    val json = prefs.getString("bookmarks", "[]") ?: "[]"
    return try {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Bookmark(o.getString("title"), o.getString("url"), o.optLong("ts", 0))
        }
    } catch (e: Exception) { emptyList() }
}

private fun saveBookmarks(bookmarks: List<Bookmark>) {
    // 注意：这里用 FloaterApp.appContext 代替 Composable 上下文
    val prefs = com.floatercapture.FloaterApp.appContext
        .getSharedPreferences("browser_prefs", android.content.Context.MODE_PRIVATE)
    val arr = org.json.JSONArray()
    bookmarks.forEach { bm ->
        arr.put(org.json.JSONObject().apply {
            put("title", bm.title); put("url", bm.url); put("ts", bm.timestamp)
        })
    }
    prefs.edit().putString("bookmarks", arr.toString()).apply()
}

private fun addBookmark(list: List<Bookmark>, title: String, url: String): List<Bookmark> {
    val filtered = list.filter { it.url != url }
    return filtered + Bookmark(title.ifBlank { url }, url)
}

private fun loadHistory(): List<HistoryEntry> {
    val prefs = com.floatercapture.FloaterApp.appContext
        .getSharedPreferences("browser_prefs", android.content.Context.MODE_PRIVATE)
    val json = prefs.getString("history", "[]") ?: "[]"
    return try {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            HistoryEntry(o.getString("title"), o.getString("url"), o.optLong("ts", 0))
        }
    } catch (e: Exception) { emptyList() }
}

private fun saveHistory(history: List<HistoryEntry>) {
    val prefs = com.floatercapture.FloaterApp.appContext
        .getSharedPreferences("browser_prefs", android.content.Context.MODE_PRIVATE)
    val arr = org.json.JSONArray()
    history.takeLast(100).forEach { entry ->
        arr.put(org.json.JSONObject().apply {
            put("title", entry.title); put("url", entry.url); put("ts", entry.timestamp)
        })
    }
    prefs.edit().putString("history", arr.toString()).apply()
}

private fun addHistory(list: List<HistoryEntry>, title: String, url: String): List<HistoryEntry> {
    val filtered = list.filter { it.url != url }
    val updated = filtered + HistoryEntry(title.ifBlank { url }, url)
    saveHistory(updated)
    return updated
}

// ==================== WebViewClient ====================

class BrowserClient(
    private val onPageStarted: (String, String?) -> Unit,
    private val onPageFinished: () -> Unit,
    private val onProgress: (Int) -> Unit,
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
        view: WebView, request: WebResourceRequest
    ): WebResourceResponse? {
        interceptor.handleResource(request.url.toString(), request)
        return null
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
}

// ==================== 资源拦截器 ====================

class ResourceInterceptor(private val context: android.content.Context) {

    private val seenUrls = ConcurrentHashMap.newKeySet<String>()
    private val repo = MediaRepository()

    private val imageExts = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico", "heic", "avif", "tiff")
    private val videoExts = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m3u8", "ts", "m4v")
    private val audioExts = setOf("mp3", "wav", "aac", "ogg", "flac", "m4a", "opus", "weba")
    private val docExts = setOf("pdf", "zip", "rar", "7z", "apk", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "tar", "gz", "bz2")
    private val scriptExts = setOf("js", "css", "json", "xml", "html", "htm")

    private val skipDomains = setOf("google-analytics", "googletagmanager", "doubleclick",
        "facebook.com/tr", "analytics", "tracking", "pixel", "beacon", "stat", "collect")

    fun handleResource(url: String, request: WebResourceRequest) {
        if (url.isBlank() || url.length < 10) return

        val lower = url.lowercase()
        for (skip in skipDomains) { if (lower.contains(skip)) return }

        val ext = url.substringAfterLast('.').substringBefore('?').substringBefore('#').lowercase()
        val type = when {
            ext in imageExts -> MediaType.IMAGE
            ext in videoExts -> MediaType.VIDEO
            ext in audioExts -> MediaType.AUDIO
            ext in docExts -> MediaType.DOCUMENT
            ext in scriptExts -> MediaType.TEXT
            lower.contains("/image") || lower.contains("/img") || lower.contains("/photo") || lower.contains("/thumb") -> MediaType.IMAGE
            lower.contains("/video") || lower.contains("/media") -> MediaType.VIDEO
            lower.contains("/audio") || lower.contains("/music") || lower.contains("/sound") -> MediaType.AUDIO
            lower.contains("/download") || lower.contains("/file") -> MediaType.DOCUMENT
            else -> null
        } ?: return

        if (!seenUrls.add(url)) return

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
            thumbnailUrl = if (type == MediaType.IMAGE) url else "",
            timestamp = System.currentTimeMillis()
        )

        kotlinx.coroutines.MainScope().launch {
            try { repo.insert(item) } catch (_: Exception) {}
        }
    }
}
