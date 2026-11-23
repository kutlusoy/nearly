package com.kutlusoy.nearly.chat

import com.kutlusoy.nearly.nostr.GiftWrap
import com.kutlusoy.nearly.nostr.Interest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MatchTest {
    private val relay = FakeRelay()
    private val anna = newSession()
    private val ben = newSession()
    private val annaStore = MemoryChatStore()
    private val benStore = MemoryChatStore()
    private val annaMatches = mutableListOf<String>()
    private val benMatches = mutableListOf<String>()
    private val annaChat = ChatService(relay, anna, annaStore, onMatch = { annaMatches += it }).also { it.start() }
    private val benChat = ChatService(relay, ben, benStore, onMatch = { benMatches += it }).also { it.start() }

    @Test
    fun aSignalAloneShowsNothing() {
        assertEquals(SwipeOutcome.Recorded, annaChat.swipe(ben.hex(), SwipeDecision.Like))
        assertTrue(benStore.likedMe(anna.hex()))
        assertTrue(benStore.matches().isEmpty() && benMatches.isEmpty() && benStore.conversations().isEmpty())
        assertTrue(annaStore.matches().isEmpty())
    }

    @Test
    fun bothLikesMakeAMatchOnBothPhones() {
        annaChat.swipe(ben.hex(), SwipeDecision.Like)
        assertEquals(SwipeOutcome.Match, benChat.swipe(anna.hex(), SwipeDecision.Like))
        assertEquals(listOf(anna.hex()), benStore.matches())
        assertEquals(listOf(ben.hex()), annaStore.matches())
        assertEquals(listOf(ben.hex()), annaMatches)
        assertEquals(listOf(anna.hex()), benMatches)
    }

    @Test
    fun theOrderDoesNotMatterAndItHappensOnce() {
        benChat.swipe(anna.hex(), SwipeDecision.Like)
        annaChat.swipe(ben.hex(), SwipeDecision.Like)
        annaChat.swipe(ben.hex(), SwipeDecision.Like)
        assertEquals(1, annaMatches.size)
        assertEquals(1, benMatches.size)
    }

    @Test
    fun aSkipOrLaterSendsNothing() {
        annaChat.swipe(ben.hex(), SwipeDecision.Later)
        annaChat.swipe(ben.hex(), SwipeDecision.Skip)
        assertTrue(relay.events.isEmpty())
        assertEquals("skip", annaStore.swipes()[ben.hex()]?.first)
    }

    @Test
    fun aBlockedPersonNeverMatches() {
        benStore.setBlocked(anna.hex(), true)
        annaChat.swipe(ben.hex(), SwipeDecision.Like)
        assertFalse(benStore.likedMe(anna.hex()))
        assertEquals(SwipeOutcome.Recorded, benChat.swipe(anna.hex(), SwipeDecision.Like))
        assertTrue(benStore.matches().isEmpty())
    }

    @Test
    fun atMostOneHundredLikesPerDay() {
        repeat(ChatService.MAX_LIKES_PER_DAY) { annaChat.swipe(newSession().hex(), SwipeDecision.Like) }
        assertEquals(SwipeOutcome.Limit, annaChat.swipe(newSession().hex(), SwipeDecision.Like))
        assertEquals(SwipeOutcome.Recorded, annaChat.swipe(newSession().hex(), SwipeDecision.Skip))
    }

    @Test
    fun theSignalHasItsOwnKindAndIsNotAChatMessage() {
        annaChat.swipe(ben.hex(), SwipeDecision.Like)
        val rumor = assertNotNull(GiftWrap.unwrap(ben, relay.events.single())).rumor
        assertEquals(Interest.KIND_INTEREST, rumor.kind)
        assertTrue(Interest.isInterest(rumor))
        assertFalse(Interest.isInterest(rumor.copy(content = "hi")))
    }

    @Test
    fun swipedPeopleStayOutOfTheDeckForTheirTime() {
        val day = 24 * 3600L
        assertTrue(Swipes.hides("like", 0, 400 * day))
        assertTrue(Swipes.hides("later", 0, 6 * day))
        assertFalse(Swipes.hides("later", 0, 8 * day))
        assertTrue(Swipes.hides("skip", 0, 29 * day))
        assertFalse(Swipes.hides("skip", 0, 31 * day))
    }
}
