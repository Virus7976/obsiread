package app.obsiread.ui

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import app.obsiread.ObsiReadApp
import app.obsiread.data.Clipper
import kotlinx.coroutines.launch

val LocalApp = staticCompositionLocalOf<ObsiReadApp> { error("ObsiReadApp not provided") }

/** Ways to move around that any screen can use. */
class Nav(
    val openVideo: (String) -> Unit,
    /** Opens a page in the app's own browser. [note] says why, at the bottom, once. */
    val openBrowser: (url: String, note: String?) -> Unit = { _, _ -> },
    val openReview: () -> Unit = {},
    val openHighlights: () -> Unit = {},
)

val LocalNav = staticCompositionLocalOf<Nav> { error("Nav not provided") }

sealed interface Screen {
    data object Home : Screen
    data class Reader(val id: String) : Screen
    data object Settings : Screen
    data object Review : Screen
    data object Highlights : Screen
    data class Template(val id: String) : Screen
    data class Browser(val url: String, val note: String? = null) : Screen
}

@Composable
fun AppRoot(app: ObsiReadApp, incomingLink: String?, onLinkConsumed: () -> Unit) {
    val settings by app.settings.state.collectAsState()
    MarginaliaTheme(settings) {
        CompositionLocalProvider(LocalApp provides app) {
            val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
            // Home stays underneath. A second Back in the same frame must not take it away too.
            fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
            BackHandler(enabled = stack.size > 1) { pop() }
            // Each screen keeps its place while another one is on top: open tab, scroll, search box.
            val saved = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            fun toast(text: String) = android.widget.Toast.makeText(ctx, text, android.widget.Toast.LENGTH_SHORT).show()

            // The reader on top is replaced by the next video, so Back still goes home.
            fun pushReader(id: String) {
                val top = stack.last()
                if (top is Screen.Reader) { if (top.id != id) stack[stack.lastIndex] = Screen.Reader(id) }
                else stack.add(Screen.Reader(id))
            }
            val nav = remember {
                Nav(
                    openVideo = { id -> if (app.library.get(id) != null) pushReader(id) },
                    openBrowser = { url, note -> stack.add(Screen.Browser(url, note)) },
                    openReview = { if (stack.last() != Screen.Review) stack.add(Screen.Review) },
                    openHighlights = { if (stack.last() != Screen.Highlights) stack.add(Screen.Highlights) },
                )
            }

            // An article another Obsi app asked to open.
            val openItem by app.openItem.collectAsState()
            androidx.compose.runtime.LaunchedEffect(openItem) {
                val (id, at) = openItem ?: return@LaunchedEffect
                app.openItem.value = null
                if (at >= 0) app.pendingSeek.value = id to at
                nav.openVideo(id)
            }

            // A page shared from a browser. It is read in the background and opens when it is ready,
            // whatever screen is open. If it fails, the reason shows.
            var addLink by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
            androidx.compose.runtime.LaunchedEffect(incomingLink) {
                val link = incomingLink ?: return@LaunchedEffect
                onLinkConsumed()
                scope.launch {
                    runCatching { app.addFromUrl(link) }
                        .onSuccess { id -> nav.openVideo(id) }
                        .onFailure { e -> nav.openBrowser(link, (e.message ?: "Could not read it by itself.") + " Sign in or wait for the page, then tap Read this page.") }
                }
            }
            CompositionLocalProvider(LocalNav provides nav) {
            // The daily reminder opens Review.
            val wantReview by app.openReview.collectAsState()
            androidx.compose.runtime.LaunchedEffect(wantReview) {
                if (wantReview) { app.openReview.value = false; nav.openReview() }
            }
            val spring = LocalSpring.current

            // Self-healing: when a new release is found (for example a playback fix), say so right away.
            val update by app.updates.state.collectAsState()
            var later by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
            (update as? app.obsiread.update.UpdateState.Available)?.info?.let { info ->
                if (later != info.version) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { later = info.version },
                        title = { androidx.compose.material3.Text("Update ready: ${info.version}") },
                        text = {
                            androidx.compose.material3.Text(
                                info.notes.trim().ifBlank { "A new version is ready." }.take(500),
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = { later = info.version; app.updates.installFlow(info) }) {
                                androidx.compose.material3.Text("Install")
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { later = info.version }) {
                                androidx.compose.material3.Text("Later")
                            }
                        },
                    )
                }
            }

            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AnimatedContent(
                    targetState = stack.last(),
                    // Like an iPhone: a new screen slides in over the old one from the right edge, and the old one
                    // drifts a little to the left under it. Back is the same in reverse. Nothing scales and nothing
                    // fades out from under you, so the page you are looking at stays solid the whole way.
                    transitionSpec = {
                        val deeper = depth(targetState) >= depth(initialState)
                        val move = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(380, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.9f, 0.1f, 1f))
                        if (deeper) {
                            (slideInHorizontally(move) { it } togetherWith slideOutHorizontally(move) { -it / 4 })
                                .apply { targetContentZIndex = 1f }
                        } else {
                            (slideInHorizontally(move) { -it / 4 } togetherWith slideOutHorizontally(move) { it })
                                .apply { targetContentZIndex = -1f }
                        }
                    },
                    label = "screens",
                ) { screen ->
                    // Each screen is solid, so the one underneath never shows through while they slide.
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    saved.SaveableStateProvider(screen.toString()) {
                    when (screen) {
                        Screen.Home -> HomeScreen(
                            incoming = addLink,
                            onConsumed = { addLink = null },
                            onOpen = nav.openVideo,
                            onSettings = { stack.add(Screen.Settings) },
                        )
                        // The reader also goes back by itself when its video is removed, so it checks that it is on top.
                        is Screen.Reader -> ReaderScreen(screen.id, onBack = { if (stack.last() == screen) pop() })
                        Screen.Settings -> SettingsScreen(
                            onBack = { pop() },
                            onEditTemplate = { stack.add(Screen.Template(it)) },
                        )
                        is Screen.Template -> TemplateEditorScreen(screen.id, onBack = { pop() })
                        // When the page is read, the reader takes the browser's place, so Back goes home.
                        is Screen.Browser -> BrowserScreen(screen.url, screen.note, onBack = { pop() }, onRead = { id ->
                            if (stack.last() == screen) stack[stack.lastIndex] = Screen.Reader(id)
                        })
                        Screen.Review -> ReviewScreen(onBack = { pop() })
                        Screen.Highlights -> HighlightsScreen(onBack = { pop() })
                    }
                    }
                    }
                }
            }
            }
        }
    }
}

private fun depth(s: Screen) = when (s) {
    Screen.Home -> 0
    is Screen.Reader -> 1
    Screen.Settings -> 1
    Screen.Review -> 1
    Screen.Highlights -> 1
    is Screen.Template -> 2
    is Screen.Browser -> 1
}
