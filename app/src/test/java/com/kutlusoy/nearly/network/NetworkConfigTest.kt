package com.kutlusoy.nearly.network

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkConfigTest {
    private val ports = NetworkPorts()

    @Test
    fun torrcIsClientOnly() {
        val text = TorConfig.torrc(File("/data/tor"), ports)
        assertTrue("SocksPort 127.0.0.1:9050" in text)
        assertTrue("ControlPort 127.0.0.1:9051" in text)
        assertTrue("CookieAuthentication 1" in text)
        assertTrue("ClientOnly 1" in text)
        assertTrue("ORPort 0" in text)
        assertTrue("ExitRelay 0" in text)
        assertTrue("ExitPolicy reject *:*" in text)
        assertFalse("RelayBandwidthRate" in text)
    }

    @Test
    fun i2pConfigHasOnlyTheSocksProxyAndNoTransit() {
        val text = I2pConfig.conf(ipv6 = false, ports = ports)
        assertTrue("ipv6 = false" in text)
        assertTrue("notransit = true" in text)
        assertTrue("floodfill = false" in text)
        assertTrue("[socksproxy]\nenabled = true\naddress = 127.0.0.1\nport = 4447" in text)
        for (section in listOf("http", "httpproxy", "sam", "bob", "i2cp", "i2pcontrol", "upnp")) {
            assertTrue(section, "[$section]\nenabled = false" in text)
        }
    }

    @Test
    fun portsMoveUpToTheNextFreeOne() {
        val busy = setOf(9050, 9060, 9051, 4447)
        val resolved = NetworkPorts().resolved { it !in busy }
        assertEquals(9070, resolved.torSocks)
        assertEquals(9061, resolved.torControl)
        assertEquals(4457, resolved.i2pSocks)
    }

    @Test
    fun portsStayWhenFree() {
        assertEquals(NetworkPorts(), NetworkPorts().resolved { true })
    }

    @Test
    fun sanitizerRemovesIdentifiers() {
        val line = "connect 192.168.1.5:9050 abcdefghijklmnop.onion npub1qqqqqqqqqqqqqqqqqqqqqqqqqqqq /data/user/0/app/tor"
        assertEquals("connect <ip> <onion> <nostr> <path>", LogSanitizer.clean(line))
    }

    @Test
    fun bootstrapUsesTheLatestLine() {
        val lines = listOf("Bootstrapped 5% (conn)", "x", "Bootstrapped 100% (done): Done")
        assertEquals(100, TorLogStatus.bootstrap(lines))
        assertNull(TorLogStatus.bootstrap(listOf("nothing")))
    }
}
