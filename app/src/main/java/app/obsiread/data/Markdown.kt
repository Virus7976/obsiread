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

/** Turns the cleaned article HTML into Markdown for Obsidian. */
object Markdown {
    fun from(html: String): String {
        val root = Jsoup.parseBodyFragment(html).body()
        val out = StringBuilder()
        blocks(root, out, "")
        return out.toString().replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun esc(s: String) = s.replace(Regex("([*_`\\[\\]])"), "\\\\$1")

    private fun inline(n: Node): String = when (n) {
        is TextNode -> esc(n.text())
        is Element -> {
            val inner = n.childNodes().joinToString("") { inline(it) }
            when (n.tagName()) {
                "a" -> {
                    val href = n.attr("href")
                    if (href.isBlank() || inner.isBlank()) inner else "[${inner.trim()}]($href)"
                }
                "em", "i" -> if (inner.isBlank()) inner else "*${inner.trim()}*"
                "strong", "b" -> if (inner.isBlank()) inner else "**${inner.trim()}**"
                "code" -> "`${n.text()}`"
                "br" -> "  \n"
                "img" -> image(n)
                else -> inner
            }
        }
        else -> ""
    }

    private fun image(img: Element): String {
        val src = img.attr("src")
        if (src.isBlank()) return ""
        return "![${img.attr("alt").replace("]", "")}]($src)"
    }

    private fun blocks(el: Element, out: StringBuilder, prefix: String) {
        fun para(text: String) {
            val t = text.trim()
            if (t.isEmpty()) return
            t.lines().forEach { out.append(prefix).append(it.trim()).append('\n') }
            out.append(prefix.trimEnd()).append('\n')
        }
        val inlineRun = StringBuilder()
        fun flush() { if (inlineRun.isNotBlank()) para(inlineRun.toString()); inlineRun.clear() }
        for (n in el.childNodes()) {
            if (n is TextNode) { inlineRun.append(inline(n)); continue }
            if (n !is Element) continue
            when (val tag = n.tagName()) {
                "h1", "h2", "h3", "h4", "h5", "h6" -> { flush(); para("#".repeat(tag[1] - '0') + " " + n.childNodes().joinToString("") { inline(it) }.trim()) }
                "p" -> { flush(); para(n.childNodes().joinToString("") { inline(it) }) }
                "ul", "ol" -> {
                    flush()
                    var k = 1
                    for (li in n.children().filter { it.tagName() == "li" }) {
                        val mark = if (tag == "ol") "${k++}. " else "- "
                        val nested = li.children().filter { it.tagName() == "ul" || it.tagName() == "ol" }
                        val text = li.childNodes().filterNot { it in nested }.joinToString("") { inline(it) }.trim()
                        out.append(prefix).append(mark).append(text).append('\n')
                        for (sub in nested) {
                            val inner = StringBuilder()
                            blocks(Element("div").appendChild(sub.clone()), inner, "")
                            inner.lines().filter { it.isNotBlank() }.forEach { out.append(prefix).append("    ").append(it).append('\n') }
                        }
                    }
                    out.append(prefix.trimEnd()).append('\n')
                }
                "blockquote" -> { flush(); blocks(n, out, "$prefix> "); out.append(prefix.trimEnd()).append('\n') }
                "comment" -> {
                    flush()
                    val q = prefix + "> ".repeat((n.attr("depth").toIntOrNull() ?: 0) + 1)
                    n.selectFirst("cmeta")?.let { m ->
                        val name = m.selectFirst("b")?.text().orEmpty()
                        val rest = m.text().removePrefix(name).trim()
                        para2(out, q, "**$name**" + (if (rest.isNotEmpty()) " $rest" else ""))
                    }
                    n.selectFirst("cbody")?.let { blocks(it, out, q) }
                    out.append('\n')
                }
                "pre" -> {
                    flush()
                    out.append(prefix).append("```\n")
                    n.wholeText().trimEnd().lines().forEach { out.append(prefix).append(it).append('\n') }
                    out.append(prefix).append("```\n\n")
                }
                "img" -> { flush(); para(image(n)) }
                "figure" -> {
                    flush()
                    n.select("img").forEach { para(image(it)) }
                    n.selectFirst("figcaption")?.let { para("*" + it.text().trim() + "*") }
                }
                "hr" -> { flush(); para("---") }
                "table" -> { flush(); table(n, out, prefix) }
                "a", "em", "i", "strong", "b", "code", "span", "sup", "sub", "br" -> inlineRun.append(inline(n))
                else -> { flush(); blocks(n, out, prefix) }
            }
        }
        flush()
    }

    private fun para2(out: StringBuilder, prefix: String, text: String) {
        out.append(prefix).append(text.trim()).append('\n').append(prefix.trimEnd()).append('\n')
    }

    private fun table(t: Element, out: StringBuilder, prefix: String) {
        val rows = t.select("tr").map { tr -> tr.children().filter { it.tagName() == "td" || it.tagName() == "th" }.map { c -> c.childNodes().joinToString("") { inline(it) }.trim().replace("|", "\\|").replace("\n", " ") } }
            .filter { it.isNotEmpty() }
        if (rows.isEmpty()) return
        val cols = rows.maxOf { it.size }
        fun line(r: List<String>) = "| " + (0 until cols).joinToString(" | ") { r.getOrElse(it) { "" } } + " |"
        out.append(prefix).append(line(rows[0])).append('\n')
        out.append(prefix).append("|" + " --- |".repeat(cols)).append('\n')
        rows.drop(1).forEach { out.append(prefix).append(line(it)).append('\n') }
        out.append('\n')
    }
}
