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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.prudent66.vetguide.ui.navigation.Destinations
import com.prudent66.vetguide.ui.screens.cases.CaseListScreen
import com.prudent66.vetguide.ui.screens.diagnose.DiagnoseScreen
import com.prudent66.vetguide.ui.screens.home.HomeScreen
import com.prudent66.vetguide.ui.screens.knowledge.KnowledgeDetailScreen
import com.prudent66.vetguide.ui.screens.knowledge.KnowledgeScreen
import com.prudent66.vetguide.ui.screens.patients.AddPatientScreen
import com.prudent66.vetguide.ui.screens.patients.PatientListScreen
import com.prudent66.vetguide.ui.screens.settings.SettingsScreen

/**
 * App scaffold: bottom navigation bar (top-level tabs) + NavHost.
 * Drill-in routes (add patient, case timeline) hide the bottom bar.
 */
@Composable
fun VetguideApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (Destinations.isTopLevel(currentRoute)) {
                VetguideBottomBar(navController, currentRoute)
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destinations.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destinations.HOME) { HomeScreen() }
            composable(Destinations.DIAGNOSE) {
                DiagnoseScreen(
                    onOpenCondition = { conditionId ->
                        navController.navigate(Destinations.knowledgeDetail(conditionId))
                    },
                )
            }
            composable(Destinations.PATIENTS) {
                PatientListScreen(
                    onPatientClick = { patientId ->
                        navController.navigate(Destinations.casesForPatient(patientId))
                    },
                    onAddPatientClick = { navController.navigate(Destinations.PATIENTS_ADD) },
                )
            }
            composable(Destinations.KNOWLEDGE) {
                KnowledgeScreen(
                    onConditionClick = { id ->
                        navController.navigate(Destinations.knowledgeDetail(id))
                    },
                )
            }
            composable(Destinations.SETTINGS) { SettingsScreen() }
            composable(Destinations.PATIENTS_ADD) {
                AddPatientScreen(
                    onSaved = navController::popBackStack,
                    onDismiss = navController::popBackStack,
                )
            }
            composable(Destinations.CASES_FOR_PATIENT) {
                CaseListScreen(onBack = navController::popBackStack)
            }
            composable(Destinations.KB_CONDITION_DETAIL) {
                KnowledgeDetailScreen(onBack = navController::popBackStack)
            }
        }
    }
}

@Composable
private fun VetguideBottomBar(
    navController: NavHostController,
    currentRoute: String?,
) {
    NavigationBar {
        Destinations.items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) { navigateToTopLevel(navController, item.route) } },
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
}

private fun navigateToTopLevel(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
