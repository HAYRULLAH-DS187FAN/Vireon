package com.vireon.editor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val VireonDarkColorScheme = darkColorScheme(
    primary = VireonBlue,
    onPrimary = VireonText,
    secondary = VireonPurple,
    background = VireonBackground,
    onBackground = VireonText,
    surface = VireonSurface,
    onSurface = VireonText,
    surfaceVariant = VireonSurfaceElevated,
    onSurfaceVariant = VireonMuted
)

@Composable
fun VireonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VireonDarkColorScheme,
        content = content
    )
}
