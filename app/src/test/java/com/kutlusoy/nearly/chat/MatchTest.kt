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
    private val queue = mutableListOf<Pair<Long, Runnable>>()
    private val manual: (Long, Runnable) -> AutoCloseable = { seconds, task ->
        val entry = seconds to task
        queue += entry
        AutoCloseable { queue.remove(entry) }
    }
    private fun runLater() = queue.toList().also { queue.clear() }.forEach { it.second.run() }
    private val annaChat = ChatService(relay, anna, annaStore, onMatch = { annaMatches += it }, later = manual).also { it.start() }
    private val benChat = ChatService(relay, ben, benStore, onMatch = { benMatches += it }, later = manual).also { it.start() }

    @Test
    fun aSignalAloneShowsNothing() {
        assertEquals(SwipeOutcome.Pending, annaChat.swipe(ben.hex(), SwipeDecision.Like))
        runLater()
        assertTrue(benStore.likedMe(anna.hex()))
        assertTrue(benStore.matches().isEmpty() && benMatches.isEmpty() && benStore.conversations().isEmpty())
        assertTrue(annaStore.matches().isEmpty())
    }

    @Test
    fun bothLikesMakeAMatchOnBothPhones() {
        annaChat.swipe(ben.hex(), SwipeDecision.Like)
        runLater()
        assertEquals(SwipeOutcome.Match, benChat.swipe(anna.hex(), SwipeDecision.Like))
        assertEquals(listOf(anna.hex()), benStore.matches())
        assertEquals(listOf(ben.hex()), annaStore.matches())
        assertEquals(listOf(ben.hex()), annaMatches)
        assertEquals(listOf(anna.hex()), benMatches)
    }

    @Test
    fun theOrderDoesNotMatterAndItHappensOnce() {
        benChat.swipe(anna.hex(), SwipeDecision.Like)
        runLater()
        annaChat.swipe(ben.hex(), SwipeDecision.Like)
        annaChat.swipe(ben.hex(), SwipeDecision.Like)
        runLater()
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
        runLater()
        assertFalse(benStore.likedMe(anna.hex()))
        assertEquals(SwipeOutcome.Pending, benChat.swipe(anna.hex(), SwipeDecision.Like))
        runLater()
        assertTrue(benStore.matches().isEmpty())
    }

    @Test
    fun atMostOneHundredLikesPerDay() {
        repeat(ChatService.MAX_LIKES_PER_DAY) { annaChat.swipe(newSession().hex(), SwipeDecision.Like) }
        runLater()
        assertEquals(SwipeOutcome.Limit, annaChat.swipe(newSession().hex(), SwipeDecision.Like))
        assertEquals(SwipeOutcome.Recorded, annaChat.swipe(newSession().hex(), SwipeDecision.Skip))
    }

    @Test
    fun theSignalHasItsOwnKindAndIsNotAChatMessage() {
        annaChat.swipe(ben.hex(), SwipeDecision.Like)
        runLater()
        val rumor = assertNotNull(GiftWrap.unwrap(ben, relay.events.single())).rumor
        assertEquals(Interest.KIND_INTEREST, rumor.kind)
        assertTrue(Interest.isInterest(rumor))
        assertFalse(Interest.isInterest(rumor.copy(content = "hi")))
    }

    @Test
    fun aLikeCanBeTakenBackBeforeTheSignalLeaves() {
        assertEquals(SwipeOutcome.Pending, annaChat.swipe(ben.hex(), SwipeDecision.Like))
        assertTrue(relay.events.isEmpty(), "the signal waits")
        assertTrue(annaChat.undoSwipe(ben.hex()))
        runLater()
        assertTrue(relay.events.isEmpty(), "nothing ever left")
        assertFalse(Swipes.hides(annaStore.swipes()[ben.hex()]!!.first, annaStore.swipes()[ben.hex()]!!.second, 100), "the person is back in the deck")
        assertFalse(annaChat.undoSwipe(ben.hex()))
    }

    @Test
    fun aLikeCannotBeTakenBackAfterTheSignalLeft() {
        annaChat.swipe(ben.hex(), SwipeDecision.Like)
        runLater()
        assertEquals(1, relay.events.size)
        assertFalse(annaChat.undoSwipe(ben.hex()))
    }

    @Test
    fun whenThePersonLikedFirstTheMatchComesAtOnceAndNothingWaits() {
        benChat.swipe(anna.hex(), SwipeDecision.Like)
        runLater()
        assertEquals(SwipeOutcome.Match, annaChat.swipe(ben.hex(), SwipeDecision.Like))
        assertTrue(queue.isEmpty())
    }

    @Test
    fun closingTheAppSendsALikeThatWasStillWaiting() {
        annaChat.swipe(ben.hex(), SwipeDecision.Like)
        annaChat.stop()
        assertEquals(1, relay.events.size)
    }

    @Test
    fun theSignalExpiresAfterThirtyDays() {
        val now = 1_800_000_000L
        val wrap = Interest.wrap(anna, ben.hex(), now)
        assertEquals((now + Interest.LIFETIME).toString(), wrap.tagValues("expiration").single())
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
