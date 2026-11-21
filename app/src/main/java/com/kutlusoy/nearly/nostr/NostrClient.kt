package com.kutlusoy.nearly.nostr

import com.kutlusoy.nearly.network.RelayRouter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class Subscription internal constructor(val id: String, internal val filters: List<Filter>, internal val onEvent: (Event) -> Unit, internal val onEose: () -> Unit)

enum class RelayState { Idle, Standby, Connecting, Open, WaitingToRetry, Failed }

data class RelayStatus(val url: String, val state: RelayState, val active: Boolean, val attempts: Int, val retryInMs: Long?, val lastError: String?)

// Keeps a small set of relays connected, merges their events, drops duplicates and every event with a bad id or signature.
// A lost connection is retried with a growing delay. A relay that keeps failing is replaced by a spare one from the list.
class NostrClient(
    relays: List<String>,
    private val router: RelayRouter,
    private val allowInsecure: Boolean = false,
    private val connectionFactory: (String) -> RelayConnection = { RelayConnection(it, router, allowInsecure) },
    private val scheduler: Scheduler = ExecutorScheduler(),
    private val backoff: Backoff = Backoff(),
    private val targetConnections: Int = 4,
    private val swapAfterFailures: Int = 3,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    interface PublishListener {
        fun onResult(relay: String, accepted: Boolean, message: String)
    }

    private class Entry(val url: String) {
        var active = false
        var connection: RelayConnection? = null
        var state = RelayState.Idle
        var attempts = 0
        var retryAt: Long? = null
        var lastError: String? = null
        var task: Cancelable? = null
    }

    private val lock = Any()
    private val entries = relays.distinct().map { Entry(it) }.toMutableList()
    private var started = false
    private val subscriptions = ConcurrentHashMap<String, Subscription>()
    private val publishListeners = ConcurrentHashMap<String, PublishListener>()
    private val published = LinkedHashMap<String, Event>()
    private val acked = HashMap<String, MutableSet<String>>()
    private val seen = object : LinkedHashMap<String, Boolean>(1024, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>) = size > MAX_SEEN
    }
    private val eoseCount = ConcurrentHashMap<String, Int>()

    fun connect() {
        synchronized(lock) {
            if (started) return
            started = true
            entries.take(targetConnections).forEach {
                it.active = true
                start(it)
            }
            entries.filter { !it.active }.forEach { it.state = RelayState.Standby }
        }
    }

    fun disconnect() {
        synchronized(lock) {
            started = false
            for (entry in entries) {
                entry.task?.cancel()
                entry.task = null
                entry.connection?.close()
                entry.connection = null
                entry.state = RelayState.Idle
                entry.active = false
                entry.attempts = 0
                entry.retryAt = null
            }
            subscriptions.clear()
        }
    }

    // Use this when the network came back or the network mode changed: waiting relays are tried at once and blocked ones get another chance
    fun retryNow() {
        synchronized(lock) {
            if (!started) return
            for (entry in entries.filter { it.active && it.state != RelayState.Open && it.state != RelayState.Connecting }) {
                entry.attempts = 0
                start(entry)
            }
            while (entries.count { it.active } < targetConnections) {
                val spare = entries.firstOrNull { !it.active && it.state != RelayState.Failed } ?: break
                spare.active = true
                start(spare)
            }
        }
    }

    fun statuses(): List<RelayStatus> = synchronized(lock) {
        entries.map { RelayStatus(it.url, it.state, it.active, it.attempts, it.retryAt?.let { at -> maxOf(0L, at - clock()) }, it.lastError) }
    }

    fun subscribe(filters: List<Filter>, onEvent: (Event) -> Unit, onEose: () -> Unit = {}): Subscription {
        val subscription = Subscription(UUID.randomUUID().toString().take(16), filters, onEvent, onEose)
        subscriptions[subscription.id] = subscription
        synchronized(lock) { openConnections().forEach { it.subscribe(subscription.id, filters) } }
        return subscription
    }

    fun unsubscribe(subscription: Subscription) {
        subscriptions.remove(subscription.id)
        synchronized(lock) { openConnections().forEach { it.unsubscribe(subscription.id) } }
    }

    // The event is kept and sent to every relay that has not confirmed it yet, also to relays that connect later
    fun publish(event: Event, listener: PublishListener? = null) {
        listener?.let { publishListeners[event.id] = it }
        synchronized(lock) {
            published[event.id] = event
            while (published.size > MAX_PUBLISHED) published.remove(published.keys.first())
            for (entry in entries) if (entry.active && entry.state == RelayState.Open) entry.connection?.publish(event)
        }
    }

    private fun openConnections() = entries.filter { it.active && it.state == RelayState.Open }.mapNotNull { it.connection }

    private fun start(entry: Entry) {
        entry.task?.cancel()
        entry.task = null
        entry.retryAt = null
        entry.connection?.close()
        val connection = connectionFactory(entry.url)
        entry.connection = connection
        entry.state = RelayState.Connecting
        connection.connect(listenerFor(entry, connection))
    }

    private fun handleOpen(entry: Entry, connection: RelayConnection) = synchronized(lock) {
        if (!started || entry.connection !== connection) return
        entry.state = RelayState.Open
        entry.attempts = 0
        entry.lastError = null
        subscriptions.values.forEach { connection.subscribe(it.id, it.filters) }
        published.values.filter { entry.url !in acked[it.id].orEmpty() }.forEach { connection.publish(it) }
    }

    private fun handleClosed(entry: Entry, connection: RelayConnection, reason: String) = synchronized(lock) {
        if (!started || entry.connection !== connection) return
        entry.lastError = reason
        entry.connection = null
        if (connection.permanentFailure) {
            entry.state = RelayState.Failed
            swapOut(entry)
            return
        }
        entry.attempts++
        if (entry.attempts >= swapAfterFailures && swapOut(entry)) return
        val delay = backoff.delay(entry.attempts)
        entry.state = RelayState.WaitingToRetry
        entry.retryAt = clock() + delay
        entry.task = scheduler.schedule(delay) {
            synchronized(lock) { if (started && entry.active && entry.state == RelayState.WaitingToRetry) start(entry) }
        }
    }

    // Replaces a failing relay by a spare one. The failing relay goes to the end of the list and becomes a spare itself.
    private fun swapOut(entry: Entry): Boolean {
        if (!entry.active) return false
        val spare = entries.firstOrNull { !it.active && it.state != RelayState.Failed } ?: return false
        entry.task?.cancel()
        entry.task = null
        entry.retryAt = null
        entry.active = false
        if (entry.state != RelayState.Failed) entry.state = RelayState.Standby
        entries.remove(entry)
        entries.add(entry)
        spare.active = true
        spare.attempts = 0
        start(spare)
        return true
    }

    private fun listenerFor(entry: Entry, connection: RelayConnection) = object : RelayListener {
        override fun onOpen() = handleOpen(entry, connection)

        override fun onClosed(reason: String) = handleClosed(entry, connection, reason)

        override fun onMessage(message: RelayMessage) {
            when (message) {
                is RelayMessage.EventMessage -> {
                    val subscription = subscriptions[message.subscription] ?: return
                    if (!message.event.isValid()) return
                    val fresh = synchronized(seen) { seen.put(message.event.id, true) == null }
                    if (fresh) subscription.onEvent(message.event)
                }
                is RelayMessage.Eose -> {
                    val subscription = subscriptions[message.subscription] ?: return
                    // Done when the first relay has delivered its stored events
                    if (eoseCount.merge(subscription.id, 1, Int::plus) == 1) subscription.onEose()
                }
                is RelayMessage.Ok -> {
                    // A relay that already has the event answers with duplicate, that counts as delivered as well
                    if (message.accepted || message.message.startsWith("duplicate")) synchronized(lock) { acked.getOrPut(message.eventId) { mutableSetOf() }.add(entry.url) }
                    publishListeners[message.eventId]?.onResult(entry.url, message.accepted, message.message)
                }
                else -> {}
            }
        }
    }

    private companion object {
        const val MAX_SEEN = 5_000
        const val MAX_PUBLISHED = 100
    }
}
