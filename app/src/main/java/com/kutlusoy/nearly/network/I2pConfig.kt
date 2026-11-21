package com.kutlusoy.nearly.network

import java.io.File

object I2pConfig {
    fun conf(ipv6: Boolean, ports: NetworkPorts): String = buildString {
        appendLine("daemon = false")
        appendLine("service = false")
        appendLine("log = stdout")
        appendLine("loglevel = info")
        appendLine("ipv4 = true")
        appendLine("ipv6 = $ipv6")
        appendLine("notransit = true")
        appendLine("floodfill = false")
        appendLine("[socksproxy]")
        appendLine("enabled = true")
        appendLine("address = ${Loopback.HOST}")
        appendLine("port = ${ports.i2pSocks}")
        for (section in DISABLED) {
            appendLine("[$section]")
            appendLine("enabled = false")
        }
        appendLine("[reseed]")
        appendLine("verify = true")
    }

    fun arguments(conf: File, dataDir: File): List<String> = listOf("--conf=${conf.absolutePath}", "--datadir=${dataDir.absolutePath}")

    private val DISABLED = listOf("http", "httpproxy", "sam", "bob", "i2cp", "i2pcontrol", "upnp")
}
