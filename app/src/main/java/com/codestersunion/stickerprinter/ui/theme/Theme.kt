package com.codestersunion.stickerprinter.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Kid-friendly light color scheme - bright and cheerful!
private val KidFriendlyColorScheme = lightColorScheme(
    primary = SunnyYellow,
    onPrimary = DarkChocolate,
    primaryContainer = SunnyYellowLight,
    onPrimaryContainer = DarkChocolate,
    
    secondary = PlayfulCoral,
    onSecondary = Color.White,
    secondaryContainer = PlayfulCoralLight,
    onSecondaryContainer = DarkChocolate,
    
    tertiary = SkyBlue,
    onTertiary = DarkChocolate,
    tertiaryContainer = SkyBlueLight,
    onTertiaryContainer = DarkChocolate,
    
    background = CreamyWhite,
    onBackground = DarkChocolate,
    
    surface = Color.White,
    onSurface = DarkChocolate,
    surfaceVariant = MintCream,
    onSurfaceVariant = WarmBrown,
    
    error = AlertRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = DarkChocolate,
    
    outline = SoftGray,
    outlineVariant = Color(0xFFE0E0E0)
)

// Keeping a dark scheme for accessibility, but still kid-friendly
private val KidFriendlyDarkColorScheme = darkColorScheme(
    primary = SunnyYellowLight,
    onPrimary = DarkChocolate,
    primaryContainer = SunnyYellowDark,
    onPrimaryContainer = Color.White,
    
    secondary = PlayfulCoralLight,
    onSecondary = DarkChocolate,
    secondaryContainer = PlayfulCoralDark,
    onSecondaryContainer = Color.White,
    
    tertiary = SkyBlueLight,
    onTertiary = DarkChocolate,
    tertiaryContainer = SkyBlueDark,
    onTertiaryContainer = Color.White,
    
    background = Color(0xFF1A1A2E),
    onBackground = CreamyWhite,
    
    surface = Color(0xFF252547),
    onSurface = CreamyWhite,
    surfaceVariant = Color(0xFF2D2D4A),
    onSurfaceVariant = Color(0xFFE0E0E0),
    
    error = AlertRed,
    onError = Color.White,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun StickerPrinterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Force light theme for kid-friendliness by default
    forceLight: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        forceLight -> KidFriendlyColorScheme
        darkTheme -> KidFriendlyDarkColorScheme
        else -> KidFriendlyColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Use a soft color for status bar
            window.statusBarColor = SkyBlue.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
