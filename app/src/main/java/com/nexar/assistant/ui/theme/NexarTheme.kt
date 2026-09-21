package com.nexar.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NexarCyan = Color(0xFF00D4FF)
val NexarBlue = Color(0xFF1A4AFF)
val NexarDeepBlue = Color(0xFF0066CC)
val NexarBackground = Color(0xFF050A14)
val NexarSurface = Color(0xFF0A1628)
val NexarSurfaceVariant = Color(0xFF0F2040)
val NexarOnSurface = Color(0xFFE0F0FF)
val NexarOnSurfaceVariant = Color(0xFF8AABCC)
val NexarError = Color(0xFFFF5555)
val NexarSuccess = Color(0xFF00FF88)
val NexarWarning = Color(0xFFFFAA00)

private val NexarColorScheme = darkColorScheme(
    primary = NexarCyan,
    onPrimary = Color(0xFF002233),
    primaryContainer = Color(0xFF003A52),
    onPrimaryContainer = NexarCyan,
    secondary = NexarBlue,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF001A5C),
    onSecondaryContainer = Color(0xFFADBEFF),
    background = NexarBackground,
    onBackground = NexarOnSurface,
    surface = NexarSurface,
    onSurface = NexarOnSurface,
    surfaceVariant = NexarSurfaceVariant,
    onSurfaceVariant = NexarOnSurfaceVariant,
    error = NexarError,
    onError = Color(0xFF000000),
    outline = Color(0xFF1A4060),
    outlineVariant = Color(0xFF0F2A40),
)

@Composable
fun NexarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NexarColorScheme,
        content = content
    )
}
