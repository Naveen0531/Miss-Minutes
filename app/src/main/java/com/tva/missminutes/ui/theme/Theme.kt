package com.tva.missminutes.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── TVA Dark Color Scheme ─────────────────────────────────────────────────
private val TVADarkColorScheme = darkColorScheme(
    primary          = TVAAmber,
    onPrimary        = TVABackground,
    primaryContainer = TVASurface,
    onPrimaryContainer = TVAWarmOrange,

    secondary        = TVAWarmOrange,
    onSecondary      = TVABackground,
    secondaryContainer = TVASurfaceVariant,
    onSecondaryContainer = TVAMuted,

    tertiary         = TVAHotOrange,
    onTertiary       = TVABackground,

    background       = TVABackground,
    onBackground     = TVAAmber,

    surface          = TVASurface,
    onSurface        = TVAAmber,
    surfaceVariant   = TVASurfaceVariant,
    onSurfaceVariant = TVAMuted,

    outline          = TVAMuted,
    outlineVariant   = TVADimmed,

    error            = TVAError,
    onError          = TVABackground,
    errorContainer   = TVAErrorMuted,
    onErrorContainer = TVAError,

    scrim            = Color(0xCC0A0604),
    inverseSurface   = TVAAmber,
    inverseOnSurface = TVABackground,
)

@Composable
fun MissMinutesTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Transparent status bar to let the black bleed through
            window.statusBarColor = TVABackground.toArgb()
            window.navigationBarColor = TVABackground.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = TVADarkColorScheme,
        typography  = MissMinutesTypography,
        content     = content
    )
}
