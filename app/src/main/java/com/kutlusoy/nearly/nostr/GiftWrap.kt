package com.kutlusoy.nearly.nostr

import java.security.SecureRandom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

// An unsigned event, the inner layer of NIP-17 and NIP-59
data class Rumor(val pubkey: String, val createdAt: Long, val kind: Int, val tags: List<List<String>>, val content: String) {
    val id: String get() = Event.computeId(pubkey, createdAt, kind, tags, content)

    fun toJson(): String {
        val full = Event(id, pubkey, createdAt, kind, tags, content, "")
        return full.toJson().replace(",\"sig\":\"\"}", "}")
    }

    fun tagValues(name: String): List<String> = tags.filter { it.size >= 2 && it[0] == name }.map { it[1] }

    companion object {
        fun parse(text: String): Rumor {
            val o = Json.parseToJsonElement(text).jsonObject
            return Rumor(
                pubkey = requireNotNull(o["pubkey"]).jsonPrimitive.content,
                createdAt = requireNotNull(o["created_at"]).jsonPrimitive.long,
                kind = requireNotNull(o["kind"]).jsonPrimitive.int,
                tags = requireNotNull(o["tags"]).jsonArray.map { tag -> tag.jsonArray.map { it.jsonPrimitive.content } },
                content = requireNotNull(o["content"]).jsonPrimitive.content,
            )
        }
    }
}

class Unwrapped(val rumor: Rumor, val senderPubkey: String)

// NIP-59 gift wrap: rumor -> seal (kind 13, signed by the sender) -> wrap (kind 1059, signed by a one-time key)
object GiftWrap {
    const val KIND_SEAL = 13
    const val KIND_WRAP = 1059
    private const val RANDOM_PAST_SECONDS = 2 * 24 * 3600L
    private val random = SecureRandom()

    fun wrap(session: Session, rumor: Rumor, recipientPubkey: String, now: Long = System.currentTimeMillis() / 1000): Event {
        require(rumor.pubkey == NostrKeys.toHex(session.publicKey)) { "the rumor must come from the sender" }
        val recipient = NostrKeys.fromHex(recipientPubkey)
        val sealContent = session.useSecretKey { sk ->
            val key = Nip44.conversationKey(sk, recipient)
            try {
                Nip44.encrypt(rumor.toJson(), key)
            } finally {
                key.fill(0)
            }
        }
        val seal = Event.sign(session, KIND_SEAL, sealContent, emptyList(), randomPast(now))
        val ephemeral = NostrKeys.generatePrivateKey()
        try {
            val key = Nip44.conversationKey(ephemeral, recipient)
            val wrapContent = try {
                Nip44.encrypt(seal.toJson(), key)
            } finally {
                key.fill(0)
            }
            val pubkey = NostrKeys.toHex(NostrKeys.publicKey(ephemeral))
            val tags = listOf(listOf("p", recipientPubkey))
            val createdAt = randomPast(now)
            val id = Event.computeId(pubkey, createdAt, KIND_WRAP, tags, wrapContent)
            val sig = Schnorr.sign(NostrKeys.fromHex(id), ephemeral)
            return Event(id, pubkey, createdAt, KIND_WRAP, tags, wrapContent, NostrKeys.toHex(sig))
        } finally {
            ephemeral.fill(0)
        }
    }

    // Returns null for anything that is not a valid wrap for this account, never throws on foreign data
    fun unwrap(session: Session, wrap: Event): Unwrapped? {
        if (wrap.kind != KIND_WRAP || !wrap.isValid()) return null
        return try {
            val sealJson = session.useSecretKey { sk ->
                val key = Nip44.conversationKey(sk, NostrKeys.fromHex(wrap.pubkey))
                try {
                    Nip44.decrypt(wrap.content, key)
                } finally {
                    key.fill(0)
                }
            }
            val seal = Event.parse(sealJson)
            if (seal.kind != KIND_SEAL || !seal.isValid() || seal.tags.isNotEmpty()) return null
            val rumorJson = session.useSecretKey { sk ->
                val key = Nip44.conversationKey(sk, NostrKeys.fromHex(seal.pubkey))
                try {
                    Nip44.decrypt(seal.content, key)
                } finally {
                    key.fill(0)
                }
            }
            val rumor = Rumor.parse(rumorJson)
            // The sender of the seal must be the author of the message, otherwise anyone could write in the name of someone else
            if (rumor.pubkey != seal.pubkey) return null
            Unwrapped(rumor, seal.pubkey)
        } catch (e: Exception) {
            null
        }
    }

    private fun randomPast(now: Long): Long = now - (random.nextLong().and(Long.MAX_VALUE) % RANDOM_PAST_SECONDS)
}

// NIP-17 private direct messages
object DirectMessage {
    const val KIND_MESSAGE = 14

    // Two wraps: one for the recipient and one for the sender, so the sender sees the message on every device
    fun create(session: Session, recipientPubkey: String, text: String, now: Long = System.currentTimeMillis() / 1000): List<Event> {
        val own = NostrKeys.toHex(session.publicKey)
        val rumor = Rumor(own, now, KIND_MESSAGE, listOf(listOf("p", recipientPubkey)), text)
        return listOf(GiftWrap.wrap(session, rumor, recipientPubkey, now), GiftWrap.wrap(session, rumor, own, now))
    }

    fun read(session: Session, wrap: Event): Unwrapped? = GiftWrap.unwrap(session, wrap)?.takeIf { it.rumor.kind == KIND_MESSAGE }

    // Filter for the wraps addressed to this account
    fun inboxFilter(session: Session, since: Long? = null) =
        Filter(kinds = listOf(GiftWrap.KIND_WRAP), tags = mapOf("p" to listOf(NostrKeys.toHex(session.publicKey))), since = since)
}
