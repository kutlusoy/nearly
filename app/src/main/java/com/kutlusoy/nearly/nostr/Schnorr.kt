package com.kutlusoy.nearly.nostr

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.math.ec.ECPoint

// BIP-340 Schnorr signatures on secp256k1, as Nostr events use them
object Schnorr {
    private val params = SECNamedCurves.getByName("secp256k1")
    private val n: BigInteger = params.n
    private val p: BigInteger = params.curve.field.characteristic

    fun sign(message: ByteArray, privateKey: ByteArray, aux: ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }): ByteArray {
        require(message.size == 32 && aux.size == 32) { "message and aux must have 32 bytes" }
        require(NostrKeys.isValidPrivateKey(privateKey)) { "invalid private key" }
        val d0 = BigInteger(1, privateKey)
        val point = params.g.multiply(d0).normalize()
        val d = if (isOdd(point)) n.subtract(d0) else d0
        val px = bytes32(point.affineXCoord.toBigInteger())
        val t = xor(bytes32(d), taggedHash("BIP0340/aux", aux))
        val k0 = BigInteger(1, taggedHash("BIP0340/nonce", t, px, message)).mod(n)
        check(k0.signum() != 0) { "nonce is zero" }
        val r = params.g.multiply(k0).normalize()
        val k = if (isOdd(r)) n.subtract(k0) else k0
        val rx = bytes32(r.affineXCoord.toBigInteger())
        val e = BigInteger(1, taggedHash("BIP0340/challenge", rx, px, message)).mod(n)
        val s = k.add(e.multiply(d)).mod(n)
        t.fill(0)
        return rx + bytes32(s)
    }

    fun verify(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        if (message.size != 32 || signature.size != 64 || publicKey.size != 32) return false
        val x = BigInteger(1, publicKey)
        val r = BigInteger(1, signature.copyOfRange(0, 32))
        val s = BigInteger(1, signature.copyOfRange(32, 64))
        if (x >= p || r >= p || s >= n) return false
        val point = try {
            params.curve.decodePoint(byteArrayOf(2) + publicKey)
        } catch (e: IllegalArgumentException) {
            return false
        }
        val e = BigInteger(1, taggedHash("BIP0340/challenge", signature.copyOfRange(0, 32), publicKey, message)).mod(n)
        val result = params.g.multiply(s).add(point.multiply(e).negate()).normalize()
        return !result.isInfinity && !isOdd(result) && result.affineXCoord.toBigInteger() == r
    }

    private fun isOdd(point: ECPoint) = point.affineYCoord.toBigInteger().testBit(0)

    private fun bytes32(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        val out = ByteArray(32)
        val length = minOf(raw.size, 32)
        raw.copyInto(out, 32 - length, raw.size - length, raw.size)
        return out
    }

    private fun xor(a: ByteArray, b: ByteArray) = ByteArray(32) { (a[it].toInt() xor b[it].toInt()).toByte() }

    private fun taggedHash(tag: String, vararg parts: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val tagHash = digest.digest(tag.toByteArray(Charsets.UTF_8))
        digest.update(tagHash)
        digest.update(tagHash)
        parts.forEach { digest.update(it) }
        return digest.digest()
    }
}
