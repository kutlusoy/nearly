package com.kutlusoy.nearly.nostr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventTest {
    private fun hex(s: String) = NostrKeys.fromHex(s.lowercase())

    @Test
    fun bip340Vector0() {
        val sk = hex("0000000000000000000000000000000000000000000000000000000000000003")
        val pk = hex("F9308A019258C31049344F85F89D5229B531C845836F99B08601F113BCE036F9")
        val msg = ByteArray(32)
        val expected = "E907831F80848D1069A5371B402410364BDF1C5F8307B0084C55F1CE2DCA821525F66A4A85EA8B71E482A74F382D2CE5EBEEE8FDB2172F477DF4900D310536C0".lowercase()
        assertEquals(NostrKeys.toHex(pk), NostrKeys.toHex(NostrKeys.publicKey(sk)))
        val sig = Schnorr.sign(msg, sk, ByteArray(32))
        assertEquals(expected, NostrKeys.toHex(sig))
        assertTrue(Schnorr.verify(msg, sig, pk))
    }

    @Test
    fun bip340Vector1() {
        val sk = hex("B7E151628AED2A6ABF7158809CF4F3C762E7160F38B4DA56A784D9045190CFEF")
        val pk = hex("DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659")
        val msg = hex("243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89")
        val expected = "6896BD60EEAE296DB48A229FF71DFE071BDE413E6D43F917DC8DCF8C78DE33418906D11AC976ABCCB20B091292BFF4EA897EFCB639EA871CFA95F6DE339E4B0A".lowercase()
        assertEquals(NostrKeys.toHex(pk), NostrKeys.toHex(NostrKeys.publicKey(sk)))
        assertEquals(expected, NostrKeys.toHex(Schnorr.sign(msg, sk, hex("0000000000000000000000000000000000000000000000000000000000000001"))))
    }

    @Test
    fun verifyRejectsWrongSignatureAndKey() {
        val sk = NostrKeys.generatePrivateKey()
        val msg = ByteArray(32) { it.toByte() }
        val sig = Schnorr.sign(msg, sk)
        val pk = NostrKeys.publicKey(sk)
        assertTrue(Schnorr.verify(msg, sig, pk))
        val broken = sig.copyOf().also { it[10] = (it[10].toInt() xor 1).toByte() }
        assertFalse(Schnorr.verify(msg, broken, pk))
        assertFalse(Schnorr.verify(msg, sig, NostrKeys.publicKey(NostrKeys.generatePrivateKey())))
        assertFalse(Schnorr.verify(ByteArray(32), sig, pk))
    }

    private fun session(): Session = testSession("anna")

    @Test
    fun signedEventIsValidAndTamperProof() {
        val event = Event.sign(session(), 1, "Hello \"Nearly\"\nline two \\ tab\t", listOf(listOf("t", "nearly")), createdAt = 1_700_000_000)
        assertTrue(event.isValid())
        assertEquals(event, Event.parse(event.toJson()))
        assertFalse(event.copy(content = "other").isValid())
        assertFalse(event.copy(createdAt = 1).isValid())
        assertFalse(event.copy(kind = 2).isValid())
        assertFalse(event.copy(sig = "00".repeat(64)).isValid())
        assertEquals(listOf("nearly"), event.tagValues("t"))
    }

    @Test
    fun idFollowsTheNip01Serialization() {
        val id = Event.computeId("pk", 1, 1, emptyList(), "hi")
        val expected = NostrKeys.toHex(java.security.MessageDigest.getInstance("SHA-256").digest("[0,\"pk\",1,1,[],\"hi\"]".toByteArray()))
        assertEquals(expected, id)
    }

    @Test
    fun filterJson() {
        val filter = Filter(kinds = listOf(1, 20), authors = listOf("ab"), tags = mapOf("g" to listOf("u0w")), since = 5, limit = 10)
        assertEquals("{\"authors\":[\"ab\"],\"kinds\":[1,20],\"#g\":[\"u0w\"],\"since\":5,\"limit\":10}", filter.toJson())
    }

    @Test
    fun relayMessagesParseAndBadOnesAreIgnored() {
        val event = Event.sign(session(), 1, "x", createdAt = 1)
        val message = RelayMessage.parse("[\"EVENT\",\"s1\",${event.toJson()}]")
        assertEquals(event, (message as RelayMessage.EventMessage).event)
        assertEquals(RelayMessage.Eose("s1"), RelayMessage.parse("[\"EOSE\",\"s1\"]"))
        assertEquals(RelayMessage.Ok("id", true, "ok"), RelayMessage.parse("[\"OK\",\"id\",true,\"ok\"]"))
        assertNull(RelayMessage.parse("not json"))
        assertNull(RelayMessage.parse("[\"EVENT\",\"s1\",{\"id\":1}]"))
        assertNull(RelayMessage.parse("[\"UNKNOWN\"]"))
        assertNotNull(RelayMessage.parse("[\"NOTICE\",\"hello\"]"))
    }
}
