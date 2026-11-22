package com.kutlusoy.nearly.nostr

import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.min

fun interface Cancelable {
    fun cancel()
}

interface Scheduler {
    fun schedule(delayMs: Long, task: () -> Unit): Cancelable
}

class ExecutorScheduler : Scheduler {
    private val executor = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "nostr-retry").apply { isDaemon = true } }

    override fun schedule(delayMs: Long, task: () -> Unit): Cancelable {
        val future: ScheduledFuture<*> = executor.schedule(task, delayMs, TimeUnit.MILLISECONDS)
        return Cancelable { future.cancel(false) }
    }
}

// Exponential delay with jitter, so many clients do not hit a relay at the same moment
class Backoff(
    private val initialMs: Long = 2_000,
    private val maxMs: Long = 300_000,
    private val random: () -> Double = Math::random,
) {
    // attempt counts from 1
    fun delay(attempt: Int): Long {
        val exponent = (attempt - 1).coerceIn(0, 20)
        val base = min(maxMs, initialMs shl exponent)
        return (base * (0.8 + 0.4 * random())).toLong()
    }
}

// The relay list of the app (relays.txt): one address per line, lines with # are comments
object RelayList {
    fun parse(text: String, preferDarknet: Boolean = true): List<String> {
        val relays = text.lineSequence().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .filter { valid(it) }
            .distinct()
            .toList()
        return if (preferDarknet) relays.sortedBy { if (isDarknet(it)) 0 else 1 } else relays
    }

    fun isDarknet(url: String): Boolean {
        val host = runCatching { URI(url).host }.getOrNull() ?: return false
        return host.endsWith(".onion", true) || host.endsWith(".i2p", true)
    }

    private fun valid(url: String): Boolean = runCatching {
        val uri = URI(url)
        (uri.scheme.equals("wss", true) || uri.scheme.equals("ws", true)) && !uri.host.isNullOrEmpty()
    }.getOrDefault(false)
}
