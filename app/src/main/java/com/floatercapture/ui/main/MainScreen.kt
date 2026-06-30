package com.floatercapture.ui.main

import android.content.Intent
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.floatercapture.FloaterApp
import com.floatercapture.data.model.MediaItem
import com.floatercapture.data.model.MediaType
import com.floatercapture.data.repository.MediaRepository
import com.floatercapture.service.FloatingWindowService
import com.floatercapture.service.MediaCaptureService
import com.floatercapture.service.ScreenCaptureService
import com.floatercapture.service.TrafficSnifferService
import com.floatercapture.util.PermissionHelper
import com.floatercapture.ui.theme.*
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController) {
    val context = FloaterApp.appContext

    // 使用 collectAsState 订阅 StateFlow，避免同步读取阻塞 UI
    val isFloatingRunning by FloatingWindowService.isRunning.collectAsState()

    val mediaRepository = remember { MediaRepository() }
    val mediaItems by mediaRepository.getAll().collectAsState(initial = emptyList())
    val recentItems = mediaItems.take(5)

    // 权限状态使用 remember + LaunchedEffect 延迟检查，避免主线程阻塞
    var overlayGranted by remember { mutableStateOf(false) }
    var accessibilityGranted by remember { mutableStateOf(false) }
    var vpnRunning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // 在协程中检查权限，避免主线程 IPC 阻塞
        overlayGranted = PermissionHelper.canDrawOverlays(context)
        accessibilityGranted = MediaCaptureService.isServiceEnabled(context)
        vpnRunning = TrafficSnifferService.isRunning()
    }

    val showPermissionWarning = !overlayGranted || !accessibilityGranted

    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 标题
            item {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        text = "FloaterCapture",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "悬浮窗内容抓取助手",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 状态卡片区
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ElevatedCard(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (isFloatingRunning) {
                                FloatingWindowService.stopService(context)
                            } else {
                                if (!PermissionHelper.canDrawOverlays(context)) {
                                    PermissionHelper.openOverlaySettings(context)
                                } else {
                                    FloatingWindowService.startService(context)
                                }
                            }
                        },
                    ) {
                        StatusCardContent(
                            title = "悬浮窗",
                            isActive = isFloatingRunning,
                            activeLabel = "运行中",
                            inactiveLabel = "未运行",
                            activeIcon = Icons.Default.Visibility,
                            inactiveIcon = Icons.Default.VisibilityOff,
                        )
                    }

                    ElevatedCard(
                        modifier = Modifier.weight(1f),
                    ) {
                        StatusCardContent(
                            title = "无障碍服务",
                            isActive = accessibilityGranted,
                            activeLabel = "已开启",
                            inactiveLabel = "未开启",
                            activeIcon = Icons.Default.CheckCircle,
                            inactiveIcon = Icons.Default.Cancel,
                        )
                    }
                }
            }

            // VPN 流量嗅探卡片
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (vpnRunning) {
                            TrafficSnifferService.stop(context)
                            vpnRunning = false
                        } else {
                            val prepareIntent = TrafficSnifferService.requestVpnPermission(context)
                            if (prepareIntent != null) {
                                prepareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(prepareIntent)
                            } else {
                                TrafficSnifferService.start(context)
                                vpnRunning = true
                            }
                        }
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = if (vpnRunning) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "流量嗅探 (VPN)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = if (vpnRunning) "正在拦截网络流量" else "点击开启流量嗅探",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (vpnRunning) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            imageVector = if (vpnRunning) Icons.Default.ToggleOn else Icons.Default.ToggleOff,
                            contentDescription = null,
                            tint = if (vpnRunning) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
            }

            // 权限检查卡片
            if (showPermissionWarning) {
                item {
                    ElevatedCard(
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = WarningOrange.copy(alpha = 0.1f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = WarningOrange,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "需要权限",
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "请开启悬浮窗权限和无障碍服务以正常使用",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = {
                                    if (!overlayGranted) {
                                        PermissionHelper.openOverlaySettings(context)
                                    }
                                    if (!accessibilityGranted) {
                                        PermissionHelper.openAccessibilitySettings(context)
                                    }
                                },
                            ) {
                                Text("前往设置")
                            }
                        }
                    }
                }
            }

            // 最近捕获
            item {
                Text(
                    text = "最近捕获",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (recentItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
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
                                text = "暂无捕获内容，开启悬浮窗开始使用",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(recentItems) { mediaItem ->
                    RecentCaptureItem(mediaItem)
                }
            }

            // 快速操作
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            if (!PermissionHelper.canDrawOverlays(context)) {
                                PermissionHelper.openOverlaySettings(context)
                            } else {
                                FloatingWindowService.startService(context)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.FiberManualRecord, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("开始捕获", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = { navController.navigate("media_list") },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("媒体列表", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = { navController.navigate("downloads") },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("下载", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCardContent(
    title: String,
    isActive: Boolean,
    activeLabel: String,
    inactiveLabel: String,
    activeIcon: ImageVector,
    inactiveIcon: ImageVector,
) {
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (isActive) activeIcon else inactiveIcon,
            contentDescription = null,
            tint = if (isActive) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isActive) activeLabel else inactiveLabel,
            style = MaterialTheme.typography.bodySmall,
            color = if (isActive) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecentCaptureItem(item: MediaItem) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = getMediaTypeIcon(item.type),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.sourceAppName.ifEmpty { "未知来源" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = dateFormat.format(Date(item.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = item.type.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun getMediaTypeIcon(type: MediaType): ImageVector {
    return when (type) {
        MediaType.IMAGE -> Icons.Default.Image
        MediaType.VIDEO -> Icons.Default.Videocam
        MediaType.DOCUMENT -> Icons.Default.Description
        MediaType.OTHER -> Icons.Default.InsertDriveFile
        else -> Icons.Default.InsertDriveFile
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    FloaterCaptureTheme {
        MainScreen(navController = NavController(FloaterApp.appContext))
    }
}
