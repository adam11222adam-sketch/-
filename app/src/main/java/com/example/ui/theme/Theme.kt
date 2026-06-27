package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = PlumPrimaryDark,
    secondary = PlumSecondaryDark,
    tertiary = PlumPrimary,
    background = BackgroundDark,
    surface = SurfaceDark,
    onPrimary = Color.White,
    onSecondary = TextDark,
    onBackground = TextDark,
    onSurface = TextDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = PlumPrimary,
    secondary = PlumSecondary,
    tertiary = PlumTertiary,
    primaryContainer = PlumTertiary,
    secondaryContainer = PlumSecondary,
    background = NaturalBg,
    surface = WhiteSurface,
    onPrimary = Color.White,
    onSecondary = DarkCharcoal,
    onSecondaryContainer = DarkCharcoal,
    onTertiary = PlumPrimary,
    onBackground = DarkCharcoal,
    onSurface = DarkCharcoal,
    outline = OutlineGrey,
    outlineVariant = OutlineGrey
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Custom brand color persistence: fallback is true, but we encourage custom schematic
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
