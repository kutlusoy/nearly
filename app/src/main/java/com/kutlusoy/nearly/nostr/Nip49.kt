package com.kutlusoy.nearly.nostr

import java.security.SecureRandom
import java.text.Normalizer
import org.bouncycastle.crypto.generators.SCrypt
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter

class WrongPasswordException : Exception("wrong password or damaged key")

// Password encrypted private keys as in NIP-49: scrypt for the key, XChaCha20-Poly1305 for the encryption
object Nip49 {
    const val DEFAULT_LOG_N = 16
    private const val VERSION = 0x02
    private const val PRIVATE_KEY_SIZE = 32
    private const val SALT_SIZE = 16
    private const val NONCE_SIZE = 24
    private const val TAG_SIZE = 16
    private const val PAYLOAD_SIZE = 1 + 1 + SALT_SIZE + NONCE_SIZE + 1 + PRIVATE_KEY_SIZE + TAG_SIZE

    // 0x00 the key was handled insecurely before, 0x01 it was not, 0x02 unknown
    const val SECURITY_UNKNOWN = 0x02

    fun encrypt(
        privateKey: ByteArray,
        password: String,
        logN: Int = DEFAULT_LOG_N,
        keySecurity: Int = SECURITY_UNKNOWN,
        random: SecureRandom = SecureRandom(),
    ): String {
        require(privateKey.size == PRIVATE_KEY_SIZE) { "private key must have 32 bytes" }
        require(logN in 10..22) { "unreasonable scrypt cost" }
        val salt = ByteArray(SALT_SIZE).also { random.nextBytes(it) }
        val nonce = ByteArray(NONCE_SIZE).also { random.nextBytes(it) }
        val key = deriveKey(password, salt, logN)
        try {
            val cipher = seal(true, key, nonce, byteArrayOf(keySecurity.toByte()), privateKey)
            val out = ByteArray(PAYLOAD_SIZE)
            var pos = 0
            out[pos++] = VERSION.toByte()
            out[pos++] = logN.toByte()
            salt.copyInto(out, pos); pos += SALT_SIZE
            nonce.copyInto(out, pos); pos += NONCE_SIZE
            out[pos++] = keySecurity.toByte()
            cipher.copyInto(out, pos)
            return Bech32.encode("ncryptsec", out)
        } finally {
            key.fill(0)
        }
    }

    fun decrypt(ncryptsec: String, password: String): ByteArray {
        val decoded = Bech32.decode(ncryptsec.trim())
        require(decoded.hrp == "ncryptsec") { "not an ncryptsec" }
        val data = decoded.data
        require(data.size == PAYLOAD_SIZE && data[0].toInt() == VERSION) { "unsupported ncryptsec" }
        val logN = data[1].toInt() and 0xff
        require(logN in 10..22) { "unreasonable scrypt cost" }
        val salt = data.copyOfRange(2, 2 + SALT_SIZE)
        val nonce = data.copyOfRange(2 + SALT_SIZE, 2 + SALT_SIZE + NONCE_SIZE)
        val security = data[2 + SALT_SIZE + NONCE_SIZE]
        val cipher = data.copyOfRange(3 + SALT_SIZE + NONCE_SIZE, data.size)
        val key = deriveKey(password, salt, logN)
        try {
            return try {
                seal(false, key, nonce, byteArrayOf(security), cipher)
            } catch (e: org.bouncycastle.crypto.InvalidCipherTextException) {
                throw WrongPasswordException()
            }
        } finally {
            key.fill(0)
        }
    }

    private fun deriveKey(password: String, salt: ByteArray, logN: Int): ByteArray {
        val normalized = Normalizer.normalize(password, Normalizer.Form.NFKC).toByteArray(Charsets.UTF_8)
        try {
            return SCrypt.generate(normalized, salt, 1 shl logN, 8, 1, 32)
        } finally {
            normalized.fill(0)
        }
    }

    // XChaCha20-Poly1305: HChaCha20 derives a subkey from the key and the first 16 nonce bytes, the rest feeds ChaCha20-Poly1305
    private fun seal(encrypt: Boolean, key: ByteArray, nonce: ByteArray, aad: ByteArray, input: ByteArray): ByteArray {
        val subKey = hChaCha20(key, nonce.copyOfRange(0, 16))
        try {
            val shortNonce = ByteArray(12)
            nonce.copyInto(shortNonce, 4, 16, 24)
            val cipher = ChaCha20Poly1305()
            cipher.init(encrypt, AEADParameters(KeyParameter(subKey), TAG_SIZE * 8, shortNonce, aad))
            val out = ByteArray(cipher.getOutputSize(input.size))
            var length = cipher.processBytes(input, 0, input.size, out, 0)
            length += cipher.doFinal(out, length)
            return out.copyOf(length)
        } finally {
            subKey.fill(0)
        }
    }

    internal fun hChaCha20(key: ByteArray, nonce16: ByteArray): ByteArray {
        val s = IntArray(16)
        s[0] = 0x61707865; s[1] = 0x3320646e; s[2] = 0x79622d32; s[3] = 0x6b206574
        for (i in 0 until 8) s[4 + i] = le(key, i * 4)
        for (i in 0 until 4) s[12 + i] = le(nonce16, i * 4)
        repeat(10) {
            quarter(s, 0, 4, 8, 12); quarter(s, 1, 5, 9, 13); quarter(s, 2, 6, 10, 14); quarter(s, 3, 7, 11, 15)
            quarter(s, 0, 5, 10, 15); quarter(s, 1, 6, 11, 12); quarter(s, 2, 7, 8, 13); quarter(s, 3, 4, 9, 14)
        }
        val out = ByteArray(32)
        val words = intArrayOf(s[0], s[1], s[2], s[3], s[12], s[13], s[14], s[15])
        for (i in words.indices) for (b in 0 until 4) out[i * 4 + b] = (words[i] ushr (8 * b)).toByte()
        return out
    }

    private fun le(b: ByteArray, o: Int) =
        (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8) or ((b[o + 2].toInt() and 0xff) shl 16) or ((b[o + 3].toInt() and 0xff) shl 24)

    private fun quarter(s: IntArray, a: Int, b: Int, c: Int, d: Int) {
        s[a] += s[b]; s[d] = Integer.rotateLeft(s[d] xor s[a], 16)
        s[c] += s[d]; s[b] = Integer.rotateLeft(s[b] xor s[c], 12)
        s[a] += s[b]; s[d] = Integer.rotateLeft(s[d] xor s[a], 8)
        s[c] += s[d]; s[b] = Integer.rotateLeft(s[b] xor s[c], 7)
    }
}
