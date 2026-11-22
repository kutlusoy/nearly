package com.kutlusoy.nearly.nostr

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KeyCryptoTest {
    @Test
    fun hChaCha20MatchesTheReferenceVector() {
        val key = NostrKeys.fromHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val nonce = NostrKeys.fromHex("000000090000004a0000000031415927")
        assertEquals("82413b4227b27bfed30e42508a877d73a0f9e4d58a74a853c12ec41326d3ecdc", NostrKeys.toHex(Nip49.hChaCha20(key, nonce)))
    }

    @Test
    fun publicKeyOfOneIsTheGeneratorX() {
        val one = ByteArray(32).also { it[31] = 1 }
        assertEquals("79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798", NostrKeys.toHex(NostrKeys.publicKey(one)))
    }

    @Test
    fun nip19Examples() {
        val sk = NostrKeys.decodeNsec("nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5")
        assertEquals("67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa", NostrKeys.toHex(sk))
        assertEquals("nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5", NostrKeys.nsec(sk))
        val pk = NostrKeys.decodeNpub("npub10elfcs4fr0l0r8af98jlmgdh9c8tcxjvz9qkw038js35mp4dma8qzvjptg")
        assertEquals("7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e", NostrKeys.toHex(pk))
        assertEquals("npub10elfcs4fr0l0r8af98jlmgdh9c8tcxjvz9qkw038js35mp4dma8qzvjptg", NostrKeys.npub(pk))
    }

    @Test
    fun nip49Vector() {
        val key = Nip49.decrypt(
            "ncryptsec1qgg9947rlpvqu76pj5ecreduf9jxhselq2nae2kghhvd5g7dgjtcxfqtd67p9m0w57lspw8gsq6yphnm8623nsl8xn9j4jdzz84zm3frztj3z7s35vpzmqf6ksu8r89qk5z2zxfmu5gv8th8wclt0h4p",
            "nostr",
        )
        assertEquals("3501454135014541350145413501453fefb02227e449e57cf4d3a3ce05378683", NostrKeys.toHex(key))
    }

    @Test
    fun roundTripAndWrongPassword() {
        val sk = NostrKeys.generatePrivateKey()
        val encrypted = Nip49.encrypt(sk, "Correct-Horse-9!", logN = 12)
        assertContentEquals(sk, Nip49.decrypt(encrypted, "Correct-Horse-9!"))
        assertFailsWith<WrongPasswordException> { Nip49.decrypt(encrypted, "Correct-Horse-8!") }
    }

    @Test
    fun badChecksumIsRejected() {
        val encrypted = Nip49.encrypt(NostrKeys.generatePrivateKey(), "pw", logN = 10)
        val broken = encrypted.dropLast(1) + (if (encrypted.last() == 'q') 'p' else 'q')
        assertFailsWith<IllegalArgumentException> { Nip49.decrypt(broken, "pw") }
    }
}
