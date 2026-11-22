package com.kutlusoy.nearly.chat

import com.kutlusoy.nearly.nostr.Event
import com.kutlusoy.nearly.nostr.GiftWrap
import com.kutlusoy.nearly.nostr.NostrKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatServiceTest {
    private val relay = FakeRelay()
    private val alice = newSession()
    private val bob = newSession()
    private var clock = 1_800_000_000L
    private val aliceStore = MemoryChatStore()
    private val bobStore = MemoryChatStore()
    private val incoming = mutableListOf<ChatMessage>()

    private fun service(session: com.kutlusoy.nearly.nostr.Session, store: ChatStore, onIncoming: (ChatMessage) -> Unit = {}) =
        ChatService(relay, session, store, clock = { clock }, onIncoming = onIncoming)

    @Test
    fun aMessageReachesTheOtherPersonAndIsMarkedSentForTheSender() {
        val a = service(alice, aliceStore)
        val b = service(bob, bobStore) { incoming += it }
        a.start()
        b.start()
        val result = a.send(bob.hex(), "  Hello Bob  ") as SendResult.Queued
        assertEquals("Hello Bob", result.message.text)

        val mine = aliceStore.messages(bob.hex()).single()
        assertEquals(MessageStatus.Sent, mine.status)
        assertTrue(mine.mine)

        val theirs = bobStore.messages(alice.hex()).single()
        assertEquals("Hello Bob", theirs.text)
        assertEquals(MessageStatus.Received, theirs.status)
        assertFalse(theirs.mine)
        assertFalse(theirs.read)
        assertEquals(1, bobStore.unreadTotal())
        assertEquals(listOf("Hello Bob"), incoming.map { it.text })
    }

    @Test
    fun theConversationGoesBothWaysAndTheSenderDoesNotSeeItsOwnCopyAsANewMessage() {
        val a = service(alice, aliceStore)
        val b = service(bob, bobStore)
        a.start()
        b.start()
        a.send(bob.hex(), "one")
        clock += 5
        b.send(alice.hex(), "two")
        clock += 5
        a.send(bob.hex(), "three")
        assertEquals(listOf("one", "two", "three"), aliceStore.messages(bob.hex()).map { it.text })
        assertEquals(listOf(true, false, true), aliceStore.messages(bob.hex()).map { it.mine })
        assertEquals(listOf("one", "two", "three"), bobStore.messages(alice.hex()).map { it.text })
    }

    @Test
    fun theOfflinePersonFindsTheMessagesWhenItStarts() {
        service(alice, aliceStore).apply { start() }.send(bob.hex(), "while you were away")
        service(bob, bobStore).start()
        assertEquals("while you were away", bobStore.messages(alice.hex()).single().text)
    }

    @Test
    fun aNewPhoneOfTheSenderGetsTheWholeHistoryFromTheRelays() {
        val a = service(alice, aliceStore)
        val b = service(bob, bobStore)
        a.start()
        b.start()
        a.send(bob.hex(), "first")
        clock += 3
        b.send(alice.hex(), "answer")
        val newPhone = MemoryChatStore()
        service(alice, newPhone).start()
        assertEquals(listOf("first", "answer"), newPhone.messages(bob.hex()).map { it.text })
        assertEquals(listOf(true, false), newPhone.messages(bob.hex()).map { it.mine })
        assertEquals(1, newPhone.conversations().size)
    }

    @Test
    fun theSameWrapTwiceIsReadOnce() {
        val b = service(bob, bobStore)
        b.start()
        service(alice, aliceStore).send(bob.hex(), "once")
        val wrap = relay.events.first { it.tags.any { t -> t == listOf("p", bob.hex()) } }
        relay.deliverAgain(wrap)
        relay.deliverAgain(wrap)
        assertEquals(1, bobStore.messages(alice.hex()).size)
    }

    @Test
    fun foreignAndBrokenEventsAreIgnored() {
        service(bob, bobStore).start()
        val junk = GiftWrap.wrap(alice, com.kutlusoy.nearly.nostr.Rumor(alice.hex(), clock, 7, listOf(listOf("p", bob.hex())), "not a message"), bob.hex(), clock)
        relay.publish(junk)
        val stranger = newSession()
        val toSomeoneElse = GiftWrap.wrap(alice, com.kutlusoy.nearly.nostr.Rumor(alice.hex(), clock, 14, listOf(listOf("p", stranger.hex())), "hi"), stranger.hex(), clock)
        relay.publish(toSomeoneElse)
        val empty = GiftWrap.wrap(alice, com.kutlusoy.nearly.nostr.Rumor(alice.hex(), clock, 14, listOf(listOf("p", bob.hex())), "   "), bob.hex(), clock)
        relay.publish(empty)
        assertTrue(bobStore.conversations().isEmpty())
    }

    @Test
    fun aMessageToYourselfOrToNobodyOrAnEmptyOneIsRefused() {
        val a = service(alice, aliceStore)
        assertEquals(SendResult.ToSelf, a.send(alice.hex(), "x"))
        assertEquals(SendResult.BadPeer, a.send("npub1nope", "x"))
        assertEquals(SendResult.Empty, a.send(bob.hex(), "   "))
        assertEquals(SendResult.TooLong, a.send(bob.hex(), "a".repeat(ChatService.MAX_TEXT + 1)))
        assertTrue(aliceStore.conversations().isEmpty())
        assertTrue(relay.events.isEmpty())
    }

    @Test
    fun aMessageWithoutAnyRelayStaysSendingAndFailsWhenRelaysRefuseIt() {
        val silent = FakeRelay(answers = 0)
        val a = ChatService(silent, alice, aliceStore, clock = { clock })
        a.send(bob.hex(), "waiting")
        assertEquals(MessageStatus.Sending, aliceStore.messages(bob.hex()).single().status)

        val refusing = FakeRelay(answers = 3, accept = false)
        val other = MemoryChatStore()
        ChatService(refusing, alice, other, clock = { clock }).send(bob.hex(), "refused")
        assertEquals(MessageStatus.Failed, other.messages(bob.hex()).single().status)
    }

    @Test
    fun aFailedMessageCanBeSentAgainAndIsThenOnTheRelay() {
        val refusing = FakeRelay(answers = 3, accept = false)
        val store = MemoryChatStore()
        val a = ChatService(refusing, alice, store, clock = { clock })
        a.send(bob.hex(), "try again")
        val failed = store.messages(bob.hex()).single()
        assertEquals(MessageStatus.Failed, failed.status)

        refusing.accept = true
        clock += 10
        a.retry(failed.id)
        val now = store.messages(bob.hex()).single()
        assertEquals("try again", now.text)
        assertEquals(MessageStatus.Sent, now.status)
        assertTrue(now.id != failed.id)
        assertEquals(2, refusing.events.size)
    }

    @Test
    fun aMessageThatWasStillSendingWhenTheAppClosedFailsAtTheNextStart() {
        val silent = FakeRelay(answers = 0)
        ChatService(silent, alice, aliceStore, clock = { clock }).send(bob.hex(), "lost")
        clock += 600
        service(alice, aliceStore).start()
        assertEquals(MessageStatus.Failed, aliceStore.messages(bob.hex()).single().status)
    }

    @Test
    fun theNameOfThePersonComesFromTheProfileOnTheRelay() {
        val b = service(bob, bobStore)
        b.publishProfile("Bob", "Hi there", 30)
        service(alice, aliceStore).resolveProfile(bob.hex())
        assertEquals("Bob", aliceStore.peerName(bob.hex()))
        assertEquals(PeerProfile("Bob", "Hi there", 30), aliceStore.peerProfile(bob.hex()))
    }

    @Test
    fun peersCanBeEnteredAsNpubNostrLinkOrHex() {
        val hex = bob.hex()
        val npub = NostrKeys.npub(bob.publicKey)
        assertEquals(hex, ChatService.parsePeer(npub))
        assertEquals(hex, ChatService.parsePeer("  nostr:$npub  "))
        assertEquals(hex, ChatService.parsePeer(hex.uppercase()))
        assertNull(ChatService.parsePeer("hello"))
        assertNull(ChatService.parsePeer(NostrKeys.nsec(ByteArray(32) { 1 })))
        assertNull(ChatService.parsePeer(""))
    }

    @Test
    fun theWrapsLookLikeWrapsAndTheRelayNeverSeesTheText() {
        service(alice, aliceStore).send(bob.hex(), "secret words")
        assertEquals(2, relay.events.size)
        assertTrue(relay.events.all { it.kind == GiftWrap.KIND_WRAP && "secret" !in it.content && it.pubkey != alice.hex() })
        assertNotNull(relay.events.firstOrNull { it.tags.contains(listOf("p", bob.hex())) })
        assertNotNull(relay.events.firstOrNull { it.tags.contains(listOf("p", alice.hex())) })
        val first: Event = relay.events.first()
        assertTrue(first.isValid())
    }
}
