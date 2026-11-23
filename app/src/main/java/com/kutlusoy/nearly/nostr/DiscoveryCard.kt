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

// An answer to one of the fixed questions of the profile
data class CardPrompt(val q: Int, val a: String)

// The facts beyond the basics. Every value is optional, codes are the lower case names of the enums of the profile.
data class CardExtras(
    val pronouns: String = "",
    val school: String = "",
    val height: Int? = null,
    val drinking: String? = null,
    val smoking: String? = null,
    val exercise: String? = null,
    val orientation: String? = null,
    val family: String? = null,
    val zodiac: String? = null,
    val diet: String? = null,
    val pets: String = "",
    val link: String = "",
    val prompts: List<CardPrompt> = emptyList(),
)

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
    val photos: List<PhotoRef> = emptyList(),
    val extras: CardExtras = CardExtras(),
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
    val photos: List<PhotoRef> = emptyList(),
    val extras: CardExtras = CardExtras(),
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
    const val MAX_PHOTOS = 6
    const val MIN_AGE = 18
    const val MAX_AGE = 99
    private val GENDERS = setOf("w", "m", "x")
    private val GOALS = setOf("serious", "open", "casual", "fun", "friends", "unsure")
    private val LANGUAGE = Regex("[a-z]{2,3}")
    private val FREQUENCIES = setOf("never", "sometimes", "often")
    private val ORIENTATIONS = setOf("straight", "gay", "lesbian", "bisexual", "asexual", "pansexual", "other")
    private val FAMILY = setOf("want", "dont", "have", "unsure")
    private val ZODIAC = setOf("aries", "taurus", "gemini", "cancer", "leo", "virgo", "libra", "scorpio", "sagittarius", "capricorn", "aquarius", "pisces")
    private val DIETS = setOf("omnivore", "vegetarian", "vegan", "pescatarian", "other")
    private val LINK = Regex("[A-Za-z0-9@._:/#?=&%~+-]{1,80}")
    const val MAX_PRONOUNS = 20
    const val MAX_PETS = 40
    const val MAX_LINK = 80
    const val MAX_PROMPTS = 3
    const val PROMPT_COUNT = 10
    const val MAX_PROMPT_ANSWER = 140
    const val MIN_HEIGHT = 100
    const val MAX_HEIGHT = 250

    fun create(session: Session, data: CardData, now: Long = System.currentTimeMillis() / 1000): Event {
        require(Geohash.isCell(data.cell, CELL_LENGTH)) { "the cell needs $CELL_LENGTH characters" }
        val tags = listOf(
            listOf("d", D),
            listOf("t", TAG),
            listOf("g", data.cell),
            listOf("g", data.cell.take(4)),
            listOf("g", data.cell.take(3)),
            listOf("expiration", (now + LIFETIME).toString()),
        ) + data.photos.take(MAX_PHOTOS).map { PostCodec.imetaTag(it) }
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
            val x = data.extras
            if (x.pronouns.isNotBlank()) put("pro", x.pronouns.take(MAX_PRONOUNS))
            if (x.school.isNotBlank()) put("school", x.school.take(MAX_TEXT))
            x.height?.let { put("height", it) }
            x.drinking?.let { put("drink", it) }
            x.smoking?.let { put("smoke", it) }
            x.exercise?.let { put("ex", it) }
            x.orientation?.let { put("ori", it) }
            x.family?.let { put("fam", it) }
            x.zodiac?.let { put("zod", it) }
            x.diet?.let { put("diet", it) }
            if (x.pets.isNotBlank()) put("pets", x.pets.take(MAX_PETS))
            if (x.link.isNotBlank()) put("link", x.link.take(MAX_LINK))
            if (x.prompts.isNotEmpty()) {
                put("prompts", buildJsonArray {
                    x.prompts.take(MAX_PROMPTS).forEach { add(buildJsonObject { put("q", it.q); put("a", it.a.take(MAX_PROMPT_ANSWER)) }) }
                })
            }
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
            photos = PostCodec.parseImeta(event, MAX_PHOTOS),
            extras = parseExtras(o),
        )
    }

    // Each value is checked on its own, a bad one is dropped and the rest of the card stays
    private fun parseExtras(o: JsonObject): CardExtras {
        fun code(name: String, allowed: Set<String>) = o[name]?.text()?.takeIf { it in allowed }
        val prompts = (o["prompts"] as? JsonArray).orEmpty().mapNotNull { element ->
            val p = element as? JsonObject ?: return@mapNotNull null
            val q = (p["q"] as? JsonPrimitive)?.intOrNull?.takeIf { it in 0 until PROMPT_COUNT } ?: return@mapNotNull null
            val a = clean(p["a"]?.text(), MAX_PROMPT_ANSWER, keepNewlines = true).takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CardPrompt(q, a)
        }.distinctBy { it.q }.take(MAX_PROMPTS)
        return CardExtras(
            pronouns = clean(o["pro"]?.text(), MAX_PRONOUNS),
            school = clean(o["school"]?.text(), MAX_TEXT),
            height = (o["height"] as? JsonPrimitive)?.intOrNull?.takeIf { it in MIN_HEIGHT..MAX_HEIGHT },
            drinking = code("drink", FREQUENCIES),
            smoking = code("smoke", FREQUENCIES),
            exercise = code("ex", FREQUENCIES),
            orientation = code("ori", ORIENTATIONS),
            family = code("fam", FAMILY),
            zodiac = code("zod", ZODIAC),
            diet = code("diet", DIETS),
            pets = clean(o["pets"]?.text(), MAX_PETS),
            link = clean(o["link"]?.text(), MAX_LINK).takeIf { LINK.matches(it) } ?: "",
            prompts = prompts,
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

    // The newest own card, to restore the profile on a new phone. Gives null when there is none or it was hidden.
    fun loadOwn(onResult: (DiscoveryCard?) -> Unit) {
        val own = NostrKeys.toHex(session.publicKey)
        var newest: Event? = null
        var done = false
        var subscription: AutoCloseable? = null
        subscription = transport.subscribe(
            listOf(Filter(kinds = listOf(CardCodec.KIND), authors = listOf(own), tags = mapOf("d" to listOf(CardCodec.D)), limit = 5)),
            { event -> if (event.pubkey == own) newest = Replaceable.newest(listOfNotNull(newest, event)) },
            {
                if (!done) {
                    done = true
                    onResult(newest?.let { CardCodec.parse(it) })
                    subscription?.close()
                }
            },
        )
        if (done) subscription.close()
    }

    // Asks the relays to delete the card (NIP-09) and empties the public profile, used when the account is deleted
    fun deleteRequest(now: Long = System.currentTimeMillis() / 1000): Event {
        val own = NostrKeys.toHex(session.publicKey)
        val event = Event.sign(session, 5, "", listOf(listOf("a", "${CardCodec.KIND}:$own:${CardCodec.D}")), now)
        transport.publish(event)
        return event
    }

    // Keeps the subscription open. The caller keeps the newest event of a key: a card, or null when the person hid the card.
    fun watch(cells: List<String>, onEvent: (event: Event, card: DiscoveryCard?) -> Unit): AutoCloseable =
        transport.subscribe(listOf(CardCodec.filter(cells)), { event ->
            if (event.kind == CardCodec.KIND && event.tagValues("d").firstOrNull() == CardCodec.D && event.createdAt <= System.currentTimeMillis() / 1000 + 300) {
                onEvent(event, CardCodec.parse(event))
            }
        })
}
