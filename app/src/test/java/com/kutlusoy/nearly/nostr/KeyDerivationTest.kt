package com.kutlusoy.nearly.nostr

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyDerivationTest {
    private val cheap = KeyDerivation.Cost(memoryKiB = 1024, iterations = 2, parallelism = 1)

    private fun derive(u: String, e: String, p: String) = NostrKeys.toHex(KeyDerivation.derive(u, e, p, cheap))

    @Test
    fun wordListIsTheBip39EnglishList() {
        assertEquals(2048, Passphrase.words.size)
        assertEquals(2048, Passphrase.words.toSet().size)
        assertEquals("abandon", Passphrase.words.first())
        assertEquals("zoo", Passphrase.words.last())
        val text = Passphrase.words.joinToString("\n", postfix = "\n")
        assertEquals("2f5eed53a4727b4bf8880d8f3f199efc90e58503646d9ff8eff3a2ed3b24dbda", NostrKeys.toHex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray())))
    }

    @Test
    fun generatedPassphraseHasTheRequestedWordsFromTheList() {
        val phrase = Passphrase.generate()
        val words = assertNotNull(Passphrase.parse(phrase))
        assertEquals(Passphrase.DEFAULT_WORDS, words.size)
        assertEquals(55, Passphrase.bits(words.size))
        assertNotEquals(phrase, Passphrase.generate())
    }

    @Test
    fun parseAcceptsSeparatorsAndCaseButOnlyListWords() {
        assertEquals(listOf("abandon", "zoo"), Passphrase.parse("  Abandon-ZOO "))
        assertEquals(listOf("abandon", "zoo"), Passphrase.parse("abandon, zoo"))
        assertNull(Passphrase.parse("abandon notaword"))
        assertNull(Passphrase.parse(""))
        assertEquals("abandon zoo", Passphrase.canonical("Abandon-Zoo"))
        assertEquals("My pass word", Passphrase.canonical("  My   pass\tword "))
    }

    @Test
    fun sameInputsGiveTheSameKeyOnEveryDevice() {
        val a = derive("Anna", "Anna@Example.org", "abandon ability able about above")
        assertEquals(a, derive(" anna ", " ANNA@example.ORG ", "Abandon-Ability-Able-About-Above"))
        assertEquals(64, a.length)
    }

    @Test
    fun anyChangedInputGivesAnotherKey() {
        val base = derive("anna", "anna@example.org", "abandon ability able about above")
        assertNotEquals(base, derive("anna2", "anna@example.org", "abandon ability able about above"))
        assertNotEquals(base, derive("anna", "anna@example.com", "abandon ability able about above"))
        assertNotEquals(base, derive("anna", "anna@example.org", "abandon ability able about abuse"))
    }

    @Test
    fun costIsPartOfTheResult() {
        val other = KeyDerivation.Cost(memoryKiB = 2048, iterations = 2, parallelism = 1)
        assertNotEquals(derive("a", "b@c.de", "abandon zoo"), NostrKeys.toHex(KeyDerivation.derive("a", "b@c.de", "abandon zoo", other)))
    }

    @Test
    fun derivedKeyIsAValidNostrKey() {
        val key = KeyDerivation.derive("anna", "anna@example.org", Passphrase.generate(), cheap)
        assertTrue(NostrKeys.isValidPrivateKey(key))
        assertEquals(32, NostrKeys.publicKey(key).size)
    }

    @Test
    fun goldenValueForTheDefaultCost() {
        val key = KeyDerivation.derive("anna", "anna@example.org", "abandon ability able about above")
        // Regression value of this implementation (not an external vector): a change of any parameter or of the salt scheme breaks every account
        assertEquals("5fc77977c7d06a8f46da03601735c56443ca4a9eeaa9e8e8809eccea48c05205", NostrKeys.toHex(key))
    }
}
