package app.obsiread.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Reads the page out loud with the phone's own voice, one paragraph after another.
 * [onPara] gets the number of each paragraph as it starts, so the page can follow along.
 */
class ReadAloud(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var inited = false
    private var ok = false
    private var pending: (() -> Unit)? = null
    private var lastId = ""

    var speaking by mutableStateOf(false)
        private set
    /** True when the phone has no voice to read with. */
    var failed by mutableStateOf(false)
        private set
    var onPara: (Int) -> Unit = {}

    private val tts = TextToSpeech(context.applicationContext) { status ->
        main.post {
            inited = true
            ok = status == TextToSpeech.SUCCESS
            pending?.invoke()
            pending = null
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) {
                val i = id.substringBefore(':').toIntOrNull() ?: return
                main.post { if (speaking) onPara(i) }
            }
            override fun onDone(id: String) { if (id == lastId) main.post { speaking = false } }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String) { main.post { speaking = false } }
            override fun onError(id: String, errorCode: Int) { main.post { speaking = false } }
        })
    }

    /** Reads [paras] from number [from] to the end, at [rate] (1 is normal speed). */
    fun start(paras: List<String>, from: Int, rate: Float) {
        val go = go@{
            if (!ok) { failed = true; return@go }
            failed = false
            tts.stop()
            tts.setSpeechRate(rate)
            var any = false
            for (i in from.coerceAtLeast(0)..paras.lastIndex) {
                chunks(paras[i]).forEachIndexed { k, t ->
                    lastId = "$i:$k"
                    tts.speak(t, TextToSpeech.QUEUE_ADD, null, lastId)
                    any = true
                }
            }
            speaking = any
        }
        if (inited) go() else pending = go
    }

    fun stop() {
        pending = null
        speaking = false
        tts.stop()
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    companion object {
        /** Splits [text] into pieces short enough for the voice, at the end of a sentence where it can. */
        fun chunks(text: String, max: Int = 3800): List<String> {
            val t = text.replace(Regex("\\s+"), " ").trim()
            if (t.isEmpty()) return emptyList()
            val out = ArrayList<String>()
            var rest = t
            while (rest.length > max) {
                val cut = rest.lastIndexOfAny(charArrayOf('.', '!', '?'), max - 1).takeIf { it > max / 2 }
                    ?: rest.lastIndexOf(' ', max - 1).takeIf { it > 0 } ?: (max - 1)
                out += rest.substring(0, cut + 1).trim()
                rest = rest.substring(cut + 1).trim()
            }
            if (rest.isNotEmpty()) out += rest
            return out
        }
    }
}
