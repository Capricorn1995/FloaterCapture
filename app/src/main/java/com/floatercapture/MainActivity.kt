package com.floatercapture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.floatercapture.ui.browser.BrowserScreen
import com.floatercapture.ui.browser.ResourceListScreen
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

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "browser"

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Language, "浏览器") },
                    label = { Text("浏览器") },
                    selected = currentRoute == "browser",
                    onClick = {
                        navController.navigate("browser") {
                            popUpTo("browser") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.PhotoLibrary, "资源") },
                    label = { Text("资源列表") },
                    selected = currentRoute == "resources",
                    onClick = {
                        navController.navigate("resources") {
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "browser",
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            composable("browser") {
                BrowserScreen(
                    onNavigateToResources = {
                        navController.navigate("resources") { launchSingleTop = true }
                    }
                )
            }
            composable("resources") {
                ResourceListScreen(
                    onNavigateToBrowser = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
