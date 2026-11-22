package com.kutlusoy.nearly.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kutlusoy.nearly.R
import com.kutlusoy.nearly.chat.ChatError
import com.kutlusoy.nearly.chat.ChatUiState
import com.kutlusoy.nearly.chat.ContactUi
import com.kutlusoy.nearly.chat.ConversationUi
import com.kutlusoy.nearly.network.LinkState
import com.kutlusoy.nearly.network.NetworkStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// The chat list in the style of the direct messages of Instagram. Each chat is private between two people.
@Composable
fun ChatsScreen(
    chat: ChatUiState,
    myNpub: String,
    onOpen: (String) -> Unit,
    onStart: (String) -> Unit,
    onClearError: () -> Unit,
    onNetworkSettings: () -> Unit,
    now: Long = System.currentTimeMillis() / 1000,
    onFollowRequest: (String) -> Unit = {},
    onDeleteRequest: (String) -> Unit = {},
    onBlockRequest: (String) -> Unit = {},
) {
    var dialog by remember { mutableStateOf(false) }
    var requests by remember { mutableStateOf(false) }
    if (requests) {
        RequestsScreen(chat.requests, now, onFollowRequest, onDeleteRequest, onBlockRequest, onBack = { requests = false })
        return
    }
    if (dialog) {
        NewChatScreen(
            error = chat.error,
            following = chat.following,
            onOpenContact = onOpen,
            onStart = onStart,
            onBack = {
                dialog = false
                onClearError()
            },
        )
        return
    }
    Column(Modifier.fillMaxSize()) {
        NearlyTopBar {
            IconButton(onClick = { dialog = true }, modifier = Modifier.testTag("new-chat")) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.chats_new))
            }
        }
        NetworkBanner(chat.network, onNetworkSettings)
        if (chat.requests.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().clickable { requests = true }.padding(horizontal = 16.dp, vertical = 10.dp).testTag("requests-row"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    pluralStringResource(R.plurals.chats_requests, chat.requests.size, chat.requests.size),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(R.string.requests_review), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
        if (chat.conversations.isEmpty()) {
            EmptyChats(myNpub, onStart = { dialog = true })
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(chat.conversations, key = { it.peer }) { item -> ConversationRow(item, now) { onOpen(item.peer) } }
                item { MyIdCard(myNpub, Modifier.padding(16.dp)) }
            }
        }
    }
}

@Composable
fun NetworkBanner(status: NetworkStatus, onSettings: () -> Unit) {
    val text = when (status.state) {
        LinkState.Online -> stringResource(R.string.network_online, status.relaysOpen, status.relaysTotal)
        LinkState.Connecting -> stringResource(R.string.network_connecting)
        LinkState.StartingTor -> stringResource(R.string.network_starting_tor, status.torPercent)
        LinkState.TorMissing -> stringResource(R.string.network_tor_missing)
        LinkState.Off -> stringResource(R.string.network_off)
    }
    val good = status.state == LinkState.Online
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSettings).padding(horizontal = 16.dp, vertical = 6.dp).testTag("network-banner"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(if (good) com.kutlusoy.nearly.ui.theme.NearlyColors.Teal else MaterialTheme.colorScheme.outline, CircleShape))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.labelMedium)
            if (status.state == LinkState.TorMissing) {
                Text(stringResource(R.string.network_tor_missing_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (!good) {
                Text(stringResource(R.string.network_banner_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (status.state == LinkState.TorMissing) TextButton(onClick = onSettings) { Text(stringResource(R.string.network_settings)) }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun EmptyChats(myNpub: String, onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandMark(Icons.Filled.Email, size = 72)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.chats_empty_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.chats_empty_help),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onStart, modifier = Modifier.testTag("start-chat")) { Text(stringResource(R.string.chats_start)) }
        Spacer(Modifier.height(24.dp))
        MyIdCard(myNpub, Modifier.fillMaxWidth())
    }
}

@Composable
private fun MyIdCard(npub: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.chats_my_id), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(npub, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("my-id"))
            Text(stringResource(R.string.chats_my_id_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { copyText(context, npub) }) { Text(stringResource(R.string.reg_words_copy)) }
                OutlinedButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.chats_share_text, npub))
                    }
                    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text(stringResource(R.string.chats_share)) }
            }
        }
    }
}

@Composable
private fun ConversationRow(item: ConversationUi, now: Long, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp).testTag("chat-${item.peer.take(8)}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(item.name, size = 52)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = if (item.unread > 0) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val preview = if (item.last.mine) stringResource(R.string.chats_you, messagePreview(item.last)) else messagePreview(item.last)
            Text(
                preview,
                style = MaterialTheme.typography.bodyMedium,
                color = if (item.unread > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (item.unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatTime(item.last.at, now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.unread > 0) {
                Spacer(Modifier.height(4.dp))
                Box(Modifier.size(20.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                    Text(item.unread.coerceAtMost(99).toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

// Messages of people the user does not follow stay here until the user follows the person or deletes them
@Composable
private fun RequestsScreen(requests: List<ConversationUi>, now: Long, onFollow: (String) -> Unit, onDelete: (String) -> Unit, onBlock: (String) -> Unit, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
            Text(stringResource(R.string.requests_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Text(
            stringResource(R.string.requests_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        if (requests.isEmpty()) {
            Text(stringResource(R.string.requests_empty), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(requests, key = { it.peer }) { item ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).testTag("request-${item.peer.take(8)}")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(item.name, size = 44)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(messagePreview(item.last), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Text(formatTime(item.last.at, now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onFollow(item.peer) }, modifier = Modifier.testTag("request-follow-${item.peer.take(8)}")) { Text(stringResource(R.string.requests_follow)) }
                            OutlinedButton(onClick = { onDelete(item.peer) }, modifier = Modifier.testTag("request-delete-${item.peer.take(8)}")) { Text(stringResource(R.string.requests_delete)) }
                            OutlinedButton(onClick = { onBlock(item.peer) }, modifier = Modifier.testTag("request-block-${item.peer.take(8)}")) { Text(stringResource(R.string.block_action), color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun NewChatScreen(error: ChatError?, following: List<ContactUi>, onOpenContact: (String) -> Unit, onStart: (String) -> Unit, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
            Text(stringResource(R.string.newchat_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.newchat_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(200) },
                label = { Text(stringResource(R.string.newchat_label)) },
                singleLine = true,
                isError = error == ChatError.BadId || error == ChatError.ToSelf || error == ChatError.Blocked,
                supportingText = {
                    when (error) {
                        ChatError.BadId -> Text(stringResource(R.string.newchat_bad_id))
                        ChatError.ToSelf -> Text(stringResource(R.string.newchat_to_self))
                        ChatError.Blocked -> Text(stringResource(R.string.newchat_blocked))
                        else -> Unit
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("newchat-input"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { clipboardText(context)?.let { text = it.trim().take(200) } }) { Text(stringResource(R.string.newchat_paste)) }
                Button(onClick = { onStart(text) }, enabled = text.isNotBlank(), modifier = Modifier.testTag("newchat-start")) { Text(stringResource(R.string.newchat_start)) }
            }
        }
        if (following.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Text(
                stringResource(R.string.newchat_following),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
            )
            LazyColumn(Modifier.weight(1f)) {
                items(following, key = { it.peer }) { contact -> ContactRow(contact) { onOpenContact(contact.peer) } }
            }
        }
    }
}

private fun clipboardText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Nearly", text))
}

// Time today, the day before that, the date of older messages
fun formatTime(at: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val moment = Instant.ofEpochSecond(at).atZone(zone)
    val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
    val day: LocalDate = moment.toLocalDate()
    return if (day == today) moment.format(DateTimeFormatter.ofPattern("HH:mm")) else moment.format(DateTimeFormatter.ofPattern("dd.MM."))
}
