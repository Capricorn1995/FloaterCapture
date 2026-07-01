package com.floatercapture.ui.browser

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.floatercapture.data.model.MediaItem
import com.floatercapture.data.model.MediaType
import com.floatercapture.data.repository.DownloadRepository
import com.floatercapture.data.repository.MediaRepository
import com.floatercapture.service.DownloadService
import com.floatercapture.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceListScreen(
    onNavigateToBrowser: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mediaRepository = remember { MediaRepository() }
    val scope = rememberCoroutineScope()
    val allItems by mediaRepository.getAll().collectAsState(initial = emptyList())

    var selectedType by remember { mutableStateOf<MediaType?>(null) }
    var showFilter by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectMode by remember { mutableStateOf(false) }

    // 去重（按 URL）
    val uniqueItems = remember(allItems) {
        allItems.distinctBy { it.url }.sortedByDescending { it.timestamp }
    }
    val filteredItems = remember(uniqueItems, selectedType) {
        if (selectedType == null) uniqueItems
        else uniqueItems.filter { it.type == selectedType }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("资源列表 (${filteredItems.size})") },
                navigationIcon = {
                    IconButton(onClick = onNavigateToBrowser) {
                        Icon(Icons.Default.ArrowBack, "返回浏览器")
                    }
                },
                actions = {
                    // 类型筛选
                    Box {
                        IconButton(onClick = { showFilter = true }) {
                            Icon(Icons.Default.FilterList, "筛选")
                        }
                        DropdownMenu(expanded = showFilter, onDismissRequest = { showFilter = false }) {
                            listOf(null to "全部", MediaType.IMAGE to "🖼 图片",
                                MediaType.VIDEO to "🎬 视频", MediaType.AUDIO to "🎵 音频",
                                MediaType.DOCUMENT to "📄 文档", MediaType.TEXT to "📝 文本")
                                .forEach { (type, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, fontWeight = if (selectedType == type) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = { selectedType = type; showFilter = false },
                                    leadingIcon = { if (selectedType == type) Icon(Icons.Default.Check, null) }
                                )
                            }
                        }
                    }
                    // 全选/取消
                    IconButton(onClick = {
                        if (selectMode) {
                            if (selectedItems.size == filteredItems.size) selectedItems = emptySet()
                            else selectedItems = filteredItems.map { it.id }.toSet()
                        }
                        selectMode = !selectMode
                    }) {
                        Icon(
                            if (selectMode && selectedItems.size == filteredItems.size) Icons.Default.Deselect else Icons.Default.Checklist,
                            "选择"
                        )
                    }
                    // 批量下载
                    if (selectMode && selectedItems.isNotEmpty()) {
                        IconButton(onClick = {
                            val toDownload = filteredItems.filter { it.id in selectedItems && !it.isDownloaded }
                            if (toDownload.isNotEmpty()) {
                                DownloadService.startDownload(context, toDownload)
                            }
                            selectedItems = emptySet(); selectMode = false
                        }) {
                            Icon(Icons.Default.Download, "下载选中")
                        }
                    }
                    // 清空
                    IconButton(onClick = {
                        scope.launch { mediaRepository.deleteAll() }
                    }) {
                        Icon(Icons.Default.DeleteSweep, "清空")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (filteredItems.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Inbox, null, Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(8.dp))
                    Text("访问网页后资源自动出现在这里", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }

        // 统计条
        Column(Modifier.padding(innerPadding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(MediaType.IMAGE to "图片", MediaType.VIDEO to "视频",
                    MediaType.AUDIO to "音频", MediaType.DOCUMENT to "文档")
                    .forEach { (t, l) ->
                    FilterChip(
                        selected = selectedType == t,
                        onClick = { selectedType = if (selectedType == t) null else t },
                        label = { Text("$l ${uniqueItems.count { it.type == t }}", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // 网格列表
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    ResourceCard(
                        item = item,
                        isSelected = item.id in selectedItems,
                        showCheckbox = selectMode,
                        onToggleSelect = {
                            selectedItems = if (item.id in selectedItems)
                                selectedItems - item.id
                            else selectedItems + item.id
                        },
                        onDownload = {
                            if (!item.isDownloaded) {
                                DownloadService.startDownload(context, listOf(item))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ResourceCard(
    item: MediaItem,
    isSelected: Boolean,
    showCheckbox: Boolean,
    onToggleSelect: () -> Unit,
    onDownload: () -> Unit
) {
    var showDetail by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (showCheckbox) onToggleSelect()
                else showDetail = true
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            // 缩略图区域
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                when {
                    // 图片类型且不是图标类 — 直接加载 URL
                    item.type == MediaType.IMAGE && !item.url.contains("favicon") && !item.url.contains(".ico") -> {
                        AsyncImage(
                            model = item.url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.extraSmall),
                            contentScale = ContentScale.Crop
                        )
                    }
                    // 视频类型 — 显示占位
                    item.type == MediaType.VIDEO -> {
                        Icon(Icons.Default.PlayCircle, null, Modifier.size(36.dp),
                            tint = WarningOrange.copy(alpha = 0.7f))
                    }
                    item.type == MediaType.AUDIO -> {
                        Icon(Icons.Default.MusicNote, null, Modifier.size(36.dp),
                            tint = SuccessGreen.copy(alpha = 0.7f))
                    }
                    item.type == MediaType.DOCUMENT -> {
                        Icon(Icons.Default.Description, null, Modifier.size(36.dp),
                            tint = InfoBlue.copy(alpha = 0.7f))
                    }
                    else -> {
                        Icon(Icons.Default.InsertDriveFile, null, Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }

                // 选中标记
                if (showCheckbox) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        modifier = Modifier.align(Alignment.TopEnd).padding(2.dp)
                    )
                }

                // 已下载标记
                if (item.isDownloaded) {
                    Icon(Icons.Default.DownloadDone, null,
                        Modifier.size(16.dp).align(Alignment.BottomEnd).padding(2.dp),
                        tint = SuccessGreen)
                }
            }

            // 信息行
            Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                Text(
                    text = item.type.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // 详情弹窗
    if (showDetail) {
        AlertDialog(
            onDismissRequest = { showDetail = false },
            title = { Text("资源详情", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    DetailRow("类型", item.type.displayName)
                    DetailRow("URL", if (item.url.length > 50) item.url.take(50) + "..." else item.url)
                    DetailRow("大小", if (item.fileSize > 0) formatSize(item.fileSize) else "未知")
                    DetailRow("MIME", item.mimeType.ifEmpty { "未知" })
                    if (item.isDownloaded) DetailRow("状态", "已下载")
                }
            },
            confirmButton = {
                if (!item.isDownloaded) {
                    Button(onClick = { onDownload(); showDetail = false }) {
                        Icon(Icons.Default.Download, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("下载")
                    }
                } else {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    OutlinedButton(onClick = {
                        val f = File(item.localFilePath)
                        if (f.exists()) {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                ctx, "com.floatercapture.fileprovider", f
                            )
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, item.mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            ctx.startActivity(intent)
                        }
                        showDetail = false
                    }) { Text("打开") }
                }
            },
            dismissButton = { TextButton(onClick = { showDetail = false }) { Text("关闭") } }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label: ", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    else -> "${"%.1f".format(bytes.toDouble() / 1024 / 1024)}MB"
}
