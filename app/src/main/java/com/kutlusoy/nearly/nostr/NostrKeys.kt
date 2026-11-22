package com.kutlusoy.nearly.nostr

import java.math.BigInteger
import java.security.SecureRandom
import org.bouncycastle.asn1.sec.SECNamedCurves

object NostrKeys {
    private val params = SECNamedCurves.getByName("secp256k1")
    private val order: BigInteger = params.n

    fun generatePrivateKey(random: SecureRandom = SecureRandom()): ByteArray {
        while (true) {
            val candidate = ByteArray(32).also { random.nextBytes(it) }
            if (isValidPrivateKey(candidate)) return candidate
        }
    }

    fun isValidPrivateKey(key: ByteArray): Boolean {
        if (key.size != 32) return false
        val d = BigInteger(1, key)
        return d.signum() > 0 && d < order
    }

    // x-only public key as in BIP-340
    fun publicKey(privateKey: ByteArray): ByteArray {
        require(isValidPrivateKey(privateKey)) { "invalid private key" }
        val point = params.g.multiply(BigInteger(1, privateKey)).normalize()
        return point.affineXCoord.encoded
    }

    fun npub(publicKey: ByteArray): String = Bech32.encode("npub", publicKey)

    fun nsec(privateKey: ByteArray): String = Bech32.encode("nsec", privateKey)

    fun decodeNsec(text: String): ByteArray {
        val decoded = Bech32.decode(text.trim())
        require(decoded.hrp == "nsec" && decoded.data.size == 32) { "not an nsec" }
        require(isValidPrivateKey(decoded.data)) { "invalid private key" }
        return decoded.data
    }

    fun decodeNpub(text: String): ByteArray {
        val decoded = Bech32.decode(text.trim())
        require(decoded.hrp == "npub" && decoded.data.size == 32) { "not an npub" }
        return decoded.data
    }

    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun fromHex(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "odd hex length" }
        return ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
