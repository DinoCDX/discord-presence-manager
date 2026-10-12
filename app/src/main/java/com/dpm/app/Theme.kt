package com.dpm.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
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
    surfaceContainer = Color(0xFFEFEDF4),
    surfaceContainerHigh = Color(0xFFE9E7EF),
    outline = Color(0xFF777680),
    outlineVariant = Color(0xFFC7C5D0)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFBEC2FF),
    onPrimary = Color(0xFF14209A),
    primaryContainer = Color(0xFF2E3AAC),
    onPrimaryContainer = Color(0xFFE0E0FF),
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
    surfaceContainer = Color(0xFF1F1F23),
    surfaceContainerHigh = Color(0xFF2A2A2D),
    outline = Color(0xFF918F9A),
    outlineVariant = Color(0xFF46464F)
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
    MaterialTheme(colorScheme = scheme, typography = DPMTypography, shapes = DPMShapes, content = content)
}
