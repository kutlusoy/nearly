package com.kutlusoy.nearly.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.kutlusoy.nearly.media.BlurHash
import com.kutlusoy.nearly.nostr.PhotoRef

// How a screen gets a photo. The app provides the loader of the view model, without one the photos stay placeholders.
val LocalPhotoLoader = staticCompositionLocalOf<suspend (PhotoRef) -> ImageBitmap?> { { null } }

// A photo of a person: the blurred placeholder first, the photo when it is loaded, nothing when it cannot be loaded
@Composable
fun RemotePhoto(photo: PhotoRef, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val loader = LocalPhotoLoader.current
    var loaded by remember(photo.sha256) { mutableStateOf<ImageBitmap?>(null) }
    val placeholder = remember(photo.blurhash) { photo.blurhash?.let(::blurred) }
    LaunchedEffect(photo.sha256) { loaded = loader(photo) }
    val shown = loaded ?: placeholder
    Box(modifier) {
        if (shown != null) Image(shown, contentDescription = photo.alt, contentScale = contentScale, modifier = Modifier.fillMaxSize())
    }
}

private fun blurred(hash: String): ImageBitmap? = runCatching {
    val size = 32
    Bitmap.createBitmap(BlurHash.decode(hash, size, size), size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}.getOrNull()
