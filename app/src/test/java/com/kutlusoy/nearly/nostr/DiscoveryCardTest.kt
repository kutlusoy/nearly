package com.kutlusoy.nearly.nostr

import com.kutlusoy.nearly.chat.FakeRelay
import com.kutlusoy.nearly.chat.newSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiscoveryCardTest {
    private val now = 1_800_000_000L
    private val data = CardData(
        name = "Anna", age = 31, gender = "w", looking = "serious", languages = listOf("de", "en"),
        job = "Nurse", city = "Berlin", about = "Coffee and climbing", interests = listOf("Climbing", "Coffee"), cell = "u33db",
    )

    @Test
    fun geohashMatchesTheKnownExample() {
        assertEquals("u4pruydqqvj", Geohash.encode(57.64911, 10.40744, 11))
        val b = Geohash.bounds("u4pru")
        assertTrue(57.64911 in b.latLo..b.latHi && 10.40744 in b.lonLo..b.lonHi)
    }

    @Test
    fun aCellHasEightNeighborsAndTheDistanceIsAFewKilometers() {
        val cells = Geohash.withNeighbors("u33db")
        assertEquals(9, cells.size)
        assertEquals(9, cells.toSet().size)
        assertTrue("u33db" in cells)
        val far = cells.filter { it != "u33db" }.map { Geohash.distanceKm("u33db", it) }
        assertTrue(far.all { it in 2.0..8.0 }, "$far")
        assertEquals(0.0, Geohash.distanceKm("u33db", "u33db"), 1e-9)
        assertEquals(5, Geohash.lengthForRadius(10))
        assertEquals(4, Geohash.lengthForRadius(50))
        assertEquals(3, Geohash.lengthForRadius(160))
    }

    @Test
    fun aCardRoundTripsWithThreeCellLengthsAndAnExpiry() {
        val session = newSession()
        val event = CardCodec.create(session, data, now)
        assertEquals(CardCodec.KIND, event.kind)
        assertEquals(listOf("u33db", "u33d", "u33"), event.tagValues("g"))
        assertEquals((now + CardCodec.LIFETIME).toString(), event.tagValues("expiration").single())
        val card = assertNotNull(CardCodec.parse(event, now))
        assertEquals("Anna", card.name)
        assertEquals(31, card.age)
        assertEquals("w", card.gender)
        assertEquals("serious", card.looking)
        assertEquals(listOf("de", "en"), card.languages)
        assertEquals(listOf("Climbing", "Coffee"), card.interests)
        assertEquals("u33db", card.cell)
        assertEquals(event.pubkey, card.pubkey)
    }

    @Test
    fun badCardsAreDropped() {
        val session = newSession()
        fun event(content: String, tags: List<List<String>> = listOf(listOf("d", "nearly.card"), listOf("t", "nearly"), listOf("g", "u33db")), at: Long = now) =
            Event.sign(session, CardCodec.KIND, content, tags, at)
        val ok = """{"v":1,"name":"Anna","age":30}"""
        assertNotNull(CardCodec.parse(event(ok), now))
        assertNull(CardCodec.parse(event("""{"v":1,"name":"Anna","age":17}"""), now), "under 18")
        assertNull(CardCodec.parse(event("""{"v":1,"name":"Anna","age":100}"""), now), "over 99")
        assertNull(CardCodec.parse(event("""{"v":1,"name":"","age":30}"""), now), "no name")
        assertNull(CardCodec.parse(event("not json"), now))
        assertNull(CardCodec.parse(event(""), now), "hidden card")
        assertNull(CardCodec.parse(event(ok, listOf(listOf("d", "other"), listOf("t", "nearly"), listOf("g", "u33db"))), now), "other address")
        assertNull(CardCodec.parse(event(ok, listOf(listOf("d", "nearly.card"), listOf("g", "u33db"))), now), "no nearly tag")
        assertNull(CardCodec.parse(event(ok, listOf(listOf("d", "nearly.card"), listOf("t", "nearly"))), now), "no cell")
        assertNull(CardCodec.parse(event(ok, listOf(listOf("d", "nearly.card"), listOf("t", "nearly"), listOf("g", "u33"))), now), "cell too short")
        assertNull(CardCodec.parse(event(ok, listOf(listOf("d", "nearly.card"), listOf("t", "nearly"), listOf("g", "u33db"), listOf("expiration", (now - 1).toString()))), now), "expired")
        assertNull(CardCodec.parse(event(ok, at = now + 4000), now), "from the future")
        assertNull(CardCodec.parse(event(ok, at = now - CardCodec.LIFETIME - 10), now), "too old")
    }

    @Test
    fun foreignTextIsCleanedAndShortened() {
        val session = newSession()
        val content = """{"v":1,"name":"An\u0000na​","age":30,"gender":"z","looking":"marriage","about":"${"x".repeat(900)}","langs":["de","DE","toolong1","x"],"interests":["a","a"," ","b\u0007"]}"""
        val card = assertNotNull(CardCodec.parse(Event.sign(session, CardCodec.KIND, content, listOf(listOf("d", "nearly.card"), listOf("t", "nearly"), listOf("g", "u33db")), now), now))
        assertEquals("Anna", card.name)
        assertNull(card.gender)
        assertNull(card.looking)
        assertEquals(CardCodec.MAX_ABOUT, card.about.length)
        assertEquals(listOf("de"), card.languages)
        assertEquals(listOf("a", "b"), card.interests)
    }

    @Test
    fun theServiceFindsCardsByCellAndSeesHiding() {
        val relay = FakeRelay()
        val anna = newSession()
        val ben = newSession()
        val seen = mutableMapOf<String, DiscoveryCard?>()
        val watch = DiscoveryService(relay, ben).watch(Geohash.withNeighbors("u33db")) { event, card -> seen[event.pubkey] = card }
        DiscoveryService(relay, anna).publish(data, System.currentTimeMillis() / 1000)
        val annaKey = NostrKeys.toHex(anna.publicKey)
        assertEquals("Anna", seen[annaKey]?.name)

        DiscoveryService(relay, anna).hide("u33db", System.currentTimeMillis() / 1000 + 1)
        assertTrue(annaKey in seen)
        assertNull(seen[annaKey])

        val far = newSession()
        DiscoveryService(relay, far).publish(data.copy(cell = "dr5re"), System.currentTimeMillis() / 1000)
        assertTrue(NostrKeys.toHex(far.publicKey) !in seen, "a card from another area is not delivered")
        watch.close()
    }
}
