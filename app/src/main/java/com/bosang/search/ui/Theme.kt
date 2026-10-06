package com.bosang.search.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF2B4C8C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4F7),
    onPrimaryContainer = Color(0xFF0E1E40),
    secondary = Color(0xFF0E7C72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3EFEB),
    onSecondaryContainer = Color(0xFF00201D),
    tertiary = Color(0xFFA4501E),
    tertiaryContainer = Color(0xFFFBE3D4),
    onTertiaryContainer = Color(0xFF3A1600),
    background = Color(0xFFF5F6F9),
    surface = Color(0xFFF5F6F9),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEDEFF4),
    surfaceVariant = Color(0xFFE6E9F0),
    onSurfaceVariant = Color(0xFF4A5060),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFAFC4F2),
    onPrimary = Color(0xFF102552),
    primaryContainer = Color(0xFF243E73),
    onPrimaryContainer = Color(0xFFDCE4F7),
    secondary = Color(0xFF7FD3C8),
    onSecondary = Color(0xFF003732),
    secondaryContainer = Color(0xFF0D4A44),
    onSecondaryContainer = Color(0xFFD3EFEB),
    tertiary = Color(0xFFF2B48C),
    tertiaryContainer = Color(0xFF5C2C10),
    onTertiaryContainer = Color(0xFFFBE3D4),
    background = Color(0xFF111318),
    surface = Color(0xFF111318),
    surfaceContainer = Color(0xFF1B1E25),
    surfaceContainerHigh = Color(0xFF242833),
    surfaceVariant = Color(0xFF2A2F38),
    onSurfaceVariant = Color(0xFFB9BFCC),
)

@Composable
fun BosangTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
