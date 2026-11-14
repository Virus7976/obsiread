package app.obsiread.ui

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.obsiread.web.Defuddle
import app.obsiread.web.WebReader
import kotlinx.coroutines.launch

/**
 * A plain browser inside the app. It is for pages the app cannot read by itself: a site that wants
 * you to sign in, or shows a check first. You do that here, like in any browser, then tap
 * Read this page. Sign-ins stay, so next time the page reads by itself.
 */
@Composable
fun BrowserScreen(startUrl: String, note: String?, onBack: () -> Unit, onRead: (String) -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var web by remember { mutableStateOf<WebView?>(null) }
    var url by remember { mutableStateOf(startUrl) }
    var title by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var canBack by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }

    BackHandler(enabled = canBack) { web?.goBack() }
    LaunchedEffect(note) { if (!note.isNullOrBlank()) snackbar.showSnackbar(note) }
    DisposableEffect(Unit) { onDispose { web?.destroy() } }

    fun read() {
        val v = web ?: return
        reading = true
        scope.launch {
            runCatching {
                val found = Defuddle.run(v)
                app.addFound(found, v.url ?: url)
            }.onSuccess { id -> onRead(id) }
                .onFailure { snackbar.showSnackbar(it.message ?: "Could not read this page.") }
            reading = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding()))
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, "Close the browser") }
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(title.ifBlank { "Loading…" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(
                        runCatching { java.net.URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault(url),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (canBack) IconButton(onClick = { web?.goBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back a page") }
                IconButton(onClick = { web?.reload() }) { Icon(Icons.Rounded.Refresh, "Load again") }
            }
            if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            else Spacer(Modifier.height(4.dp))
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { c ->
                    WebView(c).also { v ->
                        WebReader.setUp(v)
                        v.webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, u: String?, favicon: Bitmap?) {
                                u?.let { url = it }
                                canBack = view.canGoBack()
                            }
                            override fun onPageFinished(view: WebView, u: String?) {
                                u?.let { url = it }
                                title = view.title.orEmpty()
                                canBack = view.canGoBack()
                            }
                        }
                        v.webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView, p: Int) { progress = p }
                            override fun onReceivedTitle(view: WebView, t: String?) { title = t.orEmpty() }
                        }
                        v.loadUrl(startUrl)
                        web = v
                    }
                },
            )
        }
        Surface(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
            shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 8.dp,
        ) {
            Button(onClick = { read() }, enabled = !reading, shapes = ButtonDefaults.shapes(), modifier = Modifier.padding(6.dp)) {
                if (reading) LoadingIndicator(Modifier.size(20.dp)) else Icon(Icons.Rounded.AutoStories, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (reading) "Reading…" else "Read this page")
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp))
    }
}
