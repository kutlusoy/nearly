package com.kutlusoy.nearly.nostr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GiftWrapTest {
    private fun account(name: String): Session = testSession(name)

    private fun hex(s: Session) = NostrKeys.toHex(s.publicKey)

    @Test
    fun recipientAndSenderCanReadTheMessage() {
        val anna = account("anna")
        val ben = account("ben")
        val (toBen, toAnna) = DirectMessage.create(anna, hex(ben), "Hallo Ben 🙂", now = 1_700_000_000)
        val received = assertNotNull(DirectMessage.read(ben, toBen))
        assertEquals("Hallo Ben 🙂", received.rumor.content)
        assertEquals(hex(anna), received.senderPubkey)
        assertEquals(listOf(hex(ben)), received.rumor.tagValues("p"))
        assertEquals("Hallo Ben 🙂", assertNotNull(DirectMessage.read(anna, toAnna)).rumor.content)
    }

    @Test
    fun otherAccountsCannotRead() {
        val anna = account("anna")
        val ben = account("ben")
        val eve = account("eve")
        val (toBen, _) = DirectMessage.create(anna, hex(ben), "private")
        assertNull(DirectMessage.read(eve, toBen))
        assertNull(DirectMessage.read(anna, toBen))
    }

    @Test
    fun wrapHidesTheSenderAndTheTime() {
        val anna = account("anna")
        val ben = account("ben")
        val now = 1_700_000_000L
        val (wrap, _) = DirectMessage.create(anna, hex(ben), "secret message", now)
        assertEquals(GiftWrap.KIND_WRAP, wrap.kind)
        assertNotEquals(hex(anna), wrap.pubkey)
        assertEquals(listOf(hex(ben)), wrap.tagValues("p"))
        assertTrue(wrap.isValid())
        assertTrue(wrap.createdAt <= now && wrap.createdAt > now - 2 * 24 * 3600)
        assertTrue("secret" !in wrap.content)
        val second = DirectMessage.create(anna, hex(ben), "x", now).first()
        assertNotEquals(wrap.pubkey, second.pubkey)
    }

    @Test
    fun forgedAuthorIsRejected() {
        val anna = account("anna")
        val ben = account("ben")
        val eve = account("eve")
        // Eve seals a message that claims to come from Anna
        val rumor = Rumor(hex(anna), 1, DirectMessage.KIND_MESSAGE, listOf(listOf("p", hex(ben))), "send money")
        val sealContent = eve.useSecretKey { sk -> Nip44.encrypt(rumor.toJson(), Nip44.conversationKey(sk, ben.publicKey)) }
        val seal = Event.sign(eve, GiftWrap.KIND_SEAL, sealContent)
        val ephemeral = NostrKeys.generatePrivateKey()
        val content = Nip44.encrypt(seal.toJson(), Nip44.conversationKey(ephemeral, ben.publicKey))
        val pubkey = NostrKeys.toHex(NostrKeys.publicKey(ephemeral))
        val tags = listOf(listOf("p", hex(ben)))
        val id = Event.computeId(pubkey, 5, GiftWrap.KIND_WRAP, tags, content)
        val forged = Event(id, pubkey, 5, GiftWrap.KIND_WRAP, tags, content, NostrKeys.toHex(Schnorr.sign(NostrKeys.fromHex(id), ephemeral)))
        assertNull(GiftWrap.unwrap(ben, forged))
    }

    @Test
    fun tamperedAndForeignEventsAreIgnored() {
        val anna = account("anna")
        val ben = account("ben")
        val (wrap, _) = DirectMessage.create(anna, hex(ben), "x")
        assertNull(GiftWrap.unwrap(ben, wrap.copy(content = wrap.content.reversed())))
        assertNull(GiftWrap.unwrap(ben, Event.sign(anna, 1, "plain note")))
    }

    @Test
    fun inboxFilterTargetsTheOwnWraps() {
        val ben = account("ben")
        assertEquals("{\"kinds\":[1059],\"#p\":[\"${hex(ben)}\"]}", DirectMessage.inboxFilter(ben).toJson())
    }

    @Test
    fun rumorHasNoSignatureAndAStableId() {
        val rumor = Rumor("ab".repeat(32), 10, 14, listOf(listOf("p", "cd".repeat(32))), "hi")
        assertTrue("\"sig\"" !in rumor.toJson())
        assertEquals(rumor, Rumor.parse(rumor.toJson()))
        assertEquals(rumor.id, Event.computeId(rumor.pubkey, 10, 14, rumor.tags, "hi"))
    }
}
