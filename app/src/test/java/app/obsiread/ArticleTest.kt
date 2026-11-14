package app.obsiread

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import app.obsiread.data.AppSettings
import app.obsiread.data.Article
import app.obsiread.data.ArticleDoc
import app.obsiread.data.Clipper
import app.obsi.core.data.Highlights
import app.obsiread.data.Markdown
import app.obsiread.obsidian.ClipperImport
import app.obsiread.obsidian.NoteBuilder
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleTest {
    // What Defuddle found on src/test/resources/article.html. Made with scripts/defuddle-fixture.mjs.
    private val clipped = Clipper.fromDefuddle(File("src/test/resources/article.json").readText(), "https://example.com/essays/slow-reading?utm_source=twitter#top")
    private val built = ArticleDoc.build(clipped.html, clipped.meta.title, "Ada Lovelace", clipped.meta.image)
    private val doc = built.doc
    private val settings = ClipperImport.apply(File("src/main/assets/default_clipper_settings.json").readText(), AppSettings())

    @Test
    fun readsTheArticleDetails() {
        val m = clipped.meta
        assertEquals("Why Slow Reading Wins", m.title)
        assertEquals("Ada Lovelace", m.author)
        assertEquals("The Quiet Page", m.site)
        assertEquals("https://example.com/img/hero.jpg", m.image)
        assertEquals("2026-10-02T09:30:00Z", m.published)
        assertEquals("en", m.lang)
        assertEquals("https://example.com/essays/slow-reading", m.url)
        assertEquals(Clipper.idFor("https://www.example.com/essays/slow-reading/?utm_campaign=x"), m.id)
        assertEquals(179, m.words)
    }

    @Test
    fun keepsTheArticleAndDropsTheRest() {
        val text = org.jsoup.Jsoup.parse(clipped.html).text()
        assertTrue(text.contains("Most of us read too fast."))
        assertTrue(text.contains("Write one line about it when you finish."))
        assertFalse(text.contains("Subscribe"))
        assertFalse(text.contains("Copyright 2026"))
        assertFalse(text.contains("Ten tips"))
        assertFalse(clipped.html.contains("<script"))
        // The lazy picture gets its real address, made whole.
        assertTrue(clipped.html.contains("https://example.com/img/desk.jpg"))
        assertFalse(clipped.html.contains("data:image"))
    }

    @Test
    fun aFailedReadSaysWhy() {
        val e = runCatching { Clipper.fromDefuddle("{\"ok\":false,\"error\":\"boom\"}", "https://e.com") }.exceptionOrNull()
        assertTrue(e?.message.orEmpty().contains("boom"))
        val short = runCatching { Clipper.fromDefuddle("{\"ok\":true,\"content\":\"<p>Hi</p>\"}", "https://e.com") }.exceptionOrNull()
        assertEquals("No article was found on this page.", short?.message)
    }

    @Test
    fun cleanAddressesMatch() {
        assertEquals("https://example.com/a?id=7", Clipper.canonical("https://WWW.Example.com/a/?utm_source=x&id=7&fbclid=abc#part"))
        assertEquals(Clipper.idFor("http://example.com/a"), Clipper.idFor("https://www.example.com/a/"))
        assertEquals("https://example.com/x", Clipper.findUrl("Look at this: https://example.com/x."))
    }

    @Test
    fun pageHasTitleWordsAndStyles() {
        println(doc.text)
        // Nothing is added: the page opens with the title, not the main picture.
        assertTrue(doc.text.startsWith("Why Slow Reading Wins"))
        // The title is on the page but is not a word, so highlights never start in it.
        val t = doc.text.indexOf("Why Slow Reading Wins")
        assertTrue(t >= 0)
        assertTrue(doc.words.none { it.charStart in t until t + 21 })
        // Each word is numbered in order, and the number is its "time".
        doc.words.forEachIndexed { i, w -> assertEquals(i.toDouble(), w.start, 0.0) }
        assertEquals("Most", doc.text.substring(doc.words[0].charStart, doc.words[0].charEnd))
        val kinds = built.styles.map { it.kind }.toSet()
        listOf("title", "byline", "heading", "link", "quote", "pre", "code", "em", "strong", "bullet").forEach { assertTrue(it, it in kinds) }
        val link = built.styles.first { it.kind == "link" }
        assertEquals("One study", doc.text.substring(link.range.first, link.range.last + 1))
        // Only the picture that is in the article.
        assertEquals(1, built.images.size)
        assertEquals("https://example.com/img/desk.jpg", built.images[0].second)
        // A selection snaps to whole words.
        val a = doc.text.indexOf("slowly") + 2
        val r = doc.wordsInSelection(a, a + 12)
        assertNotNull(r)
        assertTrue(doc.plainText(r!!.first, r.last).startsWith("slowly,"))
    }

    @Test
    fun markdownForObsidian() {
        val md = Markdown.from(clipped.html)
        println(md)
        assertTrue(md.contains("## How to start"))
        assertTrue(md.contains("- Pick one essay, not a whole book."))
        assertTrue(md, md.contains("> Reading is to the mind what exercise is to the body.\n>\n\nSome people"))
        assertTrue(md.contains("```\ngrep -i \"slow\" notes.md\nwc -l notes.md\n```"))
        assertTrue(md.contains("[One study](https://example.com/research?utm_source=feed&id=7)"))
        assertTrue(md.contains("![A desk with a book](https://example.com/img/desk.jpg)"))
        assertTrue(md.contains("*surprised*"))
        assertTrue(md.contains("**stays with you**"))
        assertTrue(md.contains("`notes.md`"))
    }

    @Test
    fun noteWithHighlightsLinksBackToTheSentence() {
        val i = doc.words.indexOfFirst { doc.text.substring(it.charStart, it.charEnd) == "Reading" }
        val hl = Highlights.apply(emptyList(), doc.words, doc::plainText, i, i + 10, 0)
        val article = Article(meta = clipped.meta, highlights = hl)
        val t = settings.templates.first { it.name == "Default" }
        val note = NoteBuilder.build(t, article, Markdown.from(clipped.html), settings.copy(highlightBehavior = "keep-content"))
        println(note.markdown)
        assertEquals("Why Slow Reading Wins", note.name)
        assertTrue(note.markdown.contains("source: \"https://example.com/essays/slow-reading\""))
        assertTrue(note.markdown.contains("author:\n  - \"[[Ada Lovelace]]\""))
        assertTrue(note.markdown.contains("published: 2026-10-02"))
        assertTrue(note.content.contains("## How to start"))
        val vars = NoteBuilder.variables(article, "", settings)
        assertTrue(vars.getValue("highlights"), vars.getValue("highlights").contains("#:~:text=Reading%20is%20to%20the,is%20to%20the%20body."))
        assertEquals("example.com", vars["domain"])
    }

    @Test
    fun quoteLinkCutsLongText() {
        val l = NoteBuilder.quoteLink("https://e.com/p#old", "one two three four five six seven eight nine ten")
        assertEquals("https://e.com/p#:~:text=one%20two%20three%20four,seven%20eight%20nine%20ten", l)
    }
}
