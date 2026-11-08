package app.obsiread

import app.obsiread.data.AppSettings
import app.obsiread.data.Highlight
import app.obsiread.data.Article
import app.obsiread.data.ArticleMeta
import app.obsiread.obsidian.BuiltNote
import app.obsiread.obsidian.Updater
import app.obsiread.ui.sortedBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefinementTest {
    private fun video(position: Double, duration: Int) =
        Article(ArticleMeta("abc12345678", "A talk", "A speaker", durationSec = duration), positionSec = position)

    @Test fun aVideoInTheMiddleResumesWhereItWas() = assertEquals(120.0, video(120.0, 600).resumeSec, 0.0)

    @Test fun aFinishedVideoStartsOver() {
        assertEquals(0.0, video(595.0, 600).resumeSec, 0.0)
        assertEquals(0.0, video(600.0, 600).resumeSec, 0.0)
    }

    @Test fun anUnknownLengthKeepsThePlace() = assertEquals(120.0, video(120.0, 0).resumeSec, 0.0)

    @Test fun tappingTheSameSortReversesTheList() {
        val a = video(0.0, 0).copy(meta = ArticleMeta("a", "Apple", "x"))
        val b = video(0.0, 0).copy(meta = ArticleMeta("b", "Banana", "x"))
        val list = listOf(b, a)
        assertEquals(listOf("Apple", "Banana"), list.sortedBy("title").map { it.meta.title })
        assertEquals(listOf("Banana", "Apple"), list.sortedBy("title", reversed = true).map { it.meta.title })
    }

    // ---------- adding to a note that already exists ----------

    private val note = BuiltNote("v", "", "n", "create", emptyList(), "", "")
    private val quote = "power over your mind"
    private val old = "---\ntitle: A talk\n---\n\n> $quote\n"

    private fun merge(existing: String, vararg h: Highlight) =
        Updater.merge(existing, note, video(0.0, 600).copy(highlights = h.toList()), AppSettings())

    @Test fun aTimedNoteReachesAnExistingNote() {
        val m = merge(old, Highlight("t", 30.0, 30.0, "", "the bass drops here"))
        assertEquals(1, m.addedHighlights)
        assertTrue(m.text, m.text.contains("the bass drops here"))
        assertFalse("a second run adds nothing", merge(m.text, Highlight("t", 30.0, 30.0, "", "the bass drops here")).changed)
    }

    @Test fun aNoteWrittenAfterTheClipIsAddedWithoutRepeatingTheQuote() {
        val h = Highlight("q", 60.0, 70.0, quote, "my favourite line")
        val m = merge(old, h)
        assertEquals(1, m.addedHighlights)
        assertTrue(m.text, m.text.contains("my favourite line"))
        assertEquals(m.text, 1, Regex(quote).findAll(m.text).count())
        assertFalse("a second run adds nothing", merge(m.text, h).changed)
    }

    @Test fun aMarkWithNoWordsAddsNothing() = assertFalse(merge(old, Highlight("m", 5.0, 5.0, "", "")).changed)
}
