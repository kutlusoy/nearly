package com.kutlusoy.nearly.nostr

// A private "I like you" between two people. It is a rumor of its own kind inside a gift wrap, so relays see nothing and chat
// clients that do not know the kind ignore it. Nobody sees the signals that arrive, only a match shows up.
object Interest {
    const val KIND_INTEREST = 24312
    const val LIFETIME = 30 * 24 * 3600L
    private const val CONTENT = "{\"v\":1,\"t\":\"interest\"}"

    fun wrap(session: Session, recipient: String, now: Long = System.currentTimeMillis() / 1000): Event {
        val rumor = Rumor(NostrKeys.toHex(session.publicKey), now, KIND_INTEREST, listOf(listOf("p", recipient)), CONTENT)
        return GiftWrap.wrap(session, rumor, recipient, now, expiresAt = now + LIFETIME)
    }

    fun isInterest(rumor: Rumor): Boolean = rumor.kind == KIND_INTEREST && rumor.content == CONTENT
}
