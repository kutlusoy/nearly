package com.kutlusoy.nearly.nostr

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface PresenceMessage {
    data object Hello : PresenceMessage
    data class Here(val cell: String) : PresenceMessage
}

// Presence between friends. Both messages are rumors of their own kind inside a gift wrap for one person, so relays see neither the cell nor
// the sender, and nobody but the friend can read where the user is. `hello` says "I follow you", it carries no place. `here` carries the cell.
object Presence {
    const val KIND = 24314
    const val HERE_LIFETIME = 180L
    const val HELLO_LIFETIME = 7 * 24 * 3600L
    private val CELL_LENGTHS = 5..6

    fun hello(session: Session, recipient: String, now: Long): Event =
        GiftWrap.wrap(session, rumor(session, recipient, now, "{\"v\":1,\"t\":\"hello\"}"), recipient, now, expiresAt = now + HELLO_LIFETIME)

    fun here(session: Session, recipient: String, cell: String, now: Long): Event {
        require(Geohash.isCell(cell) && cell.length in CELL_LENGTHS) { "invalid cell" }
        return GiftWrap.wrap(session, rumor(session, recipient, now, "{\"v\":1,\"t\":\"here\",\"g\":\"$cell\"}"), recipient, now, expiresAt = now + HERE_LIFETIME)
    }

    private fun rumor(session: Session, recipient: String, now: Long, content: String) =
        Rumor(NostrKeys.toHex(session.publicKey), now, KIND, listOf(listOf("p", recipient)), content)

    fun parse(rumor: Rumor): PresenceMessage? {
        if (rumor.kind != KIND || rumor.content.length > 200) return null
        val o: JsonObject = runCatching { Json.parseToJsonElement(rumor.content).jsonObject }.getOrNull() ?: return null
        if (o["v"]?.jsonPrimitive?.intOrNull != 1) return null
        return when (o["t"]?.jsonPrimitive?.contentOrNull) {
            "hello" -> PresenceMessage.Hello
            "here" -> o["g"]?.jsonPrimitive?.contentOrNull?.takeIf { Geohash.isCell(it) && it.length in CELL_LENGTHS }?.let { PresenceMessage.Here(it) }
            else -> null
        }
    }
}
