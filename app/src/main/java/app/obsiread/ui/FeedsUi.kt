package app.obsiread.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.obsiread.data.Feed
import app.obsiread.data.FreshItem

/** How long ago, in short words: "now", "5m", "3h", "2d". */
fun ago(ms: Long, now: Long = System.currentTimeMillis()): String {
    if (ms <= 0) return ""
    val m = (now - ms).coerceAtLeast(0) / 60_000
    return when {
        m < 1 -> "now"
        m < 60 -> "${m}m"
        m < 60 * 24 -> "${m / 60}h"
        m < 60 * 24 * 60 -> "${m / (60 * 24)}d"
        else -> "${m / (60 * 24 * 30)}mo"
    }
}

/** One new post from a feed. A tap reads it into the library. The cross hides it. */
@Composable
fun FeedRow(f: FreshItem, onOpen: () -> Unit, onHide: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).heightIn(min = 56.dp).padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(f.item.title.ifBlank { f.item.link }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(f.feed.title, ago(f.item.published)).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onHide) { Icon(Icons.Rounded.Close, "Hide", Modifier.size(18.dp), tint = scheme.onSurfaceVariant) }
    }
}

/** A feed you follow, with a button to stop. */
@Composable
fun FollowedRow(feed: Feed, onUnfollow: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.RssFeed, null, Modifier.size(20.dp), tint = scheme.tertiary)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(feed.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${feed.items.size} posts" + (ago(feed.checkedAt).takeIf { it.isNotBlank() }?.let { " · read $it ago" }.orEmpty()).replace("read now ago", "read now"),
                style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.TextButton(onClick = onUnfollow) { Text("Stop", color = scheme.onSurfaceVariant) }
    }
}
