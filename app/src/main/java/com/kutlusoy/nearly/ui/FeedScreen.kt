package com.kutlusoy.nearly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kutlusoy.nearly.R
import com.kutlusoy.nearly.chat.ChatController
import com.kutlusoy.nearly.feed.FeedUiState

// Built like the Instagram feed: stories on top, people near you to follow, then the posts of the people you follow and of the user.
// The discovery settings do not apply here.
@Composable
fun FeedScreen(
    people: List<Person>,
    feed: FeedUiState = FeedUiState(),
    names: Map<String, String> = emptyMap(),
    myName: String = "",
    following: Set<String> = emptySet(),
    onFollow: (String) -> Unit = {},
    actions: FeedActions = FeedActions(),
    onCompose: () -> Unit = {},
) {
    var viewing by remember { mutableStateOf<String?>(null) }
    val nearby = remember(people, following) { nearbyPeople(people, following) }
    val nameOf = { author: String, mine: Boolean -> if (mine) myName else names[author] ?: ChatController.shortNpub(author) }
    viewing?.let { author ->
        val group = feed.stories.firstOrNull { it.author == author }
        if (group != null) {
            StoryViewer(group, nameOf(author, group.mine), actions.storySeen, onClose = { viewing = null })
            return
        }
        viewing = null
    }
    val now = System.currentTimeMillis() / 1000
    Column(Modifier.fillMaxSize()) {
        NearlyTopBar {
            IconButton(onClick = onCompose, modifier = Modifier.testTag("compose")) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.feed_compose)) }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                StoriesRow(feed.stories, { g -> nameOf(g.author, g.mine) }, myName, onOpen = { viewing = it }, onAdd = onCompose)
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
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
            if (feed.posts.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(32.dp).testTag("feed-empty"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.feed_empty_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.feed_empty_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        Button(onClick = onCompose) { Text(stringResource(R.string.feed_compose)) }
                    }
                }
            }
            items(feed.posts, key = { it.id }) { post ->
                PostCard(post, nameOf(post.author, post.mine), now, actions, following = post.author in following, onFollow = { onFollow(post.author) })
            }
        }
    }
}

@Composable
private fun NearbyCard(person: Person, onFollow: () -> Unit) {
    OutlinedCard(Modifier.width(148.dp).testTag("nearby-${person.id}")) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val first = person.photoRefs.firstOrNull()
            if (first != null) {
                RemotePhoto(first, Modifier.size(64.dp).clip(androidx.compose.foundation.shape.CircleShape))
            } else {
                Avatar(person.name, size = 64)
            }
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
