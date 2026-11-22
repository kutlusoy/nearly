package com.kutlusoy.nearly.nostr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Nip44Test {
    private fun sk(n: Int) = ByteArray(32).also { it[31] = n.toByte() }

    @Test
    fun conversationKeyVectorAndSymmetry() {
        val a = sk(1)
        val b = sk(2)
        val ab = Nip44.conversationKey(a, NostrKeys.publicKey(b))
        val ba = Nip44.conversationKey(b, NostrKeys.publicKey(a))
        assertEquals("c41c775356fd92eadc63ff5a0dc1da211b268cbea22316767095b2871ea1412d", NostrKeys.toHex(ab))
        assertEquals(NostrKeys.toHex(ab), NostrKeys.toHex(ba))
    }

    @Test
    fun paddedLengthMatchesTheSpecificationTable() {
        val table = mapOf(1 to 32, 16 to 32, 32 to 32, 33 to 64, 37 to 64, 45 to 64, 49 to 64, 64 to 64, 65 to 96, 100 to 128, 111 to 128,
            200 to 224, 250 to 256, 320 to 320, 383 to 384, 384 to 384, 400 to 448, 500 to 512, 512 to 512, 515 to 640, 700 to 768,
            800 to 896, 900 to 1024, 1020 to 1024, 65536 to 65536)
        for ((length, expected) in table) assertEquals(expected, Nip44.paddedLength(length), "length $length")
    }

    @Test
    fun roundTripWithUnicodeAndLongText() {
        val key = Nip44.conversationKey(sk(3), NostrKeys.publicKey(sk(4)))
        for (text in listOf("a", "Hallo, schön dich zu sehen 🙂", "x".repeat(5000))) {
            assertEquals(text, Nip44.decrypt(Nip44.encrypt(text, key), key))
        }
    }

    @Test
    fun payloadsDifferBecauseOfTheRandomNonce() {
        val key = Nip44.conversationKey(sk(3), NostrKeys.publicKey(sk(4)))
        assertEquals(false, Nip44.encrypt("same", key) == Nip44.encrypt("same", key))
    }

    @Test
    fun tamperingAndWrongKeyAreRejected() {
        val key = Nip44.conversationKey(sk(3), NostrKeys.publicKey(sk(4)))
        val other = Nip44.conversationKey(sk(3), NostrKeys.publicKey(sk(5)))
        val payload = Nip44.encrypt("secret", key)
        assertFailsWith<DecryptionException> { Nip44.decrypt(payload, other) }
        val raw = java.util.Base64.getDecoder().decode(payload)
        raw[40] = (raw[40].toInt() xor 1).toByte()
        assertFailsWith<DecryptionException> { Nip44.decrypt(java.util.Base64.getEncoder().encodeToString(raw), key) }
        assertFailsWith<DecryptionException> { Nip44.decrypt("#unsupported", key) }
        assertFailsWith<DecryptionException> { Nip44.decrypt("AAAA", key) }
    }

    @Test
    fun emptyAndOversizedPlaintextAreRefused() {
        val key = Nip44.conversationKey(sk(3), NostrKeys.publicKey(sk(4)))
        assertFailsWith<IllegalArgumentException> { Nip44.encrypt("", key) }
        assertFailsWith<IllegalArgumentException> { Nip44.encrypt("x".repeat(65536), key) }
    }
}
