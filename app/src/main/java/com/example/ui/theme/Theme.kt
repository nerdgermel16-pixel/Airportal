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

private val DarkColorScheme = darkColorScheme(
  primary = BluePrimaryDark,
  secondary = CyanAccent,
  tertiary = EmeraldSuccess,
  background = SlateBackgroundDark,
  surface = SlateSurfaceDark,
  surfaceVariant = SlateCardDark,
  onPrimary = Color.Black,
  onSecondary = Color.Black,
  onTertiary = Color.White,
  onBackground = Color(0xFFF8FAFC),
  onSurface = Color(0xFFF8FAFC),
  onSurfaceVariant = Color(0xFF94A3B8),
  error = CoralError
)

private val LightColorScheme = lightColorScheme(
  primary = BluePrimary,
  secondary = CyanAccent,
  tertiary = EmeraldSuccess,
  background = SlateBackgroundLight,
  surface = SlateSurfaceLight,
  surfaceVariant = SlateCardLight,
  onPrimary = Color.White,
  onSecondary = Color.White,
  onTertiary = Color.White,
  onBackground = Color(0xFF0F172A),
  onSurface = Color(0xFF0F172A),
  onSurfaceVariant = Color(0xFF64748B),
  error = CoralError
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
