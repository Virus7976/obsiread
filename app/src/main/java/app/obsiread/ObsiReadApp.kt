package app.obsiread

import android.app.Application
import app.obsiread.data.Article
import app.obsiread.data.Clipper
import app.obsiread.data.Library
import app.obsiread.data.Playlists
import app.obsiread.data.PlayQueue
import app.obsiread.data.SettingsStore
import app.obsiread.data.VaultStore
import app.obsiread.obsidian.ClipperImport
import app.obsiread.update.SelfUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ObsiReadApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var library: Library
        private set
    lateinit var updates: SelfUpdate
        private set
    lateinit var vaults: VaultStore
        private set
    lateinit var playlists: Playlists
        private set
    lateinit var queue: PlayQueue
        private set
    lateinit var reviews: app.obsiread.data.Reviews
        private set

    val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)

    /** A highlight picked in search: the reader scrolls to it when it opens. Article id and word. */
    val pendingSeek = MutableStateFlow<Pair<String, Double>?>(null)

    /** Set when the review reminder was tapped. The app opens Review and clears it. */
    val openReview = MutableStateFlow(false)

    /** Pages being read right now, by address. The library shows them as rows that are still loading. */
    val clipping = MutableStateFlow<Map<String, String>>(emptyMap())

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        vaults = VaultStore(this, settings)
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

    /** First launch: load the clipper templates that ship inside the app. */
    private fun seedFromBundledClipperSettings() {
        if (settings.value.templates.isNotEmpty()) return
        runCatching {
            val text = assets.open("default_clipper_settings.json").bufferedReader().use { it.readText() }
            settings.update { ClipperImport.apply(text, it) }
        }
    }
}
