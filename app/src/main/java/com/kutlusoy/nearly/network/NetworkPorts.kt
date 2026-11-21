package com.kutlusoy.nearly.network

import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket

object Loopback {
    const val HOST = "127.0.0.1"
}

data class NetworkPorts(
    val torSocks: Int = 9050,
    val torControl: Int = 9051,
    val i2pSocks: Int = 4447,
) {
    // Moves a port that another program already uses (Orbot, a system Tor, the other flavor) to the next free one
    fun resolved(isFree: (Int) -> Boolean = ::loopbackFree): NetworkPorts {
        val taken = mutableSetOf<Int>()
        fun pick(preferred: Int): Int {
            var port = preferred
            repeat(MAX_TRIES) {
                if (port !in taken && isFree(port)) return port.also { taken.add(it) }
                port += STEP
            }
            return preferred.also { taken.add(it) }
        }
        return NetworkPorts(pick(torSocks), pick(torControl), pick(i2pSocks))
    }

    companion object {
        val Debug = NetworkPorts(torSocks = 19050, torControl = 19051, i2pSocks = 14447)
        private const val STEP = 10
        private const val MAX_TRIES = 50

        fun loopbackFree(port: Int): Boolean = try {
            ServerSocket().use {
                it.reuseAddress = false
                it.bind(InetSocketAddress(Loopback.HOST, port))
            }
            true
        } catch (e: IOException) {
            false
        }
    }
}
