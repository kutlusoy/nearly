package com.kutlusoy.nearly.nostr

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

// What a person shows in swipe and feed: the profile for strangers, with a coarse place
data class DiscoveryCard(
    val pubkey: String,
    val createdAt: Long,
    val name: String,
    val age: Int,
    val gender: String?,
    val looking: String?,
    val languages: List<String>,
    val job: String,
    val city: String,
    val about: String,
    val interests: List<String>,
    val cell: String,
)

// What the own phone writes into a card
data class CardData(
    val name: String,
    val age: Int,
    val gender: String? = null,
    val looking: String? = null,
    val languages: List<String> = emptyList(),
    val job: String = "",
    val city: String = "",
    val about: String = "",
    val interests: List<String> = emptyList(),
    val cell: String,
)

// An addressable event (kind 30078, NIP-78) with the tags nearly and g. Foreign cards are checked like every foreign event.
object CardCodec {
    const val KIND = 30078
    const val D = "nearly.card"
    const val TAG = "nearly"
    const val CELL_LENGTH = 5
    const val LIFETIME = 30 * 24 * 3600L
    const val MAX_NAME = 40
    const val MAX_ABOUT = 500
    const val MAX_TEXT = 60
    const val MAX_INTERESTS = 10
    const val MAX_LANGUAGES = 10
    const val MIN_AGE = 18
    const val MAX_AGE = 99
    private val GENDERS = setOf("w", "m", "x")
    private val GOALS = setOf("serious", "open", "casual", "fun", "friends", "unsure")
    private val LANGUAGE = Regex("[a-z]{2,3}")

    fun create(session: Session, data: CardData, now: Long = System.currentTimeMillis() / 1000): Event {
        require(Geohash.isCell(data.cell, CELL_LENGTH)) { "the cell needs $CELL_LENGTH characters" }
        val tags = listOf(
            listOf("d", D),
            listOf("t", TAG),
            listOf("g", data.cell),
            listOf("g", data.cell.take(4)),
            listOf("g", data.cell.take(3)),
            listOf("expiration", (now + LIFETIME).toString()),
        )
        val content = buildJsonObject {
            put("v", 1)
            put("name", data.name.take(MAX_NAME))
            put("age", data.age)
            data.gender?.let { put("gender", it) }
            data.looking?.let { put("looking", it) }
            put("langs", buildJsonArray { data.languages.take(MAX_LANGUAGES).forEach { add(JsonPrimitive(it)) } })
            put("job", data.job.take(MAX_TEXT))
            put("city", data.city.take(MAX_TEXT))
            put("about", data.about.take(MAX_ABOUT))
            put("interests", buildJsonArray { data.interests.take(MAX_INTERESTS).forEach { add(JsonPrimitive(it)) } })
        }.toString()
        return Event.sign(session, KIND, content, tags, now)
    }

    // Replaces the card with an empty one of the same address and the same cells, so it is no longer found and the readers of the area see the change
    fun hide(session: Session, cell: String, now: Long = System.currentTimeMillis() / 1000): Event {
        require(Geohash.isCell(cell, CELL_LENGTH)) { "the cell needs $CELL_LENGTH characters" }
        val tags = listOf(
            listOf("d", D), listOf("t", TAG), listOf("g", cell), listOf("g", cell.take(4)), listOf("g", cell.take(3)),
            listOf("expiration", (now + LIFETIME).toString()),
        )
        return Event.sign(session, KIND, "", tags, now)
    }

    fun filter(cells: List<String>, since: Long? = null, limit: Int = 200): Filter =
        Filter(kinds = listOf(KIND), tags = mapOf("t" to listOf(TAG), "g" to cells), since = since, limit = limit)

    fun parse(event: Event, now: Long = System.currentTimeMillis() / 1000): DiscoveryCard? {
        if (event.kind != KIND || event.tagValues("d").firstOrNull() != D || TAG !in event.tagValues("t")) return null
        event.tagValues("expiration").firstOrNull()?.toLongOrNull()?.let { if (it <= now) return null }
        if (event.createdAt > now + 300 || event.createdAt < now - LIFETIME) return null
        val cell = event.tagValues("g").firstOrNull { Geohash.isCell(it, CELL_LENGTH) } ?: return null
        val o: JsonObject = try {
            Json.parseToJsonElement(event.content).jsonObject
        } catch (e: Exception) {
            return null
        }
        val name = clean(o["name"]?.text(), MAX_NAME)
        val age = (o["age"] as? JsonPrimitive)?.intOrNull
        if (name.isBlank() || age == null || age !in MIN_AGE..MAX_AGE) return null
        return DiscoveryCard(
            pubkey = event.pubkey,
            createdAt = event.createdAt,
            name = name,
            age = age,
            gender = o["gender"]?.text()?.takeIf { it in GENDERS },
            looking = o["looking"]?.text()?.takeIf { it in GOALS },
            languages = (o["langs"] as? JsonArray).orEmpty().mapNotNull { it.text()?.lowercase()?.takeIf { c -> LANGUAGE.matches(c) } }.distinct().take(MAX_LANGUAGES),
            job = clean(o["job"]?.text(), MAX_TEXT),
            city = clean(o["city"]?.text(), MAX_TEXT),
            about = clean(o["about"]?.text(), MAX_ABOUT, keepNewlines = true),
            interests = (o["interests"] as? JsonArray).orEmpty().mapNotNull { clean(it.text(), 24).takeIf { s -> s.isNotBlank() } }.distinct().take(MAX_INTERESTS),
            cell = cell,
        )
    }

    private fun JsonElement.text(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    // Removes control characters and shortens, text with line breaks or invisible characters would break cards and lists
    private fun clean(value: String?, max: Int, keepNewlines: Boolean = false): String {
        if (value == null) return ""
        val kept = value.filter { c -> !c.isISOControl() || (keepNewlines && c == '\n') }
        return kept.replace(Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u206F\\uFEFF]"), "").trim().take(max)
    }
}

// Publishes the own card and reads the cards of an area
class DiscoveryService(private val transport: NostrTransport, private val session: Session) {
    fun publish(data: CardData, now: Long = System.currentTimeMillis() / 1000): Event =
        CardCodec.create(session, data, now).also { transport.publish(it) }

    fun hide(cell: String, now: Long = System.currentTimeMillis() / 1000): Event = CardCodec.hide(session, cell, now).also { transport.publish(it) }

    // Keeps the subscription open. The caller keeps the newest event of a key: a card, or null when the person hid the card.
    fun watch(cells: List<String>, onEvent: (event: Event, card: DiscoveryCard?) -> Unit): AutoCloseable =
        transport.subscribe(listOf(CardCodec.filter(cells)), { event ->
            if (event.kind == CardCodec.KIND && event.tagValues("d").firstOrNull() == CardCodec.D && event.createdAt <= System.currentTimeMillis() / 1000 + 300) {
                onEvent(event, CardCodec.parse(event))
            }
        })
}
