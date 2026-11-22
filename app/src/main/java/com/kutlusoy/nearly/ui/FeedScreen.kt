package com.kutlusoy.nearly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kutlusoy.nearly.R
import com.kutlusoy.nearly.ui.theme.NearlyColors

// Built like the Instagram feed: people near you to follow on top, then the posts of the people you follow and of the people nearby.
// The discovery settings do not apply here.
@Composable
fun FeedScreen(
    people: List<Person>,
    posts: List<Post> = emptyList(),
    following: Set<String> = emptySet(),
    onFollow: (String) -> Unit = {},
) {
    var liked by rememberSaveable { mutableStateOf(listOf<String>()) }
    val feed = remember(posts, following) { feedPosts(posts, following) }
    val nearby = remember(people, following) { nearbyPeople(people, following) }
    Column(Modifier.fillMaxSize()) {
        NearlyTopBar()
        LazyColumn(Modifier.fillMaxSize()) {
            if (nearby.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.feed_nearby),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(nearby, key = { it.id }) { person -> NearbyCard(person) { onFollow(person.id) } }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
            items(feed, key = { it.id }) { post ->
                val isLiked = post.id in liked
                PostCard(
                    post = post,
                    liked = isLiked,
                    following = post.person.id in following,
                    onFollow = { onFollow(post.person.id) },
                    onLike = { liked = if (isLiked) liked - post.id else liked + post.id },
                )
            }
        }
    }
}

@Composable
private fun NearbyCard(person: Person, onFollow: () -> Unit) {
    OutlinedCard(Modifier.width(148.dp).testTag("nearby-${person.id}")) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Avatar(person.name, size = 64)
            Text("${person.name}, ${person.age}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                stringResource(R.string.swipe_distance, person.distanceKm),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                person.bio,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Button(onClick = onFollow, modifier = Modifier.fillMaxWidth().testTag("follow-nearby-${person.id}")) { Text(stringResource(R.string.feed_follow)) }
        }
    }
}

@Composable
private fun PostCard(post: Post, liked: Boolean, following: Boolean, onFollow: () -> Unit, onLike: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(post.person.name, size = 36)
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(post.person.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.swipe_distance, post.person.distanceKm),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (following) {
                Text(
                    stringResource(R.string.feed_following),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp).testTag("following-${post.person.id}"),
                )
            } else {
                TextButton(onClick = onFollow, modifier = Modifier.testTag("follow-post-${post.person.id}")) { Text(stringResource(R.string.feed_follow)) }
            }
        }
        val (from, to) = post.person.photoColors(0)
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).background(Brush.linearGradient(listOf(from, to))),
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
            val count = post.likes + if (liked) 1 else 0
            Text(pluralStringResource(R.plurals.feed_likes, count, count), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
