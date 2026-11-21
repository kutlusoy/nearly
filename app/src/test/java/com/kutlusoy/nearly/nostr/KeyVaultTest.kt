package com.kutlusoy.nearly.nostr

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyVaultTest {
    private val password = "Correct-Horse-9!"

    private fun vault() = KeyVault(FileKeyStore(Files.createTempDirectory("keys").toFile()), logN = 10)

    @Test
    fun policyReportsEveryRule() {
        val weak = PasswordPolicy.evaluate("abc")
        assertFalse(weak.valid)
        assertTrue(weak.isMet(PasswordRule.Lowercase))
        assertFalse(weak.isMet(PasswordRule.MinLength))
        assertEquals(0, PasswordPolicy.evaluate("").strength)
        assertTrue(PasswordPolicy.evaluate(password).valid)
        assertFalse(PasswordPolicy.evaluate("Anna-Secret-123!", username = "anna").valid)
        assertTrue(PasswordPolicy.evaluate("Correct-Horse-Battery-9!").strength == 4)
    }

    @Test
    fun createThenUnlockGivesTheSameKey() {
        val vault = vault()
        val (created, backup) = vault.create("Anna", "Anna@Example.org", password)
        val unlocked = vault.unlock("anna", " anna@example.ORG ", password)
        assertEquals(created.npub, unlocked.npub)
        assertEquals(backup, vault.exportNsec("anna", "anna@example.org", password))
        assertEquals(created.npub, NostrKeys.npub(NostrKeys.publicKey(NostrKeys.decodeNsec(backup))))
    }

    @Test
    fun wrongPasswordAndUnknownAccountAreSeparateErrors() {
        val vault = vault()
        vault.create("anna", "anna@example.org", password)
        assertFailsWith<WrongPasswordException> { vault.unlock("anna", "anna@example.org", "Correct-Horse-8!") }
        assertFailsWith<UnknownAccountException> { vault.unlock("bob", "bob@example.org", password) }
    }

    @Test
    fun weakPasswordAndDuplicateAreRefused() {
        val vault = vault()
        assertFailsWith<WeakPasswordException> { vault.create("anna", "anna@example.org", "short") }
        vault.create("anna", "anna@example.org", password)
        assertFailsWith<AccountExistsException> { vault.create("anna", "anna@example.org", password) }
    }

    @Test
    fun importKeepsTheIdentity() {
        val vault = vault()
        val nsec = "nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5"
        val expected = NostrKeys.npub(NostrKeys.publicKey(NostrKeys.decodeNsec(nsec)))
        assertEquals(expected, vault.import("anna", "anna@example.org", password, nsec).npub)
        assertEquals(expected, vault.unlock("anna", "anna@example.org", password).npub)
    }

    @Test
    fun changePasswordKeepsTheKeyAndInvalidatesTheOldPassword() {
        val vault = vault()
        val before = vault.create("anna", "anna@example.org", password).first.npub
        vault.changePassword("anna", "anna@example.org", password, "Another-Strong-7#")
        assertEquals(before, vault.unlock("anna", "anna@example.org", "Another-Strong-7#").npub)
        assertFailsWith<WrongPasswordException> { vault.unlock("anna", "anna@example.org", password) }
        assertFailsWith<WeakPasswordException> { vault.changePassword("anna", "anna@example.org", "Another-Strong-7#", "weak") }
    }

    @Test
    fun deleteNeedsThePassword() {
        val vault = vault()
        vault.create("anna", "anna@example.org", password)
        assertFailsWith<WrongPasswordException> { vault.delete("anna", "anna@example.org", "Correct-Horse-8!") }
        vault.delete("anna", "anna@example.org", password)
        assertFalse(vault.exists("anna", "anna@example.org"))
    }

    @Test
    fun lockedSessionHasNoKey() {
        val session = vault().create("anna", "anna@example.org", password).first
        assertEquals(32, session.useSecretKey { it.size })
        session.lock()
        assertTrue(session.isLocked)
        assertFailsWith<IllegalStateException> { session.useSecretKey { it } }
    }

    @Test
    fun storeRejectsPathTricks() {
        val store = FileKeyStore(Files.createTempDirectory("keys").toFile())
        assertFailsWith<IllegalArgumentException> { store.get("../etc/passwd") }
    }
}
