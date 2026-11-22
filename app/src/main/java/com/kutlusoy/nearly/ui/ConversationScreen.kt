package com.kutlusoy.nearly.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kutlusoy.nearly.R
import com.kutlusoy.nearly.chat.ChatError
import com.kutlusoy.nearly.chat.ChatMessage
import com.kutlusoy.nearly.chat.ChatService
import com.kutlusoy.nearly.chat.MessageStatus
import com.kutlusoy.nearly.chat.PhotoState

@Composable
fun ConversationScreen(
    name: String,
    messages: List<ChatMessage>,
    error: ChatError?,
    onSend: (String) -> Unit,
    onRetry: (String) -> Unit,
    onBack: () -> Unit,
    now: Long = System.currentTimeMillis() / 1000,
    onProfile: () -> Unit = {},
    loadPhoto: (String) -> ByteArray? = { null },
    onSendPhoto: (android.net.Uri, Boolean) -> Unit = { _, _ -> },
    onPhotoViewed: (String) -> Unit = {},
) {
    BackHandler(onBack = onBack)
    var once by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<String?>(null) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onSendPhoto(uri, once)
    }
    var text by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex)
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
                Row(Modifier.weight(1f).clickable(onClick = onProfile).testTag("conversation-header"), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(name, size = 40)
                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            if (messages.isEmpty()) {
                Column(Modifier.weight(1f).fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(stringResource(R.string.conversation_encrypted), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.conversation_empty), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("messages"), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        Text(
                            stringResource(R.string.conversation_encrypted),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        )
                    }
                    items(messages, key = { it.id }) { message -> Bubble(message, now, loadPhoto, onOpen = { viewing = message.id }) { onRetry(message.id) } }
                }
            }
            if (error == ChatError.PhotoTooBig) {
                Text(stringResource(R.string.photo_too_big), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (error == ChatError.TooLong) {
                Text(stringResource(R.string.conversation_too_long), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { picker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.testTag("attach-photo")) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.photo_attach), tint = MaterialTheme.colorScheme.primary)
                }
                androidx.compose.material3.FilterChip(
                    selected = once,
                    onClick = { once = !once },
                    label = { Text(stringResource(R.string.photo_once_toggle)) },
                    modifier = Modifier.padding(bottom = 8.dp).testTag("once-toggle"),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(ChatService.MAX_TEXT + 200) },
                    modifier = Modifier.weight(1f).testTag("message-input"),
                    placeholder = { Text(stringResource(R.string.conversation_input)) },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                )
                IconButton(
                    onClick = {
                        onSend(text)
                        if (text.trim().length <= ChatService.MAX_TEXT) text = ""
                    },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.testTag("send"),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.conversation_send), tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    val open = viewing?.let { id -> messages.firstOrNull { it.id == id } }
    val openInfo = open?.photo
    if (open != null && openInfo != null && openInfo.state == PhotoState.Ready) {
        PhotoViewer(open, openInfo, loadPhoto, onClose = {
            viewing = null
            if (!open.mine) onPhotoViewed(open.id)
        })
    }
}

@Composable
private fun Bubble(message: ChatMessage, now: Long, load: (String) -> ByteArray?, onOpen: () -> Unit, onRetry: () -> Unit) {
    val mine = message.mine
    val photo = message.photo
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        if (photo != null) {
            Box(Modifier.let { if (message.status == MessageStatus.Failed) it.clickable(onClick = onRetry) else it }) { PhotoBubble(message, photo, load, onOpen) }
        } else Box(
            Modifier
                .widthIn(max = 300.dp)
                .background(
                    if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp),
                )
                .let { if (message.status == MessageStatus.Failed) it.clickable(onClick = onRetry) else it }
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Text(message.text, style = MaterialTheme.typography.bodyLarge, color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
        }
        val footer = when {
            !mine -> formatTime(message.at, now)
            photoFooter(message) != null -> photoFooter(message)!!
            message.status == MessageStatus.Delivered -> formatTime(message.at, now) + " " + stringResource(R.string.message_delivered)
            message.status == MessageStatus.Sending -> stringResource(R.string.message_sending)
            message.status == MessageStatus.Failed -> stringResource(R.string.message_failed)
            else -> formatTime(message.at, now) + " " + stringResource(R.string.message_sent)
        }
        Text(
            footer,
            style = MaterialTheme.typography.labelSmall,
            color = if (message.status == MessageStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
