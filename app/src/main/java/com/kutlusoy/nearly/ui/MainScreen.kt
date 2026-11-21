package com.kutlusoy.nearly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kutlusoy.nearly.BuildConfig
import com.kutlusoy.nearly.R

private fun Tab.icon(): ImageVector = when (this) {
    Tab.Swipe -> Icons.Filled.Favorite
    Tab.Map -> Icons.Filled.Place
    Tab.Feed -> Icons.Filled.Home
    Tab.Chats -> Icons.Filled.Email
    Tab.Profile -> Icons.Filled.Person
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
fun MainScreen(account: AccountInfo, tab: Tab, onTab: (Tab) -> Unit, onSignOut: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                Tab.values().forEach { entry ->
                    NavigationBarItem(
                        selected = entry == tab,
                        onClick = { onTab(entry) },
                        icon = { Icon(entry.icon(), contentDescription = null) },
                        label = { Text(stringResource(entry.label())) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).safeDrawingPadding()) {
            if (tab == Tab.Profile) ProfileTab(account, onSignOut) else SoonTab(tab)
        }
    }
}

@Composable
private fun SoonTab(tab: Tab) {
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

@Composable
private fun ProfileTab(account: AccountInfo, onSignOut: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        BrandMark(Icons.Filled.Person, size = 104)
        Text(account.username, style = MaterialTheme.typography.headlineMedium)
        account.age?.let { Text(stringResource(R.string.profile_age, it), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.profile_id), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(shortId(account.npub), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.profile_test_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Text(stringResource(R.string.profile_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
            Text(stringResource(R.string.profile_sign_out), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

// A short form of the public key, enough to tell two people with the same name apart
fun shortId(npub: String): String = if (npub.length <= 20) npub else npub.take(12) + "..." + npub.takeLast(6)
