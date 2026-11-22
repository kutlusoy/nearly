package com.kutlusoy.nearly.nostr

import com.kutlusoy.nearly.network.RelayRouter
import com.kutlusoy.nearly.network.Route
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class BlossomException(message: String) : IOException(message)

data class BlobDescriptor(val url: String, val sha256: String, val size: Long, val type: String)

// What the server said to a delete request (BUD-12). Gone means the blob was not there any more, which is what the user wanted.
enum class DeleteResult { Deleted, Gone, Refused, Unreachable }

// Authorization token of BUD-11: a signed event of kind 24242, sent base64url without padding
object BlossomAuth {
    const val KIND = 24242
    private const val VALID_SECONDS = 300L

    fun uploadHeader(session: Session, sha256: String, serverHost: String, now: Long = System.currentTimeMillis() / 1000): String =
        header(session, "upload", "Upload photo", sha256, serverHost, now)

    fun deleteHeader(session: Session, sha256: String, serverHost: String, now: Long = System.currentTimeMillis() / 1000): String =
        header(session, "delete", "Delete photo", sha256, serverHost, now)

    private fun header(session: Session, action: String, content: String, sha256: String, serverHost: String, now: Long): String {
        val tags = listOf(
            listOf("t", action),
            listOf("expiration", (now + VALID_SECONDS).toString()),
            listOf("x", sha256),
            // Limits the token to this server, a stolen token cannot be replayed elsewhere. A delete token is the more dangerous one.
            listOf("server", serverHost.lowercase()),
        )
        val event = Event.sign(session, KIND, content, tags, now)
        return "Nostr " + Base64.getUrlEncoder().withoutPadding().encodeToString(event.toJson().toByteArray(Charsets.UTF_8))
    }
}

// Uploads to Blossom media servers (BUD-02) and loads blobs again. All calls block, run them off the main thread.
// Every request goes through RelayRouter, so over Tor the device never resolves a name and nothing goes out directly.
class BlossomClient(
    servers: List<String>,
    private val router: RelayRouter,
    private val allowInsecure: Boolean = false,
    private val clientFor: (Route) -> OkHttpClient = RelayConnection::defaultClient,
) {
    private val servers = servers.map { it.trim().trimEnd('/') }.filter { it.isNotEmpty() }

    // Tries the servers in order and returns the first success
    fun upload(session: Session, bytes: ByteArray, mime: String, now: Long = System.currentTimeMillis() / 1000): BlobDescriptor {
        require(bytes.isNotEmpty() && bytes.size <= MAX_UPLOAD) { "a photo has 1 byte to $MAX_UPLOAD bytes" }
        val hash = sha256Hex(bytes)
        val failures = mutableListOf<String>()
        for (server in servers) {
            try {
                return uploadTo(server, session, bytes, mime, hash, now)
            } catch (e: IOException) {
                failures.add("$server: ${e.message}")
            }
        }
        throw BlossomException("no server accepted the upload (${failures.joinToString("; ")})")
    }

    // Asks the server that holds the blob to delete it (BUD-12): DELETE /<sha256> with a token for this server only.
    // The owner of the key is the only one who can do it, a server may refuse anyway. Never throws for network or server trouble.
    fun delete(session: Session, blobUrl: String, sha256: String, now: Long = System.currentTimeMillis() / 1000): DeleteResult {
        val hash = sha256.lowercase()
        require(HASH.matches(hash)) { "not a SHA-256" }
        return try {
            val origin = originOf(blobUrl)
            val (client, base) = prepare(origin)
            val request = Request.Builder()
                .url("$base/$hash")
                .header("Authorization", BlossomAuth.deleteHeader(session, hash, URI(base).host, now))
                .delete()
                .build()
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200, 204 -> DeleteResult.Deleted
                    404 -> DeleteResult.Gone
                    401, 402, 403, 429 -> DeleteResult.Refused
                    else -> DeleteResult.Unreachable
                }
            }
        } catch (e: IOException) {
            DeleteResult.Unreachable
        }
    }

    // The server of a blob address: scheme, host and port, without the path
    private fun originOf(url: String): String {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            throw BlossomException("invalid address")
        }
        val host = uri.host ?: throw BlossomException("invalid address")
        return uri.scheme + "://" + host + if (uri.port >= 0) ":${uri.port}" else ""
    }

    // Loads a blob and checks that it is the one that was asked for
    fun download(url: String, expectedSha256: String, maxBytes: Int = MAX_UPLOAD): ByteArray {
        val (client, target) = prepare(url)
        client.newCall(Request.Builder().url(target).get().build()).execute().use { response ->
            if (!response.isSuccessful) throw BlossomException("download failed with ${response.code}")
            val body = response.body ?: throw BlossomException("empty answer")
            val declared = body.contentLength()
            if (declared > maxBytes) throw BlossomException("file too large")
            val data = body.source().use { source ->
                val buffer = okio.Buffer()
                var total = 0L
                while (true) {
                    val read = source.read(buffer, 8_192)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) throw BlossomException("file too large")
                }
                buffer.readByteArray()
            }
            if (sha256Hex(data) != expectedSha256.lowercase()) throw BlossomException("the file does not match its hash")
            return data
        }
    }

    private fun uploadTo(server: String, session: Session, bytes: ByteArray, mime: String, hash: String, now: Long): BlobDescriptor {
        val (client, base) = prepare(server)
        val host = URI(base).host
        val request = Request.Builder()
            .url("$base/upload")
            .header("Authorization", BlossomAuth.uploadHeader(session, hash, host, now))
            .header("X-SHA-256", hash)
            .put(bytes.toRequestBody(mime.toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code != 200 && response.code != 201) throw BlossomException("server answered ${response.code}")
            val text = response.body?.string().orEmpty()
            if (text.length > MAX_DESCRIPTOR) throw BlossomException("answer too large")
            val descriptor = parseDescriptor(text) ?: throw BlossomException("unusable answer")
            // The server must have stored exactly what was sent, and the address must be safe to put into a post
            if (descriptor.sha256 != hash || descriptor.size != bytes.size.toLong()) throw BlossomException("the server stored something else")
            if (!safeUrl(descriptor.url)) throw BlossomException("unsafe address in the answer")
            return descriptor
        }
    }

    private fun prepare(address: String): Pair<OkHttpClient, String> {
        val uri = try {
            URI(address)
        } catch (e: Exception) {
            throw BlossomException("invalid address")
        }
        val host = uri.host ?: throw BlossomException("invalid address")
        val darknet = host.endsWith(".onion", true) || host.endsWith(".i2p", true)
        val secure = uri.scheme.equals("https", true)
        if (!secure && !uri.scheme.equals("http", true)) throw BlossomException("not an http address")
        // Over a darknet address the connection is encrypted end to end, clearnet needs TLS
        if (!secure && !darknet && !allowInsecure) throw BlossomException("unencrypted connection refused")
        val route = router.route(host)
        if (route is Route.Blocked) throw BlossomException("blocked: ${route.reason}")
        val client = clientFor(route).newBuilder()
            .pingInterval(0, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build()
        return client to address
    }

    private fun safeUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        val host = uri.host ?: return false
        uri.scheme.equals("https", true) || host.endsWith(".onion", true) || host.endsWith(".i2p", true) || allowInsecure
    }.getOrDefault(false)

    companion object {
        const val MAX_UPLOAD = 10 * 1024 * 1024
        private const val MAX_DESCRIPTOR = 16 * 1024
        private val HASH = Regex("[0-9a-f]{64}")

        fun sha256Hex(bytes: ByteArray): String = NostrKeys.toHex(MessageDigest.getInstance("SHA-256").digest(bytes))

        fun parseDescriptor(text: String): BlobDescriptor? = try {
            val o = Json.parseToJsonElement(text).jsonObject
            BlobDescriptor(
                url = o.getValue("url").jsonPrimitive.content,
                sha256 = o.getValue("sha256").jsonPrimitive.content.lowercase(),
                size = o.getValue("size").jsonPrimitive.long,
                type = o["type"]?.jsonPrimitive?.content ?: "application/octet-stream",
            )
        } catch (e: Exception) {
            null
        }
    }
}

// From a photo file to a reference that a post can carry
class PhotoUploadService(private val blossom: BlossomClient, private val session: Session) {
    fun upload(bytes: ByteArray, alt: String? = null): PhotoRef {
        // High resolution photos never go to a public server, they are for the chat only
        if (bytes.size > MAX_PUBLIC_BYTES) throw BlossomException("too large for a public photo, high resolution photos are for the chat")
        val info = ImageSanitizer.inspect(bytes) ?: throw BlossomException("unsupported image, use JPEG or PNG")
        if (info.needsRotation) throw BlossomException("the photo must be rotated before the upload, its orientation lives in metadata that is removed")
        val clean = ImageSanitizer.strip(bytes)
        val blob = blossom.upload(session, clean, info.mime)
        return PhotoRef(blob.url, info.mime, blob.sha256, info.width, info.height, alt)
    }

    // Removes the file of a photo from its server
    fun delete(photo: PhotoRef): DeleteResult = blossom.delete(session, photo.url, photo.sha256)

    companion object {
        // A public photo of normal resolution is far below this, the photo processor keeps it under 150 KB
        const val MAX_PUBLIC_BYTES = 400_000
    }
}
