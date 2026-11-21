package com.kutlusoy.nearly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kutlusoy.nearly.R

private fun Tab.icon(selected: Boolean = true): ImageVector = when (this) {
    Tab.Swipe -> if (selected) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder
    Tab.Map -> if (selected) Icons.Filled.Place else Icons.Outlined.Place
    Tab.Feed -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
    Tab.Chats -> if (selected) Icons.Filled.Email else Icons.Outlined.Email
    Tab.Profile -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
}

private fun Tab.label(): Int = when (this) {
    Tab.Swipe -> R.string.tab_swipe
    Tab.Map -> R.string.tab_map
    Tab.Feed -> R.string.tab_feed
    Tab.Chats -> R.string.tab_chats
    Tab.Profile -> R.string.tab_profile
}

private fun Tab.soon(): Int = when (this) {
    Tab.Swipe -> R.string.swipe_soon
    Tab.Map -> R.string.map_soon
    Tab.Feed -> R.string.feed_soon
    Tab.Chats -> R.string.chats_soon
    Tab.Profile -> R.string.tab_profile
}

@Composable
fun MainScreen(account: AccountInfo, tab: Tab, onTab: (Tab) -> Unit, onSettings: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                // Icons only like Instagram and Tinder, the names are for screen readers
                NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                    Tab.values().forEach { entry ->
                        NavigationBarItem(
                            selected = entry == tab,
                            onClick = { onTab(entry) },
                            icon = { Icon(entry.icon(entry == tab), contentDescription = stringResource(entry.label())) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.Swipe -> SwipeScreen()
                Tab.Feed -> FeedScreen()
                Tab.Profile -> ProfileTab(account, onSettings)
                else -> SoonTab(tab)
            }
        }
    }
}

@Composable
private fun SoonTab(tab: Tab) {
    NearlyTopBar()
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandMark(tab.icon(), size = 80)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(tab.label()), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(tab.soon()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        AssistChip(onClick = {}, label = { Text(stringResource(R.string.soon_badge)) })
    }
}

// Built like the profile of Instagram: name and settings on top, picture and numbers, then the grid of photos
@Composable
private fun ProfileTab(account: AccountInfo, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(account.username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(account.username, size = 88)
            Spacer(Modifier.width(16.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat(0, R.string.profile_posts)
                Stat(0, R.string.profile_followers)
                Stat(0, R.string.profile_following)
            }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            account.age?.let { Text(stringResource(R.string.profile_age, it), style = MaterialTheme.typography.titleMedium) }
            Text(shortId(account.npub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BrandMark(Icons.Filled.Favorite, size = 64)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.profile_no_photos), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.profile_no_photos_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Stat(value: Int, label: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// A short form of the public key, enough to tell two people with the same name apart
fun shortId(npub: String): String = if (npub.length <= 20) npub else npub.take(12) + "..." + npub.takeLast(6)
