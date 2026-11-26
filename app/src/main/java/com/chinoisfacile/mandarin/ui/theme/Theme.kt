package com.chinoisfacile.mandarin.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class MandarinExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successShadow: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val learning: Color,
    val learningContainer: Color,
    val onLearningContainer: Color,
    val due: Color,
    val dueContainer: Color,
    val onDueContainer: Color,
    val errorAccent: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val neutral: Color,
    val neutralShadow: Color,
    val pathLocked: Color,
    val neutralContainer: Color,
    val onNeutralContainer: Color,
    val lessonNode: Color,
    val onLessonNode: Color,
    val lessonNodeShadow: Color,
    val lessonNodeContainer: Color,
    val onLessonNodeContainer: Color,
)

private val LightExtendedColors = MandarinExtendedColors(
    success = JadePrimary,
    onSuccess = Color.White,
    successShadow = JadeShadow,
    successContainer = JadeContainer,
    onSuccessContainer = OnJadeContainer,
    learning = GoldAccent,
    learningContainer = GoldContainer,
    onLearningContainer = OnGoldContainer,
    due = DueLight,
    dueContainer = DueLightContainer,
    onDueContainer = OnDueLightContainer,
    errorAccent = CinabreError,
    errorContainer = CinabreContainer,
    onErrorContainer = OnCinabreContainer,
    neutral = LightOnSurfaceVariant,
    neutralShadow = LightNeutralShadow,
    pathLocked = LightPathLocked,
    neutralContainer = LightSurfaceVariant,
    onNeutralContainer = LightOnSurfaceVariant,
    lessonNode = SkyAudio,
    onLessonNode = Color.White,
    lessonNodeShadow = SkyAudioShadow,
    lessonNodeContainer = SkyAudioContainer,
    onLessonNodeContainer = OnSkyAudioContainer,
)

private val DarkExtendedColors = MandarinExtendedColors(
    success = DarkJadePrimary,
    onSuccess = Color(0xFF0A2611),
    successShadow = DarkJadeShadow,
    successContainer = DarkJadeContainer,
    onSuccessContainer = DarkOnJadeContainer,
    learning = DarkGoldAccent,
    learningContainer = DarkGoldContainer,
    onLearningContainer = DarkOnGoldContainer,
    due = DarkDueAccent,
    dueContainer = DarkDueContainer,
    onDueContainer = DarkOnDueContainer,
    errorAccent = DarkErrorAccent,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    neutral = DarkOnSurfaceVariant,
    neutralShadow = DarkNeutralShadow,
    pathLocked = DarkPathLocked,
    neutralContainer = DarkSurfaceVariant,
    onNeutralContainer = DarkOnSurfaceVariant,
    lessonNode = DarkSkyAudio,
    onLessonNode = Color(0xFF002638),
    lessonNodeShadow = DarkSkyAudioShadow,
    lessonNodeContainer = DarkSkyAudioContainer,
    onLessonNodeContainer = DarkOnSkyAudioContainer,
)

private val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

object MandarinTheme {
    val extendedColors: MandarinExtendedColors
        @Composable get() = LocalExtendedColors.current
}

private val LightColors = lightColorScheme(
    primary = JadePrimary,
    onPrimary = Color.White,
    primaryContainer = JadeContainer,
    onPrimaryContainer = OnJadeContainer,
    secondary = SkyAudio,
    onSecondary = Color.White,
    secondaryContainer = SkyAudioContainer,
    onSecondaryContainer = OnSkyAudioContainer,
    tertiary = GoldAccent,
    onTertiary = Color.White,
    tertiaryContainer = GoldContainer,
    onTertiaryContainer = OnGoldContainer,
    error = CinabreError,
    onError = Color.White,
    errorContainer = CinabreContainer,
    onErrorContainer = OnCinabreContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
)

private val DarkColors = darkColorScheme(
    primary = DarkJadePrimary,
    onPrimary = Color(0xFF0B2912),
    primaryContainer = DarkJadeContainer,
    onPrimaryContainer = DarkOnJadeContainer,
    secondary = DarkSkyAudio,
    onSecondary = Color(0xFF002638),
    secondaryContainer = DarkSkyAudioContainer,
    onSecondaryContainer = DarkOnSkyAudioContainer,
    tertiary = DarkGoldAccent,
    onTertiary = Color(0xFF331D00),
    tertiaryContainer = DarkGoldContainer,
    onTertiaryContainer = DarkOnGoldContainer,
    error = DarkErrorAccent,
    onError = Color(0xFF3B0909),
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
)

@Composable
fun ChinoisFacileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MandarinTypography,
            content = content,
        )
    }
}
