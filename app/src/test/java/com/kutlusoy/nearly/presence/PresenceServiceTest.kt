package com.kutlusoy.nearly.presence

import com.kutlusoy.nearly.chat.ChatService
import com.kutlusoy.nearly.chat.FakeArea
import com.kutlusoy.nearly.chat.FakeRelay
import com.kutlusoy.nearly.chat.MemoryChatStore
import com.kutlusoy.nearly.nostr.Geohash
import com.kutlusoy.nearly.nostr.NostrKeys
import com.kutlusoy.nearly.nostr.Presence
import com.kutlusoy.nearly.nostr.PresenceMessage
import com.kutlusoy.nearly.nostr.Rumor
import com.kutlusoy.nearly.nostr.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemoryPresence : PresenceMemory {
    private var friends = emptySet<String>()
    private var incoming = emptySet<String>()
    private var explained = false
    override fun friends() = friends
    override fun setFriends(keys: Set<String>) { friends = keys }
    override fun incoming() = incoming
    override fun setIncoming(keys: Set<String>) { incoming = keys }
    override fun explained() = explained
    override fun setExplained() { explained = true }
}

class PresenceServiceTest {
    private var now = 1_800_000_000L
    private val relay = FakeRelay()

    private inner class Phone(seed: Int) {
        val session: Session = ByteArray(32) { (it + seed).toByte() }.let { Session(NostrKeys.publicKey(it), it) }
        val key = NostrKeys.toHex(session.publicKey)
        val follows = mutableSetOf<String>()
        val blocked = mutableSetOf<String>()
        val memory = MemoryPresence()
        val chatStore = MemoryChatStore()
        var settings = PresenceSettings()
        val service: PresenceService = PresenceService(relay, session, memory, FakeArea("u33db"), { follows.toSet() }, { it in blocked }, { settings }, { now }, { _, _ -> AutoCloseable {} })

        init {
            ChatService(relay, session, chatStore, onPresence = service::receive).start()
        }

        fun follow(other: Phone) {
            follows += other.key
            service.followsChanged()
        }
    }

    private fun friends(a: Phone, b: Phone) {
        a.follow(b)
        b.follow(a)
    }

    @Test
    fun aFriendshipNeedsBothSidesToFollow() {
        val a = Phone(11)
        val b = Phone(22)
        a.follow(b)
        assertEquals(emptySet(), a.memory.friends())
        assertEquals(setOf(a.key), b.memory.incoming())
        b.follow(a)
        assertEquals(setOf(b.key), a.memory.friends())
        assertEquals(setOf(a.key), b.memory.friends())
        assertEquals(emptySet(), b.memory.incoming())
    }

    @Test
    fun anActiveFriendShowsUpOnTheGridOfTheOther() {
        val a = Phone(11)
        val b = Phone(22)
        friends(a, b)
        assertTrue(a.service.start(900))
        val dot = b.service.ui().friends.single()
        assertEquals(a.key, dot.key)
        assertEquals("u33dbc", dot.cell)
        assertEquals("u33dbc", a.service.ui().ownCell)
        assertTrue(a.service.ui().active)
        assertTrue(b.service.ui().friends.isNotEmpty() && !b.service.ui().active)
    }

    @Test
    fun noPlaceLeavesWhenTheFollowIsOneSided() {
        val a = Phone(11)
        val b = Phone(22)
        a.follow(b)
        val before = relay.events.size
        a.service.start(900)
        assertEquals(before, relay.events.size, "no heartbeat without a friend")
        assertTrue(b.service.ui().friends.isEmpty())
    }

    @Test
    fun sharingWithNobodyIsRefused() {
        val a = Phone(11)
        val b = Phone(22)
        friends(a, b)
        a.settings = PresenceSettings(audience = PresenceAudience.Nobody)
        val before = relay.events.size
        assertFalse(a.service.start(900))
        assertEquals(before, relay.events.size)
        assertFalse(a.service.ui().active)
    }

    @Test
    fun theCellFollowsTheChosenSize() {
        val a = Phone(11)
        val b = Phone(22)
        friends(a, b)
        a.settings = PresenceSettings(cellLength = 5)
        a.service.start(900)
        assertEquals(5, b.service.ui().friends.single().cell.length)
    }

    @Test
    fun aHeartbeatIsNotKeptByTheChatAndExpiresAfterThreeMinutes() {
        val a = Phone(11)
        val b = Phone(22)
        friends(a, b)
        val before = relay.events.size
        a.service.start(900)
        val wrap = relay.events.drop(before).single()
        assertFalse(b.chatStore.seenWrap(wrap.id), "a heartbeat is not remembered in the database")
        assertEquals((now + Presence.HERE_LIFETIME).toString(), wrap.tags.first { it[0] == "expiration" }[1])
    }

    @Test
    fun dotsTimeOutAndTooFastHeartbeatsAreIgnored() {
        val a = Phone(11)
        val b = Phone(22)
        friends(a, b)
        a.service.start(3600)
        val first = b.service.ui().friends.single().seenAt
        now += 10
        a.service.tick()
        assertEquals(first, b.service.ui().friends.single().seenAt, "a second heartbeat within 30 seconds is ignored")
        now += 40
        a.service.tick()
        assertEquals(now, b.service.ui().friends.single().seenAt)
        now += 200
        assertTrue(b.service.ui().friends.isEmpty(), "180 seconds without a heartbeat remove the dot")
    }

    @Test
    fun theActiveTimeEndsAndTheHeartbeatsStop() {
        val a = Phone(11)
        val b = Phone(22)
        friends(a, b)
        a.service.start(60)
        now += 61
        val before = relay.events.size
        a.service.tick()
        assertFalse(a.service.ui().active)
        assertEquals(before, relay.events.size)
    }

    @Test
    fun heartbeatsFromStrangersBlockedPeopleAndTheOldAreIgnored() {
        val a = Phone(11)
        val b = Phone(22)
        val c = Phone(33)
        friends(a, b)
        c.follow(b)
        c.service.start(900)
        assertTrue(b.service.ui().friends.isEmpty(), "c is not a friend of b")
        relay.publish(Presence.here(a.session, b.key, "u33dbc", now - 1000))
        assertTrue(b.service.ui().friends.isEmpty(), "too old")
        b.blocked += a.key
        a.service.start(900)
        assertTrue(b.service.ui().friends.isEmpty(), "blocked")
        b.blocked.clear()
        b.settings = PresenceSettings(seeFriends = false)
        now += 100
        a.service.tick()
        assertTrue(b.service.ui().friends.isEmpty(), "the user does not want to see friends")
    }

    @Test
    fun unfollowingEndsTheFriendship() {
        val a = Phone(11)
        val b = Phone(22)
        friends(a, b)
        a.follows.clear()
        a.service.followsChanged()
        assertEquals(emptySet(), a.memory.friends())
        val before = relay.events.size
        a.service.start(900)
        assertEquals(before, relay.events.size)
    }

    @Test
    fun messagesAreCheckedBeforeTheyCount() {
        val a = Phone(11)
        fun rumor(content: String) = Rumor(a.key, now, Presence.KIND, emptyList(), content)
        assertEquals(PresenceMessage.Here("u33dbc"), Presence.parse(rumor("{\"v\":1,\"t\":\"here\",\"g\":\"u33dbc\"}")))
        assertEquals(PresenceMessage.Hello, Presence.parse(rumor("{\"v\":1,\"t\":\"hello\"}")))
        assertNull(Presence.parse(rumor("{\"v\":2,\"t\":\"hello\"}")))
        assertNull(Presence.parse(rumor("{\"v\":1,\"t\":\"here\",\"g\":\"u33dbcdefgh\"}")), "a cell finer than 1 km is refused")
        assertNull(Presence.parse(rumor("{\"v\":1,\"t\":\"here\",\"g\":\"ailo\"}")))
        assertNull(Presence.parse(rumor("not json")))
        assertNull(Presence.parse(Rumor(a.key, now, 14, emptyList(), "{\"v\":1,\"t\":\"hello\"}")))
    }

    @Test
    fun theGridHasSevenRowsWithTheOwnCellInTheMiddle() {
        val grid = Geohash.grid("u33dbc", 3)
        assertEquals(7, grid.size)
        assertTrue(grid.all { it.size == 7 })
        assertEquals("u33dbc", grid[3][3])
        assertEquals(49, grid.flatten().toSet().size)
        assertTrue(Geohash.bounds(grid[0][3]).centerLat > Geohash.bounds(grid[6][3]).centerLat, "north comes first")
        assertTrue(Geohash.bounds(grid[3][6]).centerLon > Geohash.bounds(grid[3][0]).centerLon, "east is on the right")
    }
}
