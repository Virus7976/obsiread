package app.obsiread.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.io.IOException
import kotlin.coroutines.resume

/**
 * Runs Defuddle, the page reader Obsidian Web Clipper uses, on a page a real browser has loaded.
 * The browser is the phone's own WebView, so a page that builds itself with scripts is read the way
 * you see it, and a site you signed in to in the app's browser knows you here too.
 */
object Defuddle {
    @Volatile private var script: String? = null

    private fun script(context: Context): String =
        script ?: context.assets.open("defuddle.js").bufferedReader().use { it.readText() }.also { script = it }

    /** Reads the page [view] shows now. Returns the JSON Defuddle gave, as text. Call on the main thread. */
    suspend fun run(view: WebView): String {
        val ctx = view.context
        expandComments(view)
        val loaded = eval(view, "typeof window.Defuddle === 'function'")
        if (loaded != "true") eval(view, script(ctx) + "\n;'ok'")
        // Defuddle works on a copy of the page, so the page you see is left as it is.
        val raw = eval(
            view,
            """
            (function () {
              try {
                // Hacker News dropped the label on its name links. Put it back so each comment keeps its author.
                document.querySelectorAll('.comhead > a[href^="user?id="]:not(.hnuser)').forEach(function (a) { a.classList.add('hnuser'); });
                var r = new window.Defuddle(document, { url: location.href }).parse();
                return JSON.stringify({
                  ok: true, url: location.href, content: r.content || '', title: r.title || '', author: r.author || '',
                  published: r.published || '', image: r.image || '', site: r.site || '', description: r.description || '',
                  domain: r.domain || '', language: r.language || document.documentElement.lang || '', wordCount: r.wordCount || 0
                });
              } catch (e) {
                return JSON.stringify({ ok: false, error: String(e && e.message || e) });
              }
            })()
            """.trimIndent(),
        )
        // The answer comes back as a quoted string. Unquote it once to get the JSON.
        return runCatching { Json.decodeFromString<String>(raw) }.getOrNull() ?: throw IOException("The page could not be read.")
    }

    /**
     * Many pages show part of themselves and load the rest as you scroll, or when you tap "more comments"
     * or "load more replies". This does the same in the page, the way you would by hand, until nothing new
     * comes: forums, comment threads, Q&A sites and long pages alike. Only buttons about comments, replies,
     * answers or posts in a thread are tapped. Links are never tapped, so it never leaves the page.
     */
    private suspend fun expandComments(view: WebView) {
        val started = eval(
            view,
            """
            (function () {
              window.__orDone = false;
              var want = /\b(more|load|show|view|see|expand|older|all)\b.{0,24}\b(comments?|repl(y|ies)|answers?|responses?|messages?|posts?|threads?|hidden items?)\b|\b\d+\s+more\b/i;
              var skip = /\b(articles?|stories|videos?|products?|news|sign|log ?in|subscribe|share|report|hide|collapse)\b/i;
              function buttons(root, out) {
                root.querySelectorAll('button, [role=button], summary').forEach(function (b) {
                  var t = (b.innerText || b.textContent || '').trim();
                  if (t.length > 0 && t.length < 60 && want.test(t) && !skip.test(t) && !b.closest('a[href]') && !b.disabled) out.push(b);
                });
                root.querySelectorAll('*').forEach(function (e) { if (e.shadowRoot) buttons(e.shadowRoot, out); });
                return out;
              }
              var tapped = new WeakSet();
              (async function () {
                var last = -1, same = 0, any = false;
                for (var i = 0; i < 40 && same < 2; i++) {
                  window.scrollTo(0, document.body.scrollHeight);
                  var bs = buttons(document, []).filter(function (b) { return !tapped.has(b); });
                  bs.slice(0, 25).forEach(function (b) { tapped.add(b); try { b.click(); } catch (e) {} });
                  await new Promise(function (r) { setTimeout(r, bs.length ? 1000 : 700); });
                  var n = document.body.innerText.length;
                  if (bs.length) any = true;
                  // A page that only grows as you scroll (a feed) gets five rounds, no more.
                  if (!any && i >= 4) break;
                  if (n === last && bs.length === 0) same++; else same = 0;
                  last = n;
                }
                window.scrollTo(0, 0);
                window.__orDone = true;
              })();
              return 'go';
            })()
            """.trimIndent(),
        )
        if (!started.contains("go")) return
        // Up to about 40 seconds for a very long thread.
        repeat(80) {
            delay(500)
            if (eval(view, "window.__orDone === true") == "true") return
        }
    }

    private suspend fun eval(view: WebView, js: String): String = suspendCancellableCoroutine { c ->
        view.evaluateJavascript(js) { c.resume(it ?: "null") }
    }
}

/**
 * Loads a page in a browser that is never shown, waits until its text stops changing, and reads it.
 */
class WebReader(private val context: Context) {
    class Blocked(message: String) : IOException(message)

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun read(url: String, timeoutMs: Long = 30_000): String = withContext(Dispatchers.Main) {
        val view = WebView(context.applicationContext)
        try {
            setUp(view)
            // A browser that is not on screen still lays the page out at a phone size.
            view.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(2200, android.view.View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, 1080, 2200)
            // Pictures are not needed to find the article, and ads never stop loading. So the page is read
            // as soon as its text stops changing, loaded or not.
            view.settings.blockNetworkImage = true
            val failed = arrayOfNulls<Throwable>(1)
            watch(view, failed)
            view.loadUrl(url)
            withTimeout(timeoutMs) { settle(view, failed) }
            Defuddle.run(view)
        } finally {
            view.stopLoading()
            view.destroy()
        }
    }

    /** Notes a page that fails to load at all, so the wait can stop early with the reason. */
    private fun watch(view: WebView, failed: Array<Throwable?>) {
        view.webViewClient = object : WebViewClient() {
            override fun onReceivedError(v: WebView, req: WebResourceRequest, err: WebResourceError) {
                if (req.isForMainFrame) failed[0] = IOException("The page did not load: ${err.description}.")
            }

            override fun onReceivedHttpError(v: WebView, req: WebResourceRequest, res: android.webkit.WebResourceResponse) {
                if (req.isForMainFrame && res.statusCode == 404) failed[0] = IOException("This page does not exist.")
            }

            override fun onPageStarted(v: WebView, u: String?, favicon: Bitmap?) {}
        }
    }

    /** Many pages add their text after loading. Wait until the amount of text stays the same. */
    private suspend fun settle(view: WebView, failed: Array<Throwable?>) {
        var last = -1
        var same = 0
        repeat(40) {
            delay(500)
            failed[0]?.let { throw it }
            val now = suspendCancellableCoroutine<String> { c ->
                view.evaluateJavascript("document.body ? document.body.innerText.length : 0") { c.resume(it ?: "0") }
            }.toIntOrNull() ?: 0
            if (now > 400 && now == last) same++ else same = 0
            last = now
            if (same >= 2) return
        }
    }

    companion object {
        @SuppressLint("SetJavaScriptEnabled")
        fun setUp(view: WebView) {
            view.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadsImagesAutomatically = true
                mediaPlaybackRequiresUserGesture = true
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        }
    }
}
