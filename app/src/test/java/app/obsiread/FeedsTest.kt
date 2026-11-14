package app.obsiread

import app.obsiread.data.Feed
import app.obsiread.data.FeedItem
import app.obsiread.data.FeedParser
import app.obsiread.data.FeedState
import app.obsiread.data.FeedStore
import app.obsiread.data.Clipper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedsTest {
    private val rss = """<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0"><channel>
  <title>Example Blog</title><link>https://example.com/</link>
  <item><title>First &amp; best</title><link>https://example.com/a</link>
    <pubDate>Tue, 10 Nov 2026 09:00:00 GMT</pubDate><description>&lt;p&gt;Hello &lt;b&gt;there&lt;/b&gt;&lt;/p&gt;</description></item>
  <item><title>Relative</title><link>/b</link><pubDate>Mon, 9 Nov 2026 09:00:00 +0000</pubDate></item>
  <item><title>No link</title></item>
</channel></rss>"""

    private val atom = """<?xml version="1.0" encoding="utf-8"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <title>Atom Site</title><link href="https://atom.example/" rel="alternate"/><link href="https://atom.example/feed" rel="self"/>
  <entry><title>Entry one</title><link href="https://atom.example/1"/><published>2026-11-12T08:30:00Z</published><summary>Short words</summary></entry>
  <entry><title>Entry two</title><link rel="alternate" href="/2"/><updated>2026-11-11T08:30:00+02:00</updated></entry>
</feed>"""

    @Test fun readsRss() {
        val p = FeedParser.parse(rss, "https://example.com/feed.xml")!!
        assertEquals("Example Blog", p.title)
        assertEquals(2, p.items.size)
        assertEquals("First & best", p.items[0].title)
        assertEquals("Hello there", p.items[0].summary)
        assertEquals("https://example.com/b", p.items[1].link)
        assertTrue(p.items[0].published > p.items[1].published)
        assertTrue(p.items[1].published > 0)
    }

    @Test fun readsAtom() {
        val p = FeedParser.parse(atom, "https://atom.example/feed")!!
        assertEquals("Atom Site", p.title)
        assertEquals("https://atom.example/", p.site)
        assertEquals(listOf("https://atom.example/1", "https://atom.example/2"), p.items.map { it.link })
        assertTrue(p.items.all { it.published > 0 })
    }

    @Test fun htmlIsNotAFeed() {
        assertNull(FeedParser.parse("<!doctype html><html><head></head><body>Hi</body></html>", "https://x.com/"))
        assertNull(FeedParser.parse("not xml at all", "https://x.com/"))
    }

    @Test fun findsTheFeedOfAPage() {
        val html = """<html><head><link rel="stylesheet" href="/s.css">
            <link rel="alternate" type="application/rss+xml" title="RSS" href="/feed.xml?a=1&amp;b=2"></head></html>"""
        assertEquals("https://site.com/feed.xml?a=1&b=2", FeedParser.discover(html, "https://site.com/post/1"))
        assertNull(FeedParser.discover("<html><head></head></html>", "https://site.com/"))
    }

    @Test fun freshSkipsHiddenAndSaved() {
        val f = Feed("https://f", "F", items = listOf(
            FeedItem("a", "https://e.com/a", 3), FeedItem("b", "https://e.com/b", 2), FeedItem("c", "https://e.com/c", 1),
        ))
        val s = FeedState(listOf(f), hidden = listOf("https://e.com/b"))
        val got = FeedStore.fresh(s, setOf(Clipper.idFor("https://e.com/c")))
        assertEquals(listOf("https://e.com/a"), got.map { it.item.link })
    }

    @Test fun badDateIsZero() {
        assertEquals(0L, FeedParser.date("yesterday"))
        assertEquals(0L, FeedParser.date(""))
    }
}
