package com.kutlusoy.nearly.nostr

data class Story(val id: String, val author: String, val createdAt: Long, val expiresAt: Long, val text: String, val photo: PhotoRef?)

// A story is a short text (kind 1) or one photo (kind 20) with the tag `nearly-story` and an expiration of 24 hours (NIP-40).
// Relays that ignore the expiration are covered by the check of the client.
object StoryCodec {
    const val TAG = "nearly-story"
    const val LIFETIME = 24 * 3600L
    const val MAX_TEXT = 140
    private const val FUTURE = 300L

    fun text(session: Session, text: String, now: Long): Event {
        require(text.isNotBlank() && text.trim().length <= MAX_TEXT) { "a story text has 1 to $MAX_TEXT characters" }
        return Event.sign(session, PostCodec.KIND_TEXT, text.trim(), tags(now), now)
    }

    fun photo(session: Session, photo: PhotoRef, caption: String, now: Long): Event {
        require(caption.trim().length <= MAX_TEXT) { "caption too long" }
        return Event.sign(session, PostCodec.KIND_PHOTO, caption.trim(), listOf(PostCodec.imetaTag(photo)) + tags(now), now)
    }

    private fun tags(now: Long) = listOf(listOf("t", TAG), listOf("expiration", (now + LIFETIME).toString()))

    fun isStory(event: Event): Boolean = (event.kind == PostCodec.KIND_TEXT || event.kind == PostCodec.KIND_PHOTO) && TAG in event.tagValues("t")

    fun parse(event: Event, now: Long): Story? {
        if (!isStory(event) || event.createdAt > now + FUTURE) return null
        val expiration = event.tagValues("expiration").firstOrNull()?.toLongOrNull()
        val expiresAt = minOf(event.createdAt + LIFETIME, expiration ?: Long.MAX_VALUE)
        if (now >= expiresAt || event.content.length > MAX_TEXT) return null
        if (event.kind == PostCodec.KIND_PHOTO) {
            val photo = PostCodec.parseImeta(event, 1).firstOrNull() ?: return null
            return Story(event.id, event.pubkey, event.createdAt, expiresAt, event.content, photo)
        }
        if (event.content.isBlank() || event.tags.any { it.firstOrNull() == "e" }) return null
        return Story(event.id, event.pubkey, event.createdAt, expiresAt, event.content, null)
    }
}
