package com.kutlusoy.nearly.feed

import com.kutlusoy.nearly.nostr.Event
import com.kutlusoy.nearly.nostr.Filter
import com.kutlusoy.nearly.nostr.NostrKeys
import com.kutlusoy.nearly.nostr.NostrTransport
import com.kutlusoy.nearly.nostr.PhotoRef
import com.kutlusoy.nearly.nostr.Post
import com.kutlusoy.nearly.nostr.PostCodec
import com.kutlusoy.nearly.nostr.Session
import com.kutlusoy.nearly.nostr.Story
import com.kutlusoy.nearly.nostr.StoryCodec

data class FeedPost(
    val id: String,
    val author: String,
    val at: Long,
    val text: String,
    val photos: List<PhotoRef>,
    val mine: Boolean,
    val state: PostState,
    val likes: Int,
    val liked: Boolean,
)

data class StoryGroup(val author: String, val mine: Boolean, val stories: List<Story>, val allSeen: Boolean)

data class FeedUiState(val posts: List<FeedPost> = emptyList(), val stories: List<StoryGroup> = emptyList())

// Posts and stories of the people the user follows and of the user, likes, deletion requests. Everything foreign is checked here.
class FeedService(
    private val transport: NostrTransport,
    private val session: Session,
    private val store: FeedStore,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
    private val isBlocked: (String) -> Boolean = { false },
    private val onChange: () -> Unit = {},
) {
    private val own = NostrKeys.toHex(session.publicKey)
    private var subscriptions = emptyList<AutoCloseable>()

    @Volatile
    private var authors: Set<String> = setOf(own)
    private var started = false
    private val deleted = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val rejections = HashMap<String, Int>()

    // Reads again when the set of followed people changed
    @Synchronized
    fun start(follows: Set<String>) {
        val next = follows + own
        if (started && next == authors) return
        subscriptions.forEach { runCatching { it.close() } }
        started = true
        authors = next
        val now = clock()
        store.prune()
        val groups = next.toList().chunked(GROUP)
        subscriptions = groups.map { group ->
            transport.subscribe(
                listOf(
                    Filter(kinds = listOf(PostCodec.KIND_TEXT, PostCodec.KIND_PHOTO), authors = group, since = now - WEEK, limit = 100),
                    Filter(kinds = listOf(PostCodec.KIND_DELETE), authors = group, since = now - WEEK, limit = 100),
                ),
                ::accept,
            )
        } + transport.subscribe(listOf(Filter(kinds = listOf(PostCodec.KIND_REACTION), authors = listOf(own), since = now - MONTH, limit = 300)), ::accept) +
            transport.subscribe(listOf(Filter(kinds = listOf(PostCodec.KIND_REACTION), tags = mapOf("p" to listOf(own)), since = now - MONTH, limit = 300)), ::accept)
    }

    @Synchronized
    fun stop() {
        subscriptions.forEach { runCatching { it.close() } }
        subscriptions = emptyList()
        started = false
    }

    private fun accept(event: Event) {
        val now = clock()
        if (event.createdAt > now + FUTURE || !event.isValid()) return
        val changed = when (event.kind) {
            PostCodec.KIND_TEXT, PostCodec.KIND_PHOTO -> acceptPost(event, now)
            PostCodec.KIND_REACTION -> acceptReaction(event)
            PostCodec.KIND_DELETE -> acceptDeletion(event)
            else -> false
        }
        if (changed) onChange()
    }

    private fun acceptPost(event: Event, now: Long): Boolean {
        if (event.pubkey !in authors || isBlocked(event.pubkey) || event.id in deleted) return false
        if (StoryCodec.isStory(event)) {
            if (StoryCodec.parse(event, now) == null) return false
        } else {
            val post = PostCodec.parse(event) ?: return false
            if (post is Post.Text && post.replyTo != null) return false
        }
        return store.add(event)
    }

    private fun acceptReaction(event: Event): Boolean {
        if (event.content != "+" || event.id in deleted) return false
        val target = event.tags.lastOrNull { it.size >= 2 && it[0] == "e" }?.get(1) ?: return false
        if (event.pubkey == own) {
            store.setLike(target, event.id)
            return true
        }
        if (isBlocked(event.pubkey) || store.get(target)?.pubkey != own) return false
        return store.addReaction(event.id, target, event.pubkey)
    }

    // A deletion request counts only for events of the same key
    private fun acceptDeletion(event: Event): Boolean {
        var changed = false
        for (id in event.tagValues("e")) {
            deleted.add(id)
            val target = store.get(id)
            if (target != null && target.pubkey == event.pubkey) {
                store.remove(id)
                changed = true
            }
            if (event.pubkey == own && store.liked().values.contains(id)) {
                store.liked().filterValues { it == id }.keys.forEach { store.setLike(it, null) }
                changed = true
            }
            store.removeReaction(id)
        }
        return changed
    }

    fun postText(text: String): Event? {
        val clean = text.trim()
        if (clean.isEmpty() || clean.length > PostCodec.MAX_TEXT) return null
        return send(PostCodec.text(session, clean, hashtags(clean), createdAt = clock()))
    }

    fun postPhotos(caption: String, photos: List<PhotoRef>): Event? {
        if (photos.isEmpty() || photos.size > PostCodec.MAX_PHOTOS || caption.length > PostCodec.MAX_TEXT) return null
        return send(PostCodec.photo(session, caption.trim(), photos, clock()))
    }

    fun storyText(text: String): Event? {
        if (text.isBlank() || text.trim().length > StoryCodec.MAX_TEXT) return null
        return send(StoryCodec.text(session, text, clock()))
    }

    fun storyPhoto(photo: PhotoRef, caption: String): Event? {
        if (caption.trim().length > StoryCodec.MAX_TEXT) return null
        return send(StoryCodec.photo(session, photo, caption, clock()))
    }

    private fun send(event: Event): Event {
        store.add(event, PostState.Sending)
        onChange()
        transport.publish(event) { _, accepted, reply -> result(event.id, accepted, reply) }
        return event
    }

    private fun result(id: String, accepted: Boolean, reply: String) {
        if (store.get(id) == null) return
        val state = store.state(id)
        if (state == PostState.Sent) return
        if (accepted || reply.startsWith("duplicate")) {
            store.setState(id, PostState.Sent)
            synchronized(rejections) { rejections.remove(id) }
            onChange()
            return
        }
        val count = synchronized(rejections) { (rejections[id] ?: 0) + 1 }.also { synchronized(rejections) { rejections[id] = it } }
        if (count >= REJECTIONS_TO_FAIL && state == PostState.Sending) {
            store.setState(id, PostState.Failed)
            onChange()
        }
    }

    fun retry(id: String) {
        val event = store.get(id)?.takeIf { it.pubkey == own && store.state(id) == PostState.Failed } ?: return
        store.setState(id, PostState.Sending)
        onChange()
        transport.publish(event) { _, accepted, reply -> result(id, accepted, reply) }
    }

    // The post leaves this phone at once, the relays are asked to delete it
    fun delete(id: String) {
        val event = store.get(id)?.takeIf { it.pubkey == own } ?: return
        transport.publish(PostCodec.deletion(session, listOf(event), clock()))
        deleted.add(id)
        store.remove(id)
        onChange()
    }

    fun like(id: String) {
        val target = store.get(id) ?: return
        if (id in store.liked() || isBlocked(target.pubkey)) return
        val reaction = PostCodec.reaction(session, target, "+", clock())
        store.setLike(id, reaction.id)
        transport.publish(reaction)
        onChange()
    }

    fun unlike(id: String) {
        val reaction = store.liked()[id] ?: return
        store.setLike(id, null)
        deleted.add(reaction)
        transport.publish(PostCodec.deletionOf(session, reaction, PostCodec.KIND_REACTION, clock()))
        onChange()
    }

    fun markSeen(id: String) {
        store.markSeen(id)
        onChange()
    }

    fun posts(): List<FeedPost> {
        val liked = store.liked()
        return store.events().asSequence()
            .filter { it.pubkey in authors && !isBlocked(it.pubkey) && !StoryCodec.isStory(it) }
            .mapNotNull { event ->
                val post = PostCodec.parse(event) ?: return@mapNotNull null
                if (post is Post.Text && post.replyTo != null) return@mapNotNull null
                val mine = event.pubkey == own
                FeedPost(
                    event.id, event.pubkey, event.createdAt, post.text, (post as? Post.Photos)?.photos.orEmpty(), mine,
                    if (mine) store.state(event.id) else PostState.Sent, if (mine) store.reactions(event.id) else 0, event.id in liked,
                )
            }
            .sortedByDescending { it.at }
            .toList()
    }

    fun stories(): List<StoryGroup> {
        val now = clock()
        val seen = store.seen()
        val groups = store.events().asSequence()
            .filter { it.pubkey in authors && !isBlocked(it.pubkey) }
            .mapNotNull { StoryCodec.parse(it, now) }
            .groupBy { it.author }
            .map { (author, items) ->
                val ordered = items.sortedBy { it.createdAt }
                StoryGroup(author, author == own, ordered, ordered.all { it.id in seen })
            }
        return groups.sortedWith(compareByDescending<StoryGroup> { it.mine }.thenBy { it.allSeen }.thenByDescending { g -> g.stories.maxOf { it.createdAt } })
    }

    private fun hashtags(text: String): List<String> = HASHTAG.findAll(text).map { it.groupValues[1].lowercase() }.distinct().take(10).toList()

    companion object {
        private const val GROUP = 100
        private const val WEEK = 7 * 24 * 3600L
        private const val MONTH = 30 * 24 * 3600L
        private const val FUTURE = 300L
        private const val REJECTIONS_TO_FAIL = 3
        private val HASHTAG = Regex("#([\\p{L}\\p{N}_]{2,30})")
    }
}
