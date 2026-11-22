package com.kutlusoy.nearly.nostr

import java.security.SecureRandom
import java.util.Base64

// What the first chunk of a photo says about the whole photo. Every chunk carries it, so any chunk can start a transfer.
data class PhotoMeta(
    val transfer: String,
    val sha256: String,
    val size: Int,
    val total: Int,
    val width: Int,
    val height: Int,
    val once: Boolean,
)

class PhotoChunk(val meta: PhotoMeta, val index: Int, val data: ByteArray)

// A high resolution photo for one person, sent inside the encrypted chat over the relays. It is cut into chunks that fit the
// relay limits, every chunk is a rumor of its own kind inside a gift wrap, so the relays see only wraps of about 55 KB.
// The sender keeps no copy on a server, the receiver puts the chunks together and checks the hash of the whole photo.
object PhotoTransfer {
    const val KIND_CHUNK = 24310
    const val KIND_RECEIPT = 24311
    const val CHUNK_BYTES = 20_000
    const val MAX_BYTES = 1_200_000
    const val MAX_CHUNKS = (MAX_BYTES + CHUNK_BYTES - 1) / CHUNK_BYTES
    private const val MAX_SIDE = 8192
    private val HEX64 = Regex("[0-9a-f]{64}")
    private val HEX32 = Regex("[0-9a-f]{32}")
    private val random = SecureRandom()

    fun chunkCount(size: Int) = (size + CHUNK_BYTES - 1) / CHUNK_BYTES

    fun newTransferId(): String = ByteArray(16).also { random.nextBytes(it) }.let { NostrKeys.toHex(it) }

    fun meta(bytes: ByteArray, width: Int, height: Int, once: Boolean, transfer: String = newTransferId()): PhotoMeta {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES) { "a chat photo has 1 byte to $MAX_BYTES bytes" }
        require(width in 1..MAX_SIDE && height in 1..MAX_SIDE) { "invalid size" }
        return PhotoMeta(transfer, BlossomClient.sha256Hex(bytes), bytes.size, chunkCount(bytes.size), width, height, once)
    }

    // One wrap per chunk, only for the recipient: a copy for the sender would double the traffic, the sender keeps the file
    fun wraps(session: Session, recipient: String, meta: PhotoMeta, bytes: ByteArray, now: Long = System.currentTimeMillis() / 1000): List<Event> =
        (0 until meta.total).map { index -> wrapChunk(session, recipient, meta, index, bytes, now) }

    fun wrapChunk(session: Session, recipient: String, meta: PhotoMeta, index: Int, bytes: ByteArray, now: Long = System.currentTimeMillis() / 1000): Event {
        val from = index * CHUNK_BYTES
        val part = bytes.copyOfRange(from, minOf(bytes.size, from + CHUNK_BYTES))
        val tags = buildList {
            add(listOf("p", recipient))
            add(listOf("i", meta.transfer))
            add(listOf("x", meta.sha256))
            add(listOf("n", index.toString()))
            add(listOf("c", meta.total.toString()))
            add(listOf("size", meta.size.toString()))
            add(listOf("dim", "${meta.width}x${meta.height}"))
            if (meta.once) add(listOf("once", "1"))
        }
        val rumor = Rumor(NostrKeys.toHex(session.publicKey), now, KIND_CHUNK, tags, Base64.getEncoder().encodeToString(part))
        return GiftWrap.wrap(session, rumor, recipient, now)
    }

    // Foreign data: everything is checked, anything odd gives null
    fun parseChunk(rumor: Rumor): PhotoChunk? {
        if (rumor.kind != KIND_CHUNK) return null
        val transfer = rumor.tagValues("i").firstOrNull()?.takeIf { HEX32.matches(it) } ?: return null
        val sha = rumor.tagValues("x").firstOrNull()?.lowercase()?.takeIf { HEX64.matches(it) } ?: return null
        val index = rumor.tagValues("n").firstOrNull()?.toIntOrNull() ?: return null
        val total = rumor.tagValues("c").firstOrNull()?.toIntOrNull() ?: return null
        val size = rumor.tagValues("size").firstOrNull()?.toIntOrNull() ?: return null
        val dim = rumor.tagValues("dim").firstOrNull()?.split("x")?.takeIf { it.size == 2 }?.let { (w, h) -> w.toIntOrNull()?.let { a -> h.toIntOrNull()?.let { b -> a to b } } } ?: return null
        if (size !in 1..MAX_BYTES || total != chunkCount(size) || total !in 1..MAX_CHUNKS || index !in 0 until total) return null
        if (dim.first !in 1..MAX_SIDE || dim.second !in 1..MAX_SIDE) return null
        if (rumor.content.length > CHUNK_BYTES * 4 / 3 + 8) return null
        val data = runCatching { Base64.getDecoder().decode(rumor.content) }.getOrNull() ?: return null
        val expected = if (index < total - 1) CHUNK_BYTES else size - (total - 1) * CHUNK_BYTES
        if (data.size != expected) return null
        return PhotoChunk(PhotoMeta(transfer, sha, size, total, dim.first, dim.second, rumor.tagValues("once").firstOrNull() == "1"), index, data)
    }

    // The receiver tells the sender that the whole photo arrived and was checked
    fun receipt(session: Session, recipient: String, transfer: String, now: Long = System.currentTimeMillis() / 1000): Event {
        val rumor = Rumor(NostrKeys.toHex(session.publicKey), now, KIND_RECEIPT, listOf(listOf("p", recipient), listOf("i", transfer)), "")
        return GiftWrap.wrap(session, rumor, recipient, now)
    }

    fun parseReceipt(rumor: Rumor): String? =
        if (rumor.kind == KIND_RECEIPT) rumor.tagValues("i").firstOrNull()?.takeIf { HEX32.matches(it) } else null
}

// Puts the chunks of one photo together. Chunks may come in any order and more than once.
class PhotoAssembler(val meta: PhotoMeta) {
    private val parts = arrayOfNulls<ByteArray>(meta.total)

    val have: Int get() = parts.count { it != null }

    fun add(chunk: PhotoChunk): Boolean {
        if (chunk.meta != meta || parts[chunk.index] != null) return false
        parts[chunk.index] = chunk.data
        return true
    }

    // The photo when every chunk is there and the whole matches its hash, null otherwise
    fun result(): ByteArray? {
        if (parts.any { it == null }) return null
        val out = ByteArray(meta.size)
        var at = 0
        for (part in parts) {
            part!!.copyInto(out, at)
            at += part.size
        }
        return out.takeIf { at == meta.size && BlossomClient.sha256Hex(it) == meta.sha256 }
    }

    companion object {
        fun from(meta: PhotoMeta, chunks: List<Pair<Int, ByteArray>>): PhotoAssembler =
            PhotoAssembler(meta).also { a -> chunks.forEach { (i, d) -> a.add(PhotoChunk(meta, i, d)) } }
    }
}
