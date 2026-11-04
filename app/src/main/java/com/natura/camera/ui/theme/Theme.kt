package com.natura.camera.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val NaturaColorScheme = darkColorScheme(
    primary = NaturaTextPrimary,
    onPrimary = NaturaBlack,
    primaryContainer = NaturaSurfaceDark,
    onPrimaryContainer = NaturaTextPrimary,
    secondary = NaturaAmber,
    onSecondary = NaturaBlack,
    secondaryContainer = NaturaSurfaceMedium,
    onSecondaryContainer = NaturaAmber,
    tertiary = NaturaTextMuted,
    onTertiary = NaturaBlack,
    background = NaturaBlack,
    onBackground = NaturaTextPrimary,
    surface = NaturaSurfaceDark,
    onSurface = NaturaTextPrimary,
    surfaceVariant = NaturaSurfaceMedium,
    onSurfaceVariant = NaturaTextMuted,
    outline = NaturaBorder,
    outlineVariant = NaturaDivider
)

@Composable
fun NaturaTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = NaturaBlack.toArgb()
                window.navigationBarColor = NaturaBlack.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                // Black bars with light status icons
                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = NaturaColorScheme,
        typography = NaturaTypography,
        content = content
    )
}
