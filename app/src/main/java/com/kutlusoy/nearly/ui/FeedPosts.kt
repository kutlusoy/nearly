package com.kutlusoy.nearly.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kutlusoy.nearly.R
import com.kutlusoy.nearly.feed.FeedPost
import com.kutlusoy.nearly.feed.PostState
import com.kutlusoy.nearly.feed.StoryGroup
import com.kutlusoy.nearly.nostr.PostCodec
import com.kutlusoy.nearly.nostr.StoryCodec
import com.kutlusoy.nearly.ui.theme.NearlyColors

// What the feed can do. A screen without a receiver does nothing.
data class FeedActions(
    val like: (String) -> Unit = {},
    val unlike: (String) -> Unit = {},
    val delete: (String) -> Unit = {},
    val retry: (String) -> Unit = {},
    val storySeen: (String) -> Unit = {},
)

@Composable
fun StoriesRow(groups: List<StoryGroup>, names: (StoryGroup) -> String, myName: String, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val mine = groups.firstOrNull { it.mine }
    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "mine") {
            Column(Modifier.width(72.dp).testTag("story-mine"), horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    Box(Modifier.clip(CircleShape).clickable { if (mine != null) onOpen(mine.author) else onAdd() }.testTag("story-open-mine")) { StoryRing(myName, mine?.allSeen ?: true, mine != null) }
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(22.dp).background(MaterialTheme.colorScheme.primary, CircleShape).clickable(onClick = onAdd).testTag("story-add"),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.feed_story_add), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp)) }
                }
                Text(stringResource(R.string.feed_story_yours), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
        items(groups.filter { !it.mine }, key = { it.author }) { group ->
            Column(Modifier.width(72.dp).clickable { onOpen(group.author) }.testTag("story-${group.author.take(8)}"), horizontalAlignment = Alignment.CenterHorizontally) {
                StoryRing(names(group), group.allSeen, true)
                Text(names(group), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun StoryRing(name: String, seen: Boolean, has: Boolean) {
    val color = if (has && !seen) NearlyColors.Rose else MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.border(if (has && !seen) 3.dp else 1.dp, color, CircleShape).padding(4.dp)) { Avatar(name, size = 56) }
}

@Composable
fun StoryViewer(group: StoryGroup, name: String, onSeen: (String) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var index by remember(group.author) { mutableStateOf(group.stories.indexOfFirst { false }.coerceAtLeast(0)) }
    val story = group.stories.getOrNull(index.coerceIn(0, group.stories.lastIndex)) ?: return onClose()
    LaunchedEffect(story.id) { onSeen(story.id) }
    val now = System.currentTimeMillis() / 1000
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("story-viewer")) {
        val photo = story.photo
        if (photo != null) {
            RemotePhoto(photo, Modifier.fillMaxSize(), ContentScale.Fit)
        } else {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(NearlyColors.Rose, NearlyColors.RoseSoft))), contentAlignment = Alignment.Center) {
                Text(story.text, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp).testTag("story-text"))
            }
        }
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight().clickable { index = (index - 1).coerceAtLeast(0) }.testTag("story-prev"))
            Box(Modifier.weight(1f).fillMaxHeight().clickable { if (index < group.stories.lastIndex) index++ else onClose() }.testTag("story-next"))
        }
        Column(Modifier.fillMaxWidth().safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                group.stories.forEachIndexed { i, _ -> Box(Modifier.weight(1f).height(3.dp).background(Color.White.copy(alpha = if (i <= index) 0.95f else 0.35f), CircleShape)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(timeAgo(((now - story.createdAt) / 60).toInt().coerceAtLeast(0)), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = onClose, modifier = Modifier.testTag("story-close")) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.photo_close), tint = Color.White) }
            }
        }
        if (photo != null && story.text.isNotBlank()) {
            Text(story.text, color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(24.dp))
        }
    }
}

@Composable
fun PostCard(post: FeedPost, name: String, now: Long, actions: FeedActions, following: Boolean, onFollow: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.feed_post_delete_title)) },
            text = { Text(stringResource(R.string.feed_post_delete_help)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; actions.delete(post.id) }, modifier = Modifier.testTag("post-delete-confirm")) { Text(stringResource(R.string.requests_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.newchat_cancel)) } },
        )
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("post-${post.id.take(8)}")) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(name, size = 36)
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp).weight(1f))
            if (post.mine) {
                IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("post-delete-${post.id.take(8)}")) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.requests_delete)) }
            } else if (!following) {
                TextButton(onClick = onFollow, modifier = Modifier.testTag("follow-post-${post.author.take(8)}")) { Text(stringResource(R.string.feed_follow)) }
            }
        }
        if (post.photos.isNotEmpty()) {
            val pager = rememberPagerState(pageCount = { post.photos.size })
            Box(Modifier.fillMaxWidth().aspectRatio(1f).testTag("post-photos-${post.id.take(8)}")) {
                HorizontalPager(pager, Modifier.fillMaxSize()) { page -> RemotePhoto(post.photos[page], Modifier.fillMaxSize()) }
                if (post.photos.size > 1) {
                    Text("${pager.currentPage + 1}/${post.photos.size}", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
        }
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (post.liked) actions.unlike(post.id) else actions.like(post.id) }, modifier = Modifier.testTag("like-${post.id.take(8)}")) {
                Icon(
                    if (post.liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(R.string.swipe_like),
                    tint = if (post.liked) NearlyColors.Rose else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (post.mine && post.likes > 0) Text(pluralStringResource(R.plurals.feed_likes, post.likes, post.likes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("post-likes-${post.id.take(8)}"))
        }
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (post.text.isNotBlank()) Row {
                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text(post.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("post-text-${post.id.take(8)}"))
            }
            when (post.state) {
                PostState.Sending -> Text(stringResource(R.string.message_sending), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PostState.Failed -> Text(stringResource(R.string.feed_post_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { actions.retry(post.id) }.testTag("post-retry-${post.id.take(8)}"))
                PostState.Sent -> Text(timeAgo(((now - post.at) / 60).toInt().coerceAtLeast(0)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun timeAgo(minutes: Int): String = when {
    minutes < 60 -> stringResource(R.string.feed_minutes, minutes)
    minutes < 1440 -> stringResource(R.string.feed_hours, minutes / 60)
    else -> stringResource(R.string.feed_days, minutes / 1440)
}

// One screen for a post and for a story. A story has one photo at most and a text of 140 characters.
@Composable
fun ComposerScreen(busy: Boolean, note: ComposeNote?, onBack: () -> Unit, onSubmit: (String, List<android.net.Uri>, Boolean) -> Unit) {
    BackHandler(onBack = onBack)
    var text by remember { mutableStateOf("") }
    var story by remember { mutableStateOf(false) }
    var photos by remember { mutableStateOf(listOf<android.net.Uri>()) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(PostCodec.MAX_PHOTOS)) { picked ->
        photos = (photos + picked).distinct().take(PostCodec.MAX_PHOTOS)
    }
    val limit = if (story) StoryCodec.MAX_TEXT else POST_TEXT_MAX
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back)) }
                Text(stringResource(R.string.feed_compose), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Button(
                    onClick = { onSubmit(text.take(limit), if (story) photos.take(1) else photos, story) },
                    enabled = !busy && (text.isNotBlank() || photos.isNotEmpty()),
                    modifier = Modifier.padding(end = 12.dp).testTag("composer-post"),
                ) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.feed_compose_post)) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    text, { text = it.take(limit) },
                    label = { Text(stringResource(R.string.feed_compose_hint)) },
                    supportingText = { Text("${text.length}/$limit") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("composer-text"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { picker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.testTag("composer-photos")) { Text(stringResource(R.string.feed_compose_photos)) }
                    FilterChip(selected = story, onClick = { story = !story; text = text.take(if (story) StoryCodec.MAX_TEXT else POST_TEXT_MAX) }, label = { Text(stringResource(R.string.feed_compose_story)) }, modifier = Modifier.testTag("composer-story"))
                }
                if (photos.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(pluralStringResource(R.plurals.feed_compose_photo_count, photos.size, photos.size), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).testTag("composer-count"))
                        TextButton(onClick = { photos = emptyList() }) { Text(stringResource(R.string.requests_delete)) }
                    }
                }
                Text(stringResource(if (story) R.string.feed_compose_story_help else R.string.feed_compose_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                note?.let { Text(stringResource(if (it == ComposeNote.UploadFailed) R.string.photo_failed else R.string.feed_compose_rejected), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("composer-note")) }
            }
        }
    }
}

const val POST_TEXT_MAX = 500
