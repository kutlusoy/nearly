package com.kutlusoy.nearly.nostr

import java.security.SecureRandom
import java.text.Normalizer

// Random word passphrases from the BIP-39 English list: 11 bits per word, uniform because the list has 2048 entries
object Passphrase {
    const val DEFAULT_WORDS = 5
    const val MIN_WORDS = 5
    const val BITS_PER_WORD = 11

    val words: List<String> by lazy {
        val stream = requireNotNull(Passphrase::class.java.getResourceAsStream("/bip39/english.txt")) { "word list missing" }
        stream.bufferedReader(Charsets.UTF_8).readText().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .also { check(it.size == 2048) { "the word list must have 2048 words" } }
    }

    private val index: Set<String> by lazy { words.toHashSet() }

    fun generate(count: Int = DEFAULT_WORDS, random: SecureRandom = SecureRandom()): String {
        require(count >= 1) { "at least one word" }
        return List(count) { words[random.nextInt(words.size)] }.joinToString(" ")
    }

    fun bits(count: Int) = count * BITS_PER_WORD

    // The words if the input consists only of list words separated by spaces, hyphens or commas, otherwise null
    fun parse(input: String): List<String>? {
        val tokens = input.trim().lowercase().split(Regex("[\\s,\\-]+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty() || !tokens.all { it in index }) return null
        return tokens
    }

    // The form that goes into the key derivation, so "Tisch-Wolke" and "tisch wolke" give the same key
    fun canonical(password: String): String {
        parse(password)?.let { return it.joinToString(" ") }
        return Normalizer.normalize(password, Normalizer.Form.NFKC).trim().replace(Regex("\\s+"), " ")
    }
}
