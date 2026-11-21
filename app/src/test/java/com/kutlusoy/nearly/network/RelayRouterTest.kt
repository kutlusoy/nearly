package com.kutlusoy.nearly.network

import java.net.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayRouterTest {
    private fun router(mode: NetworkMode) = RelayRouter({ NetworkSettings(mode) }, { NetworkPorts() })

    private fun kind(route: Route) = (route as Route.ViaProxy).kind

    private fun assertBlocked(route: Route) = assertTrue(route is Route.Blocked)

    @Test
    fun torModeSendsEverythingThroughTor() {
        val r = router(NetworkMode.Tor)
        assertEquals(Route.Kind.Tor, kind(r.route("relay.example.org")))
        assertEquals(Route.Kind.Tor, kind(r.route("abcdefghijklmnop.onion")))
        assertBlocked(r.route("relay.i2p"))
    }

    @Test
    fun i2pModeAllowsOnlyI2pHosts() {
        val r = router(NetworkMode.I2p)
        assertEquals(Route.Kind.I2p, kind(r.route("relay.i2p")))
        assertBlocked(r.route("relay.example.org"))
        assertBlocked(r.route("abcdefghijklmnop.onion"))
    }

    @Test
    fun combinedModeUsesTheNetworkOfTheAddress() {
        val r = router(NetworkMode.TorAndI2p)
        assertEquals(Route.Kind.I2p, kind(r.route("RELAY.I2P.")))
        assertEquals(Route.Kind.Tor, kind(r.route("relay.example.org")))
    }

    @Test
    fun directModeNeverLeaksDarknetHosts() {
        val r = router(NetworkMode.Direct)
        assertEquals(Route.Direct, r.route("relay.example.org"))
        assertBlocked(r.route("abcdefghijklmnop.onion"))
        assertBlocked(r.route("relay.i2p"))
    }

    @Test
    fun proxyIsLoopbackSocksAndTargetStaysUnresolved() {
        val route = router(NetworkMode.Tor).route("relay.example.org") as Route.ViaProxy
        assertEquals(Proxy.Type.SOCKS, route.proxy.type())
        assertTrue(route.proxy.address().toString().contains("127.0.0.1"))
        assertTrue(RelayRouter.target("relay.example.org", 443).isUnresolved)
    }
}
