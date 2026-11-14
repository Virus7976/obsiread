package app.obsiread.data

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** A part of the page text that is drawn in its own way. */
data class Styled(val range: IntRange, val kind: String, val href: String = "", val level: Int = 0)

/**
 * Builds the reader's page from the article HTML. The page is one piece of text, like the
 * transcript in ObsiRead, so a highlight can start and end anywhere. Each word gets a number,
 * and that number is what a highlight and the reading place are saved by. (The word's "start"
 * is that number too, so code made for times works without change.)
 */
object ArticleDoc {
    class Built(val doc: TranscriptDoc, val styles: List<Styled>, val images: List<Pair<Int, String>>)

    /** Stands in for a picture in the text. The reader draws the picture there. */
    const val IMAGE_CHAR = '￼'

    /**
     * [title] and [byline] go at the top of the page. They are not words, so a highlight never starts in
     * them. [lead] is the main picture, shown above the title when the article does not open with it.
     */
    fun build(html: String, title: String = "", byline: String = "", lead: String = ""): Built {
        val root = Jsoup.parseBodyFragment(html).body()
        val sb = StringBuilder()
        val words = ArrayList<Word>()
        val paras = ArrayList<Para>()
        val headings = ArrayList<IntRange>()
        val styles = ArrayList<Styled>()
        val images = ArrayList<Pair<Int, String>>()

        var paraStart = -1
        var paraFirst = -1

        fun endBlock() {
            if (paraStart >= 0) {
                if (words.size > paraFirst) paras += Para(paraStart, sb.length, paraFirst.toDouble(), paraFirst, words.lastIndex)
                sb.append("\n\n")
            }
            paraStart = -1
            paraFirst = -1
        }

        fun startBlock() {
            if (paraStart < 0) {
                paraStart = sb.length
                paraFirst = words.size
            }
        }

        fun addText(raw: String) {
            if (raw.isEmpty()) return
            startBlock()
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                if (c.isWhitespace()) {
                    if (sb.isNotEmpty() && sb.last() != ' ' && sb.last() != '\n' && sb.length > paraStart) sb.append(' ')
                    i++
                    continue
                }
                var j = i
                while (j < raw.length && !raw[j].isWhitespace()) j++
                val c0 = sb.length
                // A word right after a link or bold part with no space is the same word on the page.
                val glued = c0 > paraStart && sb.last() != ' ' && sb.last() != '\n' && words.isNotEmpty() && words.last().charEnd == c0
                sb.append(raw, i, j)
                if (glued) {
                    val w = words.removeAt(words.lastIndex)
                    words += w.copy(charEnd = sb.length)
                } else {
                    words += Word(words.size.toDouble(), c0, sb.length)
                }
                i = j
            }
        }

        fun inline(n: Node) {
            when (n) {
                is TextNode -> addText(n.wholeText)
                is Element -> {
                    val tag = n.tagName()
                    if (tag == "br") { if (paraStart >= 0) sb.append('\n'); return }
                    if (tag == "img") return
                    val s0 = sb.length
                    n.childNodes().forEach { inline(it) }
                    val e = sb.length
                    if (e > s0) when (tag) {
                        "a" -> n.attr("href").takeIf { it.isNotBlank() }?.let { styles += Styled(s0 until e, "link", it) }
                        "em", "i" -> styles += Styled(s0 until e, "em")
                        "strong", "b" -> styles += Styled(s0 until e, "strong")
                        "code" -> styles += Styled(s0 until e, "code")
                        "sup" -> styles += Styled(s0 until e, "sup")
                    }
                }
            }
        }

        fun image(src: String) {
            endBlock()
            images += sb.length to src
            sb.append(IMAGE_CHAR)
            sb.append("\n\n")
        }

        fun block(el: Element, depth: Int = 0, inQuote: Boolean = false) {
            for (n in el.childNodes()) {
                if (n is TextNode) {
                    if (n.isBlank) continue
                    inline(n)
                    continue
                }
                if (n !is Element) continue
                when (val tag = n.tagName()) {
                    "h1", "h2", "h3", "h4", "h5", "h6" -> {
                        endBlock()
                        val s0 = sb.length
                        n.childNodes().forEach { inline(it) }
                        if (sb.length > s0) {
                            headings += s0 until sb.length
                            styles += Styled(s0 until sb.length, "heading", level = tag[1] - '0')
                        }
                        endBlock()
                    }
                    "p" -> {
                        endBlock()
                        val s0 = sb.length
                        n.childNodes().forEach { if (it is Element && it.tagName() == "img") Unit else inline(it) }
                        if (inQuote && sb.length > s0) styles += Styled(s0 until sb.length, "quote")
                        endBlock()
                        n.select("img").forEach { image(it.attr("src")) }
                    }
                    "ul", "ol" -> {
                        endBlock()
                        var k = 1
                        for (li in n.children().filter { it.tagName() == "li" }) {
                            endBlock()
                            val mark = if (tag == "ol") "${k++}. " else "•  "
                            startBlock()
                            val m0 = sb.length
                            sb.append("    ".repeat(depth)).append(mark)
                            styles += Styled(m0 until sb.length, "bullet")
                            // A list item may hold a list of its own.
                            for (c in li.childNodes()) {
                                if (c is Element && (c.tagName() == "ul" || c.tagName() == "ol")) { endBlock(); block(Element("div").appendChild(c.clone()), depth + 1) }
                                else if (c is Element && c.tagName() == "p") inline(c)
                                else inline(c)
                            }
                            endBlock()
                        }
                    }
                    "blockquote" -> {
                        endBlock()
                        val s0 = sb.length
                        block(n, depth, inQuote = true)
                        endBlock()
                        if (sb.length > s0) styles += Styled(s0 until (sb.length - 2).coerceAtLeast(s0 + 1), "quote")
                    }
                    "pre" -> {
                        endBlock()
                        startBlock()
                        val s0 = sb.length
                        // Code keeps its lines. Its words are still words, so it can be highlighted.
                        n.wholeText().trimEnd().lines().forEachIndexed { i, line ->
                            if (i > 0) sb.append('\n')
                            addText(line.replace("\t", "    "))
                        }
                        if (sb.length > s0) styles += Styled(s0 until sb.length, "pre")
                        endBlock()
                    }
                    "comment" -> {
                        val depth = n.attr("depth").toIntOrNull() ?: 0
                        endBlock()
                        val c0 = sb.length
                        n.selectFirst("cmeta")?.let { m ->
                            startBlock()
                            val m0 = sb.length
                            m.childNodes().forEach { inline(it) }
                            if (sb.length > m0) {
                                styles += Styled(m0 until sb.length, "cmeta")
                                val name = m.selectFirst("b")?.text().orEmpty()
                                if (name.isNotEmpty()) styles += Styled(m0 until (m0 + name.length).coerceAtMost(sb.length), "cauthor", level = n.attr("hue").toIntOrNull() ?: 0)
                            }
                            endBlock()
                            // The comment sits right under its author line: one line break, not a paragraph gap.
                            if (sb.endsWith("\n\n")) sb.setLength(sb.length - 1)
                        }
                        n.selectFirst("cbody")?.let { block(it, depth) }
                        endBlock()
                        if (sb.length - 2 > c0) styles += Styled(c0 until sb.length - 2, "thread", level = depth)
                    }
                    "img" -> image(n.attr("src"))
                    "figure" -> {
                        n.select("img").forEach { image(it.attr("src")) }
                        n.selectFirst("figcaption")?.let { cap ->
                            endBlock()
                            val s0 = sb.length
                            cap.childNodes().forEach { inline(it) }
                            if (sb.length > s0) styles += Styled(s0 until sb.length, "caption")
                            endBlock()
                        }
                    }
                    "hr" -> { endBlock(); sb.append("· · ·\n\n"); styles += Styled((sb.length - 7) until (sb.length - 2), "rule") }
                    "table" -> {
                        endBlock()
                        for (tr in n.select("tr")) {
                            val cells = tr.children().filter { it.tagName() == "td" || it.tagName() == "th" }
                            if (cells.isEmpty()) continue
                            startBlock()
                            cells.forEachIndexed { i, c ->
                                if (i > 0) { val d0 = sb.length; sb.append("  |  "); styles += Styled(d0 until sb.length, "bullet") }
                                inline(c)
                            }
                            endBlock()
                        }
                    }
                    "a", "em", "i", "strong", "b", "code", "span", "sup", "sub", "br" -> inline(n)
                    else -> {
                        // div, section, article and the like: walk inside.
                        val hasBlocks = n.children().any { it.tagName() in blockTags }
                        if (hasBlocks) block(n, depth, inQuote)
                        else {
                            endBlock()
                            val s0 = sb.length
                            n.childNodes().forEach { inline(it) }
                            if (inQuote && sb.length > s0) styles += Styled(s0 until sb.length, "quote")
                            endBlock()
                        }
                    }
                }
            }
        }

        val firstImage = root.selectFirst("img")?.attr("src")
        val opensWithImage = firstImage != null && root.text().isNotEmpty() &&
            root.html().indexOf("<img") < root.html().indexOf(root.text().take(20).ifEmpty { "\u0000" }).let { if (it < 0) Int.MAX_VALUE else it }
        // The main picture is shown only where the article itself has it. Nothing is added, like the Web Clipper.
        if (title.isNotBlank()) {
            val t0 = sb.length
            sb.append(title.trim())
            styles += Styled(t0 until sb.length, "title")
            sb.append('\n')
            if (byline.isNotBlank()) {
                val b0 = sb.length
                sb.append(byline.trim())
                styles += Styled(b0 until sb.length, "byline")
                sb.append('\n')
            }
            sb.append('\n')
        }
        block(root)
        endBlock()
        // Drop the blank lines at the end, but keep every range inside the text.
        var end = sb.length
        while (end > 0 && sb[end - 1] == '\n') end--
        val text = sb.substring(0, end)
        fun fit(r: IntRange) = r.first..minOf(r.last, text.length - 1)
        val doc = TranscriptDoc(
            text = text,
            words = words,
            paras = paras.map { if (it.charEnd > text.length) it.copy(charEnd = text.length) else it },
            headings = headings.map(::fit).filter { !it.isEmpty() },
            stamps = emptyList(),
        )
        return Built(doc, styles.map { it.copy(range = fit(it.range)) }.filter { !it.range.isEmpty() }, images.filter { it.first < text.length })
    }

    private val blockTags = setOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "blockquote", "pre", "figure", "img", "table", "hr", "div", "section", "article", "comment", "cbody")
}
