package com.kutlusoy.nearly.chat

import com.kutlusoy.nearly.nostr.DirectMessage
import com.kutlusoy.nearly.nostr.Event
import com.kutlusoy.nearly.nostr.NostrKeys
import com.kutlusoy.nearly.nostr.NostrTransport
import com.kutlusoy.nearly.nostr.ProfileService
import com.kutlusoy.nearly.nostr.Session

sealed interface SendResult {
    data class Queued(val message: ChatMessage) : SendResult
    data object Empty : SendResult
    data object TooLong : SendResult
    data object ToSelf : SendResult
    data object BadPeer : SendResult
}

// Private messages of NIP-17 over the relays: a message is wrapped twice, for the other person and for the sender,
// so the sender finds the whole history again on a new phone.
class ChatService(
    private val transport: NostrTransport,
    private val session: Session,
    private val store: ChatStore,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
    private val onChange: () -> Unit = {},
    private val onIncoming: (ChatMessage) -> Unit = {},
) {
    private val own = NostrKeys.toHex(session.publicKey)
    private var inbox: AutoCloseable? = null
    private val rejections = HashMap<String, Int>()

    // Reads the inbox. Wraps carry a random time up to two days in the past, so the filter starts a little earlier than the last sync.
    fun start() {
        if (inbox != null) return
        val last = store.lastSync()
        val since = if (last > 0) last - SYNC_OVERLAP else null
        failOldSending()
        inbox = transport.subscribe(
            listOf(DirectMessage.inboxFilter(session, since)),
            { handle(it) },
            {
                store.setLastSync(clock())
                onChange()
            },
        )
    }

    fun stop() {
        inbox?.close()
        inbox = null
    }

    fun send(peer: String, raw: String): SendResult {
        val text = raw.trim()
        if (text.isEmpty()) return SendResult.Empty
        if (text.length > MAX_TEXT) return SendResult.TooLong
        if (!isPubkey(peer)) return SendResult.BadPeer
        if (peer == own) return SendResult.ToSelf
        val now = clock()
        val events = DirectMessage.create(session, peer, text, now)
        events.forEach { store.rememberWrap(it.id) }
        val message = ChatMessage(events[0].id, peer, mine = true, text = text, at = now, status = MessageStatus.Sending)
        store.add(message)
        onChange()
        // The answer of the relays about the copy for the other person decides the state, the copy for the sender is only for the own devices
        transport.publish(events[0]) { _, accepted, reply -> result(message.id, accepted, reply) }
        transport.publish(events[1])
        return SendResult.Queued(message)
    }

    // A failed message is sent again as a new one
    fun retry(id: String): SendResult {
        val old = store.message(id)?.takeIf { it.mine && it.status == MessageStatus.Failed } ?: return SendResult.Empty
        store.remove(id)
        return send(old.peer, old.text)
    }

    // The name, the text and the age of the other person from the public profile on the relays
    fun resolveProfile(peer: String) {
        if (!isPubkey(peer)) return
        ProfileService(transport, session).load(peer) { data ->
            val profile = data?.let { PeerProfile(it.name, it.about, it.age) }
            if (profile != null && profile != store.peerProfile(peer)) {
                store.setPeerProfile(peer, profile)
                onChange()
            }
        }
    }

    fun publishProfile(name: String, about: String, age: Int?, picture: String? = null) {
        if (name.isBlank()) return
        ProfileService(transport, session).publish(com.kutlusoy.nearly.nostr.ProfileData(name = name.take(com.kutlusoy.nearly.nostr.ProfileData.MAX_NAME), about = about.take(com.kutlusoy.nearly.nostr.ProfileData.MAX_ABOUT), age = age, picture = picture?.takeIf { it.startsWith("https://") && it.length <= 500 }))
    }

    private fun result(id: String, accepted: Boolean, reply: String) {
        val current = store.message(id) ?: return
        if (current.status != MessageStatus.Sending && current.status != MessageStatus.Failed) return
        if (accepted || reply.startsWith("duplicate")) {
            store.setStatus(id, MessageStatus.Sent)
            rejections.remove(id)
            onChange()
            return
        }
        val count = (rejections[id] ?: 0) + 1
        rejections[id] = count
        if (count >= REJECTIONS_TO_FAIL && current.status == MessageStatus.Sending) {
            store.setStatus(id, MessageStatus.Failed)
            onChange()
        }
    }

    // A message that was still sending when the app closed is not in the memory of the relay client any more
    private fun failOldSending() {
        val limit = clock() - SENDING_TIMEOUT
        for (conversation in store.conversations()) {
            for (m in store.messages(conversation.peer)) {
                if (m.mine && m.status == MessageStatus.Sending && m.at < limit) store.setStatus(m.id, MessageStatus.Failed)
            }
        }
    }

    private fun handle(wrap: Event) {
        if (store.seenWrap(wrap.id)) return
        store.rememberWrap(wrap.id)
        val read = DirectMessage.read(session, wrap) ?: return
        val rumor = read.rumor
        val text = rumor.content.trim()
        if (text.isEmpty() || text.length > MAX_INCOMING_TEXT) return
        val mine = rumor.pubkey == own
        val peer = if (mine) rumor.tagValues("p").firstOrNull { isPubkey(it) } ?: return else rumor.pubkey
        if (peer == own || (!mine && store.isBlocked(peer))) return
        val message = ChatMessage(wrap.id, peer, mine, text, rumor.createdAt, if (mine) MessageStatus.Sent else MessageStatus.Received, read = mine)
        if (store.add(message)) {
            onChange()
            if (!mine) onIncoming(message)
        }
    }

    companion object {
        const val MAX_TEXT = 2000
        const val MAX_INCOMING_TEXT = 5000
        private const val SYNC_OVERLAP = 3 * 24 * 3600L
        private const val SENDING_TIMEOUT = 120L
        private const val REJECTIONS_TO_FAIL = 3

        fun isPubkey(text: String) = text.length == 64 && text.all { it in '0'..'9' || it in 'a'..'f' }

        // Accepts npub1..., nostr:npub1... and the 64 hex digits of the key
        fun parsePeer(input: String): String? {
            val text = input.trim().removePrefix("nostr:").trim()
            if (isPubkey(text.lowercase())) return text.lowercase()
            return runCatching { NostrKeys.toHex(NostrKeys.decodeNpub(text)) }.getOrNull()
        }
    }
}
