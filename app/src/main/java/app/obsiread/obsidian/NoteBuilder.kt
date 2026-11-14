package app.obsiread.obsidian

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import app.obsiread.data.AppSettings
import app.obsi.core.data.ClipTemplate
import app.obsiread.data.Article
import app.obsi.core.data.TemplateProperty
import app.obsiread.data.Place
import java.time.ZonedDateTime



object NoteBuilder {
    /**
     * A link that opens the web page scrolled to [text], with the words marked. Browsers call this a
     * text fragment. Long text is cut to its first and last few words, the way Chrome makes these links.
     */
    fun quoteLink(url: String, text: String): String {
        if (url.isBlank()) return ""
        val words = text.replace(Regex("\\s+"), " ").trim().split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return url
        fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20").replace("-", "%2D").replace(",", "%2C")
        val frag = if (words.size <= 8) enc(words.joinToString(" "))
        else enc(words.take(4).joinToString(" ")) + "," + enc(words.takeLast(4).joinToString(" "))
        return url.substringBefore('#') + "#:~:text=" + frag
    }

    /** The colour is a coloured circle right after the opening marks, then the text. */
    fun styled(h: app.obsi.core.data.Highlight, settings: AppSettings): String {
        val text = h.text.replace(Regex("\\s*\\n\\s*"), " ").trim()
        if (settings.highlightSyntax != "obsidian") return text
        val emoji = app.obsi.core.data.HighlightColors.getOrNull(h.color)?.emoji.orEmpty()
        return "==$emoji$text=="
    }

    fun highlightEntry(video: Article, h: app.obsi.core.data.Highlight, settings: AppSettings): String {
        // A note with no highlighted words, such as one written while a song plays.
        if (h.text.isBlank()) {
            val note = h.note.replace(Regex("\\s*\\n\\s*"), " ").trim()
            return "- " + (if (note.isEmpty()) "" else note) + tagText(h)
        }
        val body = settings.highlightFormat
            .replace("{text}", styled(h, settings))
            .replace("{time}", Place.label(h.startSec, video.meta.words))
            .replace("{link}", quoteLink(video.meta.url, h.text))
        return (if (h.note.isBlank()) body else body + "\n\n" + h.note.trim()) + tagText(h)
    }

    /** The tags of one highlight as Obsidian tags, after a space. Empty when it has none. */
    private fun tagText(h: app.obsi.core.data.Highlight): String =
        if (h.tags.isEmpty()) "" else " " + h.tags.joinToString(" ") { "#" + it }

    fun highlightsMarkdown(video: Article, settings: AppSettings): String =
        video.highlights.sortedBy { it.startSec }.joinToString("\n\n") { highlightEntry(video, it, settings) }

    /**
     * The values a template can use, by the same names Obsidian Web Clipper uses. [article] is the
     * article as Markdown.
     */
    fun variables(video: Article, article: String, settings: AppSettings, now: ZonedDateTime = ZonedDateTime.now()): Map<String, String> {
        val m = video.meta
        val highlights = highlightsMarkdown(video, settings)
        val content = when {
            highlights.isNotBlank() && settings.highlightBehavior == "replace-content" -> highlights
            article.isNotBlank() -> article
            else -> m.description
        }
        return mapOf(
            "title" to m.title,
            "url" to m.url,
            "author" to m.author,
            "published" to m.published,
            "date" to now.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            "time" to now.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            "description" to m.description.lines().firstOrNull { it.isNotBlank() }.orEmpty().take(300),
            "fullDescription" to m.description,
            "content" to content,
            "article" to article,
            "fullContent" to article,
            "highlights" to highlights,
            "image" to m.image,
            "favicon" to (if (m.host.isBlank()) "" else "https://${m.host}/favicon.ico"),
            "minutes" to m.minutes.toString(),
            "readingTime" to "${m.minutes} min",
            "highlightCount" to video.highlights.size.toString(),
            "tags" to video.tags.joinToString(","),
            "category" to video.category,
            "domain" to m.host,
            "site" to m.siteName,
            "language" to m.lang,
            "words" to m.words.toString(),
            "id" to m.id,
        )
    }

    /**
     * Adds the always-on tags and properties to a template's properties.
     * A template value that is already filled in is kept. Tags are joined, never replaced.
     */
    fun withAutos(base: List<TemplateProperty>, autoTags: List<String>, autoProps: List<TemplateProperty>): List<TemplateProperty> {
        val out = base.toMutableList()
        fun clean(t: String) = t.trim().removePrefix("#").trim('"')
        val tagsToAdd = autoTags.map(::clean).filter { it.isNotBlank() }
        if (tagsToAdd.isNotEmpty()) {
            val i = out.indexOfFirst { it.name.equals("tags", ignoreCase = true) }
            if (i < 0) {
                out += TemplateProperty("auto:tags", "tags", tagsToAdd.joinToString(","), "multitext")
            } else {
                val have = splitList(out[i].value).map(::clean).filter { it.isNotBlank() }
                out[i] = out[i].copy(value = (have + tagsToAdd).distinct().joinToString(","))
            }
        }
        for (a in autoProps) {
            if (a.name.isBlank() || a.name.equals("tags", ignoreCase = true)) continue
            val i = out.indexOfFirst { it.name.equals(a.name, ignoreCase = true) }
            if (i < 0) out += a.copy(id = "auto:" + a.name)
            else if (out[i].value.isBlank()) out[i] = out[i].copy(value = a.value)
        }
        return out
    }

    fun build(
        template: ClipTemplate,
        video: Article,
        article: String,
        settings: AppSettings,
        valueOverrides: Map<String, String> = emptyMap(),
        nameOverride: String? = null,
        folderOverride: String? = null,
        vaultOverride: String? = null,
    ): BuiltNote {
        val vars = variables(video, article, settings)
        val props = withAutos(template.properties, settings.autoTags, settings.autoProps).map { p ->
            val raw = valueOverrides[p.id] ?: Template.render(p.value, vars)
            val type = p.type.ifBlank { settings.propertyTypes[p.name] ?: inferType(raw) }
            BuiltProperty(p, type, raw)
        }
        val content = Template.render(template.noteContentFormat, vars)
        val name = Template.safeName(nameOverride ?: Template.render(template.noteNameFormat, vars)).ifBlank { video.meta.id }
        val folder = (folderOverride ?: Template.render(template.path, vars)).trim().trim('/')
        // The vault chosen in Settings wins. A vault name inside an imported template is only a fallback.
        val vault = vaultOverride ?: settings.vault.ifBlank { template.vault }
        val md = buildString {
            if (props.isNotEmpty()) {
                append("---\n")
                props.forEach { append(yaml(it)) }
                append("---\n\n")
            }
            append(content)
        }
        return BuiltNote(vault, folder, name, template.behavior, props, content, md)
    }

    /** Obsidian's Automatic: the value decides the type. */
    fun inferType(value: String): String {
        val t = value.trim()
        return when {
            t.equals("true", true) || t.equals("false", true) -> "checkbox"
            Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(t) -> "date"
            Regex("^\\d{4}-\\d{2}-\\d{2}T.*").matches(t) && Template.parseDate(t) != null -> "datetime"
            Regex("^-?\\d+(\\.\\d+)?$").matches(t) -> "number"
            else -> "text"
        }
    }

    // ---------- YAML properties ----------

    private fun quote(s: String) =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace(Regex("\\s*\\n\\s*"), " ").trim() + "\""

    fun splitList(value: String): List<String> = NoteLists.split(value)

    fun yaml(p: BuiltProperty): String {
        val key = if (Regex("^[A-Za-z0-9_ -]+$").matches(p.property.name)) p.property.name else quote(p.property.name)
        val v = p.value.trim()
        return when (p.type) {
            "multitext" -> {
                val items = splitList(v)
                if (items.isEmpty()) "$key:\n" else "$key:\n" + items.joinToString("") { "  - ${quote(it)}\n" }
            }
            "date" -> {
                val f = Template.formatDate(v, "YYYY-MM-DD")
                if (v.isEmpty()) "$key:\n" else "$key: ${f ?: v}\n"
            }
            "datetime" -> {
                val f = Template.formatDate(v, "YYYY-MM-DD[T]HH:mm:ss")
                if (v.isEmpty()) "$key:\n" else "$key: ${f ?: v}\n"
            }
            "number" -> if (v.toDoubleOrNull() != null) "$key: $v\n" else "$key:\n"
            "checkbox" -> "$key: ${v.equals("true", true) || v == "1" || v.equals("yes", true)}\n"
            else -> if (v.isEmpty()) "$key:\n" else "$key: ${quote(v)}\n"
        }
    }
}
