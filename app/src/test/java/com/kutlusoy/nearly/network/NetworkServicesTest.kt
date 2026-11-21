package com.kutlusoy.nearly.network

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class NetworkServicesTest {
    private fun script(dir: File, name: String, body: String): File =
        File(dir, name).apply { writeText("#!/bin/sh\n$body\n"); setExecutable(true) }

    private fun await(timeoutMs: Long = 5_000, check: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (check()) return true
            Thread.sleep(50)
        }
        return false
    }

    @Test
    fun missingBinariesAreSkipped() {
        val root = Files.createTempDirectory("net").toFile()
        val services = NetworkServices(ServiceBinaries(File(root, "none"), null), root, NetworkPorts())
        services.sync(NetworkSettings(NetworkMode.TorAndI2p), active = true)
        assertFalse(services.torAvailable)
        assertEquals(ServiceState.Stopped, services.torState)
        assertEquals(ServiceState.Stopped, services.i2pState)
    }

    @Test
    fun onlyTheNeededHelperStarts() {
        assumeTrue(File("/bin/sh").exists())
        val root = Files.createTempDirectory("net").toFile()
        val bin = Files.createTempDirectory("bin").toFile()
        val services = NetworkServices(ServiceBinaries(script(bin, "tor", "sleep 30"), script(bin, "i2pd", "sleep 30")), root, NetworkPorts())
        try {
            services.sync(NetworkSettings(NetworkMode.I2p), active = true)
            assertEquals(ServiceState.Stopped, services.torState)
            assertTrue(await { services.i2pState == ServiceState.Starting })
            assertTrue(File(services.i2pDir, "i2pd.conf").readText().contains("[socksproxy]"))
        } finally {
            services.stopAll()
        }
        assertEquals(ServiceState.Stopped, services.i2pState)
    }

    @Test
    fun inactiveStopsEverything() {
        assumeTrue(File("/bin/sh").exists())
        val root = Files.createTempDirectory("net").toFile()
        val bin = Files.createTempDirectory("bin").toFile()
        val services = NetworkServices(ServiceBinaries(script(bin, "tor", "sleep 30"), null), root, NetworkPorts())
        try {
            services.sync(NetworkSettings(NetworkMode.Tor), active = true)
            assertTrue(await { services.torState == ServiceState.Starting })
            services.sync(NetworkSettings(NetworkMode.Tor), active = false)
            assertEquals(ServiceState.Stopped, services.torState)
        } finally {
            services.stopAll()
        }
    }

    @Test
    fun failingBinaryIsReportedWithItsOutput() {
        assumeTrue(File("/bin/sh").exists())
        val root = Files.createTempDirectory("net").toFile()
        val bin = Files.createTempDirectory("bin").toFile()
        val services = NetworkServices(ServiceBinaries(script(bin, "tor", "echo boom; exit 3"), null), root, NetworkPorts())
        try {
            services.sync(NetworkSettings(NetworkMode.Tor), active = true)
            assertTrue(await { services.torState is ServiceState.Failed })
            val failed = services.torState as ServiceState.Failed
            assertEquals(3, failed.exitCode)
            assertTrue("boom" in failed.detail)
        } finally {
            services.stopAll()
        }
    }
}
