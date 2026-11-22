package com.kutlusoy.nearly.nostr

// What the services need from the network. NostrClient is used through ClientTransport, tests use a fake.
interface NostrTransport {
    fun publish(event: Event, onResult: ((relay: String, accepted: Boolean, message: String) -> Unit)? = null)
    fun subscribe(filters: List<Filter>, onEvent: (Event) -> Unit, onEose: () -> Unit = {}): AutoCloseable
}

class ClientTransport(private val client: NostrClient) : NostrTransport {
    override fun publish(event: Event, onResult: ((String, Boolean, String) -> Unit)?) {
        client.publish(event, onResult?.let { cb ->
            object : NostrClient.PublishListener {
                override fun onResult(relay: String, accepted: Boolean, message: String) = cb(relay, accepted, message)
            }
        })
    }

    override fun subscribe(filters: List<Filter>, onEvent: (Event) -> Unit, onEose: () -> Unit): AutoCloseable {
        val subscription = client.subscribe(filters, onEvent, onEose)
        return AutoCloseable { client.unsubscribe(subscription) }
    }
}

class ProfileService(private val transport: NostrTransport, private val session: Session) {
    fun publish(data: ProfileData, now: Long = System.currentTimeMillis() / 1000): Event =
        ProfileCodec.sign(session, data, now).also { transport.publish(it) }

    // Reads the newest profile of an account. Gives null when none was found or it is unusable.
    fun load(pubkey: String, onResult: (ProfileData?) -> Unit) {
        val found = mutableListOf<Event>()
        var handle: AutoCloseable? = null
        handle = transport.subscribe(
            listOf(Filter(kinds = listOf(ProfileCodec.KIND), authors = listOf(pubkey), limit = 5)),
            { if (it.pubkey == pubkey) synchronized(found) { found.add(it) } },
            {
                handle?.close()
                onResult(synchronized(found) { Replaceable.newest(found) }?.let { ProfileCodec.parse(it) })
            },
        )
    }
}

class FollowNotLoadedException : IllegalStateException("the follow list is not loaded, a change would overwrite it")

// A contact list replaces the older one completely. A change that starts from an empty list, because the read failed, would erase all follows.
// So changes are possible only after the list was read, or after the account was confirmed to be new.
class FollowService(private val transport: NostrTransport, private val session: Session) {
    @Volatile
    var current: FollowList? = null
        private set

    val isLoaded: Boolean get() = current != null

    // Reads the newest list. When none is found the service stays unloaded, the caller decides whether this account is new (startEmpty).
    fun load(onDone: (found: Boolean) -> Unit) {
        val own = NostrKeys.toHex(session.publicKey)
        val found = mutableListOf<Event>()
        var handle: AutoCloseable? = null
        handle = transport.subscribe(
            listOf(Filter(kinds = listOf(FollowCodec.KIND), authors = listOf(own), limit = 5)),
            { if (it.pubkey == own) synchronized(found) { found.add(it) } },
            {
                handle?.close()
                val list = synchronized(found) { Replaceable.newest(found) }?.let { FollowCodec.parse(it) }
                if (list != null) current = list
                onDone(list != null)
            },
        )
    }

    // For an account that was just created and has no list on any relay
    fun startEmpty() {
        if (current == null) current = FollowList()
    }

    fun follow(pubkey: String, now: Long = System.currentTimeMillis() / 1000): Event? = change(now) { it.add(pubkey) }

    fun unfollow(pubkey: String, now: Long = System.currentTimeMillis() / 1000): Event? = change(now) { it.remove(pubkey) }

    private fun change(now: Long, edit: (FollowList) -> FollowList): Event? {
        val base = current ?: throw FollowNotLoadedException()
        val next = edit(base)
        if (next == base) return null
        val event = FollowCodec.sign(session, next, now)
        current = next.copy(createdAt = event.createdAt)
        transport.publish(event)
        return event
    }
}

class PostService(private val transport: NostrTransport, private val session: Session) {
    fun postText(text: String, hashtags: List<String> = emptyList(), replyTo: String? = null): Event =
        PostCodec.text(session, text, hashtags, replyTo).also { transport.publish(it) }

    fun postPhotos(caption: String, photos: List<PhotoRef>): Event =
        PostCodec.photo(session, caption, photos).also { transport.publish(it) }

    fun like(target: Event): Event = PostCodec.reaction(session, target).also { transport.publish(it) }

    // Posts of the people the user follows, newest first as they arrive
    fun followingFeed(list: FollowList, since: Long? = null, onPost: (Post) -> Unit, onEose: () -> Unit = {}): List<AutoCloseable> =
        FeedFilters.following(list, since).map { filter ->
            transport.subscribe(listOf(filter), { event -> PostCodec.parse(event)?.let(onPost) }, onEose)
        }
}
