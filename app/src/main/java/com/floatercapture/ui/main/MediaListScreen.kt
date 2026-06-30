package com.floatercapture.ui.main

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

    val typeOptions = listOf(
        null to "全部",
        MediaType.IMAGE to "图片",
        MediaType.VIDEO to "视频",
        MediaType.DOCUMENT to "文档",
        MediaType.OTHER to "其他"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("捕获历史") },
                actions = {
                    Box {
                        IconButton(onClick = { showTypeFilter = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "筛选")
                        }
                        DropdownMenu(expanded = showTypeFilter, onDismissRequest = { showTypeFilter = false }) {
                            typeOptions.forEach { (type, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, fontWeight = if (selectedType == type) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = { selectedType = type; showTypeFilter = false },
                                    leadingIcon = { if (selectedType == type) Icon(Icons.Default.Check, null) }
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { showAppFilter = true }) { Icon(Icons.Default.Apps, "按App") }
                        DropdownMenu(expanded = showAppFilter, onDismissRequest = { showAppFilter = false }) {
                            DropdownMenuItem(
                                text = { Text("全部来源", fontWeight = if (selectedApp == null) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { selectedApp = null; showAppFilter = false },
                                leadingIcon = { if (selectedApp == null) Icon(Icons.Default.Check, null) }
                            )
                            allApps.forEach { app ->
                                DropdownMenuItem(
                                    text = { Text(app, fontWeight = if (selectedApp == app) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = { selectedApp = app; showAppFilter = false },
                                    leadingIcon = { if (selectedApp == app) Icon(Icons.Default.Check, null) }
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
                    Text(if (allItems.isEmpty()) "暂无捕获内容，开启悬浮窗开始使用" else "无匹配的媒体文件",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatChip("图片", allItems.count { it.type == MediaType.IMAGE }, Icons.Default.Image)
                        StatChip("视频", allItems.count { it.type == MediaType.VIDEO }, Icons.Default.Videocam)
                        StatChip("文档", allItems.count { it.type == MediaType.DOCUMENT }, Icons.Default.Description)
                        StatChip("其他", allItems.count { it.type == MediaType.OTHER }, Icons.Default.InsertDriveFile)
                    }
                }
            }

            items(filteredItems, key = { it.id }) { item ->
                MediaListItem(
                    item = item,
                    onClick = { navController.navigate("preview/${item.id}") },
                    onDelete = { scope.launch { mediaRepository.delete(item.id) } }
                )
            }
        }
    }
}

@Composable
private fun StatChip(label: String, count: Int, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(14.dp))
            Spacer(Modifier.width(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MediaListItem(item: MediaItem, onClick: () -> Unit, onDelete: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除确认") },
            text = { Text("确定删除这条记录吗？") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)) {
                    Text("删除")
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } }
        )
    }

    Card(Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            // 缩略图区域
            Box(
                Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                val thumbUrl = getThumbUrl(item)

                if (thumbUrl != null) {
                    AsyncImage(
                        model = thumbUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.small),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // 无缩略图时显示类型图标
                    Icon(
                        imageVector = getMediaTypeIcon(item.type),
                        contentDescription = null,
                        tint = when (item.type) {
                            MediaType.IMAGE -> InfoBlue
                            MediaType.VIDEO -> WarningOrange
                            MediaType.DOCUMENT -> SuccessGreen
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

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
                    Text(
                        text = dateFormat.format(Date(item.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    if (item.fileSize > 0) {
                        Text(" · ", style = MaterialTheme.typography.labelSmall)
                        Text(formatSize(item.fileSize), style = MaterialTheme.typography.labelSmall,
                            color = SuccessGreen)
                    }
                    if (item.isDownloaded) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.DownloadDone, null, Modifier.size(14.dp), tint = SuccessGreen)
                    }
                }
            }

            Text(
                text = item.type.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 8.dp)
            )

            IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, null, Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}

private fun getThumbUrl(item: MediaItem): Any? {
    // 如果已下载到本地，直接用本地路径
    if (item.isDownloaded && item.localFilePath.isNotBlank()) {
        val f = File(item.localFilePath)
        if (f.exists()) return f
    }

    // 如果有真实 HTTP URL，用 Coil 加载
    val url = item.url
    if (url.startsWith("http://") || url.startsWith("https://")) {
        return url
    }

    // node:// 没有缩略图
    return null
}

private fun getMediaTypeIcon(type: MediaType): ImageVector {
    return when (type) {
        MediaType.IMAGE -> Icons.Default.Image
        MediaType.VIDEO -> Icons.Default.Videocam
        MediaType.DOCUMENT -> Icons.Default.Description
        MediaType.OTHER -> Icons.Default.InsertDriveFile
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> "${"%.1f".format(bytes.toDouble() / 1024 / 1024)}MB"
    }
}
