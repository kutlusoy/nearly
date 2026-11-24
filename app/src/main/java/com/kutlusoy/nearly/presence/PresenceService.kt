package com.kutlusoy.nearly.presence

import com.kutlusoy.nearly.discovery.AreaSource
import com.kutlusoy.nearly.nostr.NostrKeys
import com.kutlusoy.nearly.nostr.NostrTransport
import com.kutlusoy.nearly.nostr.Presence
import com.kutlusoy.nearly.nostr.PresenceMessage
import com.kutlusoy.nearly.nostr.Rumor
import com.kutlusoy.nearly.nostr.Session
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class FriendDot(val key: String, val cell: String, val seenAt: Long)

data class PresenceUi(
    val active: Boolean = false,
    val until: Long = 0,
    val ownCell: String? = null,
    val friends: List<FriendDot> = emptyList(),
    val friendCount: Int = 0,
    val explained: Boolean = false,
)

// The presence between friends: the user is active for a limited time and sends the cell to each friend in a private message every minute,
// and shows the cells of the friends who do the same. Nothing is public, nothing is stored on a relay (the wraps expire after three minutes).
class PresenceService(
    private val transport: NostrTransport,
    private val session: Session,
    private val memory: PresenceMemory,
    private val area: AreaSource,
    private val follows: () -> Set<String>,
    private val isBlocked: (String) -> Boolean,
    private val settings: () -> PresenceSettings,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
    private val ticker: (delaySeconds: Long, task: Runnable) -> AutoCloseable = defaultTicker,
    private val onChange: () -> Unit = {},
) {
    private val own = NostrKeys.toHex(session.publicKey)
    private val dots = HashMap<String, FriendDot>()
    private val helloSent = HashSet<String>()
    private var until = 0L
    private var timer: AutoCloseable? = null
    private var cell: String? = null
    private var cellAt = 0L
    private var cellLength = 0

    @Synchronized
    fun ui(): PresenceUi {
        val now = clock()
        dots.values.removeAll { it.seenAt <= now - TIMEOUT }
        val friends = memory.friends()
        val visible = dots.values.filter { it.key in friends && !isBlocked(it.key) }.sortedByDescending { it.seenAt }
        return PresenceUi(until > now, until, cell.takeIf { until > now }, visible, friends.count { !isBlocked(it) }, memory.explained())
    }

    // False when the user shares with nobody
    @Synchronized
    fun start(seconds: Long): Boolean {
        if (settings().audience == PresenceAudience.Nobody) return false
        until = clock() + seconds
        cell = null
        cellAt = 0
        timer?.close()
        timer = null
        tick()
        return true
    }

    @Synchronized
    fun stop() {
        timer?.close()
        timer = null
        until = 0
        cell = null
        onChange()
    }

    fun explained() {
        memory.setExplained()
        onChange()
    }

    // One heartbeat. The place is read again every five minutes.
    @Synchronized
    fun tick() {
        val now = clock()
        if (until <= now || settings().audience == PresenceAudience.Nobody) return stop()
        val length = settings().cellLength
        if (cell != null && cellLength == length && now - cellAt < READ_EVERY) {
            beat(cell!!, now)
        } else {
            area.current(length) { found ->
                synchronized(this) {
                    cell = found
                    cellLength = length
                    cellAt = clock()
                    if (found != null) beat(found, clock()) else onChange()
                }
            }
        }
        schedule()
    }

    private fun beat(here: String, now: Long) {
        memory.friends().filter { !isBlocked(it) }.sorted().take(MAX_RECIPIENTS).forEach { transport.publish(Presence.here(session, it, here, now)) }
        onChange()
    }

    private fun schedule() {
        timer?.close()
        timer = ticker(INTERVAL + (0..JITTER).random(), Runnable { tick() })
    }

    // A message from a person, already unwrapped
    @Synchronized
    fun receive(rumor: Rumor) {
        val sender = rumor.pubkey
        if (sender == own || isBlocked(sender)) return
        when (val message = Presence.parse(rumor) ?: return) {
            PresenceMessage.Hello -> hello(sender)
            is PresenceMessage.Here -> here(sender, rumor.createdAt, message.cell)
        }
    }

    // The sender follows the user. It is a friend when the user follows the sender too.
    private fun hello(sender: String) {
        if (sender in follows()) {
            if (befriend(sender) && sender !in helloSent) sayHello(sender)
        } else {
            memory.setIncoming(memory.incoming().let { if (it.size >= MAX_INCOMING) it else it + sender })
        }
    }

    private fun here(sender: String, at: Long, cell: String) {
        val now = clock()
        if (!settings().seeFriends || sender !in memory.friends() || sender !in follows()) return
        if (at < now - TIMEOUT || at > now + FUTURE) return
        if (dots[sender]?.let { now - it.seenAt < MIN_GAP } == true) return
        if (dots.size >= MAX_DOTS && sender !in dots) return
        dots[sender] = FriendDot(sender, cell, now)
        onChange()
    }

    // Call when the people the user follows changed
    @Synchronized
    fun followsChanged() {
        val now = follows()
        val friends = memory.friends()
        val incoming = memory.incoming()
        val gone = friends.filter { it !in now }
        if (gone.isNotEmpty()) {
            memory.setFriends(friends - gone.toSet())
            gone.forEach { dots.remove(it) }
        }
        now.filter { it in incoming && it !in memory.friends() }.forEach { befriend(it); sayHello(it) }
        now.filter { it !in memory.friends() && it !in helloSent }.take(MAX_HELLOS).forEach { sayHello(it) }
        onChange()
    }

    private fun befriend(key: String): Boolean {
        val friends = memory.friends()
        if (key in friends) return false
        memory.setFriends(friends + key)
        memory.setIncoming(memory.incoming() - key)
        onChange()
        return true
    }

    private fun sayHello(to: String) {
        if (!helloSent.add(to)) return
        transport.publish(Presence.hello(session, to, clock()))
    }

    companion object {
        const val INTERVAL = 60L
        const val JITTER = 10
        const val TIMEOUT = 180L
        const val FUTURE = 60L
        const val MIN_GAP = 30L
        const val READ_EVERY = 300L
        const val MAX_RECIPIENTS = 30
        const val MAX_DOTS = 300
        const val MAX_INCOMING = 500
        const val MAX_HELLOS = 100

        private val scheduler by lazy { Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "presence").apply { isDaemon = true } } }
        private val defaultTicker: (Long, Runnable) -> AutoCloseable = { seconds, task ->
            val future = scheduler.schedule(task, seconds, TimeUnit.SECONDS)
            AutoCloseable { future.cancel(false) }
        }
    }
}
