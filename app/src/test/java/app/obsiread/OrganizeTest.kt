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
import app.obsiread.data.ArticleMeta
import app.obsiread.ui.LibraryFilter
import app.obsiread.ui.sortedBy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class OrganizeTest {
    private fun v(id: String, title: String, author: String = "a", tags: List<String> = emptyList(), cat: String = "", added: Long = 0) =
        Article(ArticleMeta(id, title, author), tags = tags, category = cat, addedAt = added)

    @Test fun sortsByTitleAndAdded() {
        val list = listOf(v("1", "Zebra", added = 5), v("2", "apple", added = 9), v("3", "Mango", added = 1))
        assertEquals(listOf("2", "3", "1"), list.sortedBy("title").map { it.meta.id })
        assertEquals(listOf("2", "1", "3"), list.sortedBy("added").map { it.meta.id })
        assertEquals(listOf("1", "2", "3"), list.sortedBy("recent").map { it.meta.id })
    }

    @Test fun filtersByCategoryAndTagIgnoringCase() {
        val a = v("1", "x", tags = listOf("Focus"), cat = "Learning")
        assertTrue(LibraryFilter(category = "learning").matches(a))
        assertTrue(LibraryFilter(tag = "focus").matches(a))
        assertFalse(LibraryFilter(category = "learning", tag = "other").matches(a))
        assertTrue(LibraryFilter().matches(a))
    }
}
