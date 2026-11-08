package app.obsiread

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ObsiReadApp
        incoming = linkFrom(intent)
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
    }

    /** A web address shared from a browser or any other app. Shared text often has the title first. */
    private fun linkFrom(intent: Intent?): String? {
        intent ?: return null
        val text = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return null
        return Clipper.findUrl(text)
    }
}
