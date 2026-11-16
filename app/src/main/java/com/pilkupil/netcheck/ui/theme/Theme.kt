package com.pilkupil.netcheck.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
  primary = Color(0xFF43E6A0),
  secondary = Color(0xFF63B3ED),
  tertiary = Color(0xFFFFD166),
  background = Color(0xFF0B1220),
  surface = Color(0xFF162033),
  onPrimary = Color(0xFF07130E),
  onBackground = Color(0xFFF4F7FC),
  onSurface = Color(0xFFF4F7FC)
)

@Composable
fun NetCheckTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = DarkColorScheme,
    typography = Typography,
    content = content
  )
}
