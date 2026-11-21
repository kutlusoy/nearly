package com.kutlusoy.nearly.nostr

import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

// NIP-01 event
data class Event(
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String,
) {
    // True only when the id matches the content and the signature matches the id and the key
    fun isValid(): Boolean {
        if (id != computeId(pubkey, createdAt, kind, tags, content)) return false
        return runCatching { Schnorr.verify(NostrKeys.fromHex(id), NostrKeys.fromHex(sig), NostrKeys.fromHex(pubkey)) }.getOrDefault(false)
    }

    fun toJson(): String = buildString {
        append("{\"id\":\"").append(id).append("\",\"pubkey\":\"").append(pubkey)
        append("\",\"created_at\":").append(createdAt).append(",\"kind\":").append(kind)
        append(",\"tags\":").append(tagsJson(tags))
        append(",\"content\":").append(quote(content))
        append(",\"sig\":\"").append(sig).append("\"}")
    }

    fun tagValues(name: String): List<String> = tags.filter { it.size >= 2 && it[0] == name }.map { it[1] }

    companion object {
        fun computeId(pubkey: String, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): String {
            val serialized = "[0,\"$pubkey\",$createdAt,$kind,${tagsJson(tags)},${quote(content)}]"
            return NostrKeys.toHex(MessageDigest.getInstance("SHA-256").digest(serialized.toByteArray(Charsets.UTF_8)))
        }

        fun sign(session: Session, kind: Int, content: String, tags: List<List<String>> = emptyList(), createdAt: Long = System.currentTimeMillis() / 1000): Event {
            val pubkey = NostrKeys.toHex(session.publicKey)
            val id = computeId(pubkey, createdAt, kind, tags, content)
            val sig = session.useSecretKey { Schnorr.sign(NostrKeys.fromHex(id), it) }
            return Event(id, pubkey, createdAt, kind, tags, content, NostrKeys.toHex(sig))
        }

        fun parse(text: String): Event = fromJson(Json.parseToJsonElement(text))

        fun fromJson(element: JsonElement): Event {
            val o: JsonObject = element.jsonObject
            fun text(name: String) = requireNotNull(o[name]?.jsonPrimitive?.contentOrNull) { "missing $name" }
            return Event(
                id = text("id"),
                pubkey = text("pubkey"),
                createdAt = requireNotNull(o["created_at"]).jsonPrimitive.long,
                kind = requireNotNull(o["kind"]).jsonPrimitive.int,
                tags = requireNotNull(o["tags"]).jsonArray.map { tag -> tag.jsonArray.map { it.jsonPrimitive.content } },
                content = text("content"),
                sig = text("sig"),
            )
        }

        private fun tagsJson(tags: List<List<String>>) = tags.joinToString(",", "[", "]") { tag -> tag.joinToString(",", "[", "]") { quote(it) } }

        // NIP-01: escape only these characters, everything else is written as is
        internal fun quote(value: String): String = buildString {
            append('"')
            for (c in value) {
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '\b' -> append("\\b")
                    '\u000c' -> append("\\f")
                    else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
                }
            }
            append('"')
        }
    }
}

// Subscription filter
data class Filter(
    val ids: List<String> = emptyList(),
    val authors: List<String> = emptyList(),
    val kinds: List<Int> = emptyList(),
    val tags: Map<String, List<String>> = emptyMap(),
    val since: Long? = null,
    val until: Long? = null,
    val limit: Int? = null,
) {
    fun toJson(): String {
        val parts = mutableListOf<String>()
        if (ids.isNotEmpty()) parts.add("\"ids\":" + ids.joinToString(",", "[", "]") { Event.quote(it) })
        if (authors.isNotEmpty()) parts.add("\"authors\":" + authors.joinToString(",", "[", "]") { Event.quote(it) })
        if (kinds.isNotEmpty()) parts.add("\"kinds\":" + kinds.joinToString(",", "[", "]"))
        tags.forEach { (name, values) -> parts.add(Event.quote("#$name") + ":" + values.joinToString(",", "[", "]") { Event.quote(it) }) }
        since?.let { parts.add("\"since\":$it") }
        until?.let { parts.add("\"until\":$it") }
        limit?.let { parts.add("\"limit\":$it") }
        return parts.joinToString(",", "{", "}")
    }
}

sealed interface RelayMessage {
    data class EventMessage(val subscription: String, val event: Event) : RelayMessage
    data class Eose(val subscription: String) : RelayMessage
    data class Ok(val eventId: String, val accepted: Boolean, val message: String) : RelayMessage
    data class Notice(val message: String) : RelayMessage
    data class Closed(val subscription: String, val message: String) : RelayMessage

    companion object {
        // Returns null for messages that are unknown or malformed, a relay must never crash the client
        fun parse(text: String): RelayMessage? = try {
            val array: JsonArray = Json.parseToJsonElement(text).jsonArray
            when (array[0].jsonPrimitive.content) {
                "EVENT" -> EventMessage(array[1].jsonPrimitive.content, Event.fromJson(array[2]))
                "EOSE" -> Eose(array[1].jsonPrimitive.content)
                "OK" -> Ok(array[1].jsonPrimitive.content, array[2].jsonPrimitive.content.toBoolean(), array.getOrNull(3)?.jsonPrimitive?.content.orEmpty())
                "NOTICE" -> Notice(array[1].jsonPrimitive.content)
                "CLOSED" -> Closed(array[1].jsonPrimitive.content, array.getOrNull(2)?.jsonPrimitive?.content.orEmpty())
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
