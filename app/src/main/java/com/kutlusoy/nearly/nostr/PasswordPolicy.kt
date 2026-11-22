package com.kutlusoy.nearly.nostr

import kotlin.math.ln

data class PasswordCheck(val bits: Int, val strength: Int, val containsUsername: Boolean) {
    val valid: Boolean get() = bits >= PasswordPolicy.MIN_BITS && !containsUsername
}

// The password is the only secret behind the key, so it needs entropy. Generated passphrases from the BIP-39 list are the normal case,
// own passwords are accepted only when a rough estimate reaches the same strength.
object PasswordPolicy {
    const val MIN_BITS = Passphrase.MIN_WORDS * Passphrase.BITS_PER_WORD

    // Strength from 0 (empty) to 4 (valid), for the live display while typing
    fun evaluate(password: String, username: String = ""): PasswordCheck {
        val canonical = Passphrase.canonical(password)
        val name = AccountId.normalize(username)
        val containsUsername = name.length >= 3 && canonical.lowercase().contains(name)
        val bits = estimateBits(password)
        val strength = when {
            canonical.isEmpty() -> 0
            bits < 30 -> 1
            bits < 45 -> 2
            bits < MIN_BITS -> 3
            else -> 4
        }
        return PasswordCheck(bits, strength, containsUsername)
    }

    private fun estimateBits(password: String): Int {
        val words = Passphrase.parse(password)
        // Repeated words count once, a phrase typed by hand is not random
        if (words != null) return words.toSet().size * Passphrase.BITS_PER_WORD
        val text = Passphrase.canonical(password)
        if (text.isEmpty()) return 0
        var pool = 0
        if (text.any { it in 'a'..'z' }) pool += 26
        if (text.any { it in 'A'..'Z' }) pool += 26
        if (text.any { it in '0'..'9' }) pool += 10
        if (text.any { it.code < 128 && !it.isLetterOrDigit() }) pool += 33
        if (text.any { it.code >= 128 }) pool += 100
        val distinct = text.toSet().size
        val effectiveLength = distinct + (text.length - distinct) * 0.25
        // Halved on purpose: people choose patterns, the formula for random characters overestimates them
        return (effectiveLength * ln(pool.toDouble()) / ln(2.0) * 0.5).toInt()
    }
}
