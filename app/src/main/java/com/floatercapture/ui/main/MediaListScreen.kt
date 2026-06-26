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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.floatercapture.FloaterApp
import com.floatercapture.data.model.MediaItem
import com.floatercapture.data.model.MediaType
import com.floatercapture.data.repository.MediaRepository
import com.floatercapture.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaListScreen(navController: NavController) {
    val context = FloaterApp.appContext
    val mediaRepository = remember { MediaRepository() }
    val scope = rememberCoroutineScope()
    val allItems by mediaRepository.getAll().collectAsState(initial = emptyList())

    var selectedType by remember { mutableStateOf<MediaType?>(null) }
    var selectedApp by remember { mutableStateOf<String?>(null) }
    var showTypeFilter by remember { mutableStateOf(false) }
    var showAppFilter by remember { mutableStateOf(false) }

    // 获取所有来源 App 列表
    val allApps = remember(allItems) {
        allItems.map { it.sourceAppName.ifEmpty { it.sourcePackage } }.distinct().sorted()
    }

    // 按时间和筛选条件过滤
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
                    // 类型筛选
                    Box {
                        IconButton(onClick = { showTypeFilter = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "筛选")
                        }
                        DropdownMenu(
                            expanded = showTypeFilter,
                            onDismissRequest = { showTypeFilter = false }
                        ) {
                            typeOptions.forEach { (type, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            label,
                                            fontWeight = if (selectedType == type) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        selectedType = type
                                        showTypeFilter = false
                                    },
                                    leadingIcon = {
                                        if (selectedType == type) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    // App 筛选
                    Box {
                        IconButton(onClick = { showAppFilter = true }) {
                            Icon(Icons.Default.Apps, contentDescription = "按App筛选")
                        }
                        DropdownMenu(
                            expanded = showAppFilter,
                            onDismissRequest = { showAppFilter = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "全部来源",
                                        fontWeight = if (selectedApp == null) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    selectedApp = null
                                    showAppFilter = false
                                },
                                leadingIcon = {
                                    if (selectedApp == null) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                            allApps.forEach { app ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            app,
                                            fontWeight = if (selectedApp == app) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        selectedApp = app
                                        showAppFilter = false
                                    },
                                    leadingIcon = {
                                        if (selectedApp == app) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    // 清空全部
                    IconButton(
                        onClick = {
                            scope.launch {
                                mediaRepository.deleteAll()
                            }
                        }
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "清空全部")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (allItems.isEmpty()) "暂无捕获内容" else "无匹配的媒体文件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 统计信息
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
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
                    onDelete = {
                        scope.launch { mediaRepository.delete(item.id) }
                    }
                )
            }
        }
    }
}

@Composable
private fun StatChip(label: String, count: Int, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MediaListItem(
    item: MediaItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }

    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除确认") },
            text = { Text("确定要删除这条记录吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getMediaTypeIcon(item.type),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.sourceAppName.ifEmpty { item.sourcePackage.ifEmpty { "未知来源" } },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.description.ifEmpty { item.url.take(50) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateFormat.format(Date(item.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = item.type.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                if (item.isDownloaded) {
                    Icon(
                        Icons.Default.DownloadDone,
                        contentDescription = "已下载",
                        tint = SuccessGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun getMediaTypeIcon(type: MediaType): ImageVector {
    return when (type) {
        MediaType.IMAGE -> Icons.Default.Image
        MediaType.VIDEO -> Icons.Default.Videocam
        MediaType.DOCUMENT -> Icons.Default.Description
        MediaType.OTHER -> Icons.Default.InsertDriveFile
    }
}
