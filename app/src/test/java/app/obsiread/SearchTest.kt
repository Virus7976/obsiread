package app.obsiread

import app.obsiread.data.Highlight
import app.obsiread.data.Article
import app.obsiread.data.ArticleMeta
import app.obsiread.ui.matchesQuery
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    @Test fun everyWordMustMatch() {
        assertTrue(matchesQuery("slow time", "How to Slow Down the Passing of Time"))
        assertFalse(matchesQuery("slow money", "How to Slow Down the Passing of Time"))
    }

    @Test fun emptyQueryMatchesAll() = assertTrue(matchesQuery("  ", "anything"))

    @Test fun searchesHighlightsAndTags() {
        val v = Article(
            ArticleMeta("abc12345678", "A talk", "Someone"),
            tags = listOf("productivity"),
            highlights = listOf(Highlight("h1", 0.0, 1.0, "the only thing that matters")),
        )
        assertTrue(v.matchesQuery("only thing"))
        assertTrue(v.matchesQuery("productivity someone"))
        assertFalse(v.matchesQuery("banana"))
    }
}
