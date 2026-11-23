package app.obsiread.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.obsi.core.speech.ReadAloud
import app.obsi.core.ui.ListenSettings
import app.obsi.core.ui.OfflineItem
import app.obsiread.ObsiReadApp

/** Settings, Read aloud: the voice, speed, and the audio kept for each saved article. */
@Composable
fun ListenSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as ObsiReadApp
    val engine = remember { ReadAloud.get(ctx) }
    val articles by app.library.articles.collectAsState()
    val items = remember(articles) {
        articles.sortedByDescending { it.addedAt }.map { a ->
            OfflineItem(a.meta.id, a.meta.title, a.meta.siteName) { app.listenDoc(a.meta.id) }
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ListenSettings(engine, items, onBack)
        }
    }
}
