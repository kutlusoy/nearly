package com.kutlusoy.nearly.nostr

import com.kutlusoy.nearly.network.NetworkMode
import com.kutlusoy.nearly.network.NetworkPorts
import com.kutlusoy.nearly.network.NetworkSettings
import com.kutlusoy.nearly.network.RelayRouter
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class NostrClientTest {
    private fun session(): Session =
        KeyVault(FileKeyStore(Files.createTempDirectory("keys").toFile()), logN = 10).create("anna", "anna@example.org", "Correct-Horse-9!").first

    private fun router(mode: NetworkMode) = RelayRouter({ NetworkSettings(mode) }, { NetworkPorts() })

    private class Relay(val stored: List<Event>) {
        val server = MockWebServer()
        val received = CopyOnWriteArrayList<String>()

        init {
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    received.add(text)
                    when {
                        text.startsWith("[\"REQ\"") -> {
                            val sub = text.substringAfter("[\"REQ\",\"").substringBefore("\"")
                            stored.forEach { webSocket.send("[\"EVENT\",\"$sub\",${it.toJson()}]") }
                            webSocket.send("[\"EOSE\",\"$sub\"]")
                        }
                        text.startsWith("[\"EVENT\"") -> {
                            val id = Regex("\"id\":\"([0-9a-f]{64})\"").find(text)!!.groupValues[1]
                            webSocket.send("[\"OK\",\"$id\",true,\"\"]")
                        }
                    }
                }
            }))
            server.start()
        }

        val url: String get() = "ws://${server.hostName}:${server.port}"
    }

    @Test
    fun subscribeDeliversValidEventsOnceAndDropsForgeries() {
        val s = session()
        val good = Event.sign(s, 1, "hello", createdAt = 10)
        val forged = good.copy(id = "00".repeat(32))
        val tampered = Event.sign(s, 1, "real", createdAt = 11).copy(content = "fake")
        val relay1 = Relay(listOf(good, forged, tampered))
        val relay2 = Relay(listOf(good))
        val client = NostrClient(listOf(relay1.url, relay2.url), router(NetworkMode.Direct), allowInsecure = true)
        val events = CopyOnWriteArrayList<Event>()
        val eose = CountDownLatch(1)
        client.subscribe(listOf(Filter(kinds = listOf(1))), { events.add(it) }, { eose.countDown() })
        client.connect()
        assertTrue(eose.await(10, TimeUnit.SECONDS))
        Thread.sleep(500)
        assertEquals(listOf(good), events.toList())
        client.disconnect()
        relay1.server.shutdown()
        relay2.server.shutdown()
    }

    @Test
    fun publishReachesTheRelayAndReportsTheResult() {
        val relay = Relay(emptyList())
        val client = NostrClient(listOf(relay.url), router(NetworkMode.Direct), allowInsecure = true)
        val event = Event.sign(session(), 1, "post", createdAt = 20)
        val result = CountDownLatch(1)
        var accepted = false
        client.publish(event)
        client.connect()
        client.publish(event, object : NostrClient.PublishListener {
            override fun onResult(relay: String, accepted2: Boolean, message: String) {
                accepted = accepted2
                result.countDown()
            }
        })
        assertTrue(result.await(10, TimeUnit.SECONDS))
        assertTrue(accepted)
        assertTrue(relay.received.any { it.startsWith("[\"EVENT\"") && event.id in it })
        client.disconnect()
        relay.server.shutdown()
    }

    @Test
    fun darknetRulesAreEnforcedBeforeAnyConnection() {
        val closed = CopyOnWriteArrayList<String>()
        val listener = object : RelayListener {
            override fun onClosed(reason: String) {
                closed.add(reason)
            }
        }
        RelayConnection("wss://relay.i2p", router(NetworkMode.Tor)).connect(listener)
        RelayConnection("ws://relay.example.org", router(NetworkMode.Tor)).connect(listener)
        RelayConnection("wss://abcdefghijklmnop.onion", router(NetworkMode.Direct)).connect(listener)
        RelayConnection("http://relay.example.org", router(NetworkMode.Tor)).connect(listener)
        assertEquals(4, closed.size)
        assertTrue(closed[0].startsWith("blocked"))
        assertTrue("unencrypted" in closed[1])
        assertTrue(closed[2].startsWith("blocked"))
        assertTrue("websocket" in closed[3])
    }
}
