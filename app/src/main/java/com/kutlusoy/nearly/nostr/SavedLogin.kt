package com.kutlusoy.nearly.nostr

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Seals bytes with a key that never leaves the device
interface SecretBox {
    fun seal(plain: ByteArray): ByteArray?
    fun open(blob: ByteArray): ByteArray?
}

// AES-GCM with a key of the Android Keystore, hardware backed where the phone has it. The key cannot be exported or copied to another phone.
// Without a usable Keystore the box returns null and the user simply has to sign in again after a restart.
class AndroidKeystoreBox(private val alias: String = "nearly-saved-login") : SecretBox {
    private fun key(): SecretKey? = runCatching {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }.getOrNull()

    override fun seal(plain: ByteArray): ByteArray? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()!!) }
        cipher.iv + cipher.doFinal(plain)
    }.getOrNull()

    override fun open(blob: ByteArray): ByteArray? = runCatching {
        require(blob.size > IV_BYTES + 16)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key()!!, GCMParameterSpec(128, blob, 0, IV_BYTES)) }
        cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    }.getOrNull()

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}

// What the app needs to open the account without asking again, and to show the backup in the settings
class SavedLogin(val username: String, val email: String, val words: String, val secretKey: ByteArray, val age: Int?)

// The login of this phone until the user signs out. The file holds only the sealed bytes.
class SavedLoginStore(private val file: File, private val box: SecretBox) {
    fun save(login: SavedLogin): Boolean {
        if (listOf(login.username, login.email, login.words).any { '\n' in it }) return false
        val plain = listOf("1", login.username, login.email, login.words, NostrKeys.toHex(login.secretKey), (login.age ?: -1).toString())
            .joinToString("\n").toByteArray(Charsets.UTF_8)
        val sealed = try {
            box.seal(plain)
        } finally {
            plain.fill(0)
        } ?: return false
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(sealed)
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        return true
    }

    fun load(): SavedLogin? {
        if (!file.isFile) return null
        val plain = box.open(file.readBytes()) ?: return null
        try {
            val parts = String(plain, Charsets.UTF_8).split('\n')
            if (parts.size != 6 || parts[0] != "1") return null
            val key = runCatching { NostrKeys.fromHex(parts[4]) }.getOrNull() ?: return null
            if (!NostrKeys.isValidPrivateKey(key)) return null
            return SavedLogin(parts[1], parts[2], parts[3], key, parts[5].toIntOrNull()?.takeIf { it >= 0 })
        } finally {
            plain.fill(0)
        }
    }

    fun clear() {
        file.delete()
    }
}
