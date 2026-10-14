package com.fuerzasalvaje.senda.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fuerzasalvaje.senda.ui.auth.LoginScreen
import com.fuerzasalvaje.senda.ui.auth.RegisterScreen
import com.fuerzasalvaje.senda.ui.calendar.CalendarScreen
import com.fuerzasalvaje.senda.ui.components.AiBusyBanner
import com.fuerzasalvaje.senda.ui.components.ConfirmDialog
import com.fuerzasalvaje.senda.ui.components.EmptyState
import com.fuerzasalvaje.senda.ui.courses.CourseFormScreen
import com.fuerzasalvaje.senda.ui.courses.CourseListScreen
import com.fuerzasalvaje.senda.ui.evaluation.EvaluationFormScreen
import com.fuerzasalvaje.senda.ui.home.HomeScreen
import com.fuerzasalvaje.senda.ui.plan.StudyPlanScreen
import com.fuerzasalvaje.senda.ui.simulacra.ProgressScreen
import com.fuerzasalvaje.senda.ui.simulacra.SimulacraScreen
import com.fuerzasalvaje.senda.ui.simulacra.SimulacrumResultScreen
import com.fuerzasalvaje.senda.ui.simulacra.SimulacrumScreen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Root: login flow without a session, tabs with a session. */
@Composable
fun SendaRoot(sessionViewModel: SessionViewModel = hiltViewModel()) {
    val session by sessionViewModel.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        when (session) {
            SessionState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            SessionState.SignedOut -> AuthNavHost()
            is SessionState.SignedIn -> MainNavHost(onSignOut = sessionViewModel::signOut)
        }
        AiBusyBanner(Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun AuthNavHost() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = LoginRoute) {
        composable<LoginRoute> { LoginScreen(onGoToRegister = { navController.navigate(RegisterRoute) }) }
        composable<RegisterRoute> { RegisterScreen(onBack = { navController.popBackStack() }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainNavHost(onSignOut: () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val currentTab = Tab.entries.firstOrNull { tab -> destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true }
    var confirmSignOut by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (currentTab != null) {
                TopAppBar(
                    title = { Text(if (currentTab == Tab.HOME) "Senda" else currentTab.label) },
                    actions = {
                        IconButton(onClick = { navController.navigate(CourseListRoute) }) { Icon(Icons.Rounded.School, "Mis cursos") }
                        IconButton(onClick = { confirmSignOut = true }) { Icon(Icons.AutoMirrored.Rounded.Logout, "Cerrar sesión") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        },
        bottomBar = {
            if (currentTab != null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == currentTab,
                            onClick = { navController.navigateToTab(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentTab == Tab.HOME || currentTab == Tab.CALENDAR) {
                FloatingActionButton(
                    onClick = { navController.navigate(EvaluationFormRoute()) },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ) { Icon(Icons.Rounded.Add, "Nueva evaluación") }
            }
        },
    ) { padding ->
        // Full-screen forms draw their own bars, so they ignore the tab scaffold padding.
        val contentModifier = if (currentTab != null) Modifier.padding(padding) else Modifier
        NavHost(navController, startDestination = HomeRoute, modifier = contentModifier) {
            composable<HomeRoute> {
                HomeScreen(
                    onCreateCourse = { navController.navigate(CourseFormRoute()) },
                    onCreateEvaluation = { navController.navigate(EvaluationFormRoute()) },
                    onEditEvaluation = { navController.navigate(EvaluationFormRoute(evaluationId = it)) },
                    onOpenPlan = { navController.navigate(StudyPlanRoute) },
                )
            }
            composable<CalendarRoute> {
                CalendarScreen(
                    onEditEvaluation = { navController.navigate(EvaluationFormRoute(evaluationId = it)) },
                    onCreateCourse = { navController.navigate(CourseFormRoute()) },
                    onOpenPlan = { navController.navigate(StudyPlanRoute) },
                )
            }
            composable<FocusRoute> { ComingSoon("Pomodoro, pausas activas y bienestar llegan en una próxima fase.") }
            composable<SimulacraRoute> {
                SimulacraScreen(
                    onOpenSimulacrum = { navController.navigate(SimulacrumRoute(it)) },
                    onCreateCourse = { navController.navigate(CourseFormRoute()) },
                )
            }
            composable<ProgressRoute> {
                ProgressScreen(onOpenResult = { navController.navigate(SimulacrumResultRoute(it)) })
            }
            composable<SimulacrumRoute> {
                SimulacrumScreen(
                    onFinished = { id ->
                        // The finished attempt is replaced by its results, so Back returns to the tab.
                        navController.navigate(SimulacrumResultRoute(id)) {
                            popUpTo<SimulacrumRoute> { inclusive = true }
                        }
                    },
                    onExit = { navController.popBackStack() },
                )
            }
            composable<SimulacrumResultRoute> { SimulacrumResultScreen(onBack = { navController.popBackStack() }) }

            composable<CourseListRoute> {
                CourseListScreen(
                    onBack = { navController.popBackStack() },
                    onAdd = { navController.navigate(CourseFormRoute()) },
                    onOpen = { navController.navigate(CourseFormRoute(courseId = it)) },
                )
            }
            composable<StudyPlanRoute> { StudyPlanScreen(onBack = { navController.popBackStack() }) }
            composable<CourseFormRoute> { CourseFormScreen(onDone = { navController.popBackStack() }) }
            composable<EvaluationFormRoute> {
                EvaluationFormScreen(
                    onDone = { navController.popBackStack() },
                    onCreateCourse = { navController.navigate(CourseFormRoute()) },
                )
            }
        }
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = "¿Cerrar sesión?",
            message = "Tus datos quedan guardados en tu cuenta y se borran de este teléfono.",
            confirmLabel = "Cerrar sesión",
            onConfirm = { confirmSignOut = false; onSignOut() },
            onDismiss = { confirmSignOut = false },
        )
    }
}

private fun NavHostController.navigateToTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun ComingSoon(message: String) {
    EmptyState(
        icon = Icons.Rounded.HourglassTop,
        title = "Próximamente",
        message = message,
        modifier = Modifier.padding(top = 48.dp),
    )
}

