package com.floatercapture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.floatercapture.ui.main.DownloadListScreen
import com.floatercapture.ui.main.MainScreen
import com.floatercapture.ui.main.MediaListScreen
import com.floatercapture.ui.main.MediaPreviewScreen
import com.floatercapture.ui.settings.SettingsScreen
import com.floatercapture.ui.theme.FloaterCaptureTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FloaterCaptureTheme {
                MainApp()
            }
        }
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
        BottomNavItem("media_list", Icons.Default.List, "媒体"),
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
                            // 导航到目标路由
                            navController.navigate(item.route) {
                                // 弹出到起始目的地，但不包含它
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                // 避免重复创建相同目的地
                                launchSingleTop = true
                                // 恢复之前保存的状态
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
