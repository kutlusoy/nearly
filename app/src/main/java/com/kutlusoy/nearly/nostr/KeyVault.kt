package com.kutlusoy.nearly.nostr

class AccountExistsException : Exception("an account with this username and e-mail already exists on this device")
class UnknownAccountException : Exception("no account with this username and e-mail on this device")
class WeakPasswordException(val check: PasswordCheck) : Exception("the password does not meet the rules")

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

class KeyVault(private val store: EncryptedKeyStore, private val logN: Int = Nip49.DEFAULT_LOG_N) {

    // Creates a new key. The caller MUST show backupNsec once and ask the user to store it.
    fun create(username: String, email: String, password: String): Pair<Session, String> {
        val key = NostrKeys.generatePrivateKey()
        try {
            val session = register(username, email, password, key)
            return session to NostrKeys.nsec(key)
        } finally {
            key.fill(0)
        }
    }

    fun import(username: String, email: String, password: String, nsec: String): Session {
        val key = NostrKeys.decodeNsec(nsec)
        try {
            return register(username, email, password, key)
        } finally {
            key.fill(0)
        }
    }

    fun unlock(username: String, email: String, password: String): Session {
        val key = decrypt(username, email, password)
        try {
            return Session(NostrKeys.publicKey(key), key.copyOf())
        } finally {
            key.fill(0)
        }
    }

    // Export needs the password again, also while a session is open
    fun exportNsec(username: String, email: String, password: String): String {
        val key = decrypt(username, email, password)
        try {
            return NostrKeys.nsec(key)
        } finally {
            key.fill(0)
        }
    }

    fun changePassword(username: String, email: String, oldPassword: String, newPassword: String) {
        requireStrong(newPassword, username)
        val key = decrypt(username, email, oldPassword)
        try {
            store.put(AccountId.derive(username, email), Nip49.encrypt(key, newPassword, logN))
        } finally {
            key.fill(0)
        }
    }

    // Removes the key from the device. Without a backup the identity is lost.
    fun delete(username: String, email: String, password: String) {
        decrypt(username, email, password).fill(0)
        store.delete(AccountId.derive(username, email))
    }

    fun exists(username: String, email: String): Boolean = store.get(AccountId.derive(username, email)) != null

    private fun register(username: String, email: String, password: String, key: ByteArray): Session {
        requireStrong(password, username)
        val id = AccountId.derive(username, email)
        if (store.get(id) != null) throw AccountExistsException()
        store.put(id, Nip49.encrypt(key, password, logN))
        return Session(NostrKeys.publicKey(key), key.copyOf())
    }

    private fun decrypt(username: String, email: String, password: String): ByteArray {
        val stored = store.get(AccountId.derive(username, email)) ?: throw UnknownAccountException()
        return Nip49.decrypt(stored, password)
    }

    private fun requireStrong(password: String, username: String) {
        val check = PasswordPolicy.evaluate(password, username)
        if (!check.valid) throw WeakPasswordException(check)
    }
}
