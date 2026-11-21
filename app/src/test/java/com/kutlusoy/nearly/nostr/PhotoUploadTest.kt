package com.kutlusoy.nearly.nostr

import com.kutlusoy.nearly.network.NetworkMode
import com.kutlusoy.nearly.network.NetworkPorts
import com.kutlusoy.nearly.network.NetworkSettings
import com.kutlusoy.nearly.network.RelayRouter
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

object Images {
    private fun seg(marker: Int, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0xFF); out.write(marker)
        out.write((data.size + 2) shr 8); out.write((data.size + 2) and 0xff)
        out.write(data)
        return out.toByteArray()
    }

    val SCAN = byteArrayOf(0x12, 0x34, 0xFF.toByte(), 0x00, 0x56, 0x78)

    fun jpeg(orientation: Int = 1, exif: Boolean = true, width: Int = 640, height: Int = 480): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        out.write(seg(0xE0, "JFIF\u0000".toByteArray() + byteArrayOf(1, 1, 0, 0, 1, 0, 1, 0, 0)))
        if (exif) {
            val tiff = ByteArrayOutputStream()
            tiff.write("II".toByteArray()); tiff.write(byteArrayOf(0x2A, 0x00, 0x08, 0x00, 0x00, 0x00))
            tiff.write(byteArrayOf(0x01, 0x00))
            tiff.write(byteArrayOf(0x12, 0x01, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00, orientation.toByte(), 0x00, 0x00, 0x00))
            tiff.write(byteArrayOf(0, 0, 0, 0))
            tiff.write("GPS-LAT-48.1351-LON-11.5820".toByteArray())
            out.write(seg(0xE1, "Exif\u0000\u0000".toByteArray() + tiff.toByteArray()))
            out.write(seg(0xE1, "http://ns.adobe.com/xap/1.0/\u0000<x:xmpmeta>PRIVATE-XMP</x:xmpmeta>".toByteArray()))
            out.write(seg(0xFE, "secret comment".toByteArray()))
        }
        out.write(seg(0xE2, "ICC_PROFILE\u0000".toByteArray() + byteArrayOf(1, 1, 9, 9, 9)))
        out.write(seg(0xDB, byteArrayOf(0, 1, 2, 3)))
        out.write(seg(0xC0, byteArrayOf(8, (height shr 8).toByte(), height.toByte(), (width shr 8).toByte(), width.toByte(), 1, 1, 0x11, 0)))
        out.write(seg(0xDA, byteArrayOf(1, 1, 0, 0, 63, 0)))
        out.write(SCAN)
        out.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
        return out.toByteArray()
    }

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf((data.size shr 24).toByte(), (data.size shr 16).toByte(), (data.size shr 8).toByte(), data.size.toByte()))
        out.write(type.toByteArray()); out.write(data); out.write(byteArrayOf(0, 0, 0, 0))
        return out.toByteArray()
    }

    fun png(width: Int = 32, height: Int = 16): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        out.write(chunk("IHDR", byteArrayOf(0, 0, 0, width.toByte(), 0, 0, 0, height.toByte(), 8, 2, 0, 0, 0)))
        out.write(chunk("tEXt", "Comment\u0000secret".toByteArray()))
        out.write(chunk("eXIf", "GPS-DATA".toByteArray()))
        out.write(chunk("IDAT", byteArrayOf(1, 2, 3, 4)))
        out.write(chunk("IEND", ByteArray(0)))
        return out.toByteArray()
    }
}

class PhotoUploadTest {
    private val anna = testSession("anna")

    private fun router(mode: NetworkMode) = RelayRouter({ NetworkSettings(mode) }, { NetworkPorts() })

    private fun contains(haystack: ByteArray, needle: String) = String(haystack, Charsets.ISO_8859_1).contains(needle)

    @Test
    fun jpegMetadataIsRemovedAndPictureDataStaysTheSame() {
        val original = Images.jpeg()
        assertTrue(ImageSanitizer.hasMetadata(original))
        val clean = ImageSanitizer.strip(original)
        assertFalse(contains(clean, "GPS-LAT"))
        assertFalse(contains(clean, "PRIVATE-XMP"))
        assertFalse(contains(clean, "secret comment"))
        assertTrue(contains(clean, "JFIF"))
        assertTrue(contains(clean, "ICC_PROFILE"))
        assertTrue(clean.size < original.size)
        assertEquals(ImageInfo("image/jpeg", 640, 480, false), ImageSanitizer.inspect(clean))
        val scanStart = original.indexOfFirstSequence(byteArrayOf(0xFF.toByte(), 0xDA.toByte()))
        assertTrue(clean.copyOfRange(clean.indexOfFirstSequence(byteArrayOf(0xFF.toByte(), 0xDA.toByte())), clean.size).contentEquals(original.copyOfRange(scanStart, original.size)))
        assertTrue(ImageSanitizer.strip(clean).contentEquals(clean))
        assertFalse(ImageSanitizer.hasMetadata(clean))
    }

    private fun ByteArray.indexOfFirstSequence(seq: ByteArray): Int {
        for (i in 0..size - seq.size) if (seq.indices.all { this[i + it] == seq[it] }) return i
        return -1
    }

    @Test
    fun orientationIsDetectedBeforeItIsLost() {
        assertEquals(false, ImageSanitizer.inspect(Images.jpeg(orientation = 1))!!.needsRotation)
        assertEquals(true, ImageSanitizer.inspect(Images.jpeg(orientation = 6))!!.needsRotation)
        assertEquals(false, ImageSanitizer.inspect(Images.jpeg(exif = false))!!.needsRotation)
    }

    @Test
    fun pngTextAndExifChunksAreRemoved() {
        val original = Images.png()
        val clean = ImageSanitizer.strip(original)
        assertFalse(contains(clean, "secret"))
        assertFalse(contains(clean, "GPS-DATA"))
        assertTrue(contains(clean, "IDAT"))
        assertEquals(ImageInfo("image/png", 32, 16, false), ImageSanitizer.inspect(clean))
    }

    @Test
    fun unknownAndDamagedFilesAreRefused() {
        assertNull(ImageSanitizer.inspect("GIF89a....".toByteArray()))
        assertNull(ImageSanitizer.inspect(ByteArray(0)))
        assertNull(ImageSanitizer.inspect(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), 0x7F, 0x00)))
        assertFailsWith<IllegalArgumentException> { ImageSanitizer.strip("<html>".toByteArray()) }
    }

    private fun descriptor(hash: String, size: Int, url: String = "https://cdn.example/$hash.jpg") =
        "{\"url\":\"$url\",\"sha256\":\"$hash\",\"size\":$size,\"type\":\"image/jpeg\",\"uploaded\":1}"

    private fun client(servers: List<String>, mode: NetworkMode = NetworkMode.Direct) = BlossomClient(servers, router(mode), allowInsecure = true)

    @Test
    fun uploadSendsASignedScopedTokenAndTheExactBytes() {
        val server = MockWebServer()
        val data = ImageSanitizer.strip(Images.jpeg())
        val hash = BlossomClient.sha256Hex(data)
        server.enqueue(MockResponse().setResponseCode(201).setBody(descriptor(hash, data.size)))
        server.start()
        val result = client(listOf("http://${server.hostName}:${server.port}")).upload(anna, data, "image/jpeg", now = 1_700_000_000)
        assertEquals(hash, result.sha256)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/upload", request.path)
        assertEquals(hash, request.getHeader("X-SHA-256"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("image/jpeg"))
        assertTrue(request.body.readByteArray().contentEquals(data))
        val auth = request.getHeader("Authorization")!!
        assertTrue(auth.startsWith("Nostr "))
        assertFalse(auth.contains("=") || auth.contains("+") || auth.contains("/"))
        val token = Event.parse(String(Base64.getUrlDecoder().decode(auth.removePrefix("Nostr ")), Charsets.UTF_8))
        assertTrue(token.isValid())
        assertEquals(24242, token.kind)
        assertEquals(listOf("upload"), token.tagValues("t"))
        assertEquals(listOf(hash), token.tagValues("x"))
        assertEquals(listOf(server.hostName), token.tagValues("server"))
        assertEquals(listOf("1700000300"), token.tagValues("expiration"))
        server.shutdown()
    }

    @Test
    fun nextServerIsTriedWhenOneFails() {
        val bad = MockWebServer().apply { enqueue(MockResponse().setResponseCode(500)); start() }
        val good = MockWebServer()
        val data = ImageSanitizer.strip(Images.png())
        good.enqueue(MockResponse().setResponseCode(200).setBody(descriptor(BlossomClient.sha256Hex(data), data.size)))
        good.start()
        val result = client(listOf("http://${bad.hostName}:${bad.port}", "http://${good.hostName}:${good.port}")).upload(anna, data, "image/png")
        assertNotNull(result)
        assertEquals(1, bad.requestCount)
        bad.shutdown(); good.shutdown()
    }

    @Test
    fun aServerThatStoredSomethingElseOrReturnsAnUnsafeAddressIsRejected() {
        val data = ImageSanitizer.strip(Images.jpeg())
        val hash = BlossomClient.sha256Hex(data)
        for (body in listOf(descriptor("00".repeat(32), data.size), descriptor(hash, data.size + 1), "not json", descriptor(hash, data.size, "javascript:alert(1)"))) {
            val server = MockWebServer().apply { enqueue(MockResponse().setResponseCode(201).setBody(body)); start() }
            val strict = BlossomClient(listOf("http://${server.hostName}:${server.port}"), router(NetworkMode.Direct), allowInsecure = true)
            assertFailsWith<BlossomException> { strict.upload(anna, data, "image/jpeg") }
            server.shutdown()
        }
    }

    @Test
    fun routeAndSizeRulesAreEnforcedBeforeAnyRequest() {
        val data = ByteArray(10)
        assertTrue(assertFailsWith<BlossomException> { BlossomClient(listOf("https://x.i2p"), router(NetworkMode.Tor)).upload(anna, data, "image/jpeg") }.message!!.contains("blocked"))
        assertTrue(assertFailsWith<BlossomException> { BlossomClient(listOf("http://cdn.example.org"), router(NetworkMode.Tor)).upload(anna, data, "image/jpeg") }.message!!.contains("unencrypted"))
        assertFailsWith<IllegalArgumentException> { client(listOf("https://x")).upload(anna, ByteArray(BlossomClient.MAX_UPLOAD + 1), "image/jpeg") }
    }

    @Test
    fun downloadChecksTheHash() {
        val server = MockWebServer()
        val data = "photo bytes".toByteArray()
        server.enqueue(MockResponse().setBody(okio.Buffer().write(data)))
        server.enqueue(MockResponse().setBody(okio.Buffer().write("tampered".toByteArray())))
        server.start()
        val url = "http://${server.hostName}:${server.port}/x.jpg"
        val c = client(emptyList())
        assertTrue(c.download(url, BlossomClient.sha256Hex(data)).contentEquals(data))
        assertFailsWith<BlossomException> { c.download(url, BlossomClient.sha256Hex(data)) }
        server.shutdown()
    }

    @Test
    fun photoServiceCleansRefusesRotatedFilesAndGivesAPostReference() {
        val server = MockWebServer()
        server.start()
        val raw = Images.jpeg()
        val clean = ImageSanitizer.strip(raw)
        val hash = BlossomClient.sha256Hex(clean)
        server.enqueue(MockResponse().setResponseCode(201).setBody(descriptor(hash, clean.size, "http://${server.hostName}:${server.port}/$hash.jpg")))
        val service = PhotoUploadService(client(listOf("http://${server.hostName}:${server.port}")), anna)
        val ref = service.upload(raw, alt = "a cat")
        val sent = server.takeRequest().body.readByteArray()
        assertFalse(contains(sent, "GPS-LAT"))
        assertTrue(sent.contentEquals(clean))
        assertEquals(PhotoRef(ref.url, "image/jpeg", hash, 640, 480, "a cat"), ref)
        assertFailsWith<BlossomException> { service.upload(Images.jpeg(orientation = 6)) }
        assertFailsWith<BlossomException> { service.upload("GIF89a".toByteArray()) }
        val transport = FakeTransport()
        val event = PostService(transport, anna).postPhotos("caption", listOf(PhotoRef("https://cdn.example/$hash.jpg", ref.mime, ref.sha256, ref.width, ref.height)))
        assertNotEquals(null, PostCodec.parse(event))
        server.shutdown()
    }
}
