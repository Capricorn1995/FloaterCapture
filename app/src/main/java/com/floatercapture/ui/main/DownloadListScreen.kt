package com.floatercapture.ui.main

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.floatercapture.FloaterApp
import com.floatercapture.data.model.DownloadState
import com.floatercapture.data.model.DownloadTask
import com.floatercapture.data.model.MediaType
import com.floatercapture.data.repository.DownloadRepository
import com.floatercapture.service.DownloadService
import com.floatercapture.util.FileHelper
import com.floatercapture.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadListScreen(navController: NavController) {
    val context = FloaterApp.appContext
    val downloadRepository = remember { DownloadRepository() }
    val scope = rememberCoroutineScope()
    val allTasks by downloadRepository.getAll().collectAsState(initial = emptyList())

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("下载中", "已完成", "失败")

    val activeTasks = allTasks.filter {
        it.state == DownloadState.Downloading ||
                it.state == DownloadState.Pending ||
                it.state == DownloadState.Paused
    }
    val completedTasks = allTasks.filter { it.state == DownloadState.Completed }
    val failedTasks = allTasks.filter { it.state == DownloadState.Failed }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("下载管理") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) },
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> ActiveDownloadsTab(
                    tasks = activeTasks,
                    onPause = { task -> DownloadService.pauseAll(context) },
                    onResume = { task -> DownloadService.resumeAll(context) },
                    onCancelAll = { DownloadService.pauseAll(context) },
                )
                1 -> CompletedDownloadsTab(
                    tasks = completedTasks,
                    onClearAll = {
                        scope.launch { completedTasks.forEach { downloadRepository.delete(it.id) } }
                    },
                    onOpenFile = { task ->
                        val file = File(FileHelper.getSaveDirectory(context, FileHelper.MediaType.OTHER), task.fileName)
                        if (file.exists()) {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file,
                            )
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, FileHelper.getMimeType(task.url))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        }
                    },
                )
                2 -> FailedDownloadsTab(
                    tasks = failedTasks,
                    onRetry = { task ->
                        val mediaItem = com.floatercapture.data.model.MediaItem(
                            type = task.mediaType,
                            url = task.url,
                        )
                        DownloadService.startDownload(context, listOf(mediaItem))
                    },
                )
            }
        }
    }
}

@Composable
private fun ActiveDownloadsTab(
    tasks: List<DownloadTask>,
    onPause: (DownloadTask) -> Unit,
    onResume: (DownloadTask) -> Unit,
    onCancelAll: () -> Unit,
) {
    if (tasks.isEmpty()) {
        EmptyState(message = "暂无下载任务")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onCancelAll,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = ErrorRed,
                    ),
                ) {
                    Text("取消全部")
                }
            }
        }

        items(tasks) { task ->
            DownloadingItem(
                task = task,
                onPause = onPause,
                onResume = onResume,
            )
        }
    }
}

@Composable
private fun DownloadingItem(
    task: DownloadTask,
    onPause: (DownloadTask) -> Unit,
    onResume: (DownloadTask) -> Unit,
) {
    val progressPercent = remember(task.totalBytes, task.downloadedBytes) {
        if (task.totalBytes > 0) {
            ((task.downloadedBytes.toFloat() / task.totalBytes) * 100).toInt()
        } else {
            0
        }
    }

    val statusLabel = when (task.state) {
        DownloadState.Downloading -> "下载中"
        DownloadState.Pending -> "等待中"
        DownloadState.Paused -> "已暂停"
        else -> ""
    }

    val statusColor = when (task.state) {
        DownloadState.Downloading -> InfoBlue
        DownloadState.Pending -> WarningOrange
        DownloadState.Paused -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = getTypeIcon(task.mediaType),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = task.fileName,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$progressPercent%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = progressPercent / 100f,
                modifier = Modifier.fillMaxWidth(),
                color = statusColor,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                )

                when (task.state) {
                    DownloadState.Paused -> {
                        TextButton(onClick = { onResume(task) }) {
                            Text("恢复")
                        }
                    }
                    DownloadState.Downloading, DownloadState.Pending -> {
                        TextButton(onClick = { onPause(task) }) {
                            Text("暂停")
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun CompletedDownloadsTab(
    tasks: List<DownloadTask>,
    onClearAll: () -> Unit,
    onOpenFile: (DownloadTask) -> Unit,
) {
    if (tasks.isEmpty()) {
        EmptyState(message = "暂无已完成下载")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onClearAll,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = ErrorRed,
                    ),
                ) {
                    Text("清除已完成")
                }
            }
        }

        items(tasks) { task ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenFile(task) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = getTypeIcon(task.mediaType),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = task.fileName,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "已完成",
                        tint = SuccessGreen,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FailedDownloadsTab(
    tasks: List<DownloadTask>,
    onRetry: (DownloadTask) -> Unit,
) {
    if (tasks.isEmpty()) {
        EmptyState(message = "暂无失败任务")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tasks) { task ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.fileName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "下载失败",
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed,
                        )
                    }
                    TextButton(onClick = { onRetry(task) }) {
                        Text("重试")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Inbox,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun getTypeIcon(type: MediaType): ImageVector {
    return when (type) {
        MediaType.IMAGE -> Icons.Default.Image
        MediaType.VIDEO -> Icons.Default.Videocam
        MediaType.DOCUMENT -> Icons.Default.Description
        MediaType.OTHER -> Icons.Default.InsertDriveFile
        else -> Icons.Default.InsertDriveFile
    }
}
