package com.floatercapture.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.floatercapture.FloaterApp
import com.floatercapture.data.model.MediaItem
import com.floatercapture.data.model.MediaType
import com.floatercapture.data.repository.MediaRepository
import com.floatercapture.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaListScreen(navController: NavController) {
    val mediaRepository = remember { MediaRepository() }
    val scope = rememberCoroutineScope()
    val allItems by mediaRepository.getAll().collectAsState(initial = emptyList())

    var selectedType by remember { mutableStateOf<MediaType?>(null) }
    var selectedApp by remember { mutableStateOf<String?>(null) }
    var showTypeFilter by remember { mutableStateOf(false) }
    var showAppFilter by remember { mutableStateOf(false) }

    val allApps = remember(allItems) {
        allItems.map { it.sourceAppName.ifEmpty { it.sourcePackage } }.distinct().sorted()
    }

    val filteredItems = remember(allItems, selectedType, selectedApp) {
        allItems
            .filter { selectedType == null || it.type == selectedType }
            .filter { selectedApp == null || (it.sourceAppName.ifEmpty { it.sourcePackage }) == selectedApp }
            .sortedByDescending { it.timestamp }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("捕获历史 (${allItems.size})") },
                actions = {
                    Box {
                        IconButton(onClick = { showTypeFilter = true }) { Icon(Icons.Default.FilterList, "筛选") }
                        DropdownMenu(expanded = showTypeFilter, onDismissRequest = { showTypeFilter = false }) {
                            listOf(null to "全部", MediaType.IMAGE to "图片", MediaType.VIDEO to "视频",
                                MediaType.DOCUMENT to "文档", MediaType.OTHER to "其他")
                                .forEach { (type, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, fontWeight = if (selectedType == type) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = { selectedType = type; showTypeFilter = false },
                                    leadingIcon = { if (selectedType == type) Icon(Icons.Default.Check, null) }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { scope.launch { mediaRepository.deleteAll() } }) {
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
                    Text(if (allItems.isEmpty()) "暂无捕获内容" else "无匹配",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredItems, key = { it.id }) { item ->
                MediaItemCard(
                    item = item,
                    onClick = { navController.navigate("preview/${item.id}") },
                    onDelete = { scope.launch { mediaRepository.delete(item.id) } }
                )
            }
        }
    }
}

@Composable
private fun MediaItemCard(item: MediaItem, onClick: () -> Unit, onDelete: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除") },
            text = { Text("确定删除？") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } }
        )
    }

    Card(Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {

            // ===== 缩略图 =====
            ThumbnailBox(item)

            Spacer(Modifier.width(10.dp))

            // ===== 信息 =====
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.sourceAppName.ifEmpty { item.sourcePackage.ifEmpty { "未知" } },
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.description.ifEmpty { item.url.take(60) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dateFormat.format(Date(item.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    if (item.fileSize > 0) {
                        Text(" · ${formatSize(item.fileSize)}",
                            style = MaterialTheme.typography.labelSmall, color = SuccessGreen)
                    }
                    if (item.isDownloaded) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.DownloadDone, null, Modifier.size(14.dp), tint = SuccessGreen)
                    }
                }
            }

            // ===== 类型标签 =====
            Text(item.type.displayName, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 4.dp))

            IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, null, Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun ThumbnailBox(item: MediaItem) {
    val shape = RoundedCornerShape(8.dp)
    val bgColor = MaterialTheme.colorScheme.surfaceVariant

    // 决定缩略图数据源
    val model = resolveThumbnailModel(item)

    when {
        model != null -> {
            // 有可加载的图片源（本地文件或网络 URL）
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier
                    .size(60.dp)
                    .clip(shape),
                contentScale = ContentScale.Crop
            )
        }
        item.type == MediaType.VIDEO -> {
            // 视频：深色背景 + 播放图标
            Box(Modifier.size(60.dp).clip(shape).background(bgColor), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayCircle, null, Modifier.size(28.dp),
                    tint = WarningOrange.copy(alpha = 0.7f))
            }
        }
        else -> {
            // 其他：浅色背景 + 类型图标
            Box(Modifier.size(60.dp).clip(shape).background(bgColor), contentAlignment = Alignment.Center) {
                Icon(getTypeIcon(item.type), null, Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            }
        }
    }
}

/**
 * 决定缩略图加载源
 * 优先级：本地文件 > HTTP/HTTPS URL > null（无缩略图）
 */
private fun resolveThumbnailModel(item: MediaItem): Any? {
    // 1. 本地已下载文件
    if (item.isDownloaded && item.localFilePath.isNotBlank()) {
        val f = File(item.localFilePath)
        if (f.exists() && f.length() > 0) return f
    }

    // 2. HTTP/HTTPS URL（真实可访问的图片/视频链接）
    val url = item.url
    if ((url.startsWith("http://") || url.startsWith("https://")) &&
        url.isNotBlank() && url.length > 15) {
        // 过滤掉明显不是图片的 URL
        val lower = url.lowercase()
        val isMediaUrl = lower.let {
            it.contains(".jpg") || it.contains(".jpeg") || it.contains(".png") ||
            it.contains(".gif") || it.contains(".webp") || it.contains(".mp4") ||
            it.contains("image") || it.contains("video") || it.contains("photo") ||
            it.contains("img") || it.contains("pic") || it.contains("media")
        }
        if (isMediaUrl) return url
    }

    // 3. 没有缩略图
    return null
}

private fun getTypeIcon(type: MediaType): ImageVector = when (type) {
    MediaType.IMAGE -> Icons.Default.Image
    MediaType.VIDEO -> Icons.Default.Videocam
    MediaType.DOCUMENT -> Icons.Default.Description
    MediaType.OTHER -> Icons.Default.InsertDriveFile
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    else -> "${"%.1f".format(bytes.toDouble() / 1024 / 1024)}MB"
}
