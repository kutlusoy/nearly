package com.kutlusoy.nearly.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kutlusoy.nearly.R
import com.kutlusoy.nearly.chat.ChatController
import com.kutlusoy.nearly.chat.ChatError
import com.kutlusoy.nearly.chat.ContactUi

// The public profile of a person from the relays, with the ID to copy and the way to write to the person
@Composable
fun ContactProfileScreen(contact: ContactUi, onBack: () -> Unit, onMessage: () -> Unit, onToggleFollow: () -> Unit, onBlock: () -> Unit = {}, onUnblock: () -> Unit = {}) {
    BackHandler(onBack = onBack)
    var confirmBlock by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    val report = LocalReportPerson.current
    if (reporting && report != null) ReportDialog(onDismiss = { reporting = false }, onSend = { type, note -> reporting = false; report(contact.peer, type, note) })
    val context = LocalContext.current
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Avatar(contact.name, size = 112)
                Text(contact.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                contact.age?.let { Text(stringResource(R.string.profile_age, it), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (contact.about.isNotBlank()) Text(contact.about, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = onMessage, modifier = Modifier.testTag("message-contact")) { Text(stringResource(R.string.contact_message)) }
                    OutlinedButton(onClick = onToggleFollow, modifier = Modifier.testTag("follow-contact")) {
                        Text(stringResource(if (contact.followed) R.string.feed_following else R.string.feed_follow))
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.contact_id), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(contact.npub, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("contact-npub"))
                        Text(stringResource(R.string.contact_id_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Nearly", contact.npub))
                        }) { Text(stringResource(R.string.reg_words_copy)) }
                    }
                }
                if (!contact.blocked && report != null) {
                    OutlinedButton(onClick = { reporting = true }, modifier = Modifier.testTag("report-contact")) { Text(stringResource(R.string.report_action)) }
                }
                if (contact.blocked) {
                    OutlinedButton(onClick = onUnblock, modifier = Modifier.testTag("unblock-contact")) { Text(stringResource(R.string.block_undo)) }
                } else if (!confirmBlock) {
                    OutlinedButton(onClick = { confirmBlock = true }, modifier = Modifier.testTag("block-contact")) {
                        Text(stringResource(R.string.block_action), color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Text(stringResource(R.string.block_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onBlock, modifier = Modifier.testTag("block-confirm")) { Text(stringResource(R.string.block_confirm)) }
                        OutlinedButton(onClick = { confirmBlock = false }) { Text(stringResource(R.string.newchat_cancel)) }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// The people the user blocked, with the way back
@Composable
fun BlockedScreen(contacts: List<ContactUi>, onUnblock: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
                Text(stringResource(R.string.blocked_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(stringResource(R.string.blocked_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            if (contacts.isEmpty()) {
                Text(stringResource(R.string.blocked_empty), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(contacts, key = { it.peer }) { contact ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).testTag("blocked-${contact.peer.take(8)}"), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(contact.name, size = 44)
                            Text(contact.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                            OutlinedButton(onClick = { onUnblock(contact.peer) }, modifier = Modifier.testTag("unblock-${contact.peer.take(8)}")) { Text(stringResource(R.string.block_undo)) }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

// The people the user follows. A new person is followed with the ID.
@Composable
fun FollowingScreen(
    contacts: List<ContactUi>,
    error: ChatError?,
    onOpen: (String) -> Unit,
    onFollowId: (String) -> Unit,
    onClearError: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = { onClearError(); onBack() })
    var text by remember { mutableStateOf("") }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onClearError(); onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
                Text(stringResource(R.string.following_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(200) },
                    label = { Text(stringResource(R.string.following_add_label)) },
                    singleLine = true,
                    isError = error == ChatError.BadId || error == ChatError.ToSelf,
                    supportingText = {
                        when (error) {
                            ChatError.BadId -> Text(stringResource(R.string.newchat_bad_id))
                            ChatError.ToSelf -> Text(stringResource(R.string.newchat_to_self))
                            else -> Text(stringResource(R.string.following_add_help))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("follow-id-input"),
                )
                Button(onClick = { onFollowId(text) }, enabled = text.isNotBlank(), modifier = Modifier.testTag("follow-id-button")) {
                    Text(stringResource(R.string.feed_follow))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            if (contacts.isEmpty()) {
                Text(
                    stringResource(R.string.following_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(contacts, key = { it.peer }) { contact -> ContactRow(contact) { onOpen(contact.peer) } }
                }
            }
        }
    }
}

@Composable
fun ContactRow(contact: ContactUi, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp).testTag("contact-${contact.peer.take(8)}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(contact.name, size = 48)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(contact.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                contact.about.ifBlank { ChatController.shortNpub(contact.peer) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
