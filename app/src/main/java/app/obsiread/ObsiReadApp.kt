package app.obsiread

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import android.app.Application
import app.obsiread.data.Article
import app.obsiread.data.Clipper
import app.obsiread.data.Library
import app.obsi.core.data.Playlists
import app.obsi.core.data.PlayQueue
import app.obsi.core.data.SettingsStore
import app.obsi.core.data.VaultStore
import app.obsi.core.obsidian.ClipperImport
import app.obsi.core.update.SelfUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ObsiReadApp : Application(), coil3.SingletonImageLoader.Factory, app.obsi.core.ObsiHost {
    /**
     * Pictures are downloaded with the app's own name. Some sites, such as Wikipedia, refuse a download
     * that does not say who is asking.
     */
    override fun newImageLoader(context: coil3.PlatformContext): coil3.ImageLoader {
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "ObsiRead/" + BuildConfig.VERSION_NAME + " (https://github.com/Virus7976/obsiread)").build())
        }.build()
        return coil3.ImageLoader.Builder(context)
            .components { add(coil3.network.okhttp.OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .build()
    }

    override lateinit var settings: SettingsStore
        private set
    lateinit var library: Library
        private set
    override lateinit var updates: SelfUpdate
        private set
    override lateinit var vaults: VaultStore
        private set
    lateinit var playlists: Playlists
        private set
    lateinit var queue: PlayQueue
        private set
    lateinit var reviews: app.obsiread.data.Reviews
        private set

    override val recentTags: kotlinx.coroutines.flow.StateFlow<List<String>> by lazy {
        settings.state.map { it.recentTags }.stateIn(appScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, settings.value.recentTags)
    }

    /** An article another Obsi app asked to open, and where in it. The app opens it and clears it. */
    val openItem = MutableStateFlow<Pair<String, Double>?>(null)

    val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)

    /** A highlight picked in search: the reader scrolls to it when it opens. Article id and word. */
    val pendingSeek = MutableStateFlow<Pair<String, Double>?>(null)

    /** Set when the review reminder was tapped. The app opens Review and clears it. */
    val openReview = MutableStateFlow(false)

    /** Pages being read right now, by address. The library shows them as rows that are still loading. */
    val clipping = MutableStateFlow<Map<String, String>>(emptyMap())

    override fun onCreate() {
        super.onCreate()
        app.obsi.core.Brand.init(app.obsi.core.ObsiApp.Read)
        app.obsi.core.share.SharedHighlights.source = ::sharedHighlights
        app.obsi.core.share.SharedSetup.source = { app.obsi.core.share.SharedSetup.of(settings.value) }
        app.obsi.core.share.SharedReview.due = { reviews.due(library.articles.value).map { shared(it.video, it.highlight) } }
        app.obsi.core.share.SharedReview.act = { key, action -> reviews.act(key, action) }
        settings = SettingsStore(this)
        vaults = VaultStore(this) { settings.value.vaultFolders }
        updates = SelfUpdate(this, settings)
        library = Library(this)
        reviews = app.obsiread.data.Reviews(this)
        playlists = Playlists(this)
        queue = PlayQueue(settings)
        appScope.launch {
            settings.state.map { it.reviewReminder to it.reviewHour }.distinctUntilChanged().collect { (on, hour) ->
                ReviewReminder.schedule(this@ObsiReadApp, on, hour)
            }
        }
        seedFromBundledClipperSettings()
        updates.checkIfDue()
    }

    /**
     * Reads the page at [url] and puts it in the library. A page that is already there is not read
     * again. Returns the article id, or throws with a message that can be shown.
     */
    suspend fun addFromUrl(url: String): String {
        val id = Clipper.idFor(url)
        if (library.get(id) != null) return id
        clipping.update { it + (url to "Reading the page…") }
        try {
            val found = app.obsiread.web.WebReader(this).read(url)
            val clipped = withContext(Dispatchers.Default) { Clipper.fromDefuddle(found, url) }
            // The page may name another address as its real one, and that copy may be saved already.
            library.get(clipped.meta.id)?.let { return it.meta.id }
            library.saveBody(clipped.meta.id, clipped.html)
            library.upsert(Article(meta = clipped.meta, addedAt = System.currentTimeMillis()))
            return clipped.meta.id
        } finally {
            clipping.update { it - url }
        }
    }

    /** Saves a page Defuddle already read, from the app's own browser. Returns the article id. */
    suspend fun addFound(found: String, url: String): String {
        val clipped = withContext(Dispatchers.Default) { Clipper.fromDefuddle(found, url) }
        library.get(clipped.meta.id)?.let {
            // Read again by hand: keep the highlights, take the new text.
            library.saveBody(it.meta.id, clipped.html)
            return it.meta.id
        }
        library.saveBody(clipped.meta.id, clipped.html)
        library.upsert(Article(meta = clipped.meta, addedAt = System.currentTimeMillis()))
        return clipped.meta.id
    }

    /** Reads the page again, for an article that changed. Highlights stay by their words. */
    suspend fun refresh(id: String) {
        val a = library.get(id) ?: return
        val found = app.obsiread.web.WebReader(this).read(a.meta.url)
        val clipped = withContext(Dispatchers.Default) { Clipper.fromDefuddle(found, a.meta.url) }
        library.saveBody(id, clipped.html)
        library.edit(id) { it.copy(meta = clipped.meta.copy(id = id)) }
    }

    /** Takes an article off this phone: queue, playlists and library. */
    fun removeVideo(id: String) {
        queue.remove(id)
        playlists.all.value.filter { id in it.videoIds }.forEach { playlists.removeVideo(it.id, id) }
        library.remove(id)
    }

    fun setDone(id: String, done: Boolean) {
        if (done) { library.edit(id) { if (it.done) it else it.copy(doneAt = System.currentTimeMillis()) }; queue.remove(id) }
        else library.edit(id) { it.copy(doneAt = 0L) }
    }

    override fun sampleVariables(s: app.obsi.core.data.AppSettings): Map<String, String> {
        val v = library.articles.value.maxByOrNull { maxOf(it.playedAt, it.updatedAt) } ?: return emptyMap()
        val lines = library.loadBody(v.meta.id)?.let { app.obsiread.data.Markdown.from(it) }.orEmpty()
        return app.obsiread.obsidian.NoteBuilder.variables(v, lines, s)
    }

    @androidx.compose.runtime.Composable
    override fun IconPicker() = app.obsiread.ui.AppIconPicker()

    /** Every highlight, for the other Obsi apps to show. */
    private fun sharedHighlights(): List<app.obsi.core.share.SharedHighlight> =
        library.articles.value.flatMap { a -> a.highlights.map { h -> shared(a, h) } }

    private fun shared(a: Article, h: app.obsi.core.data.Highlight) = app.obsi.core.share.SharedHighlight(
        app = app.obsi.core.ObsiApp.Read, itemId = a.meta.id, itemTitle = a.meta.title, text = h.text, note = h.note,
        color = app.obsi.core.data.HighlightColors.getOrNull(h.color)?.ui ?: 0xFFF2C14E, at = h.startSec.toString(),
        place = a.meta.siteName, created = h.createdAt.takeIf { it > 0 } ?: a.addedAt, tags = h.tags, key = h.id,
    )

    /** First launch: load the clipper templates that ship inside the app. */
    private fun seedFromBundledClipperSettings() {
        if (settings.value.templates.isNotEmpty()) return
        runCatching {
            val text = assets.open("default_clipper_settings.json").bufferedReader().use { it.readText() }
            settings.update { ClipperImport.apply(text, it) }
        }
    }
}
