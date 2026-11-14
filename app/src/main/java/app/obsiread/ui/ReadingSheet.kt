package app.obsiread.ui
import app.obsi.core.R as CoreR

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FormatLineSpacing
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import app.obsiread.R
import app.obsi.core.data.AppSettings

/** The reader's fonts: id and the name shown. */
val ReaderFonts = listOf(
    "serif" to "Newsreader",
    "sans" to "Atkinson",
    "system" to "System",
    "systemserif" to "Noto Serif",
    "mono" to "Mono",
)

/** The typeface for a reader font, at a weight from 100 to 900. Weight needs Android 9 or newer. */
fun readerTypeface(ctx: Context, s: AppSettings): Typeface? {
    val base = when (s.readerFont) {
        "sans" -> ResourcesCompat.getFont(ctx, CoreR.font.atkinson_regular)
        "system" -> Typeface.DEFAULT
        "systemserif" -> Typeface.SERIF
        "mono" -> Typeface.MONOSPACE
        "custom" -> customTypeface(s.customFontPath) ?: ResourcesCompat.getFont(ctx, CoreR.font.newsreader)
        else -> ResourcesCompat.getFont(ctx, CoreR.font.newsreader)
    }
    return if (s.fontWeight != 400 && Build.VERSION.SDK_INT >= 28 && base != null) Typeface.create(base, s.fontWeight, false) else base
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Text("Text", Icons.Rounded.TextFields),
    Layout("Layout", Icons.Rounded.FormatLineSpacing),
    Theme("Theme", Icons.Rounded.Palette),
    Behavior("Behavior", Icons.Rounded.TouchApp),
}

/**
 * How the reader looks and acts, in one sheet. It does not dim the page, so each change shows on the
 * text above it straight away.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReadingSheet(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val s by app.settings.state.collectAsState()
    fun set(f: (AppSettings) -> AppSettings) = app.settings.update(f)
    var tab by rememberSaveable { mutableStateOf(Tab.Text) }
    val sheet = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheet, scrimColor = Color.Transparent,
        containerColor = scheme.surfaceContainerLow,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.LocalOverscrollFactory provides null) {
            // The tabs: an icon and a word each, the one you are on in a pill.
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Tab.entries.forEach { t ->
                    val on = t == tab
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .background(if (on) scheme.secondaryContainer else Color.Transparent)
                            .clickable { tab = t }.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(t.icon, null, Modifier.size(22.dp), tint = if (on) scheme.onSecondaryContainer else scheme.onSurfaceVariant)
                        Text(t.label, style = MaterialTheme.typography.labelMedium, color = if (on) scheme.onSecondaryContainer else scheme.onSurfaceVariant)
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (tab) {
                    Tab.Text -> TextTab(s, ::set)
                    Tab.Layout -> LayoutTab(s, ::set)
                    Tab.Theme -> ThemeTab(s, ::set)
                    Tab.Behavior -> BehaviorTab(s, ::set)
                }
            }
        }
    }
}

@Composable
private fun TextTab(s: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    // The fonts, each shown in itself.
    val fonts = ReaderFonts + (if (s.customFontPath.isNotBlank()) listOf("custom" to s.customFontName.ifBlank { "Mine" }) else emptyList())
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        fonts.forEach { (id, name) ->
            val on = s.readerFont == id
            val family = remember(id, s.customFontPath) { readerTypeface(ctx, s.copy(readerFont = id, fontWeight = 400))?.let { FontFamily(it) } }
            Column(
                Modifier.width(92.dp).clip(RoundedCornerShape(18.dp))
                    .background(scheme.surfaceContainerHighest)
                    .border(BorderStroke(2.dp, if (on) scheme.primary else Color.Transparent), RoundedCornerShape(18.dp))
                    .clickable { set { it.copy(readerFont = id) } }.padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Aa", fontFamily = family, fontSize = 26.sp, color = scheme.onSurface)
                Text(name, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
    Card {
        Stepper("Size", "${s.readerFontSize.toInt()}", s.readerFontSize > 14f, s.readerFontSize < 30f,
            { set { it.copy(readerFontSize = (it.readerFontSize - 1f).coerceAtLeast(14f)) } },
            { set { it.copy(readerFontSize = (it.readerFontSize + 1f).coerceAtMost(30f)) } })
        if (Build.VERSION.SDK_INT >= 28) {
            CardDivider()
            Stepper("Weight", weightName(s.fontWeight), s.fontWeight > 300, s.fontWeight < 700,
                { set { it.copy(fontWeight = (it.fontWeight - 100).coerceAtLeast(300)) } },
                { set { it.copy(fontWeight = (it.fontWeight + 100).coerceAtMost(700)) } })
        }
    }
    Hint("Add your own font in Settings, Reading.")
}

private fun weightName(w: Int) = when (w) { 300 -> "Light"; 400 -> "Regular"; 500 -> "Medium"; 600 -> "Semibold"; else -> "Bold" }

@Composable
private fun LayoutTab(s: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    Card {
        Stepper("Line spacing", "%.1f".format(s.lineHeight), s.lineHeight > 1.21f, s.lineHeight < 2.19f,
            { set { it.copy(lineHeight = ((it.lineHeight * 10 - 1).toInt() / 10f).coerceAtLeast(1.2f)) } },
            { set { it.copy(lineHeight = ((it.lineHeight * 10 + 1.5f).toInt() / 10f).coerceAtMost(2.2f)) } })
        CardDivider()
        Stepper("Paragraph gap", "%.2f".format(s.paragraphSpacing).trimEnd('0').trimEnd('.'), s.paragraphSpacing > 0.26f, s.paragraphSpacing < 1.99f,
            { set { it.copy(paragraphSpacing = (it.paragraphSpacing - 0.25f).coerceAtLeast(0.25f)) } },
            { set { it.copy(paragraphSpacing = (it.paragraphSpacing + 0.25f).coerceAtMost(2f)) } })
        CardDivider()
        Stepper("Letter spacing", "%.2f".format(s.letterSpacing), s.letterSpacing > 0.001f, s.letterSpacing < 0.099f,
            { set { it.copy(letterSpacing = ((it.letterSpacing * 100 - 1).toInt().coerceAtLeast(0)) / 100f) } },
            { set { it.copy(letterSpacing = ((it.letterSpacing * 100 + 1.5f).toInt().coerceAtMost(10)) / 100f) } })
    }
    Card {
        Stepper("Side margins", "${s.sideMargin}", s.sideMargin > 8, s.sideMargin < 48,
            { set { it.copy(sideMargin = (it.sideMargin - 4).coerceAtLeast(8)) } },
            { set { it.copy(sideMargin = (it.sideMargin + 4).coerceAtMost(48)) } })
        CardDivider()
        Stepper("Line length", "${s.maxWidth.toInt()}", s.maxWidth > 24.5f, s.maxWidth < 59.5f,
            { set { it.copy(maxWidth = (it.maxWidth - 2f).coerceAtLeast(24f)) } },
            { set { it.copy(maxWidth = (it.maxWidth + 2f).coerceAtMost(60f)) } })
    }
    Card {
        Toggle("Justify", "Both edges of the text are straight.", s.justify) { v -> set { it.copy(justify = v) } }
        CardDivider()
        Toggle("Hyphenation", "Long words break at the end of a line.", s.hyphenate) { v -> set { it.copy(hyphenate = v) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeTab(s: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // "App" follows the app's own theme and light or dark mode. The others are the reader's own colours.
    val cards = listOf<Pair<String, ReaderPalette?>>("app" to null) + ReaderPalettes.map { it.id to it }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
    val cardW = (maxWidth - 20.dp) / 3
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 3) {
        cards.forEach { (id, p) ->
            val on = s.readerTheme == id
            val page = p?.page ?: scheme.surfaceContainerHighest
            val ink = p?.text ?: scheme.onSurface
            Column(
                Modifier.width(cardW).aspectRatio(1.35f).clip(RoundedCornerShape(18.dp)).background(page)
                    .border(BorderStroke(if (on) 2.5.dp else 1.dp, if (on) scheme.primary else scheme.outlineVariant), RoundedCornerShape(18.dp))
                    .clickable { set { it.copy(readerTheme = id) } },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text("Aa", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = ink)
                Text(p?.name ?: "App", style = MaterialTheme.typography.labelMedium, color = p?.muted ?: scheme.onSurfaceVariant)
            }
        }
    }
    }
    if (s.readerTheme == "app") {
        Text("App mode", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(scheme.surfaceContainerHighest).padding(4.dp)) {
            listOf("auto" to "Auto", "light" to "Light", "dark" to "Dark", "oled" to "Black").forEach { (id, label) ->
                val on = s.appearance == id
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(if (on) scheme.primary else Color.Transparent)
                        .clickable { set { it.copy(appearance = id) } }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) scheme.onPrimary else scheme.onSurface) }
            }
        }
    }
}

@Composable
private fun BehaviorTab(s: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    Card {
        Toggle("Tap to show the bars", "A tap on the page shows or hides them.", s.tapForBars) { v -> set { it.copy(tapForBars = v) } }
        CardDivider()
        Toggle("Hide the bars as you read", "They go when you scroll down.", s.hideBarsOnScroll) { v -> set { it.copy(hideBarsOnScroll = v) } }
        CardDivider()
        Toggle("Full screen", "The status bar goes with the bars.", s.fullScreenReading) { v -> set { it.copy(fullScreenReading = v) } }
        CardDivider()
        Toggle("Show progress", "A small % in the corner while the bars are hidden.", s.showProgressHidden) { v -> set { it.copy(showProgressHidden = v) } }
    }
    Card {
        Toggle("Open links in the app", "Off opens your browser.", s.linksInApp) { v -> set { it.copy(linksInApp = v) } }
        CardDivider()
        Toggle("Keep the screen on", null, s.keepScreenOn) { v -> set { it.copy(keepScreenOn = v) } }
        CardDivider()
        Toggle("Highlighter", "Hold a word, then drag.", s.highlighterEnabled) { v -> set { it.copy(highlighterEnabled = v) } }
    }
    Card {
        Stepper("Listen speed", "%.2f".format(s.listenRate).trimEnd('0').trimEnd('.') + "x", s.listenRate > 0.51f, s.listenRate < 2.49f,
            { set { it.copy(listenRate = (it.listenRate - 0.25f).coerceAtLeast(0.5f)) } },
            { set { it.copy(listenRate = (it.listenRate + 0.25f).coerceAtMost(2.5f)) } })
    }
}

// ---------- parts ----------

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
}

@Composable
private fun CardDivider() = HorizontalDivider(Modifier.padding(start = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

@Composable
private fun Hint(text: String) =
    Text(text, Modifier.fillMaxWidth().padding(horizontal = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

@Composable
private fun Stepper(title: String, value: String, canLess: Boolean, canMore: Boolean, onLess: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, Modifier.padding(end = 12.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalIconButton(onClick = onLess, enabled = canLess) { Icon(Icons.Rounded.Remove, "Less $title") }
        Spacer(Modifier.width(6.dp))
        FilledTonalIconButton(onClick = onMore, enabled = canMore) { Icon(Icons.Rounded.Add, "More $title") }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.heightIn(min = 56.dp).padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
