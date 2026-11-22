package com.kutlusoy.nearly.nostr

import com.kutlusoy.nearly.network.NetworkMode
import com.kutlusoy.nearly.network.NetworkPorts
import com.kutlusoy.nearly.network.NetworkSettings
import com.kutlusoy.nearly.network.RelayRouter
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class ManualScheduler : Scheduler {
    class Task(val delay: Long, val run: () -> Unit) {
        @Volatile
        var cancelled = false
    }

    val tasks = CopyOnWriteArrayList<Task>()

    override fun schedule(delayMs: Long, task: () -> Unit): Cancelable {
        val t = Task(delayMs, task)
        tasks.add(t)
        return Cancelable { t.cancelled = true }
    }

    fun live() = tasks.filter { !it.cancelled }

    fun runAll() {
        val now = live()
        tasks.clear()
        now.forEach { it.run() }
    }
}

fun await(timeoutMs: Long = 10_000, check: () -> Boolean): Boolean {
    val end = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < end) {
        if (check()) return true
        Thread.sleep(25)
    }
    return check()
}

class ReconnectTest {
    private fun router(mode: NetworkMode) = RelayRouter({ NetworkSettings(mode) }, { NetworkPorts() })

    private val fixed = Backoff(random = { 0.5 })

    private fun recordingListener(received: CopyOnWriteArrayList<String>) = object : WebSocketListener() {
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            received.add(text)
            if (text.startsWith("[\"REQ\"")) {
                val sub = text.substringAfter("[\"REQ\",\"").substringBefore("\"")
                webSocket.send("[\"EOSE\",\"$sub\"]")
            } else if (text.startsWith("[\"EVENT\"")) {
                val id = Regex("\"id\":\"([0-9a-f]{64})\"").find(text)!!.groupValues[1]
                webSocket.send("[\"OK\",\"$id\",true,\"\"]")
            }
        }
    }

    @Test
    fun backoffGrowsStaysInBoundsAndCaps() {
        val b = Backoff(initialMs = 1_000, maxMs = 30_000, random = { 0.5 })
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), (1..7).map { b.delay(it) })
        assertTrue(Backoff(initialMs = 1_000, random = { 0.0 }).delay(1) == 800L)
        assertTrue(Backoff(initialMs = 1_000, random = { 1.0 }).delay(1) == 1_200L)
        assertTrue(Backoff().delay(500) <= 360_000)
    }

    @Test
    fun relayListParsesCommentsDuplicatesAndPutsDarknetFirst() {
        val text = "# comment\n\nwss://relay.damus.io\nhttp://not-a-relay\nwss://abcdefghijklmnop.onion\nwss://relay.damus.io\nws://\n  wss://nos.lol  \n"
        assertEquals(listOf("wss://abcdefghijklmnop.onion", "wss://relay.damus.io", "wss://nos.lol"), RelayList.parse(text))
        assertEquals(listOf("wss://relay.damus.io", "wss://abcdefghijklmnop.onion", "wss://nos.lol"), RelayList.parse(text, preferDarknet = false))
    }

    @Test
    fun lostConnectionIsRetriedAndSubscriptionsAndUnconfirmedEventsAreSentAgain() {
        val server = MockWebServer()
        val second = CopyOnWriteArrayList<String>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                webSocket.close(1001, "restart")
            }
        }))
        server.enqueue(MockResponse().withWebSocketUpgrade(recordingListener(second)))
        server.start()
        val scheduler = ManualScheduler()
        val client = NostrClient(listOf("ws://${server.hostName}:${server.port}"), router(NetworkMode.Direct), allowInsecure = true, scheduler = scheduler, backoff = fixed)
        val event = Event.sign(testSession("anna"), 1, "queued while offline", createdAt = 5)
        client.subscribe(listOf(Filter(kinds = listOf(1))), {})
        client.connect()
        assertTrue(await { scheduler.live().size == 1 })
        assertEquals(2_000L, scheduler.live().first().delay)
        assertEquals(RelayState.WaitingToRetry, client.statuses().first().state)
        client.publish(event)
        scheduler.runAll()
        assertTrue(await { second.any { it.startsWith("[\"REQ\"") } && second.any { it.startsWith("[\"EVENT\"") && event.id in it } })
        assertTrue(await { client.statuses().first().state == RelayState.Open })
        assertEquals(0, client.statuses().first().attempts)
        client.disconnect()
        server.shutdown()
    }

    @Test
    fun blockedRelayIsNotRetriedUntilTheModeChanges() {
        var mode = NetworkMode.Tor
        val r = RelayRouter({ NetworkSettings(mode) }, { NetworkPorts() })
        val scheduler = ManualScheduler()
        val client = NostrClient(listOf("wss://relay.i2p"), r, scheduler = scheduler, backoff = fixed)
        client.connect()
        assertTrue(await { client.statuses().first().state == RelayState.Failed })
        assertTrue(scheduler.live().isEmpty())
        assertTrue(client.statuses().first().lastError!!.startsWith("blocked"))
        mode = NetworkMode.TorAndI2p
        client.retryNow()
        assertTrue(await { client.statuses().first().state != RelayState.Failed })
        client.disconnect()
    }

    @Test
    fun failingRelayIsReplacedByASpareOne() {
        val good = MockWebServer()
        val received = CopyOnWriteArrayList<String>()
        good.enqueue(MockResponse().withWebSocketUpgrade(recordingListener(received)))
        good.start()
        val scheduler = ManualScheduler()
        val client = NostrClient(
            listOf("ws://127.0.0.1:1", "ws://${good.hostName}:${good.port}"),
            router(NetworkMode.Direct), allowInsecure = true, scheduler = scheduler, backoff = fixed, targetConnections = 1, swapAfterFailures = 2,
        )
        client.subscribe(listOf(Filter(kinds = listOf(1))), {})
        client.connect()
        assertTrue(await { scheduler.live().size == 1 })
        scheduler.runAll()
        assertTrue(await { received.any { it.startsWith("[\"REQ\"") } })
        val status = client.statuses()
        assertEquals(RelayState.Open, status.first { it.active }.state)
        assertTrue(status.first { it.url == "ws://127.0.0.1:1" }.let { !it.active })
        client.disconnect()
        good.shutdown()
    }

    @Test
    fun disconnectCancelsPendingRetries() {
        val scheduler = ManualScheduler()
        val client = NostrClient(listOf("ws://127.0.0.1:1"), router(NetworkMode.Direct), allowInsecure = true, scheduler = scheduler, backoff = fixed)
        client.connect()
        assertTrue(await { scheduler.live().size == 1 })
        client.disconnect()
        assertTrue(scheduler.live().isEmpty())
        assertEquals(RelayState.Idle, client.statuses().first().state)
    }
}
