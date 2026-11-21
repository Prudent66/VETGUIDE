package com.prudent66.vetguide

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.prudent66.vetguide.ui.navigation.Destinations
import com.prudent66.vetguide.ui.screens.cases.CaseDetailScreen
import com.prudent66.vetguide.ui.screens.cases.CaseListScreen
import com.prudent66.vetguide.ui.screens.diagnose.DiagnoseScreen
import com.prudent66.vetguide.ui.screens.home.HomeScreen
import com.prudent66.vetguide.ui.screens.knowledge.KnowledgeDetailScreen
import com.prudent66.vetguide.ui.screens.knowledge.KnowledgeScreen
import com.prudent66.vetguide.ui.screens.patients.PatientFormScreen
import com.prudent66.vetguide.ui.screens.patients.PatientListScreen
import com.prudent66.vetguide.ui.screens.settings.SettingsScreen

/**
 * App scaffold: copyright footer + bottom navigation bar (top-level tabs) +
 * NavHost. Drill-in routes (patient add/edit, case timeline, case record, KB
 * detail) hide the nav bar but keep the footer at the extreme bottom.
 */
@Composable
fun VetguideApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevel = Destinations.isTopLevel(currentRoute)

    Scaffold(
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                AppFooter(withSystemInset = !topLevel)
                if (topLevel) {
                    VetguideBottomBar(navController, currentRoute)
                }
            }
        },
    ) { innerPadding ->
        VetguideNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/** Subtle centred copyright line pinned above the system navigation bar. */
@Composable
private fun AppFooter(withSystemInset: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (withSystemInset) {
                        Modifier.navigationBarsPadding()
                    } else {
                        Modifier
                    },
                ),
        ) {
            HorizontalDivider()
            Text(
                text = "© 2026 Veloxdigitalforge - All Rights Reserved",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 16.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VetguideNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Destinations.HOME,
        modifier = modifier,
    ) {
        composable(Destinations.HOME) {
            HomeScreen(
                onPatientClick = { navController.navigate(Destinations.casesForPatient(it)) },
                onCaseClick = { navController.navigate(Destinations.caseDetail(it)) },
            )
        }
        composable(Destinations.DIAGNOSE) {
            DiagnoseScreen(
                onOpenCondition = { navController.navigate(Destinations.knowledgeDetail(it)) },
                onCaseSaved = { navController.navigate(Destinations.caseDetail(it)) },
            )
        }
        composable(Destinations.PATIENTS) {
            PatientListScreen(
                onPatientClick = { navController.navigate(Destinations.casesForPatient(it)) },
                onAddPatientClick = { navController.navigate(Destinations.PATIENTS_ADD) },
                onEditPatientClick = { navController.navigate(Destinations.patientEdit(it)) },
            )
        }
        composable(Destinations.KNOWLEDGE) {
            KnowledgeScreen(
                onConditionClick = { navController.navigate(Destinations.knowledgeDetail(it)) },
            )
        }
        composable(Destinations.SETTINGS) { SettingsScreen() }
        composable(Destinations.PATIENTS_ADD) {
            PatientFormScreen(
                onSaved = navController::popBackStack,
                onDismiss = navController::popBackStack,
            )
        }
        composable(Destinations.PATIENT_EDIT) {
            PatientFormScreen(
                onSaved = navController::popBackStack,
                onDismiss = navController::popBackStack,
            )
        }
        composable(Destinations.CASES_FOR_PATIENT) {
            CaseListScreen(
                onBack = navController::popBackStack,
                onCaseClick = { navController.navigate(Destinations.caseDetail(it)) },
                onEditPatient = { navController.navigate(Destinations.patientEdit(it)) },
            )
        }
        composable(Destinations.CASE_DETAIL) {
            CaseDetailScreen(onBack = navController::popBackStack)
        }
        composable(Destinations.KB_CONDITION_DETAIL) {
            KnowledgeDetailScreen(onBack = navController::popBackStack)
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
