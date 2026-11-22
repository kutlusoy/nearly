package com.kutlusoy.nearly.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.kutlusoy.nearly.ui.theme.NearlyTheme
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

    @Test
    fun aPostCanBeLikedAndUnliked() {
        rule.setContent { NearlyTheme { FeedScreen(people = SamplePeople, posts = SamplePosts) } }
        rule.onNodeWithText("24 likes").assertExists()
        rule.onNodeWithTag("like-p1").performClick()
        rule.onNodeWithText("25 likes").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/08-main-feed-liked.png")
        rule.onNodeWithTag("like-p1").performClick()
        rule.onNodeWithText("24 likes").assertExists()
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
