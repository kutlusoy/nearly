package com.kutlusoy.nearly.chat

import com.kutlusoy.nearly.network.NetworkLink
import com.kutlusoy.nearly.network.NetworkMode
import com.kutlusoy.nearly.network.NetworkStatus
import com.kutlusoy.nearly.nostr.NostrKeys
import com.kutlusoy.nearly.nostr.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConversationUi(val peer: String, val name: String, val last: ChatMessage, val unread: Int)

enum class ChatError { BadId, ToSelf, TooLong }

data class ChatUiState(
    val conversations: List<ConversationUi> = emptyList(),
    val unread: Int = 0,
    val openPeer: String? = null,
    val openName: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val network: NetworkStatus = NetworkStatus(),
    val error: ChatError? = null,
)

// Connects the network, the chat service and the screens: opens with the account, closes with the sign out
class ChatController(
    private val link: NetworkLink,
    private val storeFor: (accountKey: String) -> ChatStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var service: ChatService? = null
    private var store: ChatStore? = null
    private var statusJob: Job? = null

    fun open(session: Session, name: String, about: String, age: Int?, mode: NetworkMode) {
        close()
        val key = NostrKeys.toHex(session.publicKey).take(16)
        val chatStore = storeFor(key)
        store = chatStore
        val chat = ChatService(link.transport, session, chatStore, onChange = { refresh() }, onIncoming = { message ->
            if (_state.value.openPeer == message.peer) {
                chatStore.markRead(message.peer)
                refresh()
            }
        })
        service = chat
        statusJob = scope.launch { link.status.collect { s -> _state.update { it.copy(network = s) } } }
        link.start(mode)
        chat.publishProfile(name, about, age)
        chat.start()
        refresh()
        conversationsWithoutName().forEach { chat.resolveName(it) }
    }

    fun close() {
        service?.stop()
        service = null
        store = null
        statusJob?.cancel()
        statusJob = null
        link.stop()
        _state.value = ChatUiState()
    }

    fun setMode(mode: NetworkMode) {
        link.start(mode)
    }

    // Opens the chat with a person from an npub or a hex key. Gives the reason when the entry cannot be used.
    fun startChat(input: String, ownPubkey: String): Boolean {
        val peer = ChatService.parsePeer(input)
        val error = when {
            peer == null -> ChatError.BadId
            peer == ownPubkey -> ChatError.ToSelf
            else -> null
        }
        if (error != null || peer == null) {
            _state.update { it.copy(error = error) }
            return false
        }
        openConversation(peer)
        return true
    }

    fun openConversation(peer: String) {
        val chatStore = store ?: return
        chatStore.markRead(peer)
        service?.resolveName(peer)
        _state.update { it.copy(openPeer = peer, error = null) }
        refresh()
    }

    fun closeConversation() {
        _state.update { it.copy(openPeer = null, messages = emptyList(), openName = "") }
        refresh()
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun send(text: String) {
        val peer = _state.value.openPeer ?: return
        when (service?.send(peer, text)) {
            SendResult.TooLong -> _state.update { it.copy(error = ChatError.TooLong) }
            else -> Unit
        }
        refresh()
    }

    fun retry(id: String) {
        service?.retry(id)
        refresh()
    }

    fun deleteConversation(peer: String) {
        store?.deleteConversation(peer)
        if (_state.value.openPeer == peer) closeConversation() else refresh()
    }

    private fun conversationsWithoutName(): List<String> {
        val chatStore = store ?: return emptyList()
        return chatStore.conversations().map { it.peer }.filter { chatStore.peerName(it) == null }
    }

    private fun refresh() {
        val chatStore = store ?: return
        val open = _state.value.openPeer
        val conversations = chatStore.conversations().map { ConversationUi(it.peer, displayName(chatStore, it.peer), it.last, it.unread) }
        val messages = if (open != null) chatStore.messages(open) else emptyList()
        _state.update {
            it.copy(
                conversations = conversations,
                unread = chatStore.unreadTotal(),
                messages = messages,
                openName = if (open != null) displayName(chatStore, open) else "",
            )
        }
    }

    private fun displayName(chatStore: ChatStore, peer: String): String = chatStore.peerName(peer) ?: shortNpub(peer)

    companion object {
        fun shortNpub(peerHex: String): String {
            val npub = runCatching { NostrKeys.npub(NostrKeys.fromHex(peerHex)) }.getOrDefault(peerHex)
            return if (npub.length <= 20) npub else npub.take(12) + "..." + npub.takeLast(6)
        }
    }
}
