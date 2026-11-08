package app.obsiread

import app.obsiread.data.Article
import app.obsiread.data.ArticleMeta
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoneTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun aLibraryFromBeforeThisFeatureLoadsAsNotDone() {
        val old = json.encodeToString(Article.serializer(), Article(meta = ArticleMeta("a", "t", "c")))
            .replace(Regex(",?\"doneAt\":\\d+"), "")
        assertFalse(old.contains("doneAt"))
        assertFalse(json.decodeFromString(Article.serializer(), old).done)
    }

    @Test
    fun doneKeepsYourPlaceSoItCanBeUndone() {
        val v = Article(meta = ArticleMeta("a", "t", "c", durationSec = 600), positionSec = 120.0, doneAt = 5L)
        assertTrue(v.done)
        assertEquals(120.0, v.resumeSec, 0.0)
    }
}
