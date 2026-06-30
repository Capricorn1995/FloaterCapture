package com.floatercapture

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.floatercapture.service.TrafficSnifferService
import com.floatercapture.ui.main.DownloadListScreen
import com.floatercapture.ui.main.MainScreen
import com.floatercapture.ui.main.MediaListScreen
import com.floatercapture.ui.main.MediaPreviewScreen
import com.floatercapture.ui.settings.SettingsScreen
import com.floatercapture.ui.theme.FloaterCaptureTheme

class MainActivity : ComponentActivity() {

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // 用户授予 VPN 权限
            android.widget.Toast.makeText(
                this,
                "VPN 授权成功，正在启动流量嗅探",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            TrafficSnifferService.start(this)
        } else {
            android.widget.Toast.makeText(
                this,
                "VPN 授权被拒绝，无法启动流量嗅探",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FloaterCaptureTheme {
                MainApp()
            }
        }
    }

    /**
     * 启动 VPN 流量嗅探（含权限请求）
     */
    fun startVpnSniffer() {
        if (TrafficSnifferService.isRunning()) {
            // 已经在运行
            android.widget.Toast.makeText(this, "VPN 流量嗅探已在运行", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val intent = VpnService.prepare(this)
        if (intent != null) {
            // 需要用户授权
            vpnPermissionLauncher.launch(intent)
        } else {
            // 已经授权过
            TrafficSnifferService.start(this)
            android.widget.Toast.makeText(this, "正在启动流量嗅探", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 停止 VPN 流量嗅探
     */
    fun stopVpnSniffer() {
        if (!TrafficSnifferService.isRunning()) {
            android.widget.Toast.makeText(this, "VPN 流量嗅探未运行", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        TrafficSnifferService.stop(this)
        android.widget.Toast.makeText(this, "已停止流量嗅探", android.widget.Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavItems = listOf(
        BottomNavItem("main", Icons.Default.Home, "首页"),
        BottomNavItem("media_list", Icons.Default.PhotoLibrary, "媒体"),
        BottomNavItem("downloads", Icons.Default.Download, "下载"),
        BottomNavItem("settings", Icons.Default.Settings, "设置"),
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "main",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable("main") {
                MainScreen(navController = navController)
            }
            composable("media_list") {
                MediaListScreen(navController = navController)
            }
            composable("downloads") {
                DownloadListScreen(navController = navController)
            }
            composable("settings") {
                SettingsScreen(navController = navController)
            }
            composable("preview/{mediaId}") { backStackEntry ->
                val mediaId = backStackEntry.arguments?.getString("mediaId") ?: ""
                MediaPreviewScreen(mediaId = mediaId, navController = navController)
            }
        }
    }
}

data class BottomNavItem(
    val route: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
)
