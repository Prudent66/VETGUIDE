package com.prudent66.vetguide

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.prudent66.vetguide.ui.navigation.Destinations
import com.prudent66.vetguide.ui.screens.cases.CasesScreen
import com.prudent66.vetguide.ui.screens.diagnose.DiagnoseScreen
import com.prudent66.vetguide.ui.screens.home.HomeScreen
import com.prudent66.vetguide.ui.screens.knowledge.KnowledgeScreen
import com.prudent66.vetguide.ui.screens.settings.SettingsScreen

/**
 * App scaffold: bottom navigation bar + NavHost.
 * Top-level tab navigation uses saveState/restoreState so tab state survives switches.
 */
@Composable
fun VetguideApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destinations.items.forEach { item ->
                    val selected = currentRoute == item.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(item.iconRes),
                                contentDescription = item.label,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destinations.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destinations.HOME) { HomeScreen() }
            composable(Destinations.DIAGNOSE) { DiagnoseScreen() }
            composable(Destinations.CASES) { CasesScreen() }
            composable(Destinations.KNOWLEDGE) { KnowledgeScreen() }
            composable(Destinations.SETTINGS) { SettingsScreen() }
        }
    }
}
