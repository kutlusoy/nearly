package com.kutlusoy.nearly.nostr

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyVaultTest {
    @Test
    fun generatedPassphraseIsValidAndShortPasswordsAreNot() {
        assertTrue(PasswordPolicy.evaluate(Passphrase.generate()).valid)
        assertEquals(4, PasswordPolicy.evaluate(Passphrase.generate()).strength)
        assertEquals(0, PasswordPolicy.evaluate("").strength)
        assertFalse(PasswordPolicy.evaluate("Correct-Horse-9!").valid)
        assertFalse(PasswordPolicy.evaluate("Summer2026!!!!!!!!!!").valid)
        assertFalse(PasswordPolicy.evaluate("abandon ability able about").valid)
        assertFalse(PasswordPolicy.evaluate("abandon abandon abandon abandon abandon").valid)
    }

    @Test
    fun longRandomLookingPasswordIsAccepted() {
        assertTrue(PasswordPolicy.evaluate("k7#Qm2vX!pL9zR4wT8nB").valid)
    }

    @Test
    fun passwordContainingTheUsernameIsRefused() {
        assertTrue(PasswordPolicy.evaluate("abandon ability able about above", "anna").valid)
        assertFalse(PasswordPolicy.evaluate("k7#Qm2vX!anna9zR4wT8nB", "Anna").valid)
    }

    @Test
    fun sameThreeEntriesGiveTheSameIdentityOnANewDevice() {
        val first = testVault().signUp("Anna", "Anna@Example.org", TEST_PHRASE)
        val newDevice = testVault().signIn(" anna ", "anna@example.ORG", "Abandon-Ability-Able-About-Above")
        assertEquals(first.npub, newDevice.npub)
    }

    @Test
    fun signInOnTheKnownDeviceUsesTheStoredCopyAndDetectsAWrongPassword() {
        val vault = testVault()
        val created = vault.signUp("anna", "anna@example.org", TEST_PHRASE)
        assertTrue(vault.isKnownOnThisDevice("anna", "anna@example.org"))
        assertEquals(created.npub, vault.signIn("anna", "anna@example.org", TEST_PHRASE).npub)
        assertFailsWith<WrongPasswordException> { vault.signIn("anna", "anna@example.org", "abandon ability able about abuse") }
    }

    @Test
    fun wrongEntriesOnANewDeviceGiveAnotherIdentity() {
        val a = testVault().signUp("anna", "anna@example.org", TEST_PHRASE)
        assertTrue(a.npub != testVault().signIn("anna", "anna@exampel.org", TEST_PHRASE).npub)
    }

    @Test
    fun weakPasswordAndDuplicateAreRefused() {
        val vault = testVault()
        assertFailsWith<WeakPasswordException> { vault.signUp("anna", "anna@example.org", "short") }
        vault.signUp("anna", "anna@example.org", TEST_PHRASE)
        assertFailsWith<AccountExistsException> { vault.signUp("anna", "anna@example.org", TEST_PHRASE) }
    }

    @Test
    fun exportNeedsTheRightPasswordAndMatchesTheIdentity() {
        val vault = testVault()
        val session = vault.signUp("anna", "anna@example.org", TEST_PHRASE)
        val nsec = vault.exportNsec("anna", "anna@example.org", TEST_PHRASE)
        assertEquals(session.npub, NostrKeys.npub(NostrKeys.publicKey(NostrKeys.decodeNsec(nsec))))
        assertTrue(vault.exportNsec("anna", "anna@example.org", "abandon ability able about abuse") != nsec)
    }

    @Test
    fun forgetRemovesOnlyTheLocalCopy() {
        val vault = testVault()
        val npub = vault.signUp("anna", "anna@example.org", TEST_PHRASE).npub
        vault.signOutAndForget("anna", "anna@example.org")
        assertFalse(vault.isKnownOnThisDevice("anna", "anna@example.org"))
        assertEquals(npub, vault.signIn("anna", "anna@example.org", TEST_PHRASE).npub)
    }

    @Test
    fun lockedSessionHasNoKey() {
        val session = testSession("anna")
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
