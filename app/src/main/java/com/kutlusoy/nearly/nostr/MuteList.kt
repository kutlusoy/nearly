package com.kutlusoy.nearly.nostr

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

// NIP-51 mute list (kind 10000). Keys written by Nearly are private items: a JSON list of tags encrypted to the own key.
data class MuteList(
    val publicTags: List<List<String>> = emptyList(),
    val privateTags: List<List<String>> = emptyList(),
    val createdAt: Long = 0,
) {
    val keys: Set<String> get() = (publicTags + privateTags).filter { it.size >= 2 && it[0] == "p" }.map { it[1] }.toSet()

    fun add(key: String): MuteList = if (key in keys || keys.size >= MuteCodec.MAX_KEYS) this else copy(privateTags = privateTags + listOf(listOf("p", key)))

    fun remove(key: String): MuteList {
        val keep = { tags: List<List<String>> -> tags.filterNot { it.size >= 2 && it[0] == "p" && it[1] == key } }
        return copy(publicTags = keep(publicTags), privateTags = keep(privateTags))
    }
}

object MuteCodec {
    const val KIND = 10000
    const val MAX_KEYS = 5_000
    private val HEX64 = Regex("[0-9a-f]{64}")

    // Null when the content cannot be read, for example an older format of another app. Such a list MUST NOT be overwritten.
    fun parse(session: Session, event: Event): MuteList? {
        if (event.kind != KIND || event.pubkey != NostrKeys.toHex(session.publicKey)) return null
        val privateTags = if (event.content.isEmpty()) emptyList() else runCatching {
            val key = session.useSecretKey { Nip44.conversationKey(it, session.publicKey) }
            try {
                Json.parseToJsonElement(Nip44.decrypt(event.content, key)).jsonArray.map { tag -> tag.jsonArray.map { it.jsonPrimitive.content } }
            } finally {
                key.fill(0)
            }
        }.getOrNull() ?: return null
        val valid = { tags: List<List<String>> -> tags.filter { it.isNotEmpty() && (it[0] != "p" || (it.size >= 2 && HEX64.matches(it[1]))) } }
        return MuteList(valid(event.tags), valid(privateTags), event.createdAt)
    }

    fun sign(session: Session, list: MuteList, now: Long): Event {
        val json = JsonArray(list.privateTags.map { tag -> JsonArray(tag.map { JsonPrimitive(it) }) }).toString()
        val key = session.useSecretKey { Nip44.conversationKey(it, session.publicKey) }
        val content = try {
            Nip44.encrypt(json, key)
        } finally {
            key.fill(0)
        }
        return Event.sign(session, KIND, content, list.publicTags, maxOf(now, list.createdAt + 1))
    }
}

class MuteNotLoadedException : IllegalStateException("the mute list is not loaded, a change would overwrite it")

enum class MuteLoad { Found, None, Unreadable }

// Same rule as for the follow list: a change starts only from a list that was read, or from the confirmed absence of any list
class MuteService(private val transport: NostrTransport, private val session: Session) {
    @Volatile
    var current: MuteList? = null
        private set

    @Volatile
    private var unreadable = false

    val isLoaded: Boolean get() = current != null

    fun load(onDone: (MuteLoad) -> Unit) {
        val own = NostrKeys.toHex(session.publicKey)
        val found = mutableListOf<Event>()
        var handle: AutoCloseable? = null
        handle = transport.subscribe(
            listOf(Filter(kinds = listOf(MuteCodec.KIND), authors = listOf(own), limit = 5)),
            { if (it.pubkey == own) synchronized(found) { found.add(it) } },
            {
                handle?.close()
                val newest = synchronized(found) { Replaceable.newest(found) }
                val list = newest?.let { MuteCodec.parse(session, it) }
                unreadable = newest != null && list == null
                if (list != null) current = list
                onDone(if (list != null) MuteLoad.Found else if (unreadable) MuteLoad.Unreadable else MuteLoad.None)
            },
        )
    }

    // Only when no relay has a list: a new account, or a list that was never written
    fun startEmpty() {
        if (current == null && !unreadable) current = MuteList()
    }

    fun change(now: Long = System.currentTimeMillis() / 1000, edit: (MuteList) -> MuteList): Event? {
        val base = current ?: throw MuteNotLoadedException()
        val next = edit(base)
        if (next == base) return null
        val event = MuteCodec.sign(session, next, now)
        current = next.copy(createdAt = event.createdAt)
        transport.publish(event)
        return event
    }
}
