package com.kutlusoy.nearly.network

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class ServiceBinaries(val tor: File?, val i2pd: File?) {
    companion object {
        // The helpers ship as libtor.so and libi2pd.so, Android extracts them into the native library directory
        fun fromNativeLibraryDir(dir: File) = ServiceBinaries(File(dir, "libtor.so"), File(dir, "libi2pd.so"))
    }
}

class NetworkServices(
    private val binaries: ServiceBinaries,
    private val root: File,
    val ports: NetworkPorts,
    private val prepareI2pData: (File) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    private val retryAfterMs: Long = 30_000,
) {
    private class Running(val service: ChildService, val config: String, val startedAt: Long)

    private var tor: Running? = null
    private var i2p: Running? = null

    val torDir: File get() = File(root, "tor")
    val i2pDir: File get() = File(root, "i2pd")

    val torState: ServiceState @Synchronized get() = tor?.service?.state ?: ServiceState.Stopped
    val i2pState: ServiceState @Synchronized get() = i2p?.service?.state ?: ServiceState.Stopped
    val torAvailable: Boolean get() = binaries.tor?.canExecute() == true
    val i2pAvailable: Boolean get() = binaries.i2pd?.canExecute() == true

    // Tor is usable once it has built its circuits, I2P once the router answers on its proxy port
    val torReady: Boolean get() = torState == ServiceState.Running && TorLogStatus.bootstrap(torOutput()) == 100
    val i2pReady: Boolean get() = i2pState == ServiceState.Running

    fun torOutput(): List<String> = tor?.service?.output?.snapshot().orEmpty()

    fun i2pOutput(): List<String> = i2p?.service?.output?.snapshot().orEmpty()

    @Synchronized
    fun sync(settings: NetworkSettings, active: Boolean) {
        val now = clock()
        val torBinary = binaries.tor
        tor = reconcile(tor, active && settings.mode.usesTor && torBinary?.canExecute() == true, now) {
            privateDir(torDir)
            val torrc = TorConfig.torrc(torDir, ports)
            val file = File(torDir, "torrc").also { writeIfChanged(it, torrc) }
            torrc to ChildService("tor", listOf(torBinary!!.absolutePath) + TorConfig.arguments(file), torDir, ports.torSocks)
        }
        val i2pBinary = binaries.i2pd
        i2p = reconcile(i2p, active && settings.mode.usesI2p && i2pBinary?.canExecute() == true, now) {
            privateDir(i2pDir)
            prepareI2pData(i2pDir)
            val conf = I2pConfig.conf(settings.i2pIpv6, ports)
            val file = File(i2pDir, "i2pd.conf").also { writeIfChanged(it, conf) }
            conf to ChildService("i2pd", listOf(i2pBinary!!.absolutePath) + I2pConfig.arguments(file, i2pDir), i2pDir, ports.i2pSocks)
        }
    }

    @Synchronized
    fun stopAll() {
        tor?.service?.stop()
        i2p?.service?.stop()
        tor = null
        i2p = null
    }

    private fun reconcile(current: Running?, wanted: Boolean, now: Long, build: () -> Pair<String, ChildService>): Running? {
        if (!wanted) {
            current?.service?.stop()
            return null
        }
        val (config, service) = build()
        if (current != null) {
            val state = current.service.state
            val alive = state == ServiceState.Starting || state == ServiceState.Running
            if (alive && config == current.config) return current
            if (alive) current.service.stop()
            else if (config == current.config && now - current.startedAt < retryAfterMs) return current
        }
        service.start()
        return Running(service, config, now)
    }

    private fun writeIfChanged(file: File, text: String) {
        if (!file.exists() || file.readText() != text) file.writeText(text)
    }

    private fun privateDir(dir: File) {
        dir.mkdirs()
        runCatching { Files.setPosixFilePermissions(dir.toPath(), PosixFilePermissions.fromString("rwx------")) }
    }
}
