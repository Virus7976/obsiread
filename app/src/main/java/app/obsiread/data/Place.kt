package app.obsiread.data

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

/** Says where a word sits in an article, for labels on highlights. */
object Place {
    /** "12% in", or "Start" near the top. [total] is the number of words in the article. */
    fun label(word: Double, total: Int): String {
        if (total <= 0) return ""
        val pct = (word * 100 / total).toInt().coerceIn(0, 100)
        return if (pct < 1) "Start" else "$pct% in"
    }

    /** Seconds as 1:05 or 1:02:03. */
    fun time(sec: Double): String {
        val t = sec.toLong().coerceAtLeast(0)
        val h = t / 3600
        val m = (t % 3600) / 60
        val s = t % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
