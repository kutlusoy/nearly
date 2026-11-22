package com.kutlusoy.nearly.media

import com.kutlusoy.nearly.nostr.PhotoRef
import com.kutlusoy.nearly.nostr.PhotoUploadService

// Uploads a processed photo and gives the reference for a post, with the BlurHash as placeholder
class PhotoPublisher(private val upload: PhotoUploadService) {
    fun publish(photo: ProcessedPhoto, alt: String? = null): PhotoRef =
        upload.upload(photo.jpeg, alt).copy(blurhash = photo.blurhash)
}
