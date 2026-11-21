package com.kutlusoy.nearly.nostr

import com.kutlusoy.nearly.network.RelayRouter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class Subscription internal constructor(val id: String, internal val filters: List<Filter>, internal val onEvent: (Event) -> Unit, internal val onEose: () -> Unit)

// Talks to several relays at once: publishes to all, merges the events of all, drops duplicates and every event with a bad id or signature
class NostrClient(
    private val relays: List<String>,
    private val router: RelayRouter,
    private val allowInsecure: Boolean = false,
    private val connectionFactory: (String) -> RelayConnection = { RelayConnection(it, router, allowInsecure) },
) {
    interface PublishListener {
        fun onResult(relay: String, accepted: Boolean, message: String)
    }

    private class Link(val connection: RelayConnection)

    private val links = ConcurrentHashMap<String, Link>()
    private val subscriptions = ConcurrentHashMap<String, Subscription>()
    private val pending = ConcurrentHashMap<String, MutableList<Event>>()
    private val publishListeners = ConcurrentHashMap<String, PublishListener>()
    private val seen = object : LinkedHashMap<String, Boolean>(1024, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>) = size > MAX_SEEN
    }
    private val eoseCount = ConcurrentHashMap<String, Int>()
    val failures = ConcurrentHashMap<String, String>()

    @Synchronized
    fun connect() {
        for (url in relays) {
            if (links.containsKey(url)) continue
            val connection = connectionFactory(url)
            links[url] = Link(connection)
            connection.connect(listenerFor(url, connection))
        }
    }

    fun disconnect() {
        links.values.forEach { it.connection.close() }
        links.clear()
        subscriptions.clear()
    }

    fun subscribe(filters: List<Filter>, onEvent: (Event) -> Unit, onEose: () -> Unit = {}): Subscription {
        val subscription = Subscription(UUID.randomUUID().toString().take(16), filters, onEvent, onEose)
        subscriptions[subscription.id] = subscription
        links.values.forEach { it.connection.subscribe(subscription.id, filters) }
        return subscription
    }

    fun unsubscribe(subscription: Subscription) {
        subscriptions.remove(subscription.id)
        links.values.forEach { it.connection.unsubscribe(subscription.id) }
    }

    // Events are kept for relays that are not open yet and sent as soon as the connection is up
    fun publish(event: Event, listener: PublishListener? = null) {
        listener?.let { publishListeners[event.id] = it }
        for ((url, link) in links) {
            if (!link.connection.publish(event)) pending.getOrPut(url) { mutableListOf() }.add(event)
        }
    }

    private fun listenerFor(url: String, connection: RelayConnection) = object : RelayListener {
        override fun onOpen() {
            subscriptions.values.forEach { connection.subscribe(it.id, it.filters) }
            pending.remove(url)?.forEach { connection.publish(it) }
        }

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
                is RelayMessage.Ok -> publishListeners[message.eventId]?.onResult(url, message.accepted, message.message)
                else -> {}
            }
        }

        override fun onClosed(reason: String) {
            failures[url] = reason
        }
    }

    private companion object {
        const val MAX_SEEN = 5_000
    }
}
