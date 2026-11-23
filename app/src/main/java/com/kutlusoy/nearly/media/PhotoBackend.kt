package com.kutlusoy.nearly.media

import com.kutlusoy.nearly.network.NetworkLink
import com.kutlusoy.nearly.nostr.BlossomClient
import com.kutlusoy.nearly.nostr.DeleteResult
import com.kutlusoy.nearly.nostr.PhotoRef
import com.kutlusoy.nearly.nostr.PhotoUploadService
import com.kutlusoy.nearly.nostr.Session
import java.io.File
import java.io.InputStream

// Public Blossom servers for the first tests. They are NOT checked: each has to accept uploads without payment and has to work through Tor.
// The first one that accepts is used, the others are the fallback.
object BlossomServers {
    val DEFAULT = listOf("https://blossom.ditto.pub", "https://blossom.primal.net", "https://blossom.band", "https://nostr.download")
}

// Everything the app does with photo files: prepare and upload, delete, load. All calls block, run them off the main thread.
interface PhotoBackend {
    fun upload(session: Session, open: () -> InputStream, alt: String? = null): PhotoRef
    fun delete(session: Session, photo: PhotoRef): DeleteResult
    fun load(photo: PhotoRef): ByteArray
}

class BlossomPhotoBackend(
    link: NetworkLink,
    servers: List<String> = BlossomServers.DEFAULT,
    cacheDir: File,
    allowInsecure: Boolean = false,
    private val processor: PhotoProcessor = PhotoProcessor(),
    maxCacheBytes: Long = PhotoCache.DEFAULT_MAX,
) : PhotoBackend {
    private val client = BlossomClient(servers, link.router, allowInsecure)
    private val cache = PhotoCache(cacheDir, maxCacheBytes)

    override fun upload(session: Session, open: () -> InputStream, alt: String?): PhotoRef =
        PhotoPublisher(PhotoUploadService(client, session)).publish(processor.process(open), alt)

    override fun delete(session: Session, photo: PhotoRef): DeleteResult =
        client.delete(session, photo.url, photo.sha256).also { cache.remove(photo.sha256) }

    // From the cache when it is there, otherwise from the server, always checked against the hash
    override fun load(photo: PhotoRef): ByteArray =
        cache.get(photo.sha256) ?: client.download(photo.url, photo.sha256).also { cache.put(photo.sha256, it) }
}

// Downloaded photos on the disk, named by their hash, the least recently used go first when the limit is reached
class PhotoCache(private val dir: File, private val maxBytes: Long = DEFAULT_MAX) {
    init {
        dir.mkdirs()
    }

    @Synchronized
    fun get(hash: String): ByteArray? {
        val file = fileFor(hash) ?: return null
        if (!file.isFile) return null
        file.setLastModified(System.currentTimeMillis())
        return runCatching { file.readBytes() }.getOrNull()
    }

    @Synchronized
    fun put(hash: String, bytes: ByteArray) {
        val file = fileFor(hash) ?: return
        runCatching { file.writeBytes(bytes) }
        trim()
    }

    @Synchronized
    fun remove(hash: String) {
        fileFor(hash)?.delete()
    }

    private fun trim() {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            total -= file.length()
            file.delete()
        }
    }

    // Only a SHA-256 is a file name, nothing else can leave the folder
    private fun fileFor(hash: String): File? = hash.lowercase().takeIf { HASH.matches(it) }?.let { File(dir, it) }

    companion object {
        const val DEFAULT_MAX = 100L * 1024 * 1024
        private val HASH = Regex("[0-9a-f]{64}")
    }
}
