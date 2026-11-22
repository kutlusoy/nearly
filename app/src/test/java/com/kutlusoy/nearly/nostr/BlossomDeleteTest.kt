package com.kutlusoy.nearly.nostr

import com.kutlusoy.nearly.network.NetworkMode
import com.kutlusoy.nearly.network.NetworkPorts
import com.kutlusoy.nearly.network.NetworkSettings
import com.kutlusoy.nearly.network.RelayRouter
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

// Deleting a photo on a Blossom server (BUD-12)
class BlossomDeleteTest {
    private val anna = testSession("anna")

    private fun router(mode: NetworkMode) = RelayRouter({ NetworkSettings(mode) }, { NetworkPorts() })

    private fun descriptor(hash: String, size: Int, url: String) =
        "{\"url\":\"$url\",\"sha256\":\"$hash\",\"size\":$size,\"type\":\"image/jpeg\",\"uploaded\":1}"

    private fun client(servers: List<String>, mode: NetworkMode = NetworkMode.Direct) = BlossomClient(servers, router(mode), allowInsecure = true)

    @Test
    fun deleteSendsASignedTokenForThisServerOnly() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(204))
        server.start()
        val hash = BlossomClient.sha256Hex(byteArrayOf(1, 2, 3))
        val url = "http://${server.hostName}:${server.port}/$hash.jpg"
        assertEquals(DeleteResult.Deleted, client(listOf("http://other.example")).delete(anna, url, hash, now = 1_700_000_000))
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/$hash", request.path)
        val auth = request.getHeader("Authorization")!!
        assertFalse(auth.contains("=") || auth.contains("+") || auth.contains("/"))
        val token = Event.parse(String(Base64.getUrlDecoder().decode(auth.removePrefix("Nostr ")), Charsets.UTF_8))
        assertTrue(token.isValid())
        assertEquals(24242, token.kind)
        assertEquals(listOf("delete"), token.tagValues("t"))
        assertEquals(listOf(hash), token.tagValues("x"))
        assertEquals(listOf(server.hostName), token.tagValues("server"))
        assertEquals(listOf("1700000300"), token.tagValues("expiration"))
        server.shutdown()
    }

    @Test
    fun deleteAnswersAreMappedAndNothingThrows() {
        val hash = BlossomClient.sha256Hex(byteArrayOf(9))
        val expected = mapOf(200 to DeleteResult.Deleted, 204 to DeleteResult.Deleted, 404 to DeleteResult.Gone, 401 to DeleteResult.Refused, 403 to DeleteResult.Refused, 402 to DeleteResult.Refused, 429 to DeleteResult.Refused, 503 to DeleteResult.Unreachable, 500 to DeleteResult.Unreachable)
        for ((code, result) in expected) {
            val server = MockWebServer().apply { enqueue(MockResponse().setResponseCode(code)); start() }
            assertEquals(result, client(listOf("http://x.example")).delete(anna, "http://${server.hostName}:${server.port}/$hash.jpg", hash), "status $code")
            server.shutdown()
        }
        assertEquals(DeleteResult.Unreachable, client(listOf("http://x.example")).delete(anna, "http://127.0.0.1:1/$hash.jpg", hash))
        assertEquals(DeleteResult.Unreachable, BlossomClient(emptyList(), router(NetworkMode.Tor)).delete(anna, "http://cdn.example.org/$hash.jpg", hash), "unencrypted is refused")
        assertEquals(DeleteResult.Unreachable, BlossomClient(emptyList(), router(NetworkMode.Tor)).delete(anna, "https://x.i2p/$hash.jpg", hash), "blocked route")
        assertFailsWith<IllegalArgumentException> { client(listOf("https://x")).delete(anna, "https://x/y", "not a hash") }
    }

    @Test
    fun theServiceDeletesAUploadedPhoto() {
        val server = MockWebServer()
        server.start()
        val clean = ImageSanitizer.strip(Images.jpeg())
        val hash = BlossomClient.sha256Hex(clean)
        server.enqueue(MockResponse().setResponseCode(201).setBody(descriptor(hash, clean.size, "http://${server.hostName}:${server.port}/$hash.jpg")))
        server.enqueue(MockResponse().setResponseCode(200))
        val service = PhotoUploadService(client(listOf("http://${server.hostName}:${server.port}")), anna)
        val ref = service.upload(Images.jpeg())
        server.takeRequest()
        assertEquals(DeleteResult.Deleted, service.delete(ref))
        assertEquals("DELETE", server.takeRequest().method)
        server.shutdown()
    }
}
