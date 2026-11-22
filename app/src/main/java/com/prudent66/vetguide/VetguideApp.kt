package com.prudent66.vetguide

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.prudent66.vetguide.ui.components.LocalBottomBarClearance
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Extra breathing room so bottom-most buttons clear the resting footer. */
private val bottomScrollClearance = 16.dp

/**
 * Debounces nested-scroll activity from any screen so the copyright footer
 * hides only while the user is actively scrolling and returns shortly after
 * scrolling stops.
 */
private class FooterScrollTracker(
    private val scope: CoroutineScope,
    private val onActivity: (Boolean) -> Unit,
) : NestedScrollConnection {

    private var debounceJob: Job? = null

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y != 0f) notifyActive()
        return Offset.Zero
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (consumed.y != 0f || available.y != 0f) notifyActive()
        return Offset.Zero
    }

    private fun notifyActive() {
        onActivity(true)
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(SCROLL_IDLE_DEBOUNCE_MS)
            onActivity(false)
        }
    }

    private companion object {
        const val SCROLL_IDLE_DEBOUNCE_MS = 350L
    }
}

/**
 * App scaffold: copyright footer + bottom navigation bar (top-level tabs) +
 * NavHost. Drill-in routes (patient add/edit, case timeline, case record, KB
 * detail) hide the nav bar but keep the footer at the extreme bottom.
 *
 * The footer collapses smoothly while any child screen scrolls vertically so
 * reading space is maximised, and reappears ~350 ms after scrolling stops.
 */
@Composable
fun VetguideApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevel = Destinations.isTopLevel(currentRoute)

    val scrolling = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scrollTracker = remember(scope, scrolling) {
        FooterScrollTracker(scope) { active -> scrolling.value = active }
    }

    // Resting bottom height of footer + bottom nav + system bars, measured
    // only while the footer is expanded. It must NOT track the collapsing
    // Scaffold innerPadding: otherwise the re-expanded footer covers the
    // bottom-most button (e.g. "Export PDF" on Case record) at scroll rest.
    val restingBottom = remember { mutableStateOf(0.dp) }

    Scaffold(
        bottomBar = {
            AppBottomBar(
                topLevel = topLevel,
                currentRoute = currentRoute,
                navController = navController,
                scrolling = scrolling.value,
                onRestingHeight = { measured ->
                    restingBottom.value = maxOf(restingBottom.value, measured)
                },
            )
        },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        // No outer bottom padding: the viewport runs flush to the footer so no
        // dark dead band appears above it. The resting clearance is consumed
        // inside each scrollable container instead.
        CompositionLocalProvider(
            LocalBottomBarClearance provides restingBottom.value + bottomScrollClearance,
        ) {
            VetguideNavHost(
                navController = navController,
                modifier = Modifier
                    .padding(
                        PaddingValues(
                            start = innerPadding.calculateStartPadding(layoutDirection),
                            top = innerPadding.calculateTopPadding(),
                            end = innerPadding.calculateEndPadding(layoutDirection),
                            bottom = 0.dp,
                        ),
                    )
                    .nestedScroll(scrollTracker),
            )
        }
    }
}

/**
 * Bottom bar whose measured resting height is reported upward. Measurement is
 * skipped while the footer is collapsed so only the full expanded height is
 * used for the stable content clearance.
 */
@Composable
private fun AppBottomBar(
    topLevel: Boolean,
    currentRoute: String?,
    navController: NavHostController,
    scrolling: Boolean,
    onRestingHeight: (Dp) -> Unit,
) {
    val density = LocalDensity.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                if (!scrolling) onRestingHeight(with(density) { size.height.toDp() })
            },
    ) {
        AnimatedVisibility(
            visible = !scrolling,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            AppFooter(withSystemInset = !topLevel)
        }
        if (topLevel) {
            VetguideBottomBar(navController, currentRoute)
        }
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
