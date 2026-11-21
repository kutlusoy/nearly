package com.kutlusoy.nearly.network

import java.net.InetSocketAddress
import java.net.Proxy

sealed interface Route {
    // SOCKS proxy on loopback. The target host stays unresolved so the proxy resolves it, never the device
    data class ViaProxy(val proxy: Proxy, val kind: Kind) : Route
    data object Direct : Route
    data class Blocked(val reason: String) : Route

    enum class Kind { Tor, I2p }
}

class RelayRouter(private val settings: () -> NetworkSettings, private val ports: () -> NetworkPorts) {
    fun route(host: String): Route {
        val mode = settings().mode
        val name = host.trim().trimEnd('.').lowercase()
        val onion = name.endsWith(".onion")
        val i2p = name.endsWith(".i2p")
        return when (mode) {
            NetworkMode.Direct -> if (onion || i2p) Route.Blocked("$name needs Tor or I2P") else Route.Direct
            NetworkMode.Tor -> if (i2p) Route.Blocked("I2P is switched off") else tor()
            NetworkMode.I2p -> if (i2p) i2p() else Route.Blocked("only .i2p relays are reachable in I2P mode")
            NetworkMode.TorAndI2p -> if (i2p) i2p() else tor()
        }
    }

    private fun tor() = Route.ViaProxy(socks(ports().torSocks), Route.Kind.Tor)

    private fun i2p() = Route.ViaProxy(socks(ports().i2pSocks), Route.Kind.I2p)

    private fun socks(port: Int) = Proxy(Proxy.Type.SOCKS, InetSocketAddress(Loopback.HOST, port))

    companion object {
        // Use this for the relay address, it keeps the host name unresolved
        fun target(host: String, port: Int): InetSocketAddress = InetSocketAddress.createUnresolved(host, port)
    }
}
