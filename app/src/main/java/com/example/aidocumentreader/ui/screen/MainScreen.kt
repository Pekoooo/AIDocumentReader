package com.example.aidocumentreader.ui.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow

/**
 * Main screen with bottom navigation.
 *
 * NAVIGATION:
 * - Library: Upload and manage PDFs
 * - Chat: Ask questions about uploaded documents
 *
 * ARCHITECTURE:
 * - NavHost manages navigation between screens
 * - Bottom navigation bar for switching tabs
 * - Each screen is its own composable
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Add, contentDescription = "Library") },
                    label = { Text("Library") },
                    selected = currentDestination?.hierarchy?.any { it.route == "library" } == true,
                    onClick = {
                        navController.navigate("library") {
                            // Pop up to the start destination
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            // Avoid multiple copies of the same destination
                            launchSingleTop = true
                            // Restore state when reselecting a previously selected item
                            restoreState = true
                        }
                    }
                )

                NavigationBarItem(
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = "Chat") },
                    label = { Text("Chat") },
                    selected = currentDestination?.hierarchy?.any { it.route == "chat" } == true,
                    onClick = {
                        navController.navigate("chat") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "library",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("library") {
                DocumentLibraryScreen()
            }
            composable("chat") {
                RagChatScreen()
            }
        }
    }
}
