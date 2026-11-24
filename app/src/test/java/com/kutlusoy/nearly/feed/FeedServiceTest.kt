package com.kutlusoy.nearly.feed

import com.kutlusoy.nearly.chat.FakeRelay
import com.kutlusoy.nearly.nostr.Event
import com.kutlusoy.nearly.nostr.NostrKeys
import com.kutlusoy.nearly.nostr.PhotoRef
import com.kutlusoy.nearly.nostr.PostCodec
import com.kutlusoy.nearly.nostr.Session
import com.kutlusoy.nearly.nostr.StoryCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemoryFeedStore : FeedStore {
    private val rows = LinkedHashMap<String, Pair<Event, PostState>>()
    private val likes = LinkedHashMap<String, String>()
    private val reactionRows = LinkedHashMap<String, Pair<String, String>>()
    private val seenIds = LinkedHashSet<String>()
    override fun add(event: Event, state: PostState): Boolean = if (rows.containsKey(event.id)) false else { rows[event.id] = event to state; true }
    override fun get(id: String) = rows[id]?.first
    override fun events() = rows.values.map { it.first }.sortedByDescending { it.createdAt }
    override fun remove(id: String) { rows.remove(id); reactionRows.entries.removeAll { it.value.first == id } }
    override fun state(id: String) = rows[id]?.second ?: PostState.Sent
    override fun setState(id: String, state: PostState) { rows[id]?.let { rows[id] = it.first to state } }
    override fun liked(): Map<String, String> = likes.toMap()
    override fun setLike(target: String, reaction: String?) { if (reaction == null) likes.remove(target) else likes[target] = reaction }
    override fun addReaction(id: String, target: String, reactor: String): Boolean = if (reactionRows.containsKey(id)) false else { reactionRows[id] = target to reactor; true }
    override fun removeReaction(id: String) { reactionRows.remove(id) }
    override fun reactions(target: String) = reactionRows.values.filter { it.first == target }.map { it.second }.distinct().size
    override fun seen(): Set<String> = seenIds.toSet()
    override fun markSeen(id: String) { seenIds.add(id) }
    override fun prune(keep: Int) = Unit
}

class FeedServiceTest {
    private fun session(seed: Int): Session {
        val key = ByteArray(32) { (it + seed).toByte() }
        return Session(NostrKeys.publicKey(key), key)
    }

    private val anna = session(11)
    private val mia = session(23)
    private val ben = session(37)
    private fun hex(s: Session) = NostrKeys.toHex(s.publicKey)
    private var now = 1_800_000_000L
    private val relay = FakeRelay()

    private fun service(s: Session, store: FeedStore = MemoryFeedStore(), blocked: Set<String> = emptySet(), changes: () -> Unit = {}) =
        FeedService(relay, s, store, clock = { now }, isBlocked = { it in blocked }, onChange = changes)

    private val photo = PhotoRef("https://cdn.example/a.jpg", "image/jpeg", "ab".repeat(32), 100, 100)

    @Test
    fun followedPeopleAndMyOwnPostsAreShownNewestFirst() {
        service(mia).also { it.start(emptySet()) }.postText("Hello from Mia")
        now += 60
        service(ben).also { it.start(emptySet()) }.postText("Hello from Ben")
        now += 60
        val a = service(anna)
        a.start(setOf(hex(mia)))
        a.postText("My own #Climbing post")
        assertEquals(listOf("My own #Climbing post", "Hello from Mia"), a.posts().map { it.text })
        assertTrue(a.posts().first().mine)
    }

    @Test
    fun peopleNotFollowedAndBlockedPeopleAreLeftOut() {
        service(mia).also { it.start(emptySet()) }.postText("Mia")
        service(ben).also { it.start(emptySet()) }.postText("Ben")
        val a = service(anna, blocked = setOf(hex(ben)))
        a.start(setOf(hex(mia), hex(ben)))
        assertEquals(listOf("Mia"), a.posts().map { it.text })
    }

    @Test
    fun aPostMovesFromSendingToSentAndAFailedOneCanBeSentAgain() {
        val store = MemoryFeedStore()
        relay.accept = false
        relay.answers = 3
        val a = service(anna, store)
        a.start(emptySet())
        val event = a.postText("Hi")!!
        assertEquals(PostState.Failed, store.state(event.id))
        relay.accept = true
        a.retry(event.id)
        assertEquals(PostState.Sent, store.state(event.id))
        assertTrue(relay.events.any { it.id == event.id })
    }

    @Test
    fun photoPostsCarryThePhotosAndRepliesAreNotShown() {
        val a = service(mia).also { it.start(emptySet()) }
        a.postPhotos("Sunrise", listOf(photo))
        relay.publish(PostCodec.text(mia, "a reply", replyTo = "cd".repeat(32), createdAt = now))
        val b = service(anna)
        b.start(setOf(hex(mia)))
        val posts = b.posts()
        assertEquals(1, posts.size)
        assertEquals(listOf(photo), posts.single().photos)
        assertEquals("Sunrise", posts.single().text)
    }

    @Test
    fun aLikeIsStoredPublishedAndTakenBack() {
        val a = service(mia).also { it.start(emptySet()) }
        val post = a.postText("Like me")!!
        val b = service(anna)
        b.start(setOf(hex(mia)))
        b.like(post.id)
        assertTrue(b.posts().single().liked)
        val reaction = relay.events.single { it.kind == 7 }
        assertEquals(post.id, reaction.tags.last { it[0] == "e" }[1])
        a.start(emptySet())
        assertEquals(1, a.posts().single().likes, "the author counts the like")
        b.unlike(post.id)
        assertFalse(b.posts().single().liked)
        assertTrue(relay.events.any { it.kind == 5 && it.tags.any { t -> t[0] == "e" && t[1] == reaction.id } })
    }

    @Test
    fun deletingAPostRemovesItAndAsksTheRelays() {
        val a = service(anna)
        a.start(emptySet())
        val post = a.postText("Oops")!!
        a.delete(post.id)
        assertTrue(a.posts().isEmpty())
        val deletion = relay.events.single { it.kind == 5 }
        assertTrue(deletion.tags.any { it[0] == "e" && it[1] == post.id })
    }

    @Test
    fun aDeletionOfTheAuthorRemovesThePostForFollowers() {
        val m = service(mia).also { it.start(emptySet()) }
        val post = m.postText("Temporary")!!
        val b = service(anna)
        b.start(setOf(hex(mia)))
        assertEquals(1, b.posts().size)
        relay.deliverNew(PostCodec.deletionOf(mia, post.id, 1, now + 1))
        assertTrue(b.posts().isEmpty())
    }

    @Test
    fun aDeletionOfAnotherKeyChangesNothing() {
        val m = service(mia).also { it.start(emptySet()) }
        val post = m.postText("Stays")!!
        val b = service(anna)
        b.start(setOf(hex(mia), hex(ben)))
        relay.deliverNew(PostCodec.deletionOf(ben, post.id, 1, now + 1))
        assertEquals(1, b.posts().size)
    }

    @Test
    fun storiesLastTwentyFourHoursAndAreMarkedSeen() {
        val m = service(mia).also { it.start(emptySet()) }
        val story = m.storyText("Coffee time")!!
        val b = service(anna)
        b.start(setOf(hex(mia)))
        assertTrue(b.posts().isEmpty(), "a story is not a post")
        val group = b.stories().single()
        assertFalse(group.allSeen)
        b.markSeen(story.id)
        assertTrue(b.stories().single().allSeen)
        now += StoryCodec.LIFETIME + 1
        assertTrue(b.stories().isEmpty())
    }

    @Test
    fun foreignStoriesAreChecked() {
        val text = StoryCodec.text(mia, "ok", now)
        assertNotNull(StoryCodec.parse(text, now))
        assertNull(StoryCodec.parse(text, now + StoryCodec.LIFETIME), "expired")
        val old = StoryCodec.text(mia, "old", now - 2 * StoryCodec.LIFETIME)
        assertNull(StoryCodec.parse(old, now), "older than a day")
        val longer = Event.sign(mia, 1, "x".repeat(200), listOf(listOf("t", StoryCodec.TAG), listOf("expiration", (now + 100).toString())), now)
        assertNull(StoryCodec.parse(longer, now))
        val future = Event.sign(mia, 1, "soon", listOf(listOf("t", StoryCodec.TAG)), now + 3600)
        assertNull(StoryCodec.parse(future, now))
        val photoStory = StoryCodec.photo(mia, photo, "caption", now)
        assertEquals(photo, StoryCodec.parse(photoStory, now)!!.photo)
    }

    @Test
    fun postsFromTheFutureAndWithBadSignaturesAreIgnored() {
        val future = PostCodec.text(mia, "future", createdAt = now + 3600)
        val forged = PostCodec.text(mia, "forged", createdAt = now).copy(content = "changed")
        relay.publish(future)
        relay.publish(forged)
        val b = service(anna)
        b.start(setOf(hex(mia)))
        assertTrue(b.posts().isEmpty())
    }

    @Test
    fun newPostsOfFollowedPeopleArriveWhileTheFeedIsOpen() {
        var changes = 0
        val b = service(anna, changes = { changes++ })
        b.start(setOf(hex(mia)))
        relay.publish(PostCodec.text(mia, "live", createdAt = now))
        assertEquals(listOf("live"), b.posts().map { it.text })
        assertTrue(changes > 0)
    }
}
