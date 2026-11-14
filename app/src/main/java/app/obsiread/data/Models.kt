package app.obsiread.data

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import kotlinx.serialization.Serializable

@Serializable
data class ArticleMeta(
    val id: String,
    val title: String,
    val author: String,
    val description: String = "",
    val published: String = "",
    /** Not used for articles. Kept so old code that reads it still builds. */
    val durationSec: Int = 0,
    /** The page the article came from, after redirects. */
    val url: String = "",
    /** The site name, like "The Verge". */
    val site: String = "",
    /** The main picture of the article. Blank when it has none. */
    val image: String = "",
    /** Words in the article, for the reading time. */
    val words: Int = 0,
    /** The page's language code, like "en". */
    val lang: String = "",
) {
    val thumb: String get() = image
    /** Minutes to read at 230 words a minute. At least 1. */
    val minutes: Int get() = ((words + 229) / 230).coerceAtLeast(1)
    val host: String get() = runCatching { java.net.URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")
    val siteName: String get() = site.ifBlank { host }
}

@Serializable
data class Article(
    val meta: ArticleMeta,
    val chapters: List<Chapter> = emptyList(),
    val highlights: List<Highlight> = emptyList(),
    val positionSec: Double = 0.0,
    val pinned: Boolean = false,
    val updatedAt: Long = 0L,
    val clippedAt: Long = 0L,
    val tags: List<String> = emptyList(),
    val category: String = "",
    /** Empty = in the main library. Otherwise the video lives in that playlist only. */
    val playlistId: String = "",
    val addedAt: Long = 0L,
    /** Where the last clip of this video went, so it can be opened in Obsidian. */
    val noteVault: String = "",
    val notePath: String = "",
    /** What was filled in the last time this video was clipped. It comes back next time. */
    val lastClip: ClipMemo? = null,
    /** When you finished it, by hand or by watching it to the end. 0 means not done. */
    val doneAt: Long = 0L,
    /** Last time it played, so what you just watched comes first. */
    val playedAt: Long = 0L,
) {
    val done: Boolean get() = doneAt > 0

    /** Where to start. A video you finished starts over, so it does not end again in a few seconds. */
    val resumeSec: Double
        get() = if (meta.durationSec > 0 && positionSec >= meta.durationSec - 10) 0.0 else positionSec
}

