package com.kutlusoy.nearly.nostr

import com.kutlusoy.nearly.network.NetworkMode
import com.kutlusoy.nearly.network.NetworkPorts
import com.kutlusoy.nearly.network.NetworkSettings
import com.kutlusoy.nearly.network.RelayRouter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockWebServer

class PublicPhotoLimitTest {
    @Test
    fun aPhotoOverTheLimitNeverReachesAPublicServer() {
        val server = MockWebServer().apply { start() }
        val client = BlossomClient(listOf("http://${server.hostName}:${server.port}"), RelayRouter({ NetworkSettings(NetworkMode.Direct) }, { NetworkPorts() }), allowInsecure = true)
        val service = PhotoUploadService(client, testSession("anna"))
        val big = Images.jpeg() + ByteArray(PhotoUploadService.MAX_PUBLIC_BYTES)
        val failure = assertFailsWith<BlossomException> { service.upload(big) }
        assertTrue(failure.message!!.contains("public photo"))
        assertEquals(0, server.requestCount)
        server.shutdown()
    }
}
