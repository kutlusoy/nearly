package com.kutlusoy.nearly.chat

import com.kutlusoy.nearly.nostr.DirectMessage
import com.kutlusoy.nearly.nostr.Event
import com.kutlusoy.nearly.nostr.GiftWrap
import com.kutlusoy.nearly.nostr.ImageSanitizer
import com.kutlusoy.nearly.nostr.PhotoAssembler
import com.kutlusoy.nearly.nostr.PhotoTransfer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
    data object PhotoTooBig : SendResult
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
    private val acceptPhotos: (peer: String) -> Boolean = { true },
    private val background: (Runnable) -> Unit = Executors.newSingleThreadExecutor { r -> Thread(r, "photo-send").apply { isDaemon = true } }::execute,
    private val chunkTimeoutSeconds: Long = 30,
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
        dropStaleTransfers()
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
        store.message(id)?.takeIf { it.mine && it.status == MessageStatus.Failed && it.photo != null }?.let { return retryPhoto(it) }
        val old = store.message(id)?.takeIf { it.mine && it.status == MessageStatus.Failed } ?: return SendResult.Empty
        store.remove(id)
        return send(old.peer, old.text)
    }

    // A photo for one person, cut into chunks and sent one after the other. The photo stays in the store of the sender until
    // it is delivered, a photo that can be seen once is removed then.
    fun sendPhoto(peer: String, bytes: ByteArray, width: Int, height: Int, once: Boolean): SendResult {
        if (!isPubkey(peer)) return SendResult.BadPeer
        if (peer == own) return SendResult.ToSelf
        if (bytes.isEmpty() || bytes.size > PhotoTransfer.MAX_BYTES) return SendResult.PhotoTooBig
        val meta = PhotoTransfer.meta(bytes, width, height, once)
        val info = PhotoInfo(meta.transfer, width, height, bytes.size, once, PhotoState.Ready, 0, meta.total)
        val message = ChatMessage("ph:" + meta.transfer, peer, mine = true, text = "", at = clock(), status = MessageStatus.Sending, photo = info)
        store.savePhoto(meta.transfer, bytes)
        store.add(message)
        onChange()
        background { transmit(message, bytes) }
        return SendResult.Queued(message)
    }

    private fun retryPhoto(old: ChatMessage): SendResult {
        val info = old.photo ?: return SendResult.Empty
        val bytes = store.photoBytes(info.transfer) ?: return SendResult.Empty
        store.setPhoto(old.id, info.copy(have = 0))
        store.setStatus(old.id, MessageStatus.Sending)
        onChange()
        val message = old.copy(status = MessageStatus.Sending, photo = info.copy(have = 0))
        background { transmit(message, bytes) }
        return SendResult.Queued(message)
    }

    private fun transmit(message: ChatMessage, bytes: ByteArray) {
        val info = message.photo ?: return
        val meta = PhotoTransfer.meta(bytes, info.width, info.height, info.once, info.transfer)
        for (index in 0 until meta.total) {
            val wrap = PhotoTransfer.wrapChunk(session, message.peer, meta, index, bytes, clock())
            store.rememberWrap(wrap.id)
            if (!publishAndWait(wrap)) {
                store.setStatus(message.id, MessageStatus.Failed)
                onChange()
                return
            }
            store.message(message.id)?.photo?.let { store.setPhoto(message.id, it.copy(have = index + 1)) }
            onChange()
        }
        if (store.message(message.id)?.status == MessageStatus.Sending) store.setStatus(message.id, MessageStatus.Sent)
        onChange()
    }

    // True when a relay took the chunk. Three tries, each waits for the answer of the relays.
    private fun publishAndWait(wrap: Event): Boolean {
        repeat(CHUNK_TRIES) {
            val latch = CountDownLatch(1)
            val ok = java.util.concurrent.atomic.AtomicBoolean(false)
            var refused = 0
            transport.publish(wrap) { _, accepted, reply ->
                if (accepted || reply.startsWith("duplicate")) {
                    ok.set(true)
                    latch.countDown()
                } else if (++refused >= REJECTIONS_TO_FAIL) {
                    latch.countDown()
                }
            }
            latch.await(chunkTimeoutSeconds, TimeUnit.SECONDS)
            if (ok.get()) return true
        }
        return false
    }

    // Photos that were never completed are removed after a few days
    private fun dropStaleTransfers() {
        val limit = clock() - STALE_TRANSFER
        for (conversation in store.conversations()) {
            for (m in store.messages(conversation.peer)) {
                val info = m.photo ?: continue
                if (!m.mine && info.state == PhotoState.Receiving && m.at < limit) {
                    store.dropChunks(info.transfer)
                    store.remove(m.id)
                }
            }
        }
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
        val rumor = GiftWrap.unwrap(session, wrap)?.rumor ?: return
        when (rumor.kind) {
            DirectMessage.KIND_MESSAGE -> handleText(wrap, rumor)
            PhotoTransfer.KIND_CHUNK -> handleChunk(rumor)
            PhotoTransfer.KIND_RECEIPT -> handleReceipt(rumor)
        }
    }

    private fun handleReceipt(rumor: com.kutlusoy.nearly.nostr.Rumor) {
        val transfer = PhotoTransfer.parseReceipt(rumor) ?: return
        val message = store.message("ph:$transfer")?.takeIf { it.mine && it.peer == rumor.pubkey } ?: return
        val info = message.photo ?: return
        if (message.status == MessageStatus.Failed) return
        store.setStatus(message.id, MessageStatus.Delivered)
        if (info.once) {
            store.deletePhoto(transfer)
            store.setPhoto(message.id, info.copy(state = PhotoState.Gone))
        }
        onChange()
    }

    private fun handleChunk(rumor: com.kutlusoy.nearly.nostr.Rumor) {
        val sender = rumor.pubkey
        if (sender == own || store.isBlocked(sender)) return
        val chunk = PhotoTransfer.parseChunk(rumor) ?: return
        val meta = chunk.meta
        val id = "ph:${meta.transfer}"
        var message = store.message(id)
        if (message == null) {
            if (!acceptPhotos(sender) || openTransfers(sender) >= MAX_OPEN_TRANSFERS) return
            val info = PhotoInfo(meta.transfer, meta.width, meta.height, meta.size, meta.once, PhotoState.Receiving, 0, meta.total)
            message = ChatMessage(id, sender, mine = false, text = "", at = rumor.createdAt, status = MessageStatus.Received, read = true, photo = info)
            store.add(message)
        }
        val info = message.photo ?: return
        if (message.mine || message.peer != sender || info.state != PhotoState.Receiving || info.size != meta.size || info.total != meta.total) return
        if (!store.addChunk(meta.transfer, chunk.index, chunk.data)) return
        val parts = store.chunks(meta.transfer)
        if (parts.size < meta.total) {
            store.setPhoto(id, info.copy(have = parts.size))
            onChange()
            return
        }
        store.dropChunks(meta.transfer)
        val whole = PhotoAssembler.from(meta, parts).result()
        val checked = whole?.let { bytes -> ImageSanitizer.inspect(bytes)?.takeIf { it.mime == "image/jpeg" }?.let { ImageSanitizer.strip(bytes) } }
        if (checked == null) {
            store.remove(id)
            onChange()
            return
        }
        store.savePhoto(meta.transfer, checked)
        store.setPhoto(id, info.copy(state = PhotoState.Ready, have = meta.total))
        store.setRead(id, false)
        onChange()
        transport.publish(PhotoTransfer.receipt(session, sender, meta.transfer, clock()))
        store.message(id)?.let { onIncoming(it) }
    }

    private fun openTransfers(peer: String): Int = store.messages(peer).count { !it.mine && it.photo?.state == PhotoState.Receiving }

    // A photo that can be seen once is removed after the viewer closed it
    fun photoBytes(id: String): ByteArray? = store.message(id)?.photo?.takeIf { it.state == PhotoState.Ready }?.let { store.photoBytes(it.transfer) }

    fun photoViewed(id: String) {
        val message = store.message(id) ?: return
        val info = message.photo ?: return
        if (!info.once || message.mine || info.state != PhotoState.Ready) return
        store.deletePhoto(info.transfer)
        store.setPhoto(id, info.copy(state = PhotoState.Gone))
        onChange()
    }

    private fun handleText(wrap: Event, rumor: com.kutlusoy.nearly.nostr.Rumor) {
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
        private const val CHUNK_TRIES = 3
        private const val STALE_TRANSFER = 3 * 24 * 3600L
        const val MAX_OPEN_TRANSFERS = 3

        fun isPubkey(text: String) = text.length == 64 && text.all { it in '0'..'9' || it in 'a'..'f' }

        // Accepts npub1..., nostr:npub1... and the 64 hex digits of the key
        fun parsePeer(input: String): String? {
            val text = input.trim().removePrefix("nostr:").trim()
            if (isPubkey(text.lowercase())) return text.lowercase()
            return runCatching { NostrKeys.toHex(NostrKeys.decodeNpub(text)) }.getOrNull()
        }
    }
}
