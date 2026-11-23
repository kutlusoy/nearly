package com.kutlusoy.nearly.nostr

import com.kutlusoy.nearly.chat.FakeRelay
import com.kutlusoy.nearly.chat.newSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardExtrasTest {
    private val now = System.currentTimeMillis() / 1000
    private val full = CardExtras(
        pronouns = "she/her", school = "TU Berlin", height = 172, drinking = "sometimes", smoking = "never", exercise = "often",
        orientation = "bisexual", family = "unsure", zodiac = "leo", diet = "vegetarian", pets = "A dog", link = "instagram.com/anna_b",
        prompts = listOf(CardPrompt(0, "Coffee and a long walk"), CardPrompt(6, "Maps")),
    )

    private fun card(extras: CardExtras) = assertNotNull(CardCodec.parse(CardCodec.create(newSession(), CardData("Anna", 31, cell = "u33db", extras = extras), now), now))

    @Test
    fun theFactsTravelInTheCard() {
        assertEquals(full, card(full).extras)
    }

    @Test
    fun aCardWithoutFactsStaysValid() {
        assertEquals(CardExtras(), card(CardExtras()).extras)
    }

    @Test
    fun badValuesAreDroppedOneByOne() {
        val tags = listOf(listOf("d", "nearly.card"), listOf("t", "nearly"), listOf("g", "u33db"))
        val content = """{"v":1,"name":"Anna","age":30,"pro":"she\u0000/her","height":9000,"drink":"always","ori":"gay","fam":"maybe","zod":"leo","diet":"carnivore",
            "link":"has spaces and <b>","pets":"Cat","prompts":[{"q":99,"a":"x"},{"q":1,"a":"  "},{"q":2,"a":"Fine"},{"q":2,"a":"Twice"},{"q":3,"a":"a"},{"q":4,"a":"b"},{"q":5,"a":"c"}]}"""
        val x = assertNotNull(CardCodec.parse(Event.sign(newSession(), CardCodec.KIND, content, tags, now), now)).extras
        assertEquals("she/her", x.pronouns)
        assertNull(x.height)
        assertNull(x.drinking)
        assertEquals("gay", x.orientation)
        assertNull(x.family)
        assertEquals("leo", x.zodiac)
        assertNull(x.diet)
        assertEquals("", x.link)
        assertEquals("Cat", x.pets)
        assertEquals(listOf(CardPrompt(2, "Fine"), CardPrompt(3, "a"), CardPrompt(4, "b")), x.prompts)
    }

    @Test
    fun theOwnCardCanBeReadAgainOnANewPhone() {
        val relay = FakeRelay()
        val session = newSession()
        val service = DiscoveryService(relay, session)
        var got: DiscoveryCard? = null
        service.loadOwn { got = it }
        assertNull(got)
        service.publish(CardData("Anna", 31, cell = "u33db", about = "Hi", extras = full), now)
        service.publish(CardData("Anna", 31, cell = "u33db", about = "Newer", extras = full), now + 5)
        service.loadOwn { got = it }
        assertEquals("Newer", got?.about)
        assertEquals(full, got?.extras)
        service.hide("u33db", now + 10)
        got = DiscoveryCard("x", 0, "x", 18, null, null, emptyList(), "", "", "", emptyList(), "u33db")
        service.loadOwn { got = it }
        assertNull(got, "a hidden card is no profile to restore")
    }

    @Test
    fun deletingTheAccountAsksTheRelaysToDeleteTheCard() {
        val relay = FakeRelay()
        val session = newSession()
        val service = DiscoveryService(relay, session)
        service.publish(CardData("Anna", 31, cell = "u33db"), now)
        val request = service.deleteRequest(now + 1)
        assertEquals(5, request.kind)
        assertTrue(request.tagValues("a").single().endsWith(":${CardCodec.D}"))
    }
}
