package com.kutlusoy.nearly.network

import android.content.Context
import java.io.File

object AndroidNetwork {
    fun create(context: Context, ports: NetworkPorts = NetworkPorts()): NetworkServices {
        val app = context.applicationContext
        return NetworkServices(
            binaries = ServiceBinaries.fromNativeLibraryDir(File(app.applicationInfo.nativeLibraryDir)),
            root = File(app.noBackupFilesDir, "network"),
            ports = ports.resolved(),
            prepareI2pData = { copyCertificates(app, it) },
        )
    }

    private fun copyCertificates(context: Context, i2pDir: File) {
        val target = File(i2pDir, "certificates")
        if (target.isDirectory) return
        copyAssets(context, "i2pd/certificates", target)
    }

    private fun copyAssets(context: Context, path: String, target: File) {
        target.mkdirs()
        for (name in context.assets.list(path).orEmpty()) {
            val child = "$path/$name"
            if (context.assets.list(child).orEmpty().isEmpty()) {
                context.assets.open(child).use { input -> File(target, name).outputStream().use { input.copyTo(it) } }
            } else {
                copyAssets(context, child, File(target, name))
            }
        }
    }
}
