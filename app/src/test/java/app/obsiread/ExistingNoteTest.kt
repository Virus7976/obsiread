package app.obsiread

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import app.obsi.core.data.AppSettings
import app.obsi.core.data.ClipTemplate
import app.obsiread.data.Article
import app.obsi.core.data.TemplateProperty
import app.obsiread.data.ArticleMeta
import app.obsiread.obsidian.NoteBuilder
import app.obsiread.obsidian.Updater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExistingNoteTest {
    private val note = "---\nBig Idea: \"old thought\"\ntags:\n  - clippings\n  - focus\nsource: \"https://x\"\nauthor: [A, B]\n---\n\nbody text\n"

    @Test fun readsValuesAndLists() {
        val v = Updater.readValues(note)
        assertEquals("old thought", v["big idea"])
        assertEquals("clippings,focus", v["tags"])
        assertEquals("https://x", v["source"])
        assertEquals("A,B", v["author"])
    }

    @Test fun anEditedBoxReplacesTheNoteValue() {
        val t = ClipTemplate(id = "t", name = "T", properties = listOf(TemplateProperty("p1", "Big Idea", "")))
        val video = Article(ArticleMeta("abc12345678", "T", "A"))
        val built = NoteBuilder.build(t, video, "", AppSettings(), mapOf("p1" to "new thought"))
        val merged = Updater.merge(note, built, video, AppSettings(), replace = setOf("p1"))
        assertTrue(merged.text.contains("Big Idea: \"new thought\""))
        assertTrue(!merged.text.contains("old thought"))
        assertTrue(merged.text.contains("body text"))
    }

    @Test fun withoutReplaceTheOldValueStays() {
        val t = ClipTemplate(id = "t", name = "T", properties = listOf(TemplateProperty("p1", "Big Idea", "")))
        val video = Article(ArticleMeta("abc12345678", "T", "A"))
        val built = NoteBuilder.build(t, video, "", AppSettings(), mapOf("p1" to "new thought"))
        val merged = Updater.merge(note, built, video, AppSettings())
        assertTrue(merged.text.contains("old thought"))
    }
}
