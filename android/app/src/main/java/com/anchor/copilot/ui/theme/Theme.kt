package com.anchor.copilot.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Palette carried over from the web prototype's :root custom properties. */
object Anchor {
    val Dark = Color(0xFF1F3B3F)
    val Dark2 = Color(0xFF2B4E52)
    val Primary = Color(0xFF3B6E71)
    val Secondary = Color(0xFF84B59F)
    val Accent = Color(0xFFE76F51)
    val Text = Color(0xFF1F2937)
    val Muted = Color(0xFF6B7280)
    val Card = Color(0xFFF3F7F6)
    val Line = Color(0xFFE1E8E6)
    val Good = Color(0xFF2E7D5B)
    val GoodBg = Color(0xFFEAF2EE)
    val WarnBg = Color(0xFFFCEEE9)
    val SadBg = Color(0xFFE6ECF7)
    val Sad = Color(0xFF3452A0)
    val White = Color.White
    val TabInactive = Color(0xFFCFE3E1)
}

private val colors = lightColorScheme(
    primary = Anchor.Primary,
    onPrimary = Anchor.White,
    primaryContainer = Anchor.GoodBg,
    onPrimaryContainer = Anchor.Dark,
    secondary = Anchor.Secondary,
    onSecondary = Anchor.Dark,
    tertiary = Anchor.Accent,
    onTertiary = Anchor.White,
    background = Anchor.White,
    onBackground = Anchor.Text,
    surface = Anchor.White,
    onSurface = Anchor.Text,
    surfaceVariant = Anchor.Card,
    onSurfaceVariant = Anchor.Muted,
    surfaceContainer = Anchor.Card,
    surfaceContainerHigh = Anchor.White,
    inverseSurface = Anchor.Dark,
    inverseOnSurface = Anchor.White,
    inversePrimary = Anchor.Secondary,
    outline = Anchor.Line,
    outlineVariant = Anchor.Line,
    error = Anchor.Accent,
)

private val base = Typography()
private val type = base.copy(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Anchor.Text, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Anchor.Text, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Anchor.Text, lineHeight = 22.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Anchor.Text),
    bodyLarge = TextStyle(fontSize = 15.sp, color = Anchor.Text, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, color = Anchor.Text, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, color = Anchor.Muted, lineHeight = 18.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp),
)

@Composable
fun AnchorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = type, content = content)
}
