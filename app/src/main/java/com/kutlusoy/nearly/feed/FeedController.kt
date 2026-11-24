package com.kutlusoy.nearly.feed

import com.kutlusoy.nearly.network.NetworkLink
import com.kutlusoy.nearly.nostr.NostrKeys
import com.kutlusoy.nearly.nostr.PhotoRef
import com.kutlusoy.nearly.nostr.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Opens with the account like the chat controller and closes with the sign out
class FeedController(
    private val link: NetworkLink,
    private val storeFor: (accountKey: String) -> FeedStore,
) {
    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()
    private var service: FeedService? = null
    private var follows: Set<String> = emptySet()

    fun open(session: Session, isBlocked: (String) -> Boolean) {
        close()
        val store = storeFor(NostrKeys.toHex(session.publicKey).take(16))
        service = FeedService(link.transport, session, store, isBlocked = isBlocked, onChange = { refresh() })
        service?.start(follows)
        refresh()
    }

    fun close() {
        service?.stop()
        service = null
        _state.value = FeedUiState()
    }

    fun setFollows(next: Set<String>) {
        if (next == follows) return
        follows = next
        service?.start(next)
    }

    fun refresh() {
        val s = service ?: return
        _state.value = FeedUiState(s.posts(), s.stories())
    }

    fun postText(text: String) = service?.postText(text) != null

    fun postPhotos(caption: String, photos: List<PhotoRef>) = service?.postPhotos(caption, photos) != null

    fun storyText(text: String) = service?.storyText(text) != null

    fun storyPhoto(photo: PhotoRef, caption: String) = service?.storyPhoto(photo, caption) != null

    fun retry(id: String) = service?.retry(id)

    fun delete(id: String) = service?.delete(id)

    fun like(id: String) = service?.like(id)

    fun unlike(id: String) = service?.unlike(id)

    fun markSeen(id: String) = service?.markSeen(id)
}
