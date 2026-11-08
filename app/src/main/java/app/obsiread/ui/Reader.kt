package app.obsiread.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.focus.focusRequester
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.obsiread.R
import app.obsiread.data.Highlight
import app.obsiread.data.HighlightColors
import app.obsiread.data.Highlights
import app.obsiread.data.Article
import app.obsiread.data.TranscriptDoc
import app.obsiread.data.ArticleDoc
import app.obsiread.data.Markdown
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Refresh
import kotlinx.coroutines.coroutineScope
import coil3.toBitmap
import coil3.request.allowHardware
import app.obsiread.data.Place
import app.obsiread.obsidian.Template
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReaderScreen(videoId: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.settings.state.collectAsState()
    val videos by app.library.articles.collectAsState()
    val video = videos.firstOrNull { it.meta.id == videoId }
    if (video == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val snackbar = remember { SnackbarHostState() }

    var showSave by remember { mutableStateOf(false) }
    var showHighlights by remember { mutableStateOf(false) }
    var showContents by remember { mutableStateOf(false) }
    var peekUrl by remember { mutableStateOf<String?>(null) }
    var showActions by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var headerHidden by remember { mutableStateOf(false) }
    var reloads by remember(videoId) { mutableIntStateOf(0) }
    // Full screen reading: no top row, bottom bar, status bar or navigation bar.
    var immersive by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = immersive) { immersive = false }
    val activity = remember(ctx) {
        var c: android.content.Context = ctx
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        c as? android.app.Activity
    }
    DisposableEffect(immersive) {
        val window = activity?.window
        val controller = window?.let { androidx.core.view.WindowInsetsControllerCompat(it, it.decorView) }
        if (immersive) {
            controller?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
    }
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // The line under the title: who wrote it, where, when, and how long it takes.
    val byline = remember(video.meta) { bylineOf(video.meta) }
    // The article, laid out as one piece of text, built off the main thread.
    val built by produceState<ArticleDoc.Built?>(null, videoId, reloads, byline) {
        value = withContext(Dispatchers.Default) {
            app.library.loadBody(videoId)?.let { ArticleDoc.build(it, video.meta.title, byline, video.meta.image) }
        }
    }
    val doc = built?.doc
    val colors = remember(doc, video.highlights) { doc?.let { Highlights.colorByWord(it.words, video.highlights) } }
    val noted = remember(doc, video.highlights) { doc?.let { Highlights.notedRuns(it.words, video.highlights) } }
    val runs = remember(doc, video.highlights) { doc?.let { Highlights.runs(it.words, video.highlights) } }

    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val readerTypeface = remember(settings.readerFont, settings.customFontPath) {
        when (settings.readerFont) {
            "sans" -> ResourcesCompat.getFont(ctx, R.font.atkinson_regular)
            "system" -> Typeface.DEFAULT
            "custom" -> customTypeface(settings.customFontPath) ?: ResourcesCompat.getFont(ctx, R.font.newsreader)
            else -> ResourcesCompat.getFont(ctx, R.font.newsreader)
        }
    }
    val style = ReaderStyle(
        textColor = scheme.onSurface.toArgb(),
        mutedColor = scheme.onSurfaceVariant.toArgb(),
        accent = scheme.primary.toArgb(),
        sizeSp = settings.readerFontSize,
        lineMult = settings.lineHeight,
        typeface = readerTypeface,
        highlightAlpha = if (scheme.background.luminanceApprox() < 0.5f) 0.30f else 0.55f,
        holdSelects = settings.holdSelects,
        maxWidthPx = with(density) { (settings.maxWidth * settings.readerFontSize).dp.roundToPx() },
        highlighterOn = settings.highlighterEnabled,
    )
    // A picture fills the text column, and no more.
    val screenW = ctx.resources.displayMetrics.widthPixels
    val imageWidth = minOf(style.maxWidthPx, screenW - with(density) { 36.dp.roundToPx() })

    val pictures = rememberPictures(built, imageWidth)

    var tview by remember { mutableStateOf<TranscriptView?>(null) }
    // Find in this article.
    var searching by remember(videoId) { mutableStateOf(false) }
    var query by remember(videoId) { mutableStateOf("") }
    var matchCount by remember(videoId) { mutableIntStateOf(0) }
    var matchAt by remember(videoId) { mutableIntStateOf(-1) }
    LaunchedEffect(searching) {
        tview?.searching = searching
        if (!searching) { tview?.clearSearch(); query = ""; matchCount = 0; matchAt = -1 }
    }
    var editingId by remember(videoId) { mutableStateOf<String?>(null) }
    var noteFocused by remember(videoId) { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = searching) { searching = false }
    androidx.activity.compose.BackHandler(enabled = editingId != null) { editingId = null }
    LaunchedEffect(editingId) { if (editingId == null) { noteFocused = false; tview?.stopResize() } }
    // The note bar takes room from the text. Keep the marked words in view above it.
    LaunchedEffect(editingId) {
        val id = editingId ?: return@LaunchedEffect
        val w = video.highlights.firstOrNull { it.id == id }?.w0 ?: return@LaunchedEffect
        repeat(3) {
            delay(380)
            val tv = tview ?: return@LaunchedEffect
            if (w >= 0 && !tv.isComfortable(w)) tv.scrollToWord(w)
        }
    }

    // Where you are. It is saved a moment after you stop scrolling, so the next open starts here.
    var progress by remember(videoId) { mutableStateOf(0f) }
    var atWord by remember(videoId) { mutableIntStateOf(-1) }
    var lookWord by remember(videoId) { mutableIntStateOf(-1) }
    LaunchedEffect(atWord) {
        if (atWord < 0) return@LaunchedEffect
        delay(700)
        app.library.edit(videoId) { it.copy(positionSec = atWord.toDouble(), playedAt = System.currentTimeMillis()) }
    }
    // The headings, each with the first word under it, for the side panel.
    val heads = remember(built) {
        val d = built?.doc ?: return@remember emptyList<Triple<String, Int, Int>>()
        built?.styles.orEmpty().filter { it.kind == "heading" }.map { h ->
            Triple(d.text.substring(h.range.first, h.range.last + 1), h.level, d.wordAtChar(h.range.first))
        }.filter { it.third >= 0 }
    }
    val hereHead = heads.indexOfLast { it.third <= lookWord }
    val wordsLeft = ((1f - progress) * video.meta.words).toInt()
    val minutesLeft = (wordsLeft + 229) / 230

    fun openLink(url: String) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    fun share() {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, video.meta.title)
            .putExtra(Intent.EXTRA_TEXT, video.meta.url)
        runCatching { ctx.startActivity(Intent.createChooser(send, null)) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(if (immersive) 28.dp else WindowInsets.statusBars.asPaddingValues().calculateTopPadding()))
            // The top row: back, where it is from, find, and the menu. It slides away while you read down.
            AnimatedVisibility(
                visible = !immersive && (!headerHidden || searching || editingId != null),
                enter = expandVertically(spring(1f, 500f)) + fadeIn(),
                exit = shrinkVertically(spring(1f, 500f)) + fadeOut(),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    Text(
                        video.meta.siteName, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant,
                    )
                    IconButton(onClick = { searching = !searching }) { Icon(Icons.Rounded.Search, "Find in this article") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(24.dp)) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MenuTile("Find", Icons.Rounded.Search, on = searching) { menu = false; searching = true }
                                MenuTile("Contents", Icons.Rounded.FormatListBulleted, on = false, enabled = heads.isNotEmpty()) { menu = false; showContents = true }
                                MenuTile("Text only", Icons.Rounded.Fullscreen, on = false) { menu = false; immersive = true }
                            }
                            androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 4.dp), color = scheme.outlineVariant.copy(alpha = 0.4f))
                            DropdownMenuItem(
                                text = { Text("Mark as done") },
                                leadingIcon = { Icon(Icons.Rounded.TaskAlt, null) },
                                trailingIcon = { if (video.done) Icon(Icons.Rounded.Check, "On", tint = scheme.primary) },
                                onClick = { app.setDone(videoId, !video.done) },
                            )
                            DropdownMenuItem(
                                text = { Text("Open the web page") },
                                leadingIcon = { Icon(Icons.Rounded.OpenInNew, null) },
                                onClick = { menu = false; openLink(video.meta.url) },
                            )
                            DropdownMenuItem(
                                text = { Text("Share the link") },
                                leadingIcon = { Icon(Icons.Rounded.Share, null) },
                                onClick = { menu = false; share() },
                            )
                            DropdownMenuItem(
                                text = { Text("Read the page again") },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                                onClick = {
                                    menu = false
                                    scope.launch {
                                        runCatching { app.refresh(videoId) }
                                            .onSuccess { reloads++; snackbar.showSnackbar("Read the page again.") }
                                            .onFailure { snackbar.showSnackbar(it.message ?: "Could not read the page.") }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Tags and more") },
                                leadingIcon = { Icon(Icons.Rounded.Sell, null) },
                                onClick = { menu = false; showActions = true },
                            )
                        }
                    }
                }
            }

            if (searching && !immersive) {
                FindBar(
                    query = query,
                    count = matchCount,
                    at = matchAt,
                    onQuery = {
                        query = it
                        matchCount = tview?.search(it) ?: 0
                        matchAt = if (matchCount > 0) 0 else -1
                    },
                    onStep = { d -> tview?.goToMatch(matchAt + d)?.let { matchAt = it } },
                    onClose = { searching = false },
                )
            }

            // The long article is laid out only after the screen has slid in. Doing both at once made it stutter.
            var settled by remember(videoId) { mutableStateOf(false) }
            LaunchedEffect(videoId) { delay(380); settled = true }
            val fade by androidx.compose.animation.core.animateFloatAsState(if (settled) 1f else 0f, androidx.compose.animation.core.tween(220), label = "pageFade")
            Box(Modifier.weight(1f).fillMaxWidth().then(if (immersive || editingId != null) Modifier else Modifier.navigationBarsPadding()).clipToBounds().graphicsLayer { alpha = fade }) {
                val d = doc
                val b = built
                if (d != null && b != null && settled) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { c -> TranscriptView(c).also { tview = it } },
                        update = { tv ->
                            tv.onSeek = { }
                            tv.onUserScroll = { dy, atTop ->
                                if (atTop) headerHidden = false
                                else if (dy > 6) headerHidden = true else if (dy < -6) headerHidden = false
                            }
                            tv.onScrollDelta = {
                                progress = tv.progress()
                                tv.firstVisibleWord().let { w -> if (w >= 0) atWord = w }
                                tv.wordAtScreen(0.33f).let { w -> if (w >= 0) lookWord = w }
                            }
                            tv.currentColor = settings.highlightColor
                            tv.onTapWord = { w ->
                                val h = Highlights.at(video.highlights, w)
                                val at = d.words.getOrNull(w)?.charStart ?: -1
                                val link = b.styles.firstOrNull { it.kind == "link" && at in it.range }
                                when {
                                    // Tapping a highlight opens it with handles on its ends, ready to drag.
                                    h != null -> { editingId = h.id; tv.startResize(h.id, h.w0..h.w1); true }
                                    // A tap beside it puts it away.
                                    editingId != null -> { editingId = null; true }
                                    link != null -> { peekUrl = link.href; true }
                                    else -> true
                                }
                            }
                            tv.onPaint = { a, bb, color ->
                                val next = Highlights.apply(video.highlights, d.words, d::plainText, a, bb, color)
                                app.library.edit(videoId) { it.copy(highlights = next) }
                                app.settings.update { s -> s.copy(highlightColor = color) }
                                // Open the note bar on the highlight that was just made, with handles on it.
                                val made = next.firstOrNull { it.w0 == minOf(a, bb) && it.w1 == maxOf(a, bb) }
                                editingId = made?.id
                                if (made != null) tv.post { tv.startResize(made.id, made.w0..made.w1) }
                            }
                            tv.onResize = { id, a, bb ->
                                app.library.edit(videoId) { it.copy(highlights = Highlights.resize(it.highlights, d.words, d::plainText, id, a, bb)) }
                            }
                            tv.onErase = { a, bb ->
                                app.library.edit(videoId) { it.copy(highlights = Highlights.erase(it.highlights, d.words, d::plainText, a, bb)) }
                            }
                            tv.bind(d, style, b.styles, pictures, imageWidth)
                            colors?.let { tv.applyHighlights(it, runs.orEmpty()) }
                            noted?.let { tv.applyNotes(it) }
                        },
                    )
                } else if (settled && built == null && app.library.loadBody(videoId) == null) {
                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("The text of this article is missing.", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            scope.launch {
                                runCatching { app.refresh(videoId) }.onSuccess { reloads++ }.onFailure { snackbar.showSnackbar(it.message ?: "Could not read the page.") }
                            }
                        }, shapes = ButtonDefaults.shapes()) { Text("Read the page again") }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
            }
            // Note bar for the highlight just made, or the one that was tapped.
            val editing = video.highlights.firstOrNull { it.id == editingId }
            val noteVault = settings.linkedVault(settings.vault) ?: settings.vaultFolders.keys.firstOrNull()
            val tagPool = remember(editingId, noteVault) {
                app.library.articles.value.flatMap { v -> v.highlights.flatMap { it.tags } }
                    .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key } +
                    (noteVault?.let { app.vaults.indexes.value[it]?.tags?.keys }.orEmpty())
            }
            AnimatedVisibility(
                visible = editing != null,
                enter = expandVertically(spring(1f, 500f)) + fadeIn(),
                exit = shrinkVertically(spring(1f, 500f)) + fadeOut(),
            ) {
                var last by remember { mutableStateOf(editing) }
                if (editing != null) last = editing
                last?.let { h ->
                    HighlightNoteBar(
                        h = h,
                        onNote = { text -> app.library.edit(videoId) { v -> v.copy(highlights = v.highlights.map { if (it.id == h.id) it.copy(note = text) else it }) } },
                        tags = h.tags,
                        onTags = { t -> app.library.edit(videoId) { v -> v.copy(highlights = v.highlights.map { if (it.id == h.id) it.copy(tags = t) else it }) } },
                        vault = noteVault,
                        suggestTags = tagPool,
                        onTyping = { noteFocused = it },
                        onColor = { c ->
                            app.library.edit(videoId) { v -> v.copy(highlights = v.highlights.map { if (it.id == h.id) it.copy(color = c) else it }) }
                            app.settings.update { it.copy(highlightColor = c) }
                        },
                        onDelete = {
                            editingId = null
                            app.library.edit(videoId) { v -> v.copy(highlights = v.highlights.filterNot { it.id == h.id }) }
                            scope.launch {
                                val r = snackbar.showSnackbar("Highlight deleted", actionLabel = "Undo", duration = androidx.compose.material3.SnackbarDuration.Short)
                                if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) app.library.edit(videoId) { v -> v.copy(highlights = v.highlights + h) }
                            }
                        },
                        onPlay = { if (h.w0 >= 0) tview?.scrollToWord(h.w0) },
                        onClose = { editingId = null },
                    )
                }
            }
        }

        // Bottom bar: highlights, how much is left, and Clip. It slides away while you read down.
        AnimatedVisibility(
            visible = !headerHidden && !immersive && editingId == null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = androidx.compose.animation.slideInVertically(spring(1f, 500f)) { it },
            exit = androidx.compose.animation.slideOutVertically(spring(1f, 500f)) { it },
        ) {
            Surface(modifier = Modifier.fillMaxWidth(), color = scheme.surfaceContainer) {
                Column(Modifier.navigationBarsPadding()) {
                    // How far through you are, as a thin line along the top of the bar.
                    Box(Modifier.fillMaxWidth().height(2.dp).background(scheme.outlineVariant.copy(alpha = 0.35f))) {
                        Box(Modifier.fillMaxWidth(progress).height(2.dp).background(scheme.primary))
                    }
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showHighlights = true }) {
                            Icon(Icons.Rounded.BorderColor, "Highlights", Modifier.size(20.dp), tint = scheme.tertiary)
                            if (video.highlights.isNotEmpty()) {
                                Spacer(Modifier.width(6.dp))
                                Text("${video.highlights.size}", style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
                            }
                        }
                        val timeLeft = if (progress > 0.985f) "Finished" else if (progress < 0.01f) "${video.meta.minutes} min read" else "$minutesLeft min left"
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (heads.size >= 2) TextButton(onClick = { showContents = true }) {
                                Icon(Icons.Rounded.FormatListBulleted, "Contents", Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                Text(timeLeft, maxLines = 1, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                            } else Text(timeLeft, maxLines = 1, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                        }
                        // The loud button means one thing: you marked something that is not in Obsidian yet.
                        val clipped = video.clippedAt > 0
                        val unsaved = video.highlights.count { it.createdAt > video.clippedAt }
                        val label = when { !clipped -> "Clip"; unsaved > 0 -> "Clip $unsaved new"; else -> "Clipped" }
                        val icon = if (clipped && unsaved == 0) Icons.Rounded.Check else Icons.Rounded.Upload
                        if (unsaved > 0) Button(onClick = { showSave = true }, shapes = ButtonDefaults.shapes()) {
                            Icon(icon, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(label, maxLines = 1)
                        } else FilledTonalButton(onClick = { showSave = true }, shapes = ButtonDefaults.shapes()) {
                            Icon(icon, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(label, maxLines = 1)
                        }
                    }
                }
            }
        }

        // The headings in the phone's own pull-up sheet. The one you are in is marked, and a tap jumps there.
        if (showContents) {
            val sheet = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = false)
            SmoothSheet(onDismissRequest = { showContents = false }, sheetState = sheet) {
                val list = rememberLazyListState(initialFirstVisibleItemIndex = (hereHead - 2).coerceAtLeast(0))
                Text("Contents", Modifier.padding(start = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.titleLarge)
                LazyColumn(state = list, contentPadding = PaddingValues(bottom = 24.dp)) {
                    itemsIndexed(heads) { i, h ->
                        val here = i == hereHead
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (here) scheme.secondaryContainer else Color.Transparent)
                                .clickable {
                                    showContents = false
                                    tview?.scrollToWord(h.third)
                                }
                                .heightIn(min = 48.dp)
                                .padding(start = (12 + (h.second - 2).coerceAtLeast(0) * 20).dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                h.first, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = if (h.second <= 2) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                                color = if (here) scheme.onSecondaryContainer else if (h.second <= 2) scheme.onSurface else scheme.onSurfaceVariant,
                            )
                            if (here) Text("Here", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium, color = scheme.onSecondaryContainer)
                        }
                    }
                }
            }
        }

        LinkPeek(peekUrl, style, imageWidth, onClose = { peekUrl = null })

        // The only control left in full screen. It is faint, so it does not pull the eye from the text.
        AnimatedVisibility(
            visible = immersive,
            modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.displayCutout).padding(top = 6.dp, end = 6.dp),
            enter = fadeIn(), exit = fadeOut(),
        ) {
            IconButton(onClick = { immersive = false }) {
                Icon(Icons.Rounded.FullscreenExit, "Leave full screen", tint = scheme.onSurface.copy(alpha = 0.35f))
            }
        }

        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 72.dp),
        )
    }

    // A highlight picked in search: show the place it came from.
    val pending by app.pendingSeek.collectAsState()
    LaunchedEffect(pending, videoId, doc, tview) {
        val p = pending ?: return@LaunchedEffect
        if (p.first != videoId) return@LaunchedEffect
        val tv = tview ?: return@LaunchedEffect
        if (doc == null) return@LaunchedEffect
        settleText()
        tv.scrollToWord(p.second.toInt())
        app.pendingSeek.value = null
    }
    // Coming back to an article: the page opens where you were, not at the top.
    var placed by remember(videoId) { mutableStateOf(false) }
    LaunchedEffect(videoId, doc, tview) {
        doc ?: return@LaunchedEffect
        val tv = tview ?: return@LaunchedEffect
        if (placed) return@LaunchedEffect
        placed = true
        if (app.pendingSeek.value?.first == videoId) return@LaunchedEffect
        settleText()
        val at = video.positionSec.toInt()
        if (at > 0) tv.jumpToWord(at)
        progress = tv.progress()
    }

    if (showSave) {
        SaveSheet(
            video = video,
            lines = remember(videoId, reloads) { app.library.loadBody(videoId)?.let { Markdown.from(it) }.orEmpty() },
            onDismiss = { showSave = false },
            onDone = { message, ok ->
                showSave = false
                if (ok) app.library.edit(videoId) { it.copy(clippedAt = System.currentTimeMillis()) }
                scope.launch { snackbar.showSnackbar(message) }
            },
        )
    }
    if (showHighlights) {
        HighlightsSheet(
            video = video,
            onDismiss = { showHighlights = false },
            onJump = { h ->
                showHighlights = false
                if (h.w0 >= 0) tview?.scrollToWord(h.w0)
            },
        )
    }
    if (showActions) VideoActionsSheet(videoId, onDismiss = { showActions = false })
}

/** A small button that floats over the text. It casts a shadow, so it never reads as part of the page. */
@Composable
private fun FloatingPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, shadowElevation = 6.dp) {
        Row(Modifier.padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

// ---------- highlights sheet ----------

@Composable
private fun HighlightsSheet(video: Article, onDismiss: () -> Unit, onJump: (Highlight) -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbar = remember { SnackbarHostState() }
    var find by remember { mutableStateOf("") }
    val ordered = remember(video.highlights, find) { video.highlights.sortedBy { it.startSec }.filter { matchesQuery(find, it.text, it.note, it.tags.joinToString(" ")) } }
    fun change(h: Highlight, block: (Highlight) -> Highlight) =
        app.library.edit(video.meta.id) { v -> v.copy(highlights = v.highlights.map { if (it.id == h.id) block(it) else it }) }
    SmoothSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Box {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(
                    if (video.highlights.isEmpty()) "No highlights yet" else "${video.highlights.size} highlights",
                    Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
                if (video.highlights.isEmpty()) {
                    Text(
                        "Hold a word in the transcript. Drag the handles to take more, then pick a colour.",
                        Modifier.padding(horizontal = 24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // A long list is hard to scan. The box finds a word in what you highlighted or wrote.
                if (video.highlights.size >= 5) SearchField(find, { find = it }, "Search these highlights")
                if (find.isNotBlank() && ordered.isEmpty()) Text(
                    "No highlight matches \"$find\".", Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    itemsIndexed(ordered, key = { _, h -> h.id }) { _, h ->
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                                .clip(MaterialTheme.shapes.large)
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onJump(h) }) { Text(Place.label(h.startSec, video.meta.words).ifBlank { "Go to it" }) }
                                Spacer(Modifier.weight(1f))
                                ColorDots(h.color) { i -> change(h) { it.copy(color = i) } }
                                IconButton(onClick = {
                                    app.library.edit(video.meta.id) { v -> v.copy(highlights = v.highlights.filterNot { it.id == h.id }) }
                                    scope.launch {
                                        val r = snackbar.showSnackbar("Highlight deleted", actionLabel = "Undo", duration = androidx.compose.material3.SnackbarDuration.Short)
                                        if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) app.library.edit(video.meta.id) { v -> v.copy(highlights = v.highlights + h) }
                                    }
                                }) { Icon(Icons.Rounded.Close, "Remove highlight") }
                            }
                            if (h.text.isNotBlank()) Text(h.text, style = MaterialTheme.typography.bodyMedium)
                            var note by remember(h.id) { mutableStateOf(h.note) }
                            SaveWhenIdle(note, h.note) { n -> change(h) { it.copy(note = n) } }
                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Add a note") },
                                textStyle = MaterialTheme.typography.bodyMedium,
                            )
                            if (h.tags.isNotEmpty()) Text(
                                h.tags.joinToString(" ") { "#$it" }, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
        }
    }
}

/** The text view lays its words out a frame or two after it gets them. Before that it has no line positions. */
private suspend fun settleText() = repeat(3) { androidx.compose.runtime.withFrameNanos { } }

/** Saves typed text shortly after typing stops, and once more when the box goes away. */
@Composable
private fun SaveWhenIdle(text: String, saved: String, onSave: (String) -> Unit) {
    LaunchedEffect(text) { if (text != saved) { delay(450); onSave(text) } }
    val latest by androidx.compose.runtime.rememberUpdatedState(text)
    val stored by androidx.compose.runtime.rememberUpdatedState(saved)
    val save by androidx.compose.runtime.rememberUpdatedState(onSave)
    DisposableEffect(Unit) { onDispose { if (latest != stored) save(latest) } }
}

/**
 * Note editor for one highlight. It sits on the keyboard and pushes the text up, so the words you marked stay in view.
 * One slim field, then one row of buttons. The colours open only when you ask.
 */
@Composable
private fun HighlightNoteBar(
    h: Highlight,
    onNote: (String) -> Unit,
    tags: List<String>,
    onTags: (List<String>) -> Unit,
    vault: String?,
    suggestTags: List<String>,
    onTyping: (Boolean) -> Unit,
    onColor: (Int) -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var text by remember(h.id) { mutableStateOf(h.note) }
    var tagMode by remember(h.id) { mutableStateOf(false) }
    var colorOpen by remember(h.id) { mutableStateOf(false) }
    var newTag by remember(h.id) { mutableStateOf("") }
    var linking by remember(h.id) { mutableStateOf(false) }
    var noteFocus by remember(h.id) { mutableStateOf(false) }
    // Playback stays paused for the whole note: the box, the tag entry and the link picker. Only leaving all of them lets it go on.
    LaunchedEffect(noteFocus, tagMode, linking) { onTyping(noteFocus || tagMode || linking) }
    // True when the picker was opened by typing [[, so the typed brackets are replaced and not doubled.
    var linkTyped by remember(h.id) { mutableStateOf(false) }
    fun addTag(raw: String) {
        val t = raw.trim().removePrefix("#").replace(' ', '-')
        if (t.isNotEmpty() && tags.none { it.equals(t, true) }) onTags(tags + t)
        newTag = ""
    }
    androidx.compose.runtime.key(h.id) { SaveWhenIdle(text, h.note, onNote) }
    val tone = HighlightColors.getOrNull(h.color)?.ui?.let { Color(it) } ?: scheme.primary
    val circle = androidx.compose.foundation.shape.CircleShape
    // Like a notes app: roomy by default. With the keyboard up the box still leaves a few lines of the text above it.
    val screenDp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    val imeUp = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    val fieldMax = (screenDp * (if (imeUp) 0.2f else 0.36f)).dp

    Column(
        Modifier.fillMaxWidth().background(scheme.surfaceContainer)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
    ) {
        androidx.compose.material3.HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
        Column(Modifier.padding(horizontal = 12.dp).padding(top = 2.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(start = 4.dp).width(3.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(tone))
                Text(
                    h.text.ifBlank { "Note" },
                    Modifier.weight(1f).padding(horizontal = 10.dp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                )
                IconButton(onClick = { onNote(text); onClose() }) { Icon(Icons.Rounded.Check, "Done", tint = scheme.primary) }
            }
            androidx.compose.foundation.text.BasicTextField(
                value = text,
                onValueChange = { v ->
                    // Typing [[ opens the note picker, like in Obsidian.
                    if (vault != null && v.endsWith("[[") && !text.endsWith("[[")) { linkTyped = true; linking = true }
                    text = v
                },
                modifier = Modifier.fillMaxWidth().heightIn(max = fieldMax).onFocusChanged { noteFocus = it.isFocused },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(scheme.primary),
                minLines = 3,
                maxLines = 12,
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().background(scheme.surfaceContainerHighest, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                        if (text.isEmpty()) Text(
                            if (vault != null) "Add a note. Type [[ to link" else "Add a note",
                            style = MaterialTheme.typography.bodyLarge, color = scheme.outline,
                        )
                        inner()
                    }
                },
            )
            if (tags.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tags.forEach { t ->
                    androidx.compose.material3.InputChip(
                        selected = false, onClick = { onTags(tags - t) },
                        label = { Text("#$t") }, trailingIcon = { Icon(Icons.Rounded.Close, "Remove $t", Modifier.size(16.dp)) },
                    )
                }
            }
            if (tagMode) {
                PillField(
                    newTag, { newTag = it }, "Add a tag", onDone = { addTag(newTag) },
                    trailing = { if (newTag.isNotBlank()) IconButton(onClick = { addTag(newTag) }) { Icon(Icons.Rounded.Check, "Add tag") } },
                )
                val near = suggestTags.filter { s -> tags.none { it.equals(s, true) } && (newTag.isBlank() || s.contains(newTag.removePrefix("#"), true)) }.take(8)
                if (near.isNotEmpty()) androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(near, key = { "nt$it" }) { s -> androidx.compose.material3.FilterChip(selected = false, onClick = { addTag(s) }, label = { Text("#$s") }) }
                }
            }
            if (colorOpen) ColorDots(h.color, Modifier.fillMaxWidth()) { onColor(it); colorOpen = false }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(circle).clickable(onClickLabel = "Change the colour") { colorOpen = !colorOpen },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(22.dp).clip(circle).background(tone).border(2.dp, scheme.onSurface.copy(alpha = if (colorOpen) 0.9f else 0.35f), circle))
                }
                IconButton(onClick = { tagMode = !tagMode }, Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Sell, "Tags", tint = if (tagMode || tags.isNotEmpty()) scheme.primary else scheme.onSurfaceVariant)
                }
                if (vault != null) IconButton(onClick = { linkTyped = false; linking = true }, Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Link, "Link a note", tint = scheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onPlay, Modifier.size(40.dp)) { Icon(Icons.Rounded.PlayArrow, "Play from here", tint = scheme.onSurfaceVariant) }
                IconButton(onClick = onDelete, Modifier.size(40.dp)) { Icon(Icons.Rounded.Delete, "Remove highlight", tint = scheme.onSurfaceVariant) }
            }
        }
    }

    if (linking && vault != null) OmniSheet(
        vault = vault, title = "Link a note",
        kinds = setOf(app.obsiread.data.OmniKind.Note),
        onPick = { item ->
            val name = item.title
            text = (if (linkTyped && text.endsWith("[[")) text.removeSuffix("[[") else text.trimEnd() + (if (text.isBlank()) "" else " ")) + "[[$name]]"
            linking = false
        },
        onDismiss = { linking = false },
    )
}

/** Search box for the transcript in front of you. Shows how many matches and steps between them. */
@Composable
private fun FindBar(query: String, count: Int, at: Int, onQuery: (String) -> Unit, onStep: (Int) -> Unit, onClose: () -> Unit) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1f).focusRequester(focus),
            singleLine = true,
            placeholder = { Text("Find in this video") },
            shape = RoundedCornerShape(50),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onStep(1) }),
        )
        Text(
            if (query.trim().length < 2) "" else if (count == 0) "None" else "${at + 1}/$count",
            Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = { onStep(-1) }, enabled = count > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Previous match") }
        IconButton(onClick = { onStep(1) }, enabled = count > 0) { Icon(Icons.Rounded.KeyboardArrowDown, "Next match") }
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close search") }
    }
}

/** A square switch in the reader's menu: an icon over a short name, filled while it is on. */
@Composable
private fun MenuTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.size(width = 68.dp, height = 64.dp).clip(RoundedCornerShape(16.dp))
            .background(if (on) scheme.primaryContainer else scheme.surfaceContainerHighest)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        val tint = when {
            !enabled -> scheme.outline
            on -> scheme.onPrimaryContainer
            else -> scheme.onSurface
        }
        Icon(icon, null, Modifier.size(22.dp), tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}
