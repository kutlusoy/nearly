package com.kutlusoy.nearly.chat

import com.kutlusoy.nearly.network.NetworkLink
import com.kutlusoy.nearly.network.NetworkMode
import com.kutlusoy.nearly.network.NetworkStatus
import com.kutlusoy.nearly.nostr.Event
import com.kutlusoy.nearly.nostr.Filter
import com.kutlusoy.nearly.nostr.NostrKeys
import com.kutlusoy.nearly.nostr.NostrTransport
import com.kutlusoy.nearly.nostr.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// One relay in memory that everybody talks to, with the filter rules of NIP-01
class FakeRelay(var answers: Int = 1, var accept: Boolean = true) : NostrTransport {
    val events = mutableListOf<Event>()
    private class Sub(val filters: List<Filter>, val onEvent: (Event) -> Unit)

    private val subs = mutableListOf<Sub>()

    override fun publish(event: Event, onResult: ((String, Boolean, String) -> Unit)?) {
        if (accept) {
            events.add(event)
            subs.toList().forEach { sub -> if (sub.filters.any { matches(it, event) }) sub.onEvent(event) }
        }
        repeat(answers) { onResult?.invoke("fake-$it", accept, if (accept) "" else "blocked: test") }
    }

    override fun subscribe(filters: List<Filter>, onEvent: (Event) -> Unit, onEose: () -> Unit): AutoCloseable {
        val sub = Sub(filters, onEvent)
        events.toList().filter { e -> filters.any { matches(it, e) } }.forEach(onEvent)
        subs.add(sub)
        onEose()
        return AutoCloseable { subs.remove(sub) }
    }

    fun deliverAgain(event: Event) = subs.toList().forEach { if (it.filters.any { f -> matches(f, event) }) it.onEvent(event) }

    private fun matches(f: Filter, e: Event): Boolean {
        if (f.ids.isNotEmpty() && e.id !in f.ids) return false
        if (f.authors.isNotEmpty() && e.pubkey !in f.authors) return false
        if (f.kinds.isNotEmpty() && e.kind !in f.kinds) return false
        if (f.since != null && e.createdAt < f.since) return false
        if (f.until != null && e.createdAt > f.until) return false
        for ((name, values) in f.tags) {
            if (e.tags.none { it.size >= 2 && it[0] == name && it[1] in values }) return false
        }
        return true
    }
}

class FakeLink(override val transport: NostrTransport) : NetworkLink {
    private val _status = MutableStateFlow(NetworkStatus())
    override val status: StateFlow<NetworkStatus> = _status
    var startedWith: NetworkMode? = null
    var stops = 0

    override fun start(mode: NetworkMode) {
        startedWith = mode
        _status.value = NetworkStatus(mode, com.kutlusoy.nearly.network.LinkState.Online, relaysOpen = 3, relaysTotal = 3)
    }

    override fun stop() {
        stops++
        _status.value = NetworkStatus()
    }
}

class MemoryChatStore : ChatStore {
    private val rows = LinkedHashMap<String, ChatMessage>()
    private val wraps = HashSet<String>()
    private val profiles = HashMap<String, PeerProfile>()
    private val follows = LinkedHashSet<String>()
    private val blocks = LinkedHashSet<String>()
    private var sync = 0L

    override fun add(message: ChatMessage): Boolean = if (rows.containsKey(message.id)) false else { rows[message.id] = message; true }
    override fun remove(id: String) { rows.remove(id) }
    override fun setStatus(id: String, status: MessageStatus) { rows[id]?.let { rows[id] = it.copy(status = status) } }
    override fun messages(peer: String) = rows.values.filter { it.peer == peer }.sortedBy { it.at }
    override fun message(id: String) = rows[id]
    override fun conversations() = rows.values.groupBy { it.peer }.map { (peer, list) ->
        Conversation(peer, list.withIndex().maxWithOrNull(compareBy({ it.value.at }, { it.index }))!!.value, list.count { !it.read })
    }.sortedByDescending { it.last.at }
    override fun unreadTotal() = rows.values.count { !it.read }
    override fun markRead(peer: String) { rows.replaceAll { _, m -> if (m.peer == peer) m.copy(read = true) else m } }
    override fun deleteConversation(peer: String) { rows.values.removeAll { it.peer == peer } }
    override fun seenWrap(id: String) = id in wraps
    override fun rememberWrap(id: String) { wraps.add(id) }
    override fun lastSync() = sync
    override fun setLastSync(seconds: Long) { sync = seconds }
    override fun peerProfile(peer: String) = profiles[peer]
    override fun setPeerProfile(peer: String, profile: PeerProfile) { profiles[peer] = profile }
    override fun blocked() = blocks.toList()
    override fun setBlocked(peer: String, blocked: Boolean) { if (blocked) blocks.add(peer) else blocks.remove(peer) }
    override fun followed() = follows.toList()
    override fun setFollowed(peer: String, followed: Boolean) { if (followed) follows.add(peer) else follows.remove(peer) }
}

fun newSession(): Session {
    val key = NostrKeys.generatePrivateKey()
    return Session(NostrKeys.publicKey(key), key)
}

fun Session.hex(): String = NostrKeys.toHex(publicKey)

class FakeArea(var cell: String?, var granted: Boolean = true) : com.kutlusoy.nearly.discovery.AreaSource {
    override fun hasPermission() = granted
    override fun current(onResult: (String?) -> Unit) = onResult(if (granted) cell else null)
}
