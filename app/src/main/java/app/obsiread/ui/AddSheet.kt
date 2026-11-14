package app.obsiread.ui

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.rounded.Link
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.obsiread.data.Clipper
import app.obsi.core.data.Playlist
import app.obsiread.data.Article
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Add a page by its link. It is read at once, and opens in the reader when it is ready.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(initial: String?, onDismiss: () -> Unit, onPlaylist: (String) -> Unit) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf(initial.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var failedLink by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun go(raw: String) {
        val link = Clipper.findUrl(raw.trim()) ?: raw.trim().takeIf { it.contains('.') && !it.contains(' ') }?.let { "https://$it" }
        if (link == null) { error = "That is not a web address."; return }
        error = null
        busy = true
        scope.launch {
            runCatching { app.addFromUrl(link) }
                .onSuccess { id -> onDismiss(); nav.openVideo(id) }
                .onFailure { error = it.message ?: "Could not read that page."; failedLink = link }
            busy = false
        }
    }

    LaunchedEffect(Unit) {
        if (!initial.isNullOrBlank()) go(initial)
        else { sheet.awaitOpen(); runCatching { focus.requestFocus() } }
    }

    SmoothSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add a page", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                singleLine = true, shape = RoundedCornerShape(50),
                placeholder = { Text("Paste a web address") },
                leadingIcon = { Icon(Icons.Rounded.Link, null) },
                trailingIcon = {
                    if (input.isNotEmpty()) IconButton(onClick = { input = ""; error = null }) { Icon(Icons.Rounded.Close, "Clear") }
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go(input) }),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = {
                        val text = readClip(ctx)
                        if (text.isNullOrBlank()) error = "The clipboard is empty." else { input = text; go(text) }
                    },
                    enabled = !busy,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Paste and add")
                }
                if (input.isNotBlank() && !busy) Button(onClick = { go(input) }, shapes = ButtonDefaults.shapes()) { Text("Add") }
            }
            if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator(Modifier.size(40.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reading the page…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            // Some pages need you: to sign in, or to pass a check. The app's browser lets you, then reads it.
            failedLink?.let { link ->
                if (error != null) FilledTonalButton(onClick = { onDismiss(); nav.openBrowser(link, null) }, shapes = ButtonDefaults.shapes()) {
                    Text("Open it in the browser")
                }
            }
            Text(
                "Tip: in your browser, tap Share and pick ObsiRead.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A round picture, or a plain disc when there is none. */
@Composable
fun Avatar(url: String, size: Int) {
    if (url.isBlank()) Box(Modifier.size(size.dp).clip(CircleShape).then(Modifier), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape, modifier = Modifier.size(size.dp)) {}
    } else AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.size(size.dp).clip(CircleShape))
}

/** 1200 becomes 1.2K, 3400000 becomes 3.4M. */
fun compactCount(n: Long): String = when {
    n >= 1_000_000_000 -> "%.1fB".format(n / 1e9).replace(".0", "")
    n >= 1_000_000 -> "%.1fM".format(n / 1e6).replace(".0", "")
    n >= 1_000 -> "%.1fK".format(n / 1e3).replace(".0", "")
    else -> n.toString()
}

private fun readClip(ctx: Context): String? {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim()
}
