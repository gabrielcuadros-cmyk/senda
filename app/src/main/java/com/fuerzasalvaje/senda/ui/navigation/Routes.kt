package com.fuerzasalvaje.senda.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

// Type-safe routes (Navigation Compose + kotlinx-serialization).

@Serializable data object LoginRoute
@Serializable data object RegisterRoute

@Serializable data object HomeRoute
@Serializable data object CalendarRoute
@Serializable data object FocusRoute
@Serializable data object SimulacraRoute
@Serializable data object ProgressRoute

@Serializable data object StudyPlanRoute
@Serializable data object CourseListRoute
@Serializable data class CourseFormRoute(val courseId: String? = null)

/** [type] preselects Examen/Laboratorio/Proyecto for a new evaluation (EvaluationType name). */
@Serializable data class EvaluationFormRoute(val evaluationId: String? = null, val type: String? = null)

/** Bottom navigation tabs. */
enum class Tab(val route: Any, val label: String, val icon: ImageVector) {
    HOME(HomeRoute, "Inicio", Icons.Rounded.Home),
    CALENDAR(CalendarRoute, "Calendario", Icons.Rounded.CalendarMonth),
    FOCUS(FocusRoute, "Enfoque", Icons.Rounded.Timer),
    SIMULACRA(SimulacraRoute, "Simulacros", Icons.Rounded.Quiz),
    PROGRESS(ProgressRoute, "Progreso", Icons.Rounded.Insights),
}
