package dev.merqadyn.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF191A1D)
val Paper = Color(0xFFF4F0E8)
val Chalk = Color(0xFFFBF8F2)
val Rust = Color(0xFFC4573D)
val RustDark = Color(0xFF843A2A)
val Stone = Color(0xFF68645E)
val Rule = Color(0xFFD8D1C5)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Chalk,
    secondary = Rust,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Chalk,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEAE4D9),
    onSurfaceVariant = Stone,
    outline = Rule,
    error = RustDark,
)

@Composable
fun MerqadynTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
