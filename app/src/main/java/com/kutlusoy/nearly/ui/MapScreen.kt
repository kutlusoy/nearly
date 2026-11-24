package com.kutlusoy.nearly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kutlusoy.nearly.R
import com.kutlusoy.nearly.chat.ChatController
import com.kutlusoy.nearly.nostr.Geohash
import com.kutlusoy.nearly.presence.FriendDot
import com.kutlusoy.nearly.presence.PresenceAudience
import com.kutlusoy.nearly.presence.PresenceSettings
import com.kutlusoy.nearly.presence.PresenceUi

// What the map tab can do
data class MapActions(
    val start: (Long) -> Unit = {},
    val stop: () -> Unit = {},
    val explained: () -> Unit = {},
    val privacy: () -> Unit = {},
    val openProfile: (String) -> Unit = {},
    val allowLocation: () -> Unit = {},
)

val PRESENCE_DURATIONS = listOf(15 * 60L to R.string.map_for_15m, 3600L to R.string.map_for_1h, 4 * 3600L to R.string.map_for_4h)

// Seconds until midnight of the local day, at least one minute
fun secondsUntilMidnight(now: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Long {
    val moment = java.time.Instant.ofEpochSecond(now).atZone(zone)
    return java.time.Duration.between(moment, moment.toLocalDate().plusDays(1).atStartOfDay(zone)).seconds.coerceAtLeast(60)
}

// The friends who are active right now as a grid of cells, without a map picture: no tile server learns where the user looks
@Composable
fun MapScreen(
    presence: PresenceUi,
    settings: PresenceSettings,
    names: Map<String, String>,
    hasLocation: Boolean,
    actions: MapActions,
    now: Long = System.currentTimeMillis() / 1000,
) {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted -> if (granted) actions.allowLocation() }
    Column(Modifier.fillMaxSize()) {
        NearlyTopBar()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                settings.audience == PresenceAudience.Nobody && !presence.active -> Notice(R.string.map_nobody, R.string.map_nobody_help, R.string.map_open_privacy, actions.privacy, "map-nobody")
                !hasLocation -> Notice(R.string.map_location, R.string.map_location_help, R.string.location_allow, { launcher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION) }, "map-location")
                !presence.explained -> Notice(R.string.map_explain_title, R.string.map_explain, R.string.map_explain_ok, actions.explained, "map-explain", cell = settings.cellLength)
                presence.active -> ActiveBar(presence, now, actions.stop)
                else -> StartBar(now, actions.start)
            }
            val center = presence.ownCell
            if (center != null) Grid(center, presence.friends, names)
            Text(
                if (presence.friendCount == 0) stringResource(R.string.map_no_friends) else stringResource(R.string.map_friends_count, presence.friendCount),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("map-friend-count"),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            if (presence.friends.isEmpty()) {
                Text(stringResource(if (presence.active) R.string.map_looking else R.string.map_none_active), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("map-empty"))
            }
            presence.friends.forEach { dot -> FriendRow(dot, names[dot.key] ?: ChatController.shortNpub(dot.key), presence.ownCell, now) { actions.openProfile(dot.key) } }
            TextButton(onClick = actions.privacy, modifier = Modifier.testTag("map-privacy")) { Text(stringResource(R.string.map_open_privacy)) }
        }
    }
}

@Composable
private fun Notice(title: Int, help: Int, button: Int, onClick: () -> Unit, tag: String, cell: Int = 0) {
    Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(help), style = MaterialTheme.typography.bodyMedium)
        if (cell != 0) Text(stringResource(if (cell == 6) R.string.map_explain_cell_1 else R.string.map_explain_cell_5), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onClick, modifier = Modifier.testTag("$tag-button")) { Text(stringResource(button)) }
    }
}

@Composable
private fun StartBar(now: Long, onStart: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.map_start_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.map_start_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PRESENCE_DURATIONS.forEach { (seconds, label) -> OutlinedButton(onClick = { onStart(seconds) }, modifier = Modifier.testTag("map-start-$seconds")) { Text(stringResource(label)) } }
        }
        OutlinedButton(onClick = { onStart(secondsUntilMidnight(now)) }, modifier = Modifier.testTag("map-start-midnight")) { Text(stringResource(R.string.map_for_midnight)) }
    }
}

@Composable
private fun ActiveBar(presence: PresenceUi, now: Long, onStop: () -> Unit) {
    val minutes = ((presence.until - now + 59) / 60).coerceAtLeast(1)
    Row(verticalAlignment = Alignment.CenterVertically) {
        AssistChip(onClick = {}, label = { Text(stringResource(R.string.map_active, minutes)) }, modifier = Modifier.testTag("map-active"))
        Box(Modifier.weight(1f))
        Button(onClick = onStop, modifier = Modifier.testTag("map-stop")) { Text(stringResource(R.string.map_stop)) }
    }
}

@Composable
private fun Grid(center: String, friends: List<FriendDot>, names: Map<String, String>) {
    val rows = remember(center) { Geohash.grid(center, 3) }
    Column(Modifier.fillMaxWidth().testTag("map-grid"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { cell ->
                    val here = friends.filter { it.cell == cell && cell.isNotEmpty() }
                    val mine = cell == center
                    Box(
                        Modifier.weight(1f).aspectRatio(1f)
                            .background(if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                            .border(if (here.isNotEmpty()) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                            .testTag(if (mine) "cell-own" else "cell-${cell}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (here.isNotEmpty()) {
                            val first = names[here.first().key] ?: ChatController.shortNpub(here.first().key)
                            Text(if (here.size == 1) first.take(1).uppercase() else "${here.size}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        } else if (mine) {
                            Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendRow(dot: FriendDot, name: String, ownCell: String?, now: Long, onClick: () -> Unit) {
    val km = ownCell?.takeIf { it.length == dot.cell.length }?.let { Geohash.distanceKm(it, dot.cell) }
    val ago = (now - dot.seenAt).coerceAtLeast(0)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp).testTag("friend-${dot.key.take(8)}"), verticalAlignment = Alignment.CenterVertically) {
        Avatar(name, size = 40)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                if (km == null || km < 0.5) stringResource(R.string.map_same_cell) else stringResource(R.string.map_km_away, km),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(if (ago < 90) stringResource(R.string.map_now) else stringResource(R.string.feed_minutes, (ago / 60).toInt()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
    }
}
