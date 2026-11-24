package com.kutlusoy.nearly.presence

import com.kutlusoy.nearly.discovery.AreaSource
import com.kutlusoy.nearly.network.NetworkLink
import com.kutlusoy.nearly.nostr.Rumor
import com.kutlusoy.nearly.nostr.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Opens with the account and closes with the sign out, like the chat and the feed
class PresenceController(private val link: NetworkLink, private val area: AreaSource) {
    private val _state = MutableStateFlow(PresenceUi())
    val state: StateFlow<PresenceUi> = _state.asStateFlow()
    private var service: PresenceService? = null

    fun open(session: Session, memory: PresenceMemory, follows: () -> Set<String>, isBlocked: (String) -> Boolean, settings: () -> PresenceSettings) {
        close()
        service = PresenceService(link.transport, session, memory, area, follows, isBlocked, settings, onChange = { refresh() })
        service?.followsChanged()
        refresh()
    }

    fun close() {
        service?.stop()
        service = null
        _state.value = PresenceUi()
    }

    fun refresh() {
        _state.value = service?.ui() ?: return
    }

    fun receive(rumor: Rumor) = service?.receive(rumor)

    fun followsChanged() = service?.followsChanged()

    fun start(seconds: Long): Boolean = service?.start(seconds) ?: false

    fun stop() = service?.stop()

    fun explained() = service?.explained()
}
