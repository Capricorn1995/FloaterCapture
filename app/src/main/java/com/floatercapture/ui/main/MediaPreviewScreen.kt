package com.floatercapture.ui.main

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.floatercapture.FloaterApp
import com.floatercapture.data.model.MediaItem
import com.floatercapture.data.model.MediaType
import com.floatercapture.data.repository.MediaRepository
import com.floatercapture.service.DownloadService
import com.floatercapture.ui.theme.*
import com.floatercapture.util.FileHelper
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPreviewScreen(
    mediaId: String,
    navController: NavController,
) {
    val context = FloaterApp.appContext
    val mediaRepository = remember { MediaRepository() }
    val scope = rememberCoroutineScope()
    val mediaItem by mediaRepository.getById(mediaId).collectAsState(initial = null)

    val isDownloaded = remember(mediaItem) {
        mediaItem?.let { item ->
            val file = File(item.localFilePath)
            file.exists()
        } ?: false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "媒体预览",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (mediaItem == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val item = mediaItem!!
        val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            // 媒体预览区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (item.type) {
                    MediaType.IMAGE -> {
                        AsyncImage(
                            model = item.localFilePath.ifEmpty { item.url },
                            contentDescription = "图片预览",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    MediaType.VIDEO -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = "视频文件",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.description.ifEmpty { "视频文件" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    MediaType.DOCUMENT -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "文档文件",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.description.ifEmpty { "文档文件" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    MediaType.OTHER -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.InsertDriveFile,
                                contentDescription = "文件",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.description.ifEmpty { "未知文件" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Divider()

            // 详细信息卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DetailRow("来源App", item.sourceAppName.ifEmpty { "未知" })
                    DetailRow("URL", item.url?.let { url -> if (url.length > 40) url.take(40) + "..." else url } ?: "无")
                    DetailRow("时间戳", dateFormat.format(Date(item.timestamp)))
                    DetailRow("文件大小", formatFileSize(item.fileSize))
                    DetailRow("MIME类型", item.mimeType.ifEmpty { item.type.mimeType })
                }
            }

            // 底部操作栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isDownloaded) {
                    Button(
                        onClick = {
                            DownloadService.startDownload(context, listOf(item))
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("下载")
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            val file = File(item.localFilePath)
                            if (file.exists()) {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file,
                                )
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, item.type.mimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("打开")
                    }

                    TextButton(
                        onClick = {
                            val file = File(item.localFilePath)
                            if (file.exists()) {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file,
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = item.type.mimeType
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "分享文件"))
                            }
                        },
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("分享")
                    }
                }

                TextButton(
                    onClick = {
                        scope.launch {
                            mediaRepository.delete(item.id)
                        }
                        navController.popBackStack()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = ErrorRed,
                    ),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "未知"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return "%.1f %s".format(size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
