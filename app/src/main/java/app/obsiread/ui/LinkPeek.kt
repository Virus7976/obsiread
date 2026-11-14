package app.obsiread.ui

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.obsiread.data.ArticleDoc
import coil3.toBitmap
import coil3.request.allowHardware
import app.obsiread.data.ArticleMeta
import app.obsiread.data.Clipper
import app.obsi.core.obsidian.Template
import app.obsiread.web.WebReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The line under an article's title: who wrote it, where, when, and how long it takes. */
fun bylineOf(m: ArticleMeta): String = listOfNotNull(
    m.author.takeIf { it.isNotBlank() },
    m.siteName.takeIf { it.isNotBlank() && !it.equals(m.author.trim(), ignoreCase = true) },
    Template.formatDate(m.published, "D MMM YYYY"),
    "${m.minutes} min read",
).joinToString("  ·  ")

/**
 * Loads an article's pictures after its text, a few at a time. The page is laid out again each time a
 * batch is in. A picture that fails is left out, so it leaves no empty box.
 */
@Composable
fun rememberPictures(built: ArticleDoc.Built?, imageWidth: Int): Map<Int, Bitmap?> {
    val ctx = LocalContext.current
    var pictures by remember(built) { mutableStateOf<Map<Int, Bitmap?>>(built?.images?.associate { it.first to null }.orEmpty()) }
    LaunchedEffect(built) {
        val list = built?.images ?: return@LaunchedEffect
        if (list.isEmpty()) return@LaunchedEffect
        val loader = coil3.SingletonImageLoader.get(ctx)
        val got = java.util.concurrent.ConcurrentHashMap<Int, Bitmap>()
        val failed = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
        coroutineScope {
            list.forEach { (at, url) ->
                launch(Dispatchers.IO) {
                    val req = coil3.request.ImageRequest.Builder(ctx).data(url).size(imageWidth).allowHardware(false).build()
                    val r = loader.execute(req)
                    val bmp = (r as? coil3.request.SuccessResult)?.image?.let { img -> runCatching { img.toBitmap() }.getOrNull() }
                    if (bmp != null) got[at] = bmp else failed += at
                }
            }
            // Show what has arrived every so often, so one slow picture does not hold the others back.
            launch {
                var shown = 0
                while (true) {
                    delay(500)
                    if (got.size != shown) { shown = got.size; pictures = list.associate { it.first to got[it.first] } }
                    if (shown + failed.size >= list.size) break
                }
            }
        }
        pictures = list.filter { it.first !in failed }.associate { it.first to got[it.first] }
    }
    return pictures
}

private class Peeked(val json: String, val meta: ArticleMeta, val built: ArticleDoc.Built)

/**
 * A link from an article, read and shown in a card that slides up over the article. It looks like the
 * reader, but it is not kept unless you tap Save. A link inside the card opens in the same card, and
 * back steps out again.
 */
@Composable
fun LinkPeek(start: String?, style: ReaderStyle, imageWidth: Int, onClose: () -> Unit) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    // The links you followed inside the card. The last one is shown.
    var trail by remember(start) { mutableStateOf(listOfNotNull(start)) }
    val url = trail.lastOrNull()
    BackHandler(enabled = start != null) { if (trail.size > 1) trail = trail.dropLast(1) else onClose() }

    AnimatedVisibility(visible = start != null, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(interactionSource = null, indication = null) { onClose() })
    }
    AnimatedVisibility(
        visible = start != null,
        enter = slideInVertically(spring(1f, 450f)) { it },
        exit = slideOutVertically(spring(1f, 450f)) { it },
    ) {
        val scheme = MaterialTheme.colorScheme
        Surface(
            Modifier.fillMaxSize().statusBarsPadding().padding(top = 10.dp),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = scheme.background,
        ) {
            if (url == null) return@Surface
            val library by app.library.articles.collectAsState()
            val savedId = Clipper.idFor(url).takeIf { id -> library.any { it.meta.id == id } }
            val result by produceState<Result<Peeked>?>(null, url) {
                value = null
                value = runCatching {
                    val json = WebReader(app).read(url)
                    withContext(Dispatchers.Default) {
                        val c = Clipper.fromDefuddle(json, url)
                        Peeked(json, c.meta, ArticleDoc.build(c.html, c.meta.title, bylineOf(c.meta)))
                    }
                }
            }
            var saving by remember(url) { mutableStateOf(false) }
            val peeked = result?.getOrNull()

            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (trail.size > 1) IconButton(onClick = { trail = trail.dropLast(1) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    else IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") }
                    Text(
                        peeked?.meta?.siteName?.ifBlank { null } ?: runCatching { java.net.URI(url).host.removePrefix("www.") }.getOrDefault(""),
                        Modifier.weight(1f).padding(horizontal = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant,
                    )
                    when {
                        savedId != null -> FilledTonalButton(onClick = { onClose(); nav.openVideo(savedId) }, shapes = ButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Open")
                        }
                        peeked != null -> Button(
                            onClick = {
                                saving = true
                                scope.launch { runCatching { app.addFound(peeked.json, url) }; saving = false }
                            },
                            enabled = !saving, shapes = ButtonDefaults.shapes(),
                        ) { Text(if (saving) "Saving…" else "Save") }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()) {
                    val r = result
                    when {
                        r == null -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            LoadingIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Reading the page…", color = scheme.onSurfaceVariant)
                        }
                        peeked == null -> Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(r.exceptionOrNull()?.message ?: "This page could not be read.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(onClick = { onClose(); nav.openBrowser(url, null) }, shapes = ButtonDefaults.shapes()) {
                                Icon(Icons.Rounded.Language, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Open in the browser")
                            }
                        }
                        else -> {
                            val pictures = rememberPictures(peeked.built, imageWidth)
                            AndroidView(
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(0.dp)),
                                factory = { c -> TranscriptView(c) },
                                update = { tv ->
                                    tv.onSeek = { }
                                    tv.onTapWord = { w ->
                                        val at = peeked.built.doc.words.getOrNull(w)?.charStart ?: -1
                                        peeked.built.styles.firstOrNull { it.kind == "link" && at in it.range }?.let { trail = trail + it.href }
                                        true
                                    }
                                    tv.bind(peeked.built.doc, style.copy(highlighterOn = false), peeked.built.styles, pictures, imageWidth)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
