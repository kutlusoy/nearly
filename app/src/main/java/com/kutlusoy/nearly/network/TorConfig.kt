package com.kutlusoy.nearly.network

import java.io.File

object TorConfig {
    fun torrc(dataDir: File, ports: NetworkPorts): String = listOf(
        "DataDirectory ${dataDir.absolutePath}",
        "SocksPort ${Loopback.HOST}:${ports.torSocks}",
        "ControlPort ${Loopback.HOST}:${ports.torControl}",
        "CookieAuthentication 1",
        "CookieAuthFile ${File(dataDir, "control_auth_cookie").absolutePath}",
        "AvoidDiskWrites 1",
        "ClientOnly 1",
        "ORPort 0",
        "ExitRelay 0",
        "ExitPolicy reject *:*",
        "Log notice stdout",
    ).joinToString("\n", postfix = "\n")

    fun arguments(torrc: File): List<String> = listOf("-f", torrc.absolutePath, "--ignore-missing-torrc")
}
