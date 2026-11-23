package com.kutlusoy.nearly.discovery

import androidx.compose.ui.graphics.Color
import com.kutlusoy.nearly.network.NetworkLink
import com.kutlusoy.nearly.nostr.CardData
import com.kutlusoy.nearly.nostr.DiscoveryCard
import com.kutlusoy.nearly.nostr.DiscoveryService
import com.kutlusoy.nearly.nostr.Event
import com.kutlusoy.nearly.nostr.Geohash
import com.kutlusoy.nearly.nostr.NostrKeys
import com.kutlusoy.nearly.nostr.PhotoRef
import com.kutlusoy.nearly.nostr.Session
import com.kutlusoy.nearly.ui.Gender
import com.kutlusoy.nearly.ui.LookingFor
import com.kutlusoy.nearly.ui.Person
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Where the phone is, as a cell of about 5 km. The coordinates stay inside the source.
interface AreaSource {
    fun hasPermission(): Boolean

    // Calls back with the cell, or null when the place is not known (yet)
    fun current(onResult: (String?) -> Unit)
}

data class NearbyState(
    val cell: String? = null,
    val permission: Boolean = false,
    val people: List<Person> = emptyList(),
)

// The people near the user: reads the discovery cards of the area and publishes the own card
class NearbyController(
    private val link: NetworkLink,
    private val area: AreaSource,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val _state = MutableStateFlow(NearbyState())
    val state: StateFlow<NearbyState> = _state.asStateFlow()

    private var session: Session? = null
    private var own = ""
    private var service: DiscoveryService? = null
    private var watch: AutoCloseable? = null
    private var watchKey = ""
    private val cards = HashMap<String, Pair<Event, DiscoveryCard?>>()
    private var lastPublished = 0L
    private var blocked: Set<String> = emptySet()
    private var radiusKm = 25
    private var pending: CardSnapshot? = null

    // What the own card needs from the profile and the settings
    data class CardSnapshot(
        val name: String,
        val age: Int?,
        val gender: Gender?,
        val looking: LookingFor?,
        val languages: List<String>,
        val job: String,
        val city: String,
        val about: String,
        val interests: List<String>,
        val visible: Boolean,
        val photos: List<PhotoRef> = emptyList(),
        val extras: com.kutlusoy.nearly.nostr.CardExtras = com.kutlusoy.nearly.nostr.CardExtras(),
    )

    fun open(opened: Session) {
        close()
        session = opened
        own = NostrKeys.toHex(opened.publicKey)
        service = DiscoveryService(link.transport, opened)
        _state.value = NearbyState(permission = area.hasPermission())
        refreshArea()
    }

    fun close() {
        watch?.close()
        watch = null
        watchKey = ""
        service = null
        session = null
        own = ""
        pending = null
        lastPublished = 0L
        synchronized(cards) { cards.clear() }
        _state.value = NearbyState()
    }

    // Asks the source for the place again, after the permission was given or the app came back
    fun refreshArea() {
        if (session == null) return
        _state.update { it.copy(permission = area.hasPermission()) }
        area.current { cell ->
            if (cell != null && Geohash.isCell(cell, 5) && cell != _state.value.cell) {
                _state.update { it.copy(cell = cell) }
                publishPending()
                startWatch()
                rebuild()
            }
        }
    }

    // Reads the own card from the relays, to fill the profile on a new phone
    fun restoreOwn(onCard: (DiscoveryCard) -> Unit) {
        service?.loadOwn { card -> if (card != null) onCard(card) }
    }

    // The card is hidden and the relays are asked to delete it
    fun deleteCard() {
        val svc = service ?: return
        _state.value.cell?.let { svc.hide(it, maxOf(clock(), lastPublished + 1)) }
        svc.deleteRequest(clock())
        pending = null
    }

    fun setRadius(km: Int) {
        if (km == radiusKm) return
        radiusKm = km
        startWatch()
        rebuild()
    }

    fun setBlocked(keys: Set<String>) {
        blocked = keys
        rebuild()
    }

    // Writes the own card, or hides it. The card needs a place and an age, without them nothing is published.
    fun publish(snapshot: CardSnapshot) {
        pending = snapshot
        publishPending()
    }

    private fun publishPending() {
        val s = pending ?: return
        val svc = service ?: return
        val cell = _state.value.cell ?: return
        // A replacement needs a later time than the card it replaces, also when two changes happen in the same second
        val at = maxOf(clock(), lastPublished + 1)
        lastPublished = at
        if (!s.visible) {
            svc.hide(cell, at)
            return
        }
        val age = s.age ?: return
        svc.publish(
            CardData(
                name = s.name, age = age,
                gender = s.gender?.let { GENDER_CODE.getValue(it) },
                looking = s.looking?.let { it.name.lowercase() },
                languages = s.languages, job = s.job, city = s.city, about = s.about, interests = s.interests, cell = cell, photos = s.photos, extras = s.extras,
            ),
            at,
        )
    }

    private fun startWatch() {
        val svc = service ?: return
        val cell = _state.value.cell ?: return
        val length = Geohash.lengthForRadius(radiusKm)
        val key = cell.take(length) + length
        if (key == watchKey) return
        watch?.close()
        watchKey = key
        watch = svc.watch(Geohash.withNeighbors(cell.take(length))) { event, card ->
            synchronized(cards) {
                val old = cards[event.pubkey]?.first
                if (old == null || event.createdAt > old.createdAt || (event.createdAt == old.createdAt && event.id < old.id)) cards[event.pubkey] = event to card
            }
            rebuild()
        }
    }

    private fun rebuild() {
        val mine = _state.value.cell ?: return
        val now = clock()
        val people = synchronized(cards) {
            cards.values.mapNotNull { it.second }
        }.filter { it.pubkey != own && it.pubkey !in blocked }.map { person(it, mine, now) }
        _state.update { it.copy(people = people.sortedBy { p -> p.distanceKm }) }
    }

    companion object {
        val GENDER_CODE = mapOf(Gender.Woman to "w", Gender.Man to "m", Gender.NonBinary to "x")

        private val LANGUAGE_NAMES = mapOf(
            "en" to "English", "de" to "Deutsch", "fr" to "Français", "es" to "Español", "it" to "Italiano", "pt" to "Português",
            "nl" to "Nederlands", "pl" to "Polski", "tr" to "Türkçe", "sv" to "Svenska", "da" to "Dansk", "no" to "Norsk", "nb" to "Norsk",
            "fi" to "Suomi", "cs" to "Čeština", "ro" to "Română", "hu" to "Magyar", "el" to "Ελληνικά", "ru" to "Русский", "uk" to "Українська",
        )

        fun languageName(code: String): String? = LANGUAGE_NAMES[code]

        fun languageCode(name: String): String? = LANGUAGE_NAMES.entries.firstOrNull { it.value == name }?.key

        // A card as a person for the swipe deck and the feed. Without photos the card gets two colors that follow from the key.
        fun person(card: DiscoveryCard, ownCell: String, now: Long): Person {
            val h = card.pubkey.take(12).chunked(2).map { it.toInt(16) }
            val from = Color((0xFF shl 24) or ((h[0] or 0x60) shl 16) or ((h[1] or 0x60) shl 8) or (h[2] or 0x60))
            val to = Color((0xFF shl 24) or ((h[3] or 0x80) shl 16) or ((h[4] or 0x80) shl 8) or (h[5] or 0x80))
            val gender = card.gender?.let { code -> GENDER_CODE.entries.firstOrNull { it.value == code }?.key }
            return Person(
                id = card.pubkey,
                name = card.name,
                age = card.age,
                distanceKm = Geohash.distanceKm(ownCell, card.cell).coerceAtLeast(1.0),
                bio = card.about,
                from = from,
                to = to,
                gender = gender ?: Gender.NonBinary,
                lookingFor = card.looking?.let { n -> LookingFor.values().firstOrNull { it.name.lowercase() == n } } ?: LookingFor.Unsure,
                interests = card.interests,
                activeDaysAgo = ((now - card.createdAt) / 86400).toInt().coerceAtLeast(0),
                photos = card.photos.size.coerceAtLeast(1),
                job = card.job.ifBlank { null },
                city = card.city.ifBlank { null },
                languages = card.languages.mapNotNull { LANGUAGE_NAMES[it] }.distinct(),
                key = card.pubkey,
                genderKnown = gender != null,
                photoRefs = card.photos,
                extras = card.extras,
            )
        }
    }
}
