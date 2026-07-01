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
import com.floatercapture.FloaterApp
import com.floatercapture.data.model.MediaItem
import com.floatercapture.data.model.MediaType
import com.floatercapture.data.repository.MediaRepository
import com.floatercapture.ui.theme.ErrorRed
import com.floatercapture.ui.theme.SuccessGreen
import com.floatercapture.ui.theme.WarningOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class Bookmark(val title: String, val url: String, val timestamp: Long = System.currentTimeMillis())
data class HistoryEntry(val title: String, val url: String, val timestamp: Long = System.currentTimeMillis())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(onNavigateToResources: () -> Unit) {
    var currentUrl by remember { mutableStateOf("https://www.baidu.com") }
    var inputUrl by remember { mutableStateOf("") }
    var pageTitle by remember { mutableStateOf("FloaterCapture") }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }

    // 嗅探状态
    var isSniffing by remember { mutableStateOf(false) }
    var sniffResult by remember { mutableStateOf("") }

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
                                    currentUrl = url; inputUrl = ""
                                    webView?.loadUrl(url)
                                }) {
                                    Icon(Icons.Default.ArrowForward, "前往", Modifier.size(20.dp))
                                }
                            }
                        },
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                }
                // 进度条
                if (isLoading && progress in 1..99) {
                    LinearProgressIndicator(progress = progress / 100f, modifier = Modifier.fillMaxWidth())
                }
                // 导航栏
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
                    // 嗅探按钮（核心）
                    FilledTonalButton(
                        onClick = { sniffCurrentPage(webView, currentUrl) { isSniffing = false; onNavigateToResources() } },
                        enabled = !isSniffing && !isLoading,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        if (isSniffing) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text("嗅探中", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Icon(Icons.Default.YoutubeSearchedFor, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("嗅探", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    IconButton(onClick = {
                        newBookmarkTitle = pageTitle; newBookmarkUrl = currentUrl; showAddBookmark = true
                    }) { Icon(Icons.Default.Star, "收藏") }
                    IconButton(onClick = { showBookmarks = true }) { Icon(Icons.Default.Bookmarks, "书签") }
                    IconButton(onClick = { showHistory = true }) { Icon(Icons.Default.History, "历史") }
                    IconButton(onClick = onNavigateToResources) { Icon(Icons.Default.PhotoLibrary, "资源") }
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

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            currentUrl = url; pageTitle = view.title ?: url
                            isLoading = true; inputUrl = ""
                            history = addHistory(history, pageTitle, url)
                        }
                        override fun onPageFinished(view: WebView, url: String) {
                            super.onPageFinished(view, url)
                            isLoading = false
                        }
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, p: Int) { progress = p }
                    }
                    webView = this
                    loadUrl(currentUrl)
                }
            },
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        )
    }

    // ===== 嗅探结果 =====
    if (sniffResult.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { sniffResult = "" },
            title = { Text("嗅探结果") },
            text = { Text(sniffResult) },
            confirmButton = {
                Button(onClick = { sniffResult = ""; onNavigateToResources() }) {
                    Text("查看资源")
                }
            },
            dismissButton = { TextButton(onClick = { sniffResult = "" }) { Text("关闭") } }
        )
    }

    // ===== 书签对话框 =====
    if (showBookmarks) {
        AlertDialog(
            onDismissRequest = { showBookmarks = false },
            title = { Text("书签", fontWeight = FontWeight.Bold) },
            text = {
                if (bookmarks.isEmpty()) Text("暂无书签", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else LazyColumn(Modifier.height(400.dp)) {
                    items(bookmarks.sortedByDescending { it.timestamp }) { bm ->
                        ListItem(
                            headlineContent = { Text(bm.title.ifBlank { bm.url }, maxLines = 1) },
                            supportingContent = { Text(bm.url, maxLines = 1, style = MaterialTheme.typography.bodySmall) },
                            leadingContent = { Icon(Icons.Default.Star, null, tint = WarningOrange) },
                            modifier = Modifier.clickable {
                                currentUrl = bm.url; inputUrl = ""; webView?.loadUrl(bm.url); showBookmarks = false
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { bookmarks = emptyList(); saveBookmarks(bookmarks); showBookmarks = false }) { Text("清空", color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { showBookmarks = false }) { Text("关闭") } }
        )
    }

    // ===== 添加书签 =====
    if (showAddBookmark) {
        AlertDialog(
            onDismissRequest = { showAddBookmark = false },
            title = { Text("添加书签") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(newBookmarkTitle, { newBookmarkTitle = it }, label = { Text("标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(newBookmarkUrl, { newBookmarkUrl = it }, label = { Text("网址") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { Button(onClick = { bookmarks = addBookmark(bookmarks, newBookmarkTitle, newBookmarkUrl); saveBookmarks(bookmarks); showAddBookmark = false }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { showAddBookmark = false }) { Text("取消") } }
        )
    }

    // ===== 历史 =====
    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("浏览历史", fontWeight = FontWeight.Bold) },
            text = {
                if (history.isEmpty()) Text("暂无历史", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else LazyColumn(Modifier.height(400.dp)) {
                    items(history.sortedByDescending { it.timestamp }) { entry ->
                        ListItem(
                            headlineContent = { Text(entry.title.ifBlank { entry.url }, maxLines = 1) },
                            supportingContent = { Text(entry.url, maxLines = 1, style = MaterialTheme.typography.bodySmall) },
                            leadingContent = { Icon(Icons.Default.History, null) },
                            modifier = Modifier.clickable {
                                currentUrl = entry.url; inputUrl = ""; webView?.loadUrl(entry.url); showHistory = false
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { history = emptyList(); saveHistory(history); showHistory = false }) { Text("清空", color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { showHistory = false }) { Text("关闭") } }
        )
    }
}

// ==================== 嗅探逻辑 ====================

private fun sniffCurrentPage(webView: WebView?, pageUrl: String, onDone: () -> Unit) {
    val wv = webView ?: return
    val repo = MediaRepository()
    val seen = mutableSetOf<String>()

    // JS 代码：提取页面所有资源 URL
    val js = """
    (function() {
        var urls = [];
        // 所有 img
        document.querySelectorAll('img').forEach(function(el) { if(el.src) urls.push(el.src); if(el.dataset.src) urls.push(el.dataset.src); });
        // 所有 video
        document.querySelectorAll('video').forEach(function(el) { if(el.src) urls.push(el.src); });
        document.querySelectorAll('video source').forEach(function(el) { if(el.src) urls.push(el.src); });
        // 所有 audio
        document.querySelectorAll('audio').forEach(function(el) { if(el.src) urls.push(el.src); });
        document.querySelectorAll('audio source').forEach(function(el) { if(el.src) urls.push(el.src); });
        // 所有 a 标签中的下载链接
        document.querySelectorAll('a[href]').forEach(function(el) {
            var h = el.href;
            if(h.match(/\.(jpg|jpeg|png|gif|webp|bmp|svg|mp4|mkv|avi|mov|webm|mp3|wav|aac|pdf|zip|rar|7z|apk|doc|docx|xls|xlsx|ppt|pptx|js|css|json|xml|html?)(\?|#|$)/i)) urls.push(h);
        });
        // background-image
        document.querySelectorAll('*').forEach(function(el) {
            var bg = window.getComputedStyle(el).backgroundImage;
            if(bg && bg !== 'none') {
                var m = bg.match(/url\(["']?([^"')]+)["']?\)/g);
                if(m) m.forEach(function(u) {
                    var url = u.replace(/url\(["']?|["']?\)/g, '');
                    if(url.startsWith('http')) urls.push(url);
                });
            }
        });
        // og meta
        document.querySelectorAll('meta[property="og:image"], meta[property="og:video"]').forEach(function(el) {
            if(el.content) urls.push(el.content);
        });
        return JSON.stringify([...new Set(urls)]);
    })();
    """.trimIndent()

    wv.evaluateJavascript(js) { result ->
        kotlinx.coroutines.MainScope().launch {
            try {
                val json = result.trim('"').replace("\\\"", "\"").replace("\\\\", "\\")
                val arr = org.json.JSONArray(json)
                var count = 0

                for (i in 0 until arr.length()) {
                    val url = arr.getString(i)
                    if (url.isBlank() || url.length < 10) continue
                    if (!url.startsWith("http")) continue
                    if (!seen.add(url)) continue

                    // 过滤追踪
                    val lower = url.lowercase()
                    if (lower.contains("analytics") || lower.contains("tracking") || lower.contains("pixel")) continue

                    // 分类
                    val ext = url.substringAfterLast('.').substringBefore('?').substringBefore('#').lowercase()
                    val type = classifyUrl(url, ext)

                    val item = MediaItem(
                        id = UUID.randomUUID().toString(),
                        type = type,
                        url = url,
                        sourcePackage = "browser",
                        sourceAppName = "浏览器",
                        description = "${type.displayName}: ${ext.uppercase().ifEmpty { "URL" }}",
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
                    repo.insert(item)
                    count++
                }

                onDone()
            } catch (e: Exception) {
                onDone()
            }
        }
    }
}

private fun classifyUrl(url: String, ext: String): MediaType {
    val imgExts = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico", "heic", "avif", "tiff")
    val vidExts = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m3u8", "ts", "m4v")
    val audExts = setOf("mp3", "wav", "aac", "ogg", "flac", "m4a", "opus")
    val docExts = setOf("pdf", "zip", "rar", "7z", "apk", "doc", "docx", "xls", "xlsx", "ppt", "pptx")
    val txtExts = setOf("js", "css", "json", "xml", "html", "htm")

    if (ext in imgExts) return MediaType.IMAGE
    if (ext in vidExts) return MediaType.VIDEO
    if (ext in audExts) return MediaType.AUDIO
    if (ext in docExts) return MediaType.DOCUMENT
    if (ext in txtExts) return MediaType.TEXT

    val lower = url.lowercase()
    if (lower.contains("/img") || lower.contains("/image") || lower.contains("/photo")) return MediaType.IMAGE
    if (lower.contains("/video") || lower.contains("/media")) return MediaType.VIDEO
    if (lower.contains("/audio") || lower.contains("/music")) return MediaType.AUDIO
    return MediaType.OTHER
}

// ==================== 书签/历史 ====================

private fun prefs() = FloaterApp.appContext.getSharedPreferences("browser_prefs", android.content.Context.MODE_PRIVATE)

private fun loadBookmarks(): List<Bookmark> {
    val json = prefs().getString("bookmarks", "[]") ?: "[]"
    return try {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).map { i -> val o = arr.getJSONObject(i); Bookmark(o.getString("title"), o.getString("url"), o.optLong("ts", 0)) }
    } catch (e: Exception) { emptyList() }
}

private fun saveBookmarks(list: List<Bookmark>) {
    val arr = org.json.JSONArray()
    list.forEach { bm -> arr.put(org.json.JSONObject().apply { put("title", bm.title); put("url", bm.url); put("ts", bm.timestamp) }) }
    prefs().edit().putString("bookmarks", arr.toString()).apply()
}

private fun addBookmark(list: List<Bookmark>, title: String, url: String): List<Bookmark> {
    return list.filter { it.url != url } + Bookmark(title.ifBlank { url }, url)
}

private fun loadHistory(): List<HistoryEntry> {
    val json = prefs().getString("history", "[]") ?: "[]"
    return try {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).map { i -> val o = arr.getJSONObject(i); HistoryEntry(o.getString("title"), o.getString("url"), o.optLong("ts", 0)) }
    } catch (e: Exception) { emptyList() }
}

private fun saveHistory(list: List<HistoryEntry>) {
    val arr = org.json.JSONArray()
    list.takeLast(100).forEach { e -> arr.put(org.json.JSONObject().apply { put("title", e.title); put("url", e.url); put("ts", e.timestamp) }) }
    prefs().edit().putString("history", arr.toString()).apply()
}

private fun addHistory(list: List<HistoryEntry>, title: String, url: String): List<HistoryEntry> {
    val updated = list.filter { it.url != url } + HistoryEntry(title.ifBlank { url }, url)
    saveHistory(updated)
    return updated
}
