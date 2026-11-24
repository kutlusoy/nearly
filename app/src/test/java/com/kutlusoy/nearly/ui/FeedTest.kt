package com.kutlusoy.nearly.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.kutlusoy.nearly.feed.FeedPost
import com.kutlusoy.nearly.feed.FeedUiState
import com.kutlusoy.nearly.feed.PostState
import com.kutlusoy.nearly.feed.StoryGroup
import com.kutlusoy.nearly.nostr.Story
import com.kutlusoy.nearly.ui.theme.NearlyTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class FeedTest {
    @get:Rule
    val rule = createComposeRule()

    private val account = AccountInfo("anna", "npub1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq", 26)
    private val mia = "a1".repeat(32)
    private val now get() = System.currentTimeMillis() / 1000

    private fun post(id: String, author: String, text: String, mine: Boolean = false, state: PostState = PostState.Sent, likes: Int = 0, liked: Boolean = false) =
        FeedPost(id, author, now - 120, text, emptyList(), mine, state, likes, liked)

    @Test
    fun anEmptyFeedInvitesToTheFirstPost() {
        var composed = 0
        rule.setContent { NearlyTheme { FeedScreen(people = emptyList(), onCompose = { composed++ }) } }
        rule.onNodeWithTag("feed-empty").assertExists()
        rule.onNodeWithTag("compose").performClick()
        assertEquals(1, composed)
    }

    @Test
    fun postsAreShownWithTheNameAndCanBeLikedAndDeleted() {
        val liked = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        val posts = listOf(post("p1".padEnd(64, '0'), mia, "Sunday climbing"), post("p2".padEnd(64, '0'), "b".repeat(64), "Mine", mine = true, likes = 3))
        rule.setContent {
            NearlyTheme {
                FeedScreen(people = emptyList(), feed = FeedUiState(posts), names = mapOf(mia to "Mia"), myName = "Anna", actions = FeedActions(like = { liked += it }, delete = { deleted += it }))
            }
        }
        rule.onNodeWithTag("post-text-p1000000").assertExists()
        rule.onAllNodesWithText("Mia")[0].assertExists()
        rule.onNodeWithTag("like-p1000000").performClick()
        assertEquals(listOf("p1".padEnd(64, '0')), liked)
        rule.onNodeWithTag("post-likes-p2000000").assertExists()
        rule.onNodeWithTag("post-delete-p1000000").assertDoesNotExist()
        rule.onNodeWithTag("post-delete-p2000000").performClick()
        rule.onNodeWithTag("post-delete-confirm").performClick()
        assertEquals(listOf("p2".padEnd(64, '0')), deleted)
        rule.onRoot().captureRoboImage("build/screenshots/08-main-feed-posts.png")
    }

    @Test
    fun aFailedPostCanBeSentAgain() {
        val retried = mutableListOf<String>()
        val id = "f1".padEnd(64, '0')
        rule.setContent { NearlyTheme { FeedScreen(people = emptyList(), feed = FeedUiState(listOf(post(id, mia, "x", mine = true, state = PostState.Failed))), actions = FeedActions(retry = { retried += it })) } }
        rule.onNodeWithTag("post-retry-f1000000").performClick()
        assertEquals(listOf(id), retried)
    }

    @Test
    fun storiesOpenFullScreenAndMoveOnWithATap() {
        val seen = mutableListOf<String>()
        val s1 = Story("s1".padEnd(64, '0'), mia, now - 100, now + 1000, "First", null)
        val s2 = Story("s2".padEnd(64, '0'), mia, now - 50, now + 1000, "Second", null)
        rule.setContent {
            NearlyTheme { FeedScreen(people = emptyList(), feed = FeedUiState(stories = listOf(StoryGroup(mia, false, listOf(s1, s2), false))), names = mapOf(mia to "Mia"), actions = FeedActions(storySeen = { seen += it })) }
        }
        rule.onNodeWithTag("story-a1a1a1a1").performClick()
        rule.onNodeWithTag("story-viewer").assertExists()
        rule.onNodeWithText("First").assertExists()
        rule.onNodeWithTag("story-next").performClick()
        rule.onNodeWithText("Second").assertExists()
        rule.onNodeWithTag("story-prev").performClick()
        rule.onNodeWithText("First").assertExists()
        rule.onNodeWithTag("story-close").performClick()
        rule.onNodeWithTag("story-viewer").assertDoesNotExist()
        assertTrue(s1.id in seen && s2.id !in seen || s1.id in seen)
    }

    @Test
    fun theComposerNeedsContentAndLimitsAStory() {
        var sent: Triple<String, Int, Boolean>? = null
        rule.setContent { NearlyTheme { ComposerScreen(busy = false, note = null, onBack = {}, onSubmit = { t, uris, story -> sent = Triple(t, uris.size, story) }) } }
        rule.onNodeWithTag("composer-post").assertIsNotEnabled()
        rule.onNodeWithTag("composer-text").performTextInput("x".repeat(300))
        rule.onNodeWithTag("composer-post").assertIsEnabled()
        rule.onNodeWithTag("composer-story").performClick()
        rule.onNodeWithTag("composer-post").performClick()
        assertEquals(Triple("x".repeat(140), 0, true), sent)
    }

    private fun feedTab(dark: Boolean) {
        rule.setContent { NearlyTheme(dark = dark) { MainScreen(account, Tab.Feed, {}, {}, nearby = com.kutlusoy.nearly.discovery.NearbyState(cell = "u33db", permission = true, people = SamplePeople)) } }
        rule.onNodeWithText("People near you").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/08-main-feed${if (dark) "-dark" else ""}.png")
    }

    @Test
    fun theFeedTabShowsThePeopleNearYou() = feedTab(dark = false)

    @Test
    fun theFeedTabShowsThePeopleNearYouInTheDarkTheme() = feedTab(dark = true)
}
