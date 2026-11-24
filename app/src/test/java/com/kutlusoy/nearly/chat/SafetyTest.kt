package com.kutlusoy.nearly.chat

import com.kutlusoy.nearly.nostr.MuteCodec
import com.kutlusoy.nearly.nostr.MuteList
import com.kutlusoy.nearly.nostr.MuteLoad
import com.kutlusoy.nearly.nostr.MuteNotLoadedException
import com.kutlusoy.nearly.nostr.MuteService
import com.kutlusoy.nearly.nostr.NostrKeys
import com.kutlusoy.nearly.nostr.Report
import com.kutlusoy.nearly.nostr.ReportType
import com.kutlusoy.nearly.nostr.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class SafetyTest {
    private fun session(): Session {
        val key = ByteArray(32) { (it + 7).toByte() }
        return Session(NostrKeys.publicKey(key), key)
    }

    private val peer = "ab".repeat(32)
    private val other = "cd".repeat(32)

    @Test
    fun aReportNamesThePersonAndTheType() {
        val event = Report.build(session(), peer, ReportType.Spam, "bot", 100)
        assertEquals(1984, event.kind)
        assertEquals(listOf("p", peer, "spam"), event.tags.single())
        assertEquals("bot", event.content)
        assertTrue(event.isValid())
        val underage = Report.build(session(), peer, ReportType.Underage, "", 100)
        assertEquals("illegal", underage.tags.single()[2])
        assertEquals("underage", underage.content)
    }

    @Test
    fun theMuteListHidesTheKeysAndReadsBack() {
        val s = session()
        val event = MuteCodec.sign(s, MuteList().add(peer).add(other), 100)
        assertEquals(10000, event.kind)
        assertTrue(event.tags.isEmpty())
        assertFalse(event.content.contains(peer))
        assertEquals(setOf(peer, other), MuteCodec.parse(s, event)!!.keys)
        assertNull(MuteCodec.parse(s, event.copy(content = "?iv=broken")))
    }

    @Test
    fun aListThatCannotBeReadIsNeverOverwritten() {
        val s = session()
        val relay = FakeRelay()
        relay.publish(com.kutlusoy.nearly.nostr.Event.sign(s, 10000, "old format ?iv=x", emptyList(), 50))
        val service = MuteService(relay, s)
        var result: MuteLoad? = null
        service.load { result = it }
        assertEquals(MuteLoad.Unreadable, result)
        service.startEmpty()
        assertFalse(service.isLoaded)
        assertFailsWith<MuteNotLoadedException> { service.change { it.add(peer) } }
    }

    @Test
    fun blocksFollowTheAccountToANewPhone() {
        val s = session()
        val relay = FakeRelay()
        val first = ChatService(relay, s, MemoryChatStore())
        first.start()
        first.mute(peer, true)
        val second = MemoryChatStore()
        val muted = mutableListOf<String>()
        ChatService(relay, s, second, onMuted = { muted.add(it) }).start()
        assertEquals(listOf(peer), muted)
        first.mute(peer, false)
        val third = mutableListOf<String>()
        ChatService(relay, s, MemoryChatStore(), onMuted = { third.add(it) }).start()
        assertTrue(third.isEmpty())
    }

    @Test
    fun localBlocksAreAddedToTheListOnTheRelays() {
        val s = session()
        val relay = FakeRelay()
        val store = MemoryChatStore().apply { setBlocked(peer, true) }
        first(relay, s, store)
        assertTrue(relay.events.any { it.kind == 10000 })
    }

    private fun first(relay: FakeRelay, s: Session, store: ChatStore) {
        val service = ChatService(relay, s, store)
        service.mute(other, true)
        service.start()
        val event = relay.events.filter { it.kind == 10000 }.maxByOrNull { it.createdAt }
        assertNotNull(event)
        assertEquals(setOf(peer, other), MuteCodec.parse(s, event)!!.keys)
    }

    @Test
    fun spamIsRecognisedAndNormalTalkIsNot() {
        listOf("check this out https://scam.example/x", "write me on t.me/bob", "add me on telegram", "can you send money?", "Überweis mir 20 Euro", "my page: cheap.xyz").forEach { assertTrue(Suspicion.isSuspicious(it), it) }
        listOf("Hi, how was your weekend?", "See you at 8 at the cafe", "I love hiking and cooking").forEach { assertFalse(Suspicion.isSuspicious(it), it) }
    }
}
