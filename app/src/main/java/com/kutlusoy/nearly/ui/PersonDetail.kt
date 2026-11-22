package com.kutlusoy.nearly.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kutlusoy.nearly.R
import com.kutlusoy.nearly.ui.theme.NearlyColors

// The whole profile, opened from the card before anyone decides: photos, about, what the person looks for, interests
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun PersonDetail(
    person: Person,
    onClose: () -> Unit,
    onAction: (SwipeDirection) -> Unit,
    followed: Boolean = false,
    onFollow: (() -> Unit)? = null,
    onMessage: (() -> Unit)? = null,
) {
    BackHandler(onBack = onClose)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                val pager = rememberPagerState(pageCount = { person.photos })
                Box(Modifier.fillMaxWidth().aspectRatio(0.85f).testTag("photos")) {
                    HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                        val (from, to) = person.photoColors(page)
                        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(from, to))), contentAlignment = Alignment.Center) {
                            val ref = person.photoRefs.getOrNull(page)
                            if (ref != null) RemotePhoto(ref, Modifier.fillMaxSize())
                            else Text(person.name.take(1), fontSize = 160.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.35f))
                        }
                    }
                    Row(Modifier.align(Alignment.TopCenter).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(person.photos) { i ->
                            Box(
                                Modifier.size(width = 36.dp, height = 4.dp)
                                    .background(Color.White.copy(alpha = if (i == pager.currentPage) 0.95f else 0.4f), CircleShape),
                            )
                        }
                    }
                    IconButton(onClick = onClose, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).background(NearlyColors.Rose, CircleShape)) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.detail_close), tint = Color.White)
                    }
                }
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(person.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("  ${person.age}", style = MaterialTheme.typography.headlineSmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.swipe_distance, person.distanceKm),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    Text(
                        if (person.activeDaysAgo == 0) stringResource(R.string.detail_active_today) else stringResource(R.string.detail_active_days, person.activeDaysAgo),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (onFollow != null || onMessage != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            onMessage?.let { androidx.compose.material3.Button(onClick = it, modifier = Modifier.testTag("detail-message")) { Text(stringResource(R.string.contact_message)) } }
                            onFollow?.let {
                                androidx.compose.material3.OutlinedButton(onClick = it, modifier = Modifier.testTag("detail-follow")) {
                                    Text(stringResource(if (followed) R.string.feed_following else R.string.feed_follow))
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Section(R.string.detail_looking_for)
                    AssistChip(onClick = {}, label = { Text(stringResource(person.lookingFor.label())) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Section(R.string.detail_about)
                    Text(person.bio, style = MaterialTheme.typography.bodyLarge)
                    if (person.genderKnown) Text(stringResource(person.gender.singular()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    person.job?.let { Text(stringResource(R.string.detail_job, it), style = MaterialTheme.typography.bodyMedium) }
                    person.city?.let { Text(stringResource(R.string.detail_lives_in, it), style = MaterialTheme.typography.bodyMedium) }
                    if (person.languages.isNotEmpty()) Text(stringResource(R.string.detail_speaks, person.languages.joinToString(", ")), style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Section(R.string.detail_interests)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        person.interests.forEach { SuggestionChip(onClick = {}, label = { Text(it) }) }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                ActionButton(Icons.Filled.Close, stringResource(R.string.swipe_later), NearlyColors.Teal, 60) { onAction(SwipeDirection.Later) }
                ActionButton(Icons.Filled.KeyboardArrowUp, stringResource(R.string.swipe_skip), MaterialTheme.colorScheme.onSurfaceVariant, 46) { onAction(SwipeDirection.Skip) }
                ActionButton(Icons.Filled.Favorite, stringResource(R.string.swipe_like), NearlyColors.Rose, 60) { onAction(SwipeDirection.Like) }
            }
        }
    }
}

@Composable
private fun Section(title: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}
