package com.kutlusoy.nearly.nostr

class AccountExistsException : Exception("an account with this username and e-mail already exists on this device")
class UnknownAccountException : Exception("no account with this username and e-mail on this device")
class WeakPasswordException(val check: PasswordCheck) : Exception("the password is too weak")

// An unlocked account. The private key lives only in memory until lock() is called.
class Session internal constructor(val publicKey: ByteArray, private var privateKey: ByteArray?) {
    val npub: String get() = NostrKeys.npub(publicKey)
    val isLocked: Boolean get() = privateKey == null

    // Gives the key to a signing step without keeping a reference to it
    fun <T> useSecretKey(block: (ByteArray) -> T): T = block(checkNotNull(privateKey) { "session is locked" })

    fun lock() {
        privateKey?.fill(0)
        privateKey = null
    }
}

// The key is derived from username, e-mail and password (KeyDerivation), so it can always be recovered with the same three entries.
// A copy encrypted with the password (NIP-49) stays on the device so that the next sign in is fast.
class KeyVault(
    private val store: EncryptedKeyStore,
    private val logN: Int = Nip49.DEFAULT_LOG_N,
    private val cost: KeyDerivation.Cost = KeyDerivation.DEFAULT,
) {
    // The passphrase to offer when the user creates an account
    fun suggestPassphrase(): String = Passphrase.generate()

    fun signUp(username: String, email: String, password: String): Session {
        val check = PasswordPolicy.evaluate(password, username)
        if (!check.valid) throw WeakPasswordException(check)
        val id = AccountId.derive(username, email)
        if (store.get(id) != null) throw AccountExistsException()
        return derive(id, username, email, password)
    }

    // On a new device the key is derived and stored. On a known device the stored copy is decrypted, which is faster and detects a wrong password.
    fun signIn(username: String, email: String, password: String): Session {
        val id = AccountId.derive(username, email)
        val stored = store.get(id) ?: return derive(id, username, email, password)
        val key = Nip49.decrypt(stored, Passphrase.canonical(password))
        try {
            return Session(NostrKeys.publicKey(key), key.copyOf())
        } finally {
            key.fill(0)
        }
    }

    // Export needs the password again, also while a session is open
    fun exportNsec(username: String, email: String, password: String): String {
        val key = KeyDerivation.derive(username, email, password, cost)
        try {
            return NostrKeys.nsec(key)
        } finally {
            key.fill(0)
        }
    }

    // Removes the copy from this device. The account can be restored with username, e-mail and password.
    fun signOutAndForget(username: String, email: String) {
        store.delete(AccountId.derive(username, email))
    }

    fun isKnownOnThisDevice(username: String, email: String): Boolean = store.get(AccountId.derive(username, email)) != null

    private fun derive(id: String, username: String, email: String, password: String): Session {
        val key = KeyDerivation.derive(username, email, password, cost)
        try {
            store.put(id, Nip49.encrypt(key, Passphrase.canonical(password), logN))
            return Session(NostrKeys.publicKey(key), key.copyOf())
        } finally {
            key.fill(0)
        }
    }
}
