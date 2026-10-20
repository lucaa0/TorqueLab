package it.autotoolpro.obd.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Amber = Color(0xFFFFB300)
private val Graphite = Color(0xFF121417)
private val Panel = Color(0xFF1B1F24)
private val PanelHigh = Color(0xFF252A31)

private val Scheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1B1300),
    secondary = Color(0xFF8FB8DE),
    onSecondary = Color(0xFF0B1A28),
    tertiary = Color(0xFF7FD4A0),
    background = Graphite,
    onBackground = Color(0xFFE6E8EB),
    surface = Graphite,
    onSurface = Color(0xFFE6E8EB),
    surfaceVariant = Panel,
    onSurfaceVariant = Color(0xFFB4BAC2),
    surfaceContainer = Panel,
    surfaceContainerHigh = PanelHigh,
    outline = Color(0xFF3A414A),
    error = Color(0xFFFF6B6B),
)

/** Tema scuro sempre attivo: pensato per l'officina e per schermi in luce variabile. */
@Composable
fun AutoToolTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
