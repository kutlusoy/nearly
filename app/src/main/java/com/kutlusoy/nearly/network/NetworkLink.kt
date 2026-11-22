package com.kutlusoy.nearly.network

import android.content.Context
import com.kutlusoy.nearly.nostr.AndroidRelays
import com.kutlusoy.nearly.nostr.ClientTransport
import com.kutlusoy.nearly.nostr.NostrClient
import com.kutlusoy.nearly.nostr.NostrTransport
import com.kutlusoy.nearly.nostr.RelayState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class LinkState { Off, TorMissing, StartingTor, Connecting, Online }

data class NetworkStatus(
    val mode: NetworkMode = NetworkMode.Tor,
    val state: LinkState = LinkState.Off,
    val torPercent: Int = 0,
    val relaysOpen: Int = 0,
    val relaysTotal: Int = 0,
)

// What the app needs from the network: a way to talk to the relays and to see how it is going
interface NetworkLink {
    val status: StateFlow<NetworkStatus>
    val transport: NostrTransport
    fun start(mode: NetworkMode)
    fun stop()
}

// Starts Tor when the mode needs it, connects to the relays when the route is ready and reports the state
class RelayHub(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : NetworkLink {
    private val services by lazy { AndroidNetwork.create(context) }

    @Volatile
    private var mode = NetworkMode.Tor

    private val client by lazy { NostrClient(AndroidRelays.load(context), RelayRouter({ NetworkSettings(mode) }, { services.ports })) }

    override val transport: NostrTransport by lazy { ClientTransport(client) }

    private val _status = MutableStateFlow(NetworkStatus())
    override val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    private var job: Job? = null
    private var connected = false

    @Synchronized
    override fun start(mode: NetworkMode) {
        val changed = this.mode != mode
        this.mode = mode
        if (job?.isActive == true) {
            if (changed && connected) client.retryNow()
            return
        }
        job = scope.launch { run() }
    }

    @Synchronized
    override fun stop() {
        job?.cancel()
        job = null
        if (connected) client.disconnect()
        connected = false
        runCatching { services.stopAll() }
        _status.value = NetworkStatus(mode = mode, state = LinkState.Off)
    }

    private suspend fun run() {
        while (scope.isActive) {
            val current = mode
            // An error in one round must not end the loop or the app, the next round tries again
            val status = try {
                round(current)
            } catch (e: Exception) {
                NetworkStatus(current, LinkState.Off)
            }
            _status.value = status
            delay(1000)
        }
    }

    private fun round(current: NetworkMode): NetworkStatus {
        services.sync(NetworkSettings(current), active = true)
        val needsTor = current.usesTor
        return when {
            needsTor && !services.torAvailable -> NetworkStatus(current, LinkState.TorMissing)
            needsTor && !services.torReady -> NetworkStatus(current, LinkState.StartingTor, torPercent = com.kutlusoy.nearly.network.TorLogStatus.bootstrap(services.torOutput()) ?: 0)
            else -> {
                synchronized(this) {
                    if (!connected) {
                        client.connect()
                        connected = true
                    }
                }
                val relays = client.statuses().filter { it.active }
                val open = relays.count { it.state == RelayState.Open }
                NetworkStatus(current, if (open > 0) LinkState.Online else LinkState.Connecting, relaysOpen = open, relaysTotal = relays.size)
            }
        }
    }
}
