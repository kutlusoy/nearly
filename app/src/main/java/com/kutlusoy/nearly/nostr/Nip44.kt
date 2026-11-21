package com.kutlusoy.nearly.nostr

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.engines.ChaCha7539Engine
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV

class DecryptionException(message: String) : Exception(message)

// NIP-44 version 2: secp256k1 ECDH, HKDF-SHA256, ChaCha20, HMAC-SHA256, padded payload in base64
object Nip44 {
    private val params = SECNamedCurves.getByName("secp256k1")
    private val SALT = "nip44-v2".toByteArray(Charsets.US_ASCII)
    private const val MIN_PLAINTEXT = 1
    private const val MAX_PLAINTEXT = 65535

    fun conversationKey(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        require(NostrKeys.isValidPrivateKey(privateKey)) { "invalid private key" }
        val point = try {
            params.curve.decodePoint(byteArrayOf(2) + peerPublicKey)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("invalid public key")
        }
        val shared = point.multiply(BigInteger(1, privateKey)).normalize().affineXCoord.encoded
        try {
            return hmac(SALT, shared)
        } finally {
            shared.fill(0)
        }
    }

    fun encrypt(plaintext: String, conversationKey: ByteArray, nonce: ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }): String {
        require(nonce.size == 32) { "nonce must have 32 bytes" }
        val keys = messageKeys(conversationKey, nonce)
        try {
            val padded = pad(plaintext.toByteArray(Charsets.UTF_8))
            val cipher = chacha(keys.chachaKey, keys.chachaNonce, padded)
            val mac = hmac(keys.hmacKey, nonce + cipher)
            return Base64.getEncoder().encodeToString(byteArrayOf(2) + nonce + cipher + mac)
        } finally {
            keys.wipe()
        }
    }

    fun decrypt(payload: String, conversationKey: ByteArray): String {
        if (payload.isEmpty() || payload[0] == '#') throw DecryptionException("unknown encryption version")
        if (payload.length < 132 || payload.length > 87472) throw DecryptionException("invalid payload size")
        val data = try {
            Base64.getDecoder().decode(payload)
        } catch (e: IllegalArgumentException) {
            throw DecryptionException("invalid base64")
        }
        if (data.size < 99 || data.size > 65603) throw DecryptionException("invalid data size")
        if (data[0].toInt() != 2) throw DecryptionException("unknown encryption version")
        val nonce = data.copyOfRange(1, 33)
        val cipher = data.copyOfRange(33, data.size - 32)
        val mac = data.copyOfRange(data.size - 32, data.size)
        val keys = messageKeys(conversationKey, nonce)
        try {
            val expected = hmac(keys.hmacKey, nonce + cipher)
            if (!MessageDigest.isEqual(expected, mac)) throw DecryptionException("invalid mac")
            return String(unpad(chacha(keys.chachaKey, keys.chachaNonce, cipher)), Charsets.UTF_8)
        } finally {
            keys.wipe()
        }
    }

    internal fun paddedLength(length: Int): Int {
        if (length <= 32) return 32
        val nextPower = Integer.highestOneBit(length - 1) shl 1
        val chunk = if (nextPower <= 256) 32 else nextPower / 8
        return chunk * (((length - 1) / chunk) + 1)
    }

    private fun pad(plaintext: ByteArray): ByteArray {
        require(plaintext.size in MIN_PLAINTEXT..MAX_PLAINTEXT) { "invalid plaintext length" }
        val out = ByteArray(2 + paddedLength(plaintext.size))
        out[0] = (plaintext.size ushr 8).toByte()
        out[1] = plaintext.size.toByte()
        plaintext.copyInto(out, 2)
        return out
    }

    private fun unpad(padded: ByteArray): ByteArray {
        if (padded.size < 2) throw DecryptionException("invalid padding")
        val length = ((padded[0].toInt() and 0xff) shl 8) or (padded[1].toInt() and 0xff)
        if (length < MIN_PLAINTEXT || padded.size != 2 + paddedLength(length)) throw DecryptionException("invalid padding")
        return padded.copyOfRange(2, 2 + length)
    }

    private class MessageKeys(val chachaKey: ByteArray, val chachaNonce: ByteArray, val hmacKey: ByteArray) {
        fun wipe() {
            chachaKey.fill(0); chachaNonce.fill(0); hmacKey.fill(0)
        }
    }

    // HKDF-Expand with SHA-256, 76 bytes: chacha key 32, chacha nonce 12, hmac key 32
    private fun messageKeys(conversationKey: ByteArray, nonce: ByteArray): MessageKeys {
        require(conversationKey.size == 32) { "conversation key must have 32 bytes" }
        val out = ByteArray(96)
        var previous = ByteArray(0)
        for (i in 1..3) {
            previous = hmac(conversationKey, previous + nonce + byteArrayOf(i.toByte()))
            previous.copyInto(out, (i - 1) * 32)
        }
        return MessageKeys(out.copyOfRange(0, 32), out.copyOfRange(32, 44), out.copyOfRange(44, 76)).also { out.fill(0) }
    }

    private fun chacha(key: ByteArray, nonce: ByteArray, input: ByteArray): ByteArray {
        val engine = ChaCha7539Engine()
        engine.init(true, ParametersWithIV(KeyParameter(key), nonce))
        return ByteArray(input.size).also { engine.processBytes(input, 0, input.size, it, 0) }
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)
}
