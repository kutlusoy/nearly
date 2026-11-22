package com.kutlusoy.nearly.nostr

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SavedLoginTest {
    private val dir = Files.createTempDirectory("login").toFile()
    private val file = dir.resolve("session.bin")
    private val key = NostrKeys.generatePrivateKey()

    private fun login() = SavedLogin("anna", "anna@example.org", "abandon ability able about above", key.copyOf(), 26)

    @Test
    fun theLoginComesBackWithoutAnyClearTextOnDisk() {
        val store = SavedLoginStore(file, SoftwareSecretBox())
        assertTrue(store.save(login()))
        val bytes = file.readBytes()
        val text = String(bytes, Charsets.ISO_8859_1)
        assertFalse("anna" in text || NostrKeys.toHex(key) in text || "abandon" in text)
        val loaded = checkNotNull(store.load())
        assertEquals("anna", loaded.username)
        assertEquals("anna@example.org", loaded.email)
        assertEquals("abandon ability able about above", loaded.words)
        assertEquals(26, loaded.age)
        assertContentEquals(key, loaded.secretKey)
    }

    @Test
    fun anotherKeyCannotOpenItAndATamperedFileIsRefused() {
        SavedLoginStore(file, SoftwareSecretBox()).save(login())
        assertNull(SavedLoginStore(file, SoftwareSecretBox()).load())
        val box = SoftwareSecretBox()
        val store = SavedLoginStore(file, box)
        store.save(login())
        val bytes = file.readBytes()
        bytes[bytes.size - 3] = (bytes[bytes.size - 3].toInt() xor 1).toByte()
        file.writeBytes(bytes)
        assertNull(store.load())
    }

    @Test
    fun clearingRemovesTheFileAndALackingKeystoreSavesNothing() {
        val store = SavedLoginStore(file, SoftwareSecretBox())
        store.save(login())
        store.clear()
        assertFalse(file.exists())
        assertNull(store.load())
        val none = object : SecretBox {
            override fun seal(plain: ByteArray): ByteArray? = null
            override fun open(blob: ByteArray): ByteArray? = null
        }
        assertFalse(SavedLoginStore(file, none).save(login()))
        assertFalse(file.exists())
    }

    @Test
    fun aLineBreakInAnEntryIsNotSaved() {
        assertFalse(SavedLoginStore(file, SoftwareSecretBox()).save(SavedLogin("an\nna", "a@b.c", "w", key, null)))
    }
}
