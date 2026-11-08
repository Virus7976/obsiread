package app.obsiread.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.net.URI
import java.security.MessageDigest

/** One article read from the web: its details and its cleaned HTML. */
class Clipped(val meta: ArticleMeta, val html: String)

/**
 * Turns a web page into an article. The page is read by Defuddle, the reader inside Obsidian Web
 * Clipper, in the app's own browser (see web/WebReader.kt). This keeps only the parts worth reading:
 * text, headings, lists, quotes, code, tables and pictures.
 */
object Clipper {
    private val tracking = Regex("^(utm_.*|fbclid|gclid|dclid|mc_cid|mc_eid|igshid|ref_src|ref|cmpid|_hsenc|_hsmi|mkt_tok|yclid|si)$", RegexOption.IGNORE_CASE)

    /**
     * The same page always gets the same address: no tracking bits, no #part, no "www.", and
     * the scheme and host in lower case. Two shares of one article then find the same copy.
     */
    fun canonical(url: String): String {
        val u = runCatching { URI(url.trim()) }.getOrNull() ?: return url.trim()
        val host = u.host?.lowercase()?.removePrefix("www.") ?: return url.trim()
        val query = u.rawQuery?.split('&')?.filter { p -> p.isNotBlank() && !tracking.matches(p.substringBefore('=')) }?.joinToString("&")
        val path = (u.rawPath ?: "").ifEmpty { "/" }.let { if (it.length > 1) it.trimEnd('/') else it }
        return buildString {
            append("https://").append(host)
            if (u.port != -1 && u.port != 443 && u.port != 80) append(':').append(u.port)
            append(path)
            if (!query.isNullOrEmpty()) append('?').append(query)
        }
    }

    /** A short id made from the clean address. */
    fun idFor(url: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(canonical(url).toByteArray())
        return bytes.take(8).joinToString("") { "%02x".format(it) }
    }

    /** The first web address in shared text. A browser often shares "Title https://…". */
    fun findUrl(text: String): String? =
        Regex("https?://[^\\s<>\"']+").find(text)?.value?.trimEnd('.', ',', ')', ']', '>', '"', '\'')

    @kotlinx.serialization.Serializable
    private class Found(
        val ok: Boolean = false,
        val error: String = "",
        val url: String = "",
        val content: String = "",
        val title: String = "",
        val author: String = "",
        val published: String = "",
        val image: String = "",
        val site: String = "",
        val description: String = "",
        val domain: String = "",
        val language: String = "",
        val wordCount: Int = 0,
    )

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /**
     * Makes an article from what Defuddle found on a page. [json] is its answer, and [asked] is the
     * address that was opened, in case the page does not say its own.
     */
    fun fromDefuddle(json: String, asked: String): Clipped {
        val f = runCatching { this.json.decodeFromString(Found.serializer(), json) }.getOrNull()
            ?: throw IOException("The page could not be read.")
        if (!f.ok) throw IOException("The page could not be read. " + f.error)
        val url = f.url.ifBlank { asked }
        val body = clean(Jsoup.parseBodyFragment(f.content, url).body(), url)
        val text = body.text()
        if (text.length < 80) throw IOException("No article was found on this page.")
        val title = f.title.trim().ifBlank { runCatching { URI(url).host.orEmpty() }.getOrDefault(url) }
        val author = cleanByline(f.author)

        // The page often repeats its own title and a "By ..." line at the top. The reader shows both already.
        for (el in body.allElements.drop(1).filter { it.ownText().isNotBlank() }.take(3)) {
            val t = el.text()
            val isTitle = el.tagName().matches(Regex("h[1-3]")) && same(t, title)
            val isByline = t.length < 80 && author.isNotBlank() && t.contains(author, ignoreCase = true) &&
                Regex("^(by|written by|words by)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t.trim())
            if (isTitle || isByline) el.remove()
        }

        val meta = ArticleMeta(
            id = idFor(url),
            title = title,
            author = author,
            description = f.description.trim().take(500),
            published = f.published.trim(),
            url = url,
            site = f.site.trim(),
            image = f.image.trim().takeIf { it.startsWith("http") }.orEmpty(),
            words = if (f.wordCount > 0) f.wordCount else text.split(Regex("\\s+")).count { it.isNotBlank() },
            lang = f.language.substringBefore('-').lowercase(),
        )
        return Clipped(meta, body.html())
    }

    private fun same(a: String, b: String): Boolean {
        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        val x = norm(a); val y = norm(b)
        return x.isNotEmpty() && (x == y || y.startsWith(x) || x.startsWith(y) || (x.length > 12 && y.endsWith(x)))
    }

    private fun cleanByline(s: String): String =
        s.trim().removePrefix("By ").removePrefix("by ").removePrefix("BY ").lines().first().trim().take(120)

    private val keep = setOf(
        "p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote", "pre", "code", "a", "em", "i", "strong", "b",
        "img", "figure", "figcaption", "br", "hr", "table", "thead", "tbody", "tr", "td", "th", "sup", "sub", "div", "span", "section", "article",
    )

    /**
     * Keeps only plain article markup. Every link and picture gets its full address, and lazy
     * pictures get their real source.
     */
    private fun clean(root: Element, url: String): Element {
        val doc = Document.createShell(url)
        val body = doc.body()
        body.appendChild(root.clone())
        body.select("script, style, noscript, iframe, form, button, input, svg, nav, aside, footer, header").remove()
        for (img in body.select("img")) {
            val src = listOf("data-src", "data-original", "data-lazy-src", "src").firstNotNullOfOrNull { a ->
                img.absUrl(a).takeIf { it.startsWith("http") }
            } ?: img.attr("srcset").split(',').lastOrNull()?.trim()?.substringBefore(' ')?.let { s ->
                runCatching { URI(url).resolve(s).toString() }.getOrNull()
            }
            if (src.isNullOrBlank() || src.startsWith("data:")) { img.remove(); continue }
            val alt = img.attr("alt")
            // The site's own logo is not part of the article.
            val linked = img.parent()?.tagName() == "a"
            if (Regex("\\blogo\\b", RegexOption.IGNORE_CASE).containsMatchIn(alt) || (linked && src.contains("logo", ignoreCase = true))) {
                (img.parent()?.takeIf { it.tagName() == "a" && it.text().isBlank() } ?: img).remove()
                continue
            }
            img.clearAttributes()
            img.attr("src", src)
            if (alt.isNotBlank()) img.attr("alt", alt)
        }
        for (a in body.select("a")) {
            val href = a.absUrl("href")
            a.clearAttributes()
            if (href.startsWith("http")) a.attr("href", href)
        }
        for (el in body.allElements.drop(1)) {
            val tag = el.tagName()
            if (tag !in keep) { el.unwrap(); continue }
            if (tag != "a" && tag != "img") el.clearAttributes()
        }
        // Wrappers with nothing in them.
        for (el in body.select("div, span, section, article, p").reversed()) {
            if (el.children().isEmpty() && el.text().isBlank()) el.remove()
        }
        return body
    }
}
