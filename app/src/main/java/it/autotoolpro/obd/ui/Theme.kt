package it.autotoolpro.obd.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Palette da quadro strumenti: grafite calda, ambra delle spie, verde "ok", rosso avaria.
private val Amber = Color(0xFFFFB524)
private val AmberDeep = Color(0xFF3A2A06)
private val Ink = Color(0xFF0C0E11)
private val Surface0 = Color(0xFF111418)
private val Surface1 = Color(0xFF171B20)
private val Surface2 = Color(0xFF1E2329)
private val Surface3 = Color(0xFF262C33)
private val Text1 = Color(0xFFECEEF1)
private val Text2 = Color(0xFFA9B1BB)
private val Ok = Color(0xFF63D69E)
private val Fault = Color(0xFFFF6259)

private val Scheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1F1500),
    primaryContainer = AmberDeep,
    onPrimaryContainer = Color(0xFFFFD98A),
    secondary = Color(0xFF8DB9E6),
    onSecondary = Color(0xFF0A1A2A),
    secondaryContainer = Color(0xFF1C2E40),
    onSecondaryContainer = Color(0xFFCFE3F8),
    tertiary = Ok,
    onTertiary = Color(0xFF00210F),
    tertiaryContainer = Color(0xFF123323),
    onTertiaryContainer = Color(0xFFB4F2D0),
    background = Ink,
    onBackground = Text1,
    surface = Ink,
    onSurface = Text1,
    surfaceVariant = Surface2,
    onSurfaceVariant = Text2,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Surface0,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface3,
    outline = Color(0xFF6E7782),
    outlineVariant = Color(0xFF2A3139),
    error = Fault,
    onError = Color(0xFF2B0300),
    errorContainer = Color(0xFF3D1210),
    onErrorContainer = Color(0xFFFFC9C4),
)

/** Roboto Condensed di sistema: cifre strette e leggibili come sul cruscotto. */
val Condensed = FontFamily(
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Normal),
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Medium),
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Bold),
)

/** Cifre a larghezza fissa: i valori live non "ballano" mentre cambiano. */
const val TabularNums = "tnum"

private val base = Typography()

private val Type = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em),
    displayMedium = base.displayMedium.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em),
    displaySmall = base.displaySmall.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em),
    headlineLarge = base.headlineLarge.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium.copy(lineHeight = 21.sp),
    bodySmall = base.bodySmall.copy(lineHeight = 18.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall,
)

/** Numero grande dei quadranti. */
val GaugeStyle = TextStyle(
    fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 36.sp,
    letterSpacing = (-0.01).em, fontFeatureSettings = TabularNums,
)

private val Shape = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Tema scuro sempre attivo: si usa in auto e in officina, spesso con poca luce. */
@Composable
fun AutoToolTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Type, shapes = Shape, content = content)
}
