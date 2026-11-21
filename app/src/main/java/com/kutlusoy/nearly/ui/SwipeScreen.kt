package com.kutlusoy.nearly.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kutlusoy.nearly.R
import com.kutlusoy.nearly.ui.theme.NearlyColors
import kotlin.math.abs
import kotlinx.coroutines.launch

enum class SwipeDirection { Like, Later, Skip }

// One card at a time like Tinder: right likes, left means "not now", up skips without a signal
@Composable
fun SwipeScreen(
    people: List<Person> = SamplePeople,
    filter: DiscoveryFilter = DiscoveryFilter(),
    onFilters: () -> Unit = {},
    onSwiped: (Person, SwipeDirection) -> Unit = { _, _ -> },
) {
    val deck = remember(people, filter) { filter.deck(people) }
    var index by rememberSaveable(deck.map { it.id }) { mutableIntStateOf(0) }
    var detail by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val offset = remember(index) { Animatable(Offset.Zero, Offset.VectorConverter) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    val current = deck.getOrNull(index)
    val next = deck.getOrNull(index + 1)
    val threshold = area.width * 0.3f

    fun finish(direction: SwipeDirection) {
        val person = current ?: return
        val target = when (direction) {
            SwipeDirection.Like -> Offset(area.width * 1.6f, 0f)
            SwipeDirection.Later -> Offset(-area.width * 1.6f, 0f)
            SwipeDirection.Skip -> Offset(0f, -area.height * 1.6f)
        }
        scope.launch {
            offset.animateTo(target, tween(220))
            onSwiped(person, direction)
            index += 1
        }
    }

    fun release() {
        val o = offset.value
        val direction = when {
            o.y < -threshold && abs(o.y) > abs(o.x) -> SwipeDirection.Skip
            o.x > threshold -> SwipeDirection.Like
            o.x < -threshold -> SwipeDirection.Later
            else -> null
        }
        if (direction != null) finish(direction) else scope.launch { offset.animateTo(Offset.Zero, spring(Spring.DampingRatioMediumBouncy)) }
    }

    if (detail && current != null) {
        PersonDetail(
            person = current,
            onClose = { detail = false },
            onAction = { direction ->
                detail = false
                finish(direction)
            },
        )
        return
    }
    Column(Modifier.fillMaxSize()) {
        NearlyTopBar {
            AssistChip(onClick = {}, label = { Text(stringResource(R.string.swipe_preview)) })
            IconButton(onClick = onFilters) {
                Icon(ImageVector.vectorResource(R.drawable.ic_tune), contentDescription = stringResource(R.string.swipe_filters))
            }
        }
        if (current == null) {
            EmptyDeck(noMatch = deck.isEmpty(), onAgain = { index = 0 }, onFilters = onFilters)
        } else {
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).onSizeChanged { area = it },
            ) {
                if (next != null) {
                    val progress = (offset.value.getDistance() / threshold.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    PersonCard(
                        next,
                        Modifier.fillMaxSize().graphicsLayer { scaleX = 0.94f + 0.06f * progress; scaleY = 0.94f + 0.06f * progress },
                        0f, 0f, 0f, null,
                    )
                }
                val like = (offset.value.x / threshold.coerceAtLeast(1f)).coerceIn(0f, 1f)
                val later = (-offset.value.x / threshold.coerceAtLeast(1f)).coerceIn(0f, 1f)
                val skip = if (abs(offset.value.y) > abs(offset.value.x)) (-offset.value.y / threshold.coerceAtLeast(1f)).coerceIn(0f, 1f) else 0f
                PersonCard(
                    current,
                    Modifier
                        .fillMaxSize()
                        .testTag("card")
                        .graphicsLayer {
                            translationX = offset.value.x
                            translationY = offset.value.y
                            rotationZ = (offset.value.x / 40f).coerceIn(-14f, 14f)
                        }
                        .clickable { detail = true }
                        .pointerInput(index) {
                            detectDragGestures(
                                onDragEnd = { release() },
                                onDragCancel = { scope.launch { offset.animateTo(Offset.Zero) } },
                            ) { change, drag ->
                                change.consume()
                                scope.launch { offset.snapTo(offset.value + drag) }
                            }
                        },
                    like, later, skip, { detail = true },
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionButton(Icons.Filled.Close, stringResource(R.string.swipe_later), NearlyColors.Teal, 60) { finish(SwipeDirection.Later) }
                ActionButton(Icons.Filled.KeyboardArrowUp, stringResource(R.string.swipe_skip), MaterialTheme.colorScheme.onSurfaceVariant, 46) { finish(SwipeDirection.Skip) }
                ActionButton(Icons.Filled.Favorite, stringResource(R.string.swipe_like), NearlyColors.Rose, 60) { finish(SwipeDirection.Like) }
            }
            Text(
                stringResource(R.string.swipe_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun PersonCard(person: Person, modifier: Modifier, like: Float, later: Float, skip: Float, onInfo: (() -> Unit)?) {
    Box(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(person.from, person.to))),
    ) {
        Text(
            person.name.take(1),
            modifier = Modifier.align(Alignment.Center),
            fontSize = 180.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.35f),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(220.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)))),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(person.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text("  ${person.age}", style = MaterialTheme.typography.headlineSmall, color = Color.White.copy(alpha = 0.9f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Place, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.swipe_distance, person.distanceKm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Text(person.bio, style = MaterialTheme.typography.bodyLarge, color = Color.White)
        }
        if (onInfo != null) {
            IconButton(onClick = onInfo, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
                Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.swipe_open_profile), tint = Color.White)
            }
        }
        Stamp(stringResource(R.string.swipe_stamp_like), NearlyColors.Rose, like, Modifier.align(Alignment.TopStart).padding(24.dp))
        Stamp(stringResource(R.string.swipe_stamp_later), NearlyColors.Teal, later, Modifier.align(Alignment.TopEnd).padding(24.dp))
        Stamp(stringResource(R.string.swipe_stamp_skip), Color.White, skip, Modifier.align(Alignment.TopCenter).padding(top = 40.dp))
    }
}

@Composable
private fun Stamp(text: String, color: Color, alpha: Float, modifier: Modifier) {
    if (alpha <= 0.02f) return
    Text(
        text,
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .border(3.dp, color, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.ExtraBold,
        color = color,
    )
}

@Composable
fun ActionButton(icon: ImageVector, description: String, tint: Color, size: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, tint),
        shadowElevation = 4.dp,
        modifier = Modifier.size(size.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size((size * 0.5f).dp))
        }
    }
}

@Composable
private fun EmptyDeck(noMatch: Boolean, onAgain: () -> Unit, onFilters: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandMark(Icons.Filled.Place, size = 80)
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(if (noMatch) R.string.swipe_no_match_title else R.string.swipe_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(if (noMatch) R.string.swipe_no_match_help else R.string.swipe_empty_help),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        if (noMatch) {
            OutlinedButton(onClick = onFilters) { Text(stringResource(R.string.swipe_change_filters)) }
        } else {
            OutlinedButton(onClick = onAgain) { Text(stringResource(R.string.swipe_again)) }
        }
    }
}
