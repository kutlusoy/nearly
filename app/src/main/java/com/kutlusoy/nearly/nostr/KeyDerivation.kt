package com.kutlusoy.nearly.nostr

import java.security.MessageDigest
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

// The Nostr key follows from username, e-mail and password, so the same three entries give the same identity on every device.
// Username and e-mail are the salt, the password is the only secret. The public key is public, so everything rests on the password
// and on the cost of Argon2id: the password MUST have enough entropy (see Passphrase and PasswordPolicy).
object KeyDerivation {
    data class Cost(val memoryKiB: Int, val iterations: Int, val parallelism: Int)

    // 64 MiB and 4 passes. Higher memory is stronger but may not fit into the heap of every phone.
    val DEFAULT = Cost(memoryKiB = 65_536, iterations = 4, parallelism = 1)

    fun derive(username: String, email: String, password: String, cost: Cost = DEFAULT): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val salt = digest.digest(("nearly/key/v1\u0000" + AccountId.normalize(username) + "\u0000" + AccountId.normalize(email)).toByteArray(Charsets.UTF_8))
        val secret = Passphrase.canonical(password).toByteArray(Charsets.UTF_8)
        try {
            val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withMemoryAsKB(cost.memoryKiB)
                .withIterations(cost.iterations)
                .withParallelism(cost.parallelism)
                .build()
            var key = ByteArray(32)
            Argon2BytesGenerator().apply { init(parameters) }.generateBytes(secret, key)
            // A value outside the curve order is practically impossible, hashing again keeps the result deterministic
            while (!NostrKeys.isValidPrivateKey(key)) key = digest.digest(key)
            return key
        } finally {
            secret.fill(0)
        }
    }
}
