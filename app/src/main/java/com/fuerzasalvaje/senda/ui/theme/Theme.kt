package com.fuerzasalvaje.senda.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fuerzasalvaje.senda.domain.model.DifficultyLevel
import com.fuerzasalvaje.senda.domain.model.EvaluationType

private val LightColorScheme = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E4FF),
    onPrimaryContainer = Color(0xFF1A1A6E),
    secondary = Coral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3DA),
    onSecondaryContainer = Color(0xFF5C1D0C),
    tertiary = DifficultyEasy,
    background = BackgroundLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = InkMutedLight,
    surfaceContainer = SurfaceLight,
    surfaceContainerLow = SurfaceLight,
    surfaceContainerHigh = SurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineLight,
    error = DifficultyHard,
)

private val DarkColorScheme = darkColorScheme(
    primary = IndigoLight,
    onPrimary = Color(0xFF14145A),
    primaryContainer = Color(0xFF34348F),
    onPrimaryContainer = Color(0xFFE4E4FF),
    secondary = CoralLight,
    onSecondary = Color(0xFF4A1505),
    secondaryContainer = Color(0xFF6E2A16),
    onSecondaryContainer = Color(0xFFFFE3DA),
    tertiary = DifficultyEasy,
    background = BackgroundDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = InkMutedDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerLow = SurfaceDark,
    surfaceContainerHigh = SurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineDark,
    error = Color(0xFFFF6B6F),
)

/** Senda colors that Material 3 has no slot for. */
@Immutable
data class SendaColors(
    val exam: Color,
    val lab: Color,
    val project: Color,
    val easy: Color = DifficultyEasy,
    val medium: Color = DifficultyMedium,
    val hard: Color = DifficultyHard,
    val clash: Color = DifficultyHard,
    val busyDay: Color = DifficultyMedium,
) {
    fun forType(type: EvaluationType): Color = when (type) {
        EvaluationType.EXAM -> exam
        EvaluationType.LAB -> lab
        EvaluationType.PROJECT -> project
    }

    fun forDifficulty(level: DifficultyLevel): Color = when (level) {
        DifficultyLevel.EASY -> easy
        DifficultyLevel.MEDIUM -> medium
        DifficultyLevel.HARD -> hard
    }
}

private val LightSendaColors = SendaColors(exam = ExamLight, lab = LabLight, project = ProjectLight)
private val DarkSendaColors = SendaColors(exam = ExamDark, lab = LabDark, project = ProjectDark)

private val LocalSendaColors = staticCompositionLocalOf { LightSendaColors }

val SendaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Card corner radius used across Senda. */
val CardShape = RoundedCornerShape(20.dp)

@Composable
fun SendaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSendaColors provides if (darkTheme) DarkSendaColors else LightSendaColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            shapes = SendaShapes,
            content = content,
        )
    }
}

object SendaTheme {
    val colors: SendaColors
        @Composable @ReadOnlyComposable get() = LocalSendaColors.current
}
