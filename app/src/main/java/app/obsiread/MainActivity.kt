package app.obsiread

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.obsiread.data.Clipper
import app.obsiread.ui.AppRoot

class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf<String?>(null)
    private var routedAway = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ObsiReadApp
        // A restart (like a turn of the phone) must not send the same link again.
        if (savedInstanceState == null) incoming = linkFrom(intent)
        // Opened only to pass a link on: step out of the way.
        if (routedAway) { finish(); return }
        openFrom(intent)
        if (intent?.getBooleanExtra(ReviewReminder.EXTRA_REVIEW, false) == true) app.openReview.value = true
        setContent {
            AppRoot(app, incoming) { incoming = null }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(ReviewReminder.EXTRA_REVIEW, false)) (application as ObsiReadApp).openReview.value = true
        linkFrom(intent)?.let { incoming = it }
        openFrom(intent)
    }

    /** A web address shared from a browser or any other app. Shared text often has the title first. */
    private fun linkFrom(intent: Intent?): String? {
        intent ?: return null
        val text = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return null
        // A video or Bible link goes to the app that reads it.
        if (app.obsi.core.link.Links.route(this, intent, text)) {
            routedAway = true
            return null
        }
        return Clipper.findUrl(text)
    }

    /** Another Obsi app asked to open one of our articles, at a highlight. */
    private fun openFrom(intent: Intent?) {
        val id = intent?.getStringExtra(app.obsi.core.link.Links.EXTRA_OPEN_ID) ?: return
        val at = intent.getStringExtra(app.obsi.core.link.Links.EXTRA_OPEN_AT)?.toDoubleOrNull() ?: -1.0
        intent.removeExtra(app.obsi.core.link.Links.EXTRA_OPEN_ID)
        (application as ObsiReadApp).openItem.value = id to at
    }
}
