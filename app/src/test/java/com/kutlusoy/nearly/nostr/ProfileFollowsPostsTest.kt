package com.kutlusoy.nearly.nostr

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeTransport : NostrTransport {
    val stored = CopyOnWriteArrayList<Event>()
    val published = CopyOnWriteArrayList<Event>()
    var eoseDelivered = true

    override fun publish(event: Event, onResult: ((String, Boolean, String) -> Unit)?) {
        published.add(event)
        stored.add(event)
        onResult?.invoke("fake", true, "")
    }

    override fun subscribe(filters: List<Filter>, onEvent: (Event) -> Unit, onEose: () -> Unit): AutoCloseable {
        stored.filter { e -> filters.any { f -> (f.kinds.isEmpty() || e.kind in f.kinds) && (f.authors.isEmpty() || e.pubkey in f.authors) } }.forEach(onEvent)
        if (eoseDelivered) onEose()
        return AutoCloseable {}
    }
}

class ProfileFollowsPostsTest {
    private val anna = testSession("anna")
    private val ben = testSession("ben")
    private fun hex(s: Session) = NostrKeys.toHex(s.publicKey)

    @Test
    fun ageFollowsFromTheBirthDate() {
        val born = BirthDate(2000, 6, 15)
        assertEquals(25, born.ageOn(BirthDate(2026, 6, 14)))
        assertEquals(26, born.ageOn(BirthDate(2026, 6, 15)))
        assertTrue(BirthDate(2008, 11, 21).isAdultOn(BirthDate(2026, 11, 21)))
        assertFalse(BirthDate(2008, 11, 22).isAdultOn(BirthDate(2026, 11, 21)))
        BirthDate(2000, 2, 29)
        assertFailsWith<IllegalArgumentException> { BirthDate(2001, 2, 29) }
        assertFailsWith<IllegalArgumentException> { BirthDate(2001, 13, 1) }
    }

    @Test
    fun profileRoundTripAndTheDateOfBirthIsNotPartOfIt() {
        val data = ProfileData("Anna", "Hello\nworld", "https://example.org/a.jpg", 28, listOf("de", "en"))
        val event = ProfileCodec.sign(anna, data, 10)
        assertTrue(event.isValid())
        assertEquals(data, ProfileCodec.parse(event))
        assertFalse("birth" in event.content.lowercase())
    }

    @Test
    fun foreignProfilesAreCheckedAndShortened() {
        fun parse(json: String) = ProfileCodec.parse(Event.sign(ben, 0, json, emptyList(), 1))
        assertNull(parse("not json"))
        assertNull(parse("{\"name\":\"  \"}"))
        assertEquals("AB", parse("{\"name\":\"A\\u0000\\nB\"}")!!.name)
        assertEquals(ProfileData.MAX_NAME, parse("{\"name\":\"${"x".repeat(200)}\"}")!!.name.length)
        assertNull(parse("{\"name\":\"a\",\"picture\":\"http://insecure.example/a.jpg\"}")!!.picture)
        assertNull(parse("{\"name\":\"a\",\"age\":12}")!!.age)
        assertNull(parse("{\"name\":\"a\",\"age\":300}")!!.age)
        assertEquals(listOf("de"), parse("{\"name\":\"a\",\"languages\":[\"DE\",\"german\",\"de\",5]}")!!.languages)
    }

    @Test
    fun followListRoundTripKeepsOtherTagsAndOrder() {
        val list = FollowList(listOf(Follow(hex(ben), "wss://r.example", "benny")), listOf(listOf("client", "x")), "{}", 5)
        val event = FollowCodec.sign(anna, list, 100)
        assertTrue(event.isValid())
        val parsed = FollowCodec.parse(event)!!
        assertEquals(list.follows, parsed.follows)
        assertEquals(listOf(listOf("client", "x")), parsed.otherTags)
        assertEquals(100, parsed.createdAt)
    }

    @Test
    fun followListIgnoresBadAndDuplicateEntriesAndReplacementIsNewer() {
        val event = Event.sign(anna, 3, "", listOf(listOf("p", "zz"), listOf("p", hex(ben)), listOf("p", hex(ben).uppercase())), 7)
        val parsed = FollowCodec.parse(event)!!
        assertEquals(listOf(hex(ben)), parsed.follows.map { it.pubkey })
        assertEquals(8, FollowCodec.sign(anna, parsed, createdAt = 1).createdAt)
    }

    @Test
    fun newestReplaceableEventWins() {
        val a = Event.sign(anna, 0, "{\"name\":\"old\"}", emptyList(), 1)
        val b = Event.sign(anna, 0, "{\"name\":\"new\"}", emptyList(), 2)
        assertEquals(b, Replaceable.newest(listOf(a, b)))
        assertEquals(b, Replaceable.newest(listOf(b, a)))
    }

    @Test
    fun textAndPhotoPostsRoundTrip() {
        val text = PostCodec.text(anna, "Hello #Nearly", listOf("#Nearly"), replyTo = "ab".repeat(32), createdAt = 5)
        val post = PostCodec.parse(text) as Post.Text
        assertEquals(listOf("nearly"), post.hashtags)
        assertEquals("ab".repeat(32), post.replyTo)
        val photo = PhotoRef("https://blossom.example/ab.jpg", "image/jpeg", "cd".repeat(32), 800, 600, "a cat")
        val event = PostCodec.photo(anna, "caption", listOf(photo), 6)
        assertTrue(event.isValid())
        assertEquals(listOf(photo), (PostCodec.parse(event) as Post.Photos).photos)
    }

    @Test
    fun postsRefuseBadInputAndForeignPhotoTagsAreChecked() {
        assertFailsWith<IllegalArgumentException> { PostCodec.text(anna, " ") }
        assertFailsWith<IllegalArgumentException> { PostCodec.photo(anna, "", emptyList()) }
        assertFailsWith<IllegalArgumentException> { PostCodec.photo(anna, "", listOf(PhotoRef("http://x/a.jpg", "image/jpeg", "cd".repeat(32)))) }
        val bad = Event.sign(anna, 20, "", listOf(listOf("imeta", "url javascript:alert(1)", "m image/jpeg", "x ${"cd".repeat(32)}")), 1)
        assertNull(PostCodec.parse(bad))
        assertNull(PostCodec.parse(Event.sign(anna, 30000, "", emptyList(), 1)))
    }

    @Test
    fun reactionPointsToThePost() {
        val post = PostCodec.text(ben, "hi", createdAt = 1)
        val like = PostCodec.reaction(anna, post, createdAt = 2)
        assertEquals(7, like.kind)
        assertEquals(listOf(post.id), like.tagValues("e"))
        assertEquals(listOf(post.pubkey), like.tagValues("p"))
    }

    @Test
    fun feedFiltersAreCutIntoGroups() {
        val list = FollowList((1..250).map { Follow("%064x".format(it)) })
        val filters = FeedFilters.following(list, since = 9)
        assertEquals(listOf(100, 100, 50), filters.map { it.authors.size })
        assertEquals(listOf(1, 20), filters.first().kinds)
    }

    @Test
    fun profileServicePublishesAndLoadsTheNewestProfile() {
        val transport = FakeTransport()
        val service = ProfileService(transport, anna)
        service.publish(ProfileData("Old"), now = 1)
        service.publish(ProfileData("New"), now = 2)
        var loaded: ProfileData? = null
        service.load(hex(anna)) { loaded = it }
        assertEquals("New", loaded?.name)
        var other: ProfileData? = ProfileData("x")
        service.load(hex(ben)) { other = it }
        assertNull(other)
    }

    @Test
    fun followServiceNeverOverwritesAListItHasNotRead() {
        val transport = FakeTransport()
        val old = FollowCodec.sign(anna, FollowList(listOf(Follow(hex(ben)))), 50)
        transport.stored.add(old)
        val unread = FollowService(transport, anna)
        assertFailsWith<FollowNotLoadedException> { unread.follow("11".repeat(32)) }
        assertTrue(transport.published.isEmpty())
        var found = false
        unread.load { found = it }
        assertTrue(found)
        val event = unread.follow("11".repeat(32), now = 60)!!
        assertEquals(setOf(hex(ben), "11".repeat(32)), FollowCodec.parse(event)!!.follows.map { it.pubkey }.toSet())
        assertNull(unread.follow("11".repeat(32), now = 61))
        val removed = unread.unfollow(hex(ben), now = 62)!!
        assertEquals(listOf("11".repeat(32)), FollowCodec.parse(removed)!!.follows.map { it.pubkey })
        assertTrue(removed.createdAt > event.createdAt)
    }

    @Test
    fun emptyReadDoesNotUnlockChangesUntilTheAccountIsConfirmedNew() {
        val service = FollowService(FakeTransport(), anna)
        var found = true
        service.load { found = it }
        assertFalse(found)
        assertFalse(service.isLoaded)
        assertFailsWith<FollowNotLoadedException> { service.follow("11".repeat(32)) }
        service.startEmpty()
        assertNotNull(service.follow("11".repeat(32)))
    }

    @Test
    fun postServiceFeedDeliversParsedPostsOfFollowedAccounts() {
        val transport = FakeTransport()
        PostService(transport, ben).postText("from ben")
        PostService(transport, testSession("eve")).postText("from eve")
        val posts = CopyOnWriteArrayList<Post>()
        PostService(transport, anna).followingFeed(FollowList(listOf(Follow(hex(ben)))), onPost = { posts.add(it) })
        assertEquals(listOf("from ben"), posts.map { it.text })
    }
}
