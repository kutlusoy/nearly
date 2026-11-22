package com.kutlusoy.nearly.nostr

import java.text.Normalizer
import org.bouncycastle.crypto.generators.SCrypt

// Stable lookup id for username and e-mail. It names the local key file and can later serve as a relay lookup tag.
// The slow hash makes bulk guessing of known e-mail addresses expensive, it does not hide a weak combination.
object AccountId {
    private val SALT = "nearly/account-id/v1".toByteArray(Charsets.UTF_8)
    private const val COST = 1 shl 14

    fun derive(username: String, email: String): String {
        val input = (normalize(username) + "\u0000" + normalize(email)).toByteArray(Charsets.UTF_8)
        try {
            return NostrKeys.toHex(SCrypt.generate(input, SALT, COST, 8, 1, 32))
        } finally {
            input.fill(0)
        }
    }

    fun normalize(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).lowercase()

    fun isValid(id: String): Boolean = id.length == 64 && id.all { it in '0'..'9' || it in 'a'..'f' }
}
