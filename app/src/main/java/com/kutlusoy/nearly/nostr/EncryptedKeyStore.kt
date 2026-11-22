package com.kutlusoy.nearly.nostr

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

// Holds only password encrypted keys (ncryptsec), never a clear key
interface EncryptedKeyStore {
    fun put(id: String, ncryptsec: String)
    fun get(id: String): String?
    fun delete(id: String)
}

class FileKeyStore(private val dir: File) : EncryptedKeyStore {
    override fun put(id: String, ncryptsec: String) {
        val target = file(id)
        dir.mkdirs()
        runCatching { Files.setPosixFilePermissions(dir.toPath(), PosixFilePermissions.fromString("rwx------")) }
        val temp = File(dir, "$id.tmp")
        temp.writeText(ncryptsec)
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    override fun get(id: String): String? = file(id).takeIf { it.isFile }?.readText()?.trim()

    override fun delete(id: String) {
        file(id).delete()
    }

    private fun file(id: String): File {
        require(AccountId.isValid(id)) { "invalid account id" }
        return File(dir, "$id.ncryptsec")
    }
}
