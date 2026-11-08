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
        val loaded = eval(view, "typeof window.Defuddle === 'function'")
        if (loaded != "true") eval(view, script(ctx) + "\n;'ok'")
        // Defuddle works on a copy of the page, so the page you see is left as it is.
        val raw = eval(
            view,
            """
            (function () {
              try {
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
