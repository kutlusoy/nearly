package com.kutlusoy.nearly.media

import com.kutlusoy.nearly.chat.FakeLink
import com.kutlusoy.nearly.chat.FakeRelay
import com.kutlusoy.nearly.chat.newSession
import com.kutlusoy.nearly.nostr.BlossomClient
import com.kutlusoy.nearly.nostr.BlossomException
import com.kutlusoy.nearly.nostr.DeleteResult
import com.kutlusoy.nearly.nostr.PhotoRef
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class PhotoBackendTest {
    private fun dir() = File.createTempFile("photos", "").let { it.delete(); it.mkdirs(); it }

    private fun hashOf(n: Int) = BlossomClient.sha256Hex(ByteArray(10) { n.toByte() })

    @Test
    fun theCacheKeepsPhotosByHashAndDropsTheOldestOnes() {
        val cache = PhotoCache(dir(), maxBytes = 25)
        cache.put(hashOf(1), ByteArray(10) { 1 })
        Thread.sleep(20)
        cache.put(hashOf(2), ByteArray(10) { 2 })
        Thread.sleep(20)
        assertNotNull(cache.get(hashOf(1)))
        Thread.sleep(20)
        cache.put(hashOf(3), ByteArray(10) { 3 })
        assertNull(cache.get(hashOf(2)), "the least recently used one went first")
        assertNotNull(cache.get(hashOf(1)))
        assertNotNull(cache.get(hashOf(3)))
        cache.remove(hashOf(1))
        assertNull(cache.get(hashOf(1)))
    }

    @Test
    fun onlyAHashIsAFileName() {
        val root = dir()
        val cache = PhotoCache(File(root, "inner"))
        cache.put("../evil", byteArrayOf(1))
        cache.put("a".repeat(63), byteArrayOf(1))
        assertNull(cache.get("../evil"))
        assertTrue(root.walkTopDown().none { it.isFile })
    }

    @Test
    fun aPhotoIsLoadedOnceCheckedAgainstItsHashAndThenKeptOnTheDisk() {
        val server = MockWebServer()
        val bytes = ByteArray(2000) { (it % 251).toByte() }
        val hash = BlossomClient.sha256Hex(bytes)
        server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))
        server.start()
        val backend = BlossomPhotoBackend(FakeLink(FakeRelay()), emptyList(), dir(), allowInsecure = true)
        val ref = PhotoRef("http://${server.hostName}:${server.port}/$hash.jpg", "image/jpeg", hash)
        assertTrue(backend.load(ref).contentEquals(bytes))
        assertTrue(backend.load(ref).contentEquals(bytes), "second time from the disk")
        assertEquals(1, server.requestCount)
        server.shutdown()
    }

    @Test
    fun aPhotoThatDoesNotMatchItsHashIsRefusedAndNotKept() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("something else"))
        server.start()
        val backend = BlossomPhotoBackend(FakeLink(FakeRelay()), emptyList(), dir(), allowInsecure = true)
        val hash = hashOf(5)
        val ref = PhotoRef("http://${server.hostName}:${server.port}/$hash.jpg", "image/jpeg", hash)
        assertFailsWith<BlossomException> { backend.load(ref) }
        server.enqueue(MockResponse().setResponseCode(404))
        assertFailsWith<BlossomException> { backend.load(ref) }
        server.shutdown()
    }

    @Test
    fun deletingRemovesTheCachedFileToo() {
        val server = MockWebServer()
        val bytes = ByteArray(300) { 4 }
        val hash = BlossomClient.sha256Hex(bytes)
        server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))
        server.enqueue(MockResponse().setResponseCode(204))
        server.start()
        val backend = BlossomPhotoBackend(FakeLink(FakeRelay()), emptyList(), dir(), allowInsecure = true)
        val ref = PhotoRef("http://${server.hostName}:${server.port}/$hash.jpg", "image/jpeg", hash)
        backend.load(ref)
        assertEquals(DeleteResult.Deleted, backend.delete(newSession(), ref))
        server.takeRequest()
        server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))
        backend.load(ref)
        assertEquals(3, server.requestCount, "it had to be loaded again")
        server.shutdown()
    }
}
