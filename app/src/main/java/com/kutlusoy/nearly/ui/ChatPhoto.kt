package com.kutlusoy.nearly.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.kutlusoy.nearly.R
import com.kutlusoy.nearly.chat.ChatMessage
import com.kutlusoy.nearly.chat.MessageStatus
import com.kutlusoy.nearly.chat.PhotoInfo
import com.kutlusoy.nearly.chat.PhotoState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Decodes a photo down to about the given edge, so a large photo never fills the memory
private fun decode(bytes: ByteArray, edge: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= edge) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

@Composable
private fun rememberPhoto(id: String, info: PhotoInfo, edge: Int, load: (String) -> ByteArray?): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, id, info.state) {
        value = if (info.state == PhotoState.Ready) withContext(Dispatchers.Default) { load(id)?.let { decode(it, edge)?.asImageBitmap() } } else null
    }
    return bitmap
}

// The photo inside a chat bubble. A photo that can be seen once shows no preview on the receiving side.
@Composable
fun PhotoBubble(message: ChatMessage, info: PhotoInfo, load: (String) -> ByteArray?, onOpen: () -> Unit) {
    val hidden = info.once && !message.mine
    val preview = if (hidden) null else rememberPhoto(message.id, info, 480, load)
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.size(width = 220.dp, height = 220.dp * info.height / info.width.coerceAtLeast(1)).clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .let { if (info.state == PhotoState.Ready) it.clickable(onClick = onOpen) else it }
            .testTag("chat-photo"),
        contentAlignment = Alignment.Center,
    ) {
        if (preview != null) {
            Image(preview, contentDescription = stringResource(R.string.photo_label), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            val label = when {
                info.state == PhotoState.Receiving -> stringResource(R.string.photo_receiving, info.have, info.total)
                info.state == PhotoState.Gone -> stringResource(if (message.mine) R.string.photo_once_sent else R.string.photo_once_gone)
                hidden -> stringResource(R.string.photo_once_open)
                else -> stringResource(R.string.photo_label)
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        }
    }
}

// Full screen. Once photos stay out of screenshots and are removed when the viewer closes.
@Composable
fun PhotoViewer(message: ChatMessage, info: PhotoInfo, load: (String) -> ByteArray?, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        if (info.once) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            LaunchedEffect(window) { window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        }
        val full = rememberPhoto(message.id, info, 2048, load)
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("photo-viewer"), contentAlignment = Alignment.Center) {
            if (full != null) Image(full, contentDescription = stringResource(R.string.photo_label), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).testTag("photo-viewer-close")) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.photo_close), tint = Color.White)
            }
        }
    }
}

@Composable
fun messagePreview(message: ChatMessage): String = if (message.photo != null) stringResource(R.string.photo_label) else message.text

@Composable
fun photoFooter(message: ChatMessage): String? {
    val info = message.photo ?: return null
    return if (message.mine && message.status == MessageStatus.Sending) stringResource(R.string.message_sending_photo, info.have, info.total) else null
}
