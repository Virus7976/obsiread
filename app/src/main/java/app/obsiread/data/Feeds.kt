package app.obsiread.data

import android.content.Context
import app.obsi.core.data.JsonFileStore
import app.obsi.core.data.storeJson
import app.obsiread.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.IOException
import java.net.URI
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

/** One post in a feed. */
@Serializable
data class FeedItem(val title: String, val link: String, val published: Long = 0, val summary: String = "")

/** A site you follow. */
@Serializable
data class Feed(
    val url: String,
    val title: String,
    val site: String = "",
    val items: List<FeedItem> = emptyList(),
    val checkedAt: Long = 0,
    val addedAt: Long = 0,
)

@Serializable
data class FeedState(val feeds: List<Feed> = emptyList(), val hidden: List<String> = emptyList())

/** A new post, with the feed it came from. */
data class FreshItem(val item: FeedItem, val feed: Feed)

/** Reads RSS and Atom feeds, and finds the feed of a web page. */
object FeedParser {
    data class Parsed(val title: String, val site: String, val items: List<FeedItem>)

    /** The feed in [xml], or null when it is not a feed. */
    fun parse(xml: String, base: String): Parsed? = runCatching {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = true
        f.isExpandEntityReferences = false
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        val doc = f.newDocumentBuilder().parse(xml.trimStart('\uFEFF', ' ', '\n', '\r', '\t').byteInputStream())
        val root = doc.documentElement
        when (name(root)) {
            "rss" -> {
                val ch = child(root, "channel") ?: return null
                Parsed(text(ch, "title"), abs(text(ch, "link"), base), children(ch, "item").mapNotNull { rssItem(it, base) })
            }
            "RDF" -> {
                val ch = child(root, "channel")
                Parsed(ch?.let { text(it, "title") }.orEmpty(), abs(ch?.let { text(it, "link") }.orEmpty(), base), children(root, "item").mapNotNull { rssItem(it, base) })
            }
            "feed" -> Parsed(text(root, "title"), abs(atomLink(root), base), children(root, "entry").mapNotNull { atomItem(it, base) })
            else -> null
        }
    }.getOrNull()

    /** The address of the feed a web page names in its head, or null. */
    fun discover(html: String, base: String): String? {
        val tags = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html).map { it.value }
        fun attr(tag: String, a: String) =
            Regex("\\b$a\\s*=\\s*([\"'])(.*?)\\1", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(2)
        val found = tags.firstOrNull { t ->
            attr(t, "rel")?.lowercase()?.split(' ')?.contains("alternate") == true &&
                attr(t, "type")?.lowercase()?.let { it.contains("rss+xml") || it.contains("atom+xml") } == true
        } ?: return null
        val href = attr(found, "href")?.replace("&amp;", "&") ?: return null
        return abs(href, base).takeIf { it.isNotBlank() }
    }

    /** A feed date as milliseconds, or 0 when it cannot be read. */
    fun date(s: String): Long {
        val t = s.trim()
        if (t.isEmpty()) return 0
        val tries = listOf<() -> Long>(
            { ZonedDateTime.parse(t, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() },
            { ZonedDateTime.parse(t, DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss zzz", Locale.ENGLISH)).toInstant().toEpochMilli() },
            { ZonedDateTime.parse(t, DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm zzz", Locale.ENGLISH)).toInstant().toEpochMilli() },
            { OffsetDateTime.parse(t).toInstant().toEpochMilli() },
            { Instant.parse(t).toEpochMilli() },
        )
        for (f in tries) runCatching { return f() }
        return 0
    }

    /** Plain words from a summary that may hold HTML. */
    fun plain(s: String, max: Int = 220): String {
        val t = s.replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&#8217;", "'")
            .replace(Regex("\\s+"), " ").trim()
        return if (t.length <= max) t else t.take(max).substringBeforeLast(' ') + "…"
    }

    private fun rssItem(e: Element, base: String): FeedItem? {
        val link = text(e, "link").ifBlank { child(e, "guid")?.takeIf { it.getAttribute("isPermaLink") != "false" }?.textContent?.trim().orEmpty() }
        if (link.isBlank()) return null
        val title = text(e, "title").ifBlank { plain(text(e, "description"), 80) }
        return FeedItem(plain(title, 300), abs(link, base), date(text(e, "pubDate").ifBlank { text(e, "date") }), plain(text(e, "description")))
    }

    private fun atomItem(e: Element, base: String): FeedItem? {
        val link = atomLink(e).ifBlank { return null }
        return FeedItem(
            plain(text(e, "title"), 300), abs(link, base),
            date(text(e, "published").ifBlank { text(e, "updated") }),
            plain(text(e, "summary").ifBlank { text(e, "content") }),
        )
    }

    private fun atomLink(e: Element): String {
        val links = children(e, "link")
        val pick = links.firstOrNull { it.getAttribute("rel").let { r -> r.isEmpty() || r == "alternate" } } ?: links.firstOrNull()
        return pick?.getAttribute("href")?.trim().orEmpty()
    }

    private fun name(n: Node) = n.localName ?: n.nodeName.substringAfter(':')
    private fun children(e: Element, n: String): List<Element> {
        val out = ArrayList<Element>()
        val list = e.childNodes
        for (i in 0 until list.length) { val c = list.item(i); if (c is Element && name(c) == n) out += c }
        return out
    }
    private fun child(e: Element, n: String) = children(e, n).firstOrNull()
    private fun text(e: Element, n: String) = child(e, n)?.textContent?.trim().orEmpty()
    private fun abs(href: String, base: String): String =
        if (href.isBlank()) "" else runCatching { URI(base).resolve(href.trim()).toString() }.getOrDefault(href.trim())
}

/** The feeds you follow, and the posts you hid. */
class FeedStore(context: Context) : JsonFileStore<FeedState>(File(context.filesDir, "feeds.json")) {
    private val _state = MutableStateFlow(readOrNull() ?: FeedState())
    val state: StateFlow<FeedState> = _state.asStateFlow()

    override fun encode(value: FeedState) = storeJson.encodeToString(FeedState.serializer(), value)
    override fun decode(text: String) = storeJson.decodeFromString(FeedState.serializer(), text)

    private val http by lazy {
        okhttp3.OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    }

    private suspend fun get(url: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val req = okhttp3.Request.Builder().url(url)
            .header("User-Agent", "ObsiRead/" + BuildConfig.VERSION_NAME + " (https://github.com/Virus7976/obsiread)")
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, text/html;q=0.8")
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("The site said ${r.code}.")
            val body = r.body ?: throw IOException("The site sent nothing.")
            if (body.contentLength() > 8_000_000) throw IOException("That page is too big.")
            r.request.url.toString() to body.string()
        }
    }

    /** Follows the feed at [url], or the feed that the page at [url] names. Returns it, or throws with a message. */
    suspend fun follow(url: String): Feed {
        val (at, body) = get(url)
        var feedUrl = at
        var parsed = FeedParser.parse(body, at)
        if (parsed == null) {
            val found = FeedParser.discover(body, at) ?: throw IOException("That page has no feed.")
            val (at2, body2) = get(found)
            feedUrl = at2
            parsed = FeedParser.parse(body2, at2) ?: throw IOException("That page has no feed.")
        }
        val now = System.currentTimeMillis()
        val old = _state.value.feeds.firstOrNull { it.url == feedUrl }
        val feed = Feed(
            url = feedUrl, title = parsed.title.ifBlank { URI(feedUrl).host.orEmpty().removePrefix("www.") },
            site = parsed.site, items = parsed.items.take(MAX_ITEMS), checkedAt = now, addedAt = old?.addedAt ?: now,
        )
        _state.update { s -> s.copy(feeds = s.feeds.filterNot { it.url == feedUrl } + feed) }
        persist(_state.value)
        return feed
    }

    fun unfollow(url: String) {
        _state.update { s -> s.copy(feeds = s.feeds.filterNot { it.url == url }) }
        persist(_state.value)
    }

    /** Hides one post, for one you read or do not want. */
    fun hide(link: String) {
        _state.update { s -> if (link in s.hidden) s else s.copy(hidden = (s.hidden + link).takeLast(2000)) }
        persist(_state.value)
    }

    /** Reads each feed again, when it was last read more than [olderThanMs] ago. A feed that fails keeps its old posts. */
    suspend fun check(olderThanMs: Long = 30 * 60_000L) {
        val now = System.currentTimeMillis()
        for (f in _state.value.feeds) {
            if (now - f.checkedAt < olderThanMs) continue
            val parsed = runCatching { FeedParser.parse(get(f.url).second, f.url) }.getOrNull() ?: continue
            val known = parsed.items.map { it.link }.toSet()
            val items = (parsed.items + f.items.filterNot { it.link in known }).take(MAX_ITEMS)
            _state.update { s -> s.copy(feeds = s.feeds.map { if (it.url == f.url) it.copy(items = items, checkedAt = now) else it }) }
        }
        persist(_state.value)
    }

    companion object {
        const val MAX_ITEMS = 50

        /** New posts from every feed: not hidden, and not in the library yet. Newest first. */
        fun fresh(s: FeedState, inLibrary: Set<String>): List<FreshItem> {
            val hidden = s.hidden.toSet()
            return s.feeds.flatMap { f -> f.items.map { FreshItem(it, f) } }
                .filter { it.item.link !in hidden && Clipper.idFor(it.item.link) !in inLibrary }
                .distinctBy { it.item.link }
                .sortedByDescending { it.item.published }
        }
    }
}
