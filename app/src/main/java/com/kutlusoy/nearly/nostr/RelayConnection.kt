package com.kutlusoy.nearly.nostr

import com.kutlusoy.nearly.network.RelayRouter
import com.kutlusoy.nearly.network.Route
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

private object NoLocalDns : Dns {
    override fun lookup(hostname: String): List<java.net.InetAddress> = throw UnknownHostException("no local DNS in darknet mode")
}

interface RelayListener {
    fun onOpen() {}
    fun onMessage(message: RelayMessage) {}
    fun onClosed(reason: String) {}
}

// One WebSocket to one relay. The route comes from RelayRouter, so nothing reaches the network outside Tor or I2P unless the user chose direct mode.
class RelayConnection(
    val url: String,
    private val router: RelayRouter,
    private val allowInsecure: Boolean = false,
    private val clientFor: (Route) -> OkHttpClient = ::defaultClient,
) {
    enum class State { Idle, Connecting, Open, Closed }

    @Volatile
    var state: State = State.Idle
        private set

    private var socket: WebSocket? = null

    fun connect(listener: RelayListener) {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return fail(listener, "invalid relay address")
        }
        val host = uri.host ?: return fail(listener, "invalid relay address")
        val secure = uri.scheme.equals("wss", true)
        if (!secure && !uri.scheme.equals("ws", true)) return fail(listener, "not a websocket address")
        val darknet = host.endsWith(".onion", true) || host.endsWith(".i2p", true)
        // Through a darknet address the connection is encrypted end to end, clearnet needs TLS
        if (!secure && !darknet && !allowInsecure) return fail(listener, "unencrypted relay connection refused")
        val route = router.route(host)
        if (route is Route.Blocked) return fail(listener, "blocked: ${route.reason}")
        state = State.Connecting
        socket = clientFor(route).newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                state = State.Open
                listener.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                RelayMessage.parse(text)?.let { listener.onMessage(it) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                state = State.Closed
                listener.onClosed(reason.ifEmpty { "closed" })
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                state = State.Closed
                listener.onClosed(t.message ?: t.javaClass.simpleName)
            }
        })
    }

    fun subscribe(id: String, filters: List<Filter>): Boolean =
        send("[\"REQ\",${Event.quote(id)}${filters.joinToString("") { "," + it.toJson() }}]")

    fun unsubscribe(id: String): Boolean = send("[\"CLOSE\",${Event.quote(id)}]")

    fun publish(event: Event): Boolean = send("[\"EVENT\",${event.toJson()}]")

    fun close() {
        socket?.close(1000, "bye")
        socket = null
        state = State.Closed
    }

    private fun send(text: String): Boolean = state == State.Open && socket?.send(text) == true

    private fun fail(listener: RelayListener, reason: String) {
        state = State.Closed
        listener.onClosed(reason)
    }

    companion object {
        fun defaultClient(route: Route): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .pingInterval(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .connectTimeout(60, TimeUnit.SECONDS)
            if (route is Route.ViaProxy) {
                // The proxy resolves the name. Failing here makes sure the device never does.
                builder.proxy(route.proxy).dns(NoLocalDns)
            } else {
                builder.proxy(java.net.Proxy.NO_PROXY)
            }
            return builder.build()
        }
    }
}
