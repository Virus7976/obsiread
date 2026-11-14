package app.obsiread

import app.obsiread.ui.ReadAloud
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAloudTest {
    @Test fun shortTextIsOnePiece() {
        assertEquals(listOf("Hello there. Bye."), ReadAloud.chunks("  Hello   there.\n Bye. "))
    }

    @Test fun emptyTextIsNoPieces() {
        assertEquals(emptyList<String>(), ReadAloud.chunks(" \n "))
    }

    @Test fun longTextSplitsAtSentences() {
        val s = "This is one sentence. ".repeat(40).trim()
        val parts = ReadAloud.chunks(s, max = 100)
        assertTrue(parts.all { it.length <= 100 })
        assertTrue(parts.all { it.endsWith(".") })
        assertEquals(s, parts.joinToString(" "))
    }

    @Test fun longWordStillSplits() {
        val s = "x".repeat(250)
        val parts = ReadAloud.chunks(s, max = 100)
        assertTrue(parts.all { it.length <= 100 })
        assertEquals(s, parts.joinToString(""))
    }
}
