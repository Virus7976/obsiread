package app.obsiread.ui

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import app.obsiread.data.ArticleMeta
import coil3.compose.AsyncImage

/**
 * An article's picture. A page with no picture gets a quiet tile with the first letter of its site, so a
 * row never shows an empty hole.
 */
@Composable
fun ArticleThumb(
    model: ArticleMeta,
    contentDescription: String?,
    contentScale: ContentScale = ContentScale.Crop,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.background(scheme.surfaceContainerHigh).alpha(alpha), contentAlignment = Alignment.Center) {
        val letter = (model.siteName.ifBlank { model.host }.ifBlank { model.title }).trim().firstOrNull()?.uppercaseChar()?.toString() ?: ""
        Text(letter, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold), color = scheme.onSurfaceVariant.copy(alpha = 0.7f))
        if (model.thumb.isNotBlank()) AsyncImage(model.thumb, contentDescription, contentScale = contentScale, modifier = Modifier.fillMaxSize())
    }
}
