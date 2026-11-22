package com.kutlusoy.nearly.nostr

import com.kutlusoy.nearly.chat.newSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DiscoveryCardPhotosTest {
    private val now = 1_800_000_000L
    private fun hash(n: Int) = BlossomClient.sha256Hex(byteArrayOf(n.toByte()))
    private fun photo(n: Int) = PhotoRef("https://cdn.example/${hash(n)}.jpg", "image/jpeg", hash(n), 1200, 1600, "photo $n", "LEHV6nWB2yk8pyo0adR*.7kCMdnj")
    private val data = CardData(name = "Anna", age = 31, cell = "u33db")

    @Test
    fun photosTravelInTheCardInOrderAndAreCapped() {
        val session = newSession()
        val event = CardCodec.create(session, data.copy(photos = (1..9).map { photo(it) }), now)
        assertEquals(CardCodec.MAX_PHOTOS, event.tags.count { it.firstOrNull() == "imeta" })
        val card = assertNotNull(CardCodec.parse(event, now))
        assertEquals((1..6).map { photo(it) }, card.photos)
    }

    @Test
    fun aCardWithoutPhotosStaysValid() {
        val card = assertNotNull(CardCodec.parse(CardCodec.create(newSession(), data, now), now))
        assertTrue(card.photos.isEmpty())
    }

    @Test
    fun foreignPhotoTagsAreCheckedOneByOne() {
        val session = newSession()
        val good = PostCodec.imetaTag(photo(1))
        val tags = listOf(
            listOf("d", "nearly.card"), listOf("t", "nearly"), listOf("g", "u33db"),
            listOf("imeta", "url http://insecure.example/x.jpg", "m image/jpeg", "x ${hash(2)}"),
            listOf("imeta", "url https://cdn.example/a.jpg", "m text/html", "x ${hash(3)}"),
            listOf("imeta", "url https://cdn.example/b.jpg", "m image/jpeg", "x not-a-hash"),
            good,
        )
        val event = Event.sign(session, CardCodec.KIND, """{"v":1,"name":"Anna","age":30}""", tags, now)
        val card = assertNotNull(CardCodec.parse(event, now))
        assertEquals(listOf(photo(1)), card.photos)
    }

    @Test
    fun aPhotoWithoutHttpsCannotBeWritten() {
        val bad = photo(1).copy(url = "http://cdn.example/x.jpg")
        kotlin.test.assertFailsWith<IllegalArgumentException> { CardCodec.create(newSession(), data.copy(photos = listOf(bad)), now) }
    }
}
