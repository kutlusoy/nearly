package com.kutlusoy.nearly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kutlusoy.nearly.R
import com.kutlusoy.nearly.ui.theme.NearlyColors

// Built like the Instagram feed: people who are active on top, then the posts of the people you follow
@Composable
fun FeedScreen(posts: List<Post> = SamplePosts, people: List<Person> = SamplePeople) {
    var liked by rememberSaveable { mutableStateOf(listOf<String>()) }
    Column(Modifier.fillMaxSize()) {
        NearlyTopBar { AssistChip(onClick = {}, label = { Text(stringResource(R.string.swipe_preview)) }) }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(people, key = { it.id }) { person ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Avatar(person.name, size = 64)
                            Text(person.name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
            items(posts, key = { it.id }) { post ->
                val isLiked = post.id in liked
                PostCard(post, isLiked) { liked = if (isLiked) liked - post.id else liked + post.id }
            }
        }
    }
}

@Composable
private fun PostCard(post: Post, liked: Boolean, onLike: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(post.person.name, size = 36)
            Column(Modifier.padding(start = 10.dp)) {
                Text(post.person.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.swipe_distance, post.person.distanceKm),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).background(Brush.linearGradient(listOf(post.person.from, post.person.to))),
            contentAlignment = Alignment.Center,
        ) {
            Text(post.person.name.take(1), fontSize = 120.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.3f))
        }
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onLike, modifier = Modifier.testTag("like-${post.id}")) {
                Icon(
                    if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(R.string.swipe_like),
                    tint = if (liked) NearlyColors.Rose else MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = {}) { Icon(Icons.Outlined.Email, contentDescription = stringResource(R.string.feed_comment)) }
            IconButton(onClick = {}) { Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.feed_share)) }
        }
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(pluralStringResource(R.plurals.feed_likes, post.likes + if (liked) 1 else 0, post.likes + if (liked) 1 else 0), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row {
                Text(post.person.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text(post.caption, style = MaterialTheme.typography.bodyMedium)
            }
            Text(timeAgo(post.minutesAgo), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun timeAgo(minutes: Int): String = when {
    minutes < 60 -> stringResource(R.string.feed_minutes, minutes)
    minutes < 1440 -> stringResource(R.string.feed_hours, minutes / 60)
    else -> stringResource(R.string.feed_days, minutes / 1440)
}
