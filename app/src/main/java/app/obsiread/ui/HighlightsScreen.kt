package app.obsiread.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.obsiread.data.Place
import app.obsi.core.ui.HighlightSource
import app.obsi.core.ui.HighlightsLibrary

/** Every highlight in one place. The page itself is shared by every Obsi app, in obsi-core. */
@Composable
fun HighlightsScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val items by app.library.articles.collectAsState()
    val sources = remember(items) { items.map { HighlightSource(it.meta.id, it.meta.title, it.meta.author, it.addedAt, it.highlights) } }
    HighlightsLibrary(
        onBack = onBack,
        videos = sources,
        place = { src, h -> Place.label(h.startSec, items.firstOrNull { it.meta.id == src.id }?.meta?.words ?: 0) },
        onOpen = { id, h ->
            app.library.edit(id) { it.copy(positionSec = h.startSec) }
            app.pendingSeek.value = id to h.startSec
            nav.openVideo(id)
        },
        onRemove = { id, h -> app.library.edit(id) { v -> v.copy(highlights = v.highlights.filterNot { it.id == h.id }) } },
        onRestore = { id, h -> app.library.edit(id) { v -> v.copy(highlights = v.highlights + h) } },
        emptyHint = "Hold a word in an article to highlight it. Everything you highlight or note shows up here.",
    )
}
