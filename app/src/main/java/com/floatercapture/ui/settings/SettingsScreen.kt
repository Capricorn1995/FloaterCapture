package com.floatercapture.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.floatercapture.*
import com.floatercapture.data.db.PreferencesManager
import com.floatercapture.service.FloatingWindowService
import com.floatercapture.service.MediaCaptureService
import com.floatercapture.util.PermissionHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = FloaterApp.appContext
    val preferencesManager = remember { PreferencesManager(context) }
    val scope = rememberCoroutineScope()

    val isFloatingRunning by FloatingWindowService.isRunning.collectAsState()
    val isAccessibilityEnabled = remember { mutableStateOf(MediaCaptureService.isServiceEnabled(context)) }

    // 从 DataStore 读取持久化设置
    val wifiOnly by preferencesManager.wifiOnly.collectAsState(initial = false)
    val downloadNotify by preferencesManager.downloadNotify.collectAsState(initial = true)
    val maxConcurrent by preferencesManager.maxConcurrent.collectAsState(initial = 3)
    val darkMode by preferencesManager.darkMode.collectAsState(initial = false)

    var concurrentExpanded by remember { mutableStateOf(false) }
    val concurrentOptions = listOf(1, 2, 3, 5)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 分区1 - 服务设置
            item {
                SectionHeader("服务设置")
            }

            item {
                SettingsCard {
                    SwitchRow(
                        icon = Icons.Default.Visibility,
                        title = "悬浮窗",
                        subtitle = if (isFloatingRunning) "运行中" else "未运行",
                        checked = isFloatingRunning,
                        onCheckedChange = {
                            if (isFloatingRunning) {
                                FloatingWindowService.stopService(context)
                            } else {
                                FloatingWindowService.startService(context)
                            }
                        },
                    )
                }
            }

            item {
                SettingsCard {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Accessibility,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "无障碍服务",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = if (isAccessibilityEnabled.value) "已开启" else "未开启",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = { PermissionHelper.openAccessibilitySettings(context) },
                            ) {
                                Text("前往系统设置")
                            }
                        }
                    }
                }
            }

            // 分区2 - 下载设置
            item {
                SectionHeader("下载设置")
            }

            item {
                SettingsCard {
                    ExposedDropdownMenuBox(
                        expanded = concurrentExpanded,
                        onExpandedChange = { concurrentExpanded = it },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "最大并发下载数",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "$maxConcurrent",
                            style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        ExposedDropdownMenu(
                            expanded = concurrentExpanded,
                            onDismissRequest = { concurrentExpanded = false },
                        ) {
                            concurrentOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text("$option") },
                                    onClick = {
                                        scope.launch { preferencesManager.setMaxConcurrent(option) }
                                        concurrentExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsCard {
                    SwitchRow(
                        icon = Icons.Default.Wifi,
                        title = "仅WiFi下载",
                        subtitle = "仅在WiFi网络下下载文件",
                        checked = wifiOnly,
                        onCheckedChange = {
                            scope.launch { preferencesManager.setWifiOnly(it) }
                        },
                    )
                }
            }

            // 分区3 - 通知设置
            item {
                SectionHeader("通知设置")
            }

            item {
                SettingsCard {
                    SwitchRow(
                        icon = Icons.Default.Notifications,
                        title = "下载完成通知",
                        subtitle = "下载任务完成时发送通知",
                        checked = downloadNotify,
                        onCheckedChange = {
                            scope.launch { preferencesManager.setDownloadNotify(it) }
                        },
                    )
                }
            }

            // 分区4 - 外观设置
            item {
                SectionHeader("外观设置")
            }

            item {
                SettingsCard {
                    SwitchRow(
                        icon = Icons.Default.DarkMode,
                        title = "深色模式",
                        subtitle = "使用深色主题",
                        checked = darkMode,
                        onCheckedChange = {
                            scope.launch { preferencesManager.setDarkMode(it) }
                        },
                    )
                }
            }

            // 分区5 - 关于
            item {
                SectionHeader("关于")
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "版本号",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = "1.0.0",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "开源许可信息",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        Text(
                            text = "FloaterCapture - 悬浮窗内容抓取助手",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        content()
    }
}

@Composable
private fun SwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
