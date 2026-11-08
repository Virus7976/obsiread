package app.obsiread.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView

/** A set of reader colours: the page, the text, the quiet text, and the accent for links and the bar. */
data class ReaderPalette(val id: String, val name: String, val page: Color, val text: Color, val muted: Color, val accent: Color)

val ReaderPalettes = listOf(
    ReaderPalette("paper", "Paper", Color(0xFFFBF8F1), Color(0xFF1F1D1A), Color(0xFF6E685F), Color(0xFFB4532A)),
    ReaderPalette("sepia", "Sepia", Color(0xFFF3E9D2), Color(0xFF3B2F20), Color(0xFF7A6A55), Color(0xFF9C5A2C)),
    ReaderPalette("gray", "Gray", Color(0xFF4A4A4D), Color(0xFFEAE8E4), Color(0xFFB8B5B0), Color(0xFFF2B27A)),
    ReaderPalette("night", "Night", Color(0xFF1C1C1E), Color(0xFFDAD6CF), Color(0xFF9A968F), Color(0xFFE8875C)),
    ReaderPalette("contrast", "Contrast", Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFFB0B0B0), Color(0xFFFF9A5C)),
    ReaderPalette("sunset", "Sunset", Color(0xFF3B2B26), Color(0xFFF1DDCF), Color(0xFFC2A898), Color(0xFFF0A070)),
    ReaderPalette("grass", "Grass", Color(0xFF2F3328), Color(0xFFDEE4C9), Color(0xFFA9B096), Color(0xFFC5D88A)),
    ReaderPalette("nord", "Nord", Color(0xFF2E3440), Color(0xFFECEFF4), Color(0xFFA3ABBA), Color(0xFF88C0D0)),
    ReaderPalette("solarized", "Solarized", Color(0xFF002B36), Color(0xFFB4C2C2), Color(0xFF6F8A90), Color(0xFFE0A33A)),
)

fun readerPalette(id: String): ReaderPalette? = ReaderPalettes.firstOrNull { it.id == id }

/** The app's colours, changed to the reader palette: the page, the bars and the sheets all follow it. */
fun ColorScheme.withReader(p: ReaderPalette): ColorScheme {
    val dark = p.page.luminance() < 0.4f
    val up = if (dark) Color.White else Color.Black
    fun lift(f: Float) = lerp(p.page, up, f)
    return copy(
        background = p.page, onBackground = p.text,
        surface = p.page, onSurface = p.text, onSurfaceVariant = p.muted,
        surfaceVariant = lift(0.08f),
        surfaceContainerLowest = p.page, surfaceContainerLow = lift(0.03f), surfaceContainer = lift(0.05f),
        surfaceContainerHigh = lift(0.08f), surfaceContainerHighest = lift(0.11f),
        surfaceBright = lift(0.12f), surfaceDim = p.page,
        primary = p.accent, onPrimary = if (p.accent.luminance() > 0.45f) Color.Black else Color.White,
        primaryContainer = lerp(p.page, p.accent, 0.25f), onPrimaryContainer = p.text,
        secondaryContainer = lerp(p.page, p.accent, 0.18f), onSecondaryContainer = p.text,
        tertiary = p.accent,
        outline = lerp(p.page, p.text, 0.35f), outlineVariant = lerp(p.page, p.text, 0.18f),
        inverseSurface = p.text, inverseOnSurface = p.page,
    )
}

/**
 * Wraps the reader in its own colours when a reader theme is picked. The status bar icons turn dark on a
 * light page and light on a dark one.
 */
/** The status bar icon colour a reader theme asks for: dark icons on a light page. Null means the app decides. */
object BarIcons {
    var light by androidx.compose.runtime.mutableStateOf<Boolean?>(null)
}

@Composable
fun ReaderTheme(id: String, content: @Composable () -> Unit) {
    // The same layout every time, with or without a palette. A different shape would reset the screen,
    // and close the sheet you picked the theme in.
    val p = readerPalette(id)
    val base = MaterialTheme.colorScheme
    val scheme = remember(base, p) { if (p == null) base else base.withReader(p) }
    DisposableEffect(p) {
        BarIcons.light = p?.let { it.page.luminance() > 0.5f }
        onDispose { BarIcons.light = null }
    }
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        Surface(Modifier.fillMaxSize(), color = scheme.background) { content() }
    }
}
