package com.dpm.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Light = lightColorScheme(
    primary = Color(0xFF4752C4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E0FF),
    onPrimaryContainer = Color(0xFF000F5C),
    inversePrimary = Color(0xFFBEC2FF),
    secondary = Color(0xFF5B5D72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E1F9),
    onSecondaryContainer = Color(0xFF181A2C),
    tertiary = Color(0xFF77536B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD7EE),
    onTertiaryContainer = Color(0xFF2D1228),
    background = Color(0xFFFBF8FD),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FD),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    surfaceTint = Color(0xFF4752C4),
    inverseSurface = Color(0xFF303036),
    inverseOnSurface = Color(0xFFF2EFF4),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF777680),
    outlineVariant = Color(0xFFC7C5D0),
    surfaceDim = Color(0xFFDBD9DE),
    surfaceBright = Color(0xFFFBF8FD),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F2F8),
    surfaceContainer = Color(0xFFEFEDF4),
    surfaceContainerHigh = Color(0xFFE9E7EF),
    surfaceContainerHighest = Color(0xFFE4E1E9)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFBEC2FF),
    onPrimary = Color(0xFF14209A),
    primaryContainer = Color(0xFF2E3AAC),
    onPrimaryContainer = Color(0xFFE0E0FF),
    inversePrimary = Color(0xFF4752C4),
    secondary = Color(0xFFC4C5DD),
    onSecondary = Color(0xFF2D2F42),
    secondaryContainer = Color(0xFF434559),
    onSecondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFFE6BAD7),
    onTertiary = Color(0xFF44263D),
    tertiaryContainer = Color(0xFF5D3C54),
    onTertiaryContainer = Color(0xFFFFD7EE),
    background = Color(0xFF131316),
    onBackground = Color(0xFFE4E1E6),
    surface = Color(0xFF131316),
    onSurface = Color(0xFFE4E1E6),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    surfaceTint = Color(0xFFBEC2FF),
    inverseSurface = Color(0xFFE4E1E6),
    inverseOnSurface = Color(0xFF303034),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF918F9A),
    outlineVariant = Color(0xFF46464F),
    surfaceDim = Color(0xFF131316),
    surfaceBright = Color(0xFF39393D),
    surfaceContainerLowest = Color(0xFF0E0E11),
    surfaceContainerLow = Color(0xFF1B1B1F),
    surfaceContainer = Color(0xFF1F1F23),
    surfaceContainerHigh = Color(0xFF2A2A2D),
    surfaceContainerHighest = Color(0xFF353438)
)

fun Context.activity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun DPMTheme(themeMode: String, dynamic: Boolean, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val w = ctx.activity()?.window ?: return@SideEffect
            val ic = WindowCompat.getInsetsController(w, view)
            ic.isAppearanceLightStatusBars = !dark
            ic.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), content = content)
}
