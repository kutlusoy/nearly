package com.kutlusoy.nearly.nostr

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// The Keystore is not there in unit tests, this box does the same with a key in memory
class SoftwareSecretBox(private val key: ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }) : SecretBox {
    override fun seal(plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv)) }
        return iv + cipher.doFinal(plain)
    }

    override fun open(blob: ByteArray): ByteArray? = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, blob, 0, 12)) }
        cipher.doFinal(blob, 12, blob.size - 12)
    }.getOrNull()
}
