package app.obsiread.data

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class Library(private val context: Context) :
    JsonFileStore<List<Article>>(File(context.filesDir, "library.json")) {
    private val _state = MutableStateFlow(readOrNull().orEmpty())
    val articles: StateFlow<List<Article>> = _state.asStateFlow()

    override fun encode(value: List<Article>) = storeJson.encodeToString(ListSerializer(Article.serializer()), value)
    override fun decode(text: String) = storeJson.decodeFromString(ListSerializer(Article.serializer()), text)

    fun get(id: String): Article? = _state.value.firstOrNull { it.meta.id == id }

    fun upsert(video: Article) {
        _state.update { list ->
            val without = list.filterNot { it.meta.id == video.meta.id }
            listOf(video.copy(updatedAt = System.currentTimeMillis())) + without
        }
        persist(_state.value)
    }

    /** Change one video in place without moving it to the top. */
    fun edit(id: String, block: (Article) -> Article) {
        _state.update { list -> list.map { if (it.meta.id == id) block(it) else it } }
        persist(_state.value)
    }

    fun remove(id: String) {
        _state.update { list -> list.filterNot { it.meta.id == id } }
        persist(_state.value)
        File(bodyDir(), "$id.html").delete()
        File(bodyDir(), "$id.html.bak").delete()
    }

    private fun bodyDir() = File(context.filesDir, "articles").also { it.mkdirs() }

    /** The cleaned article, as HTML. It is kept apart from the list so the list stays small. */
    fun saveBody(id: String, html: String) {
        runCatching { StoreFiles.write(File(bodyDir(), "$id.html"), File(bodyDir(), "$id.html.bak"), html) }
    }

    fun loadBody(id: String): String? = runCatching {
        StoreFiles.read(File(bodyDir(), "$id.html"), File(bodyDir(), "$id.html.bak")) { it }
    }.getOrNull()
}
