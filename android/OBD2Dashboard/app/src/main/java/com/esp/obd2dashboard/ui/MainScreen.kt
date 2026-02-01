package com.esp.obd2dashboard.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.esp.obd2dashboard.viewmodel.ObdViewModel

/** Main screen with bottom navigation */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: ObdViewModel = viewModel()) {
        val navController = rememberNavController()

        Scaffold(
                topBar = {
                        TopAppBar(
                                title = { Text("OBD2 Dashboard") },
                                colors =
                                        TopAppBarDefaults.topAppBarColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                titleContentColor =
                                                        MaterialTheme.colorScheme.onPrimary
                                        )
                        )
                },
                bottomBar = {
                        NavigationBar {
                                NavigationBarItem(
                                        selected = false,
                                        onClick = { navController.navigate("connection") },
                                        icon = { Text("🔌") },
                                        label = { Text("Connect") }
                                )
                                NavigationBarItem(
                                        selected = false,
                                        onClick = { navController.navigate("metrics") },
                                        icon = { Text("📊") },
                                        label = { Text("Metrics") }
                                )
                                NavigationBarItem(
                                        selected = false,
                                        onClick = { navController.navigate("terminal") },
                                        icon = { Text("⌨️") },
                                        label = { Text("Terminal") }
                                )
                                NavigationBarItem(
                                        selected = false,
                                        onClick = { navController.navigate("debug") },
                                        icon = { Text("🐛") },
                                        label = { Text("Debug") }
                                )
                                NavigationBarItem(
                                        selected = false,
                                        onClick = { navController.navigate("streaming") },
                                        icon = { Text("📡") },
                                        label = { Text("Stream") }
                                )
                        }
                }
        ) { padding ->
                NavHost(
                        navController = navController,
                        startDestination = "connection",
                        modifier = Modifier.padding(padding)
                ) {
                        composable("connection") { ConnectionScreen(viewModel) }
                        composable("metrics") { MetricsScreen(viewModel) }
                        composable("terminal") { TerminalScreen(viewModel) }
                        composable("debug") { DebugScreen(viewModel) }
                        composable("streaming") { StreamingScreen(viewModel) }
                }
        }
}
