package com.kutlusoy.nearly.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.kutlusoy.nearly.presence.FriendDot
import com.kutlusoy.nearly.presence.PresenceAudience
import com.kutlusoy.nearly.presence.PresenceSettings
import com.kutlusoy.nearly.presence.PresenceUi
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
class MapUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val mia = "a1".repeat(32)
    private val now = 1_800_000_000L

    @Test
    fun theFirstUseExplainsBeforeAnythingIsShared() {
        var explained = 0
        rule.setContent { NearlyTheme { MapScreen(PresenceUi(), PresenceSettings(), emptyMap(), true, MapActions(explained = { explained++ }), now) } }
        rule.onNodeWithTag("map-explain").assertExists()
        rule.onNodeWithTag("map-start-900").assertDoesNotExist()
        rule.onNodeWithTag("map-explain-button").performClick()
        assertEquals(1, explained)
    }

    @Test
    fun theUserChoosesForHowLongToBeActive() {
        val started = mutableListOf<Long>()
        rule.setContent { NearlyTheme { MapScreen(PresenceUi(explained = true), PresenceSettings(), emptyMap(), true, MapActions(start = { started += it }), now) } }
        rule.onNodeWithTag("map-start-900").performClick()
        rule.onNodeWithTag("map-start-3600").performClick()
        rule.onNodeWithTag("map-start-14400").performClick()
        rule.onNodeWithTag("map-start-midnight").performScrollTo().performClick()
        assertEquals(listOf(900L, 3600L, 14400L), started.take(3))
        assertTrue(started[3] in 60..86400)
    }

    @Test
    fun anActiveUserSeesTheGridTheFriendsAndCanStop() {
        var stopped = 0
        var opened: String? = null
        val ui = PresenceUi(active = true, until = now + 900, ownCell = "u33dbc", friends = listOf(FriendDot(mia, "u33dbd", now - 20)), friendCount = 2, explained = true)
        rule.setContent { NearlyTheme { MapScreen(ui, PresenceSettings(), mapOf(mia to "Mia"), true, MapActions(stop = { stopped++ }, openProfile = { opened = it }), now) } }
        rule.onNodeWithTag("map-grid").assertExists()
        rule.onNodeWithTag("cell-own").assertExists()
        rule.onNodeWithTag("cell-u33dbd").assertExists()
        rule.onNodeWithTag("map-active").assertExists()
        rule.onNodeWithTag("friend-a1a1a1a1").performClick()
        assertEquals(mia, opened)
        rule.onRoot().captureRoboImage("build/screenshots/16-map-grid.png")
        rule.onNodeWithTag("map-stop").performClick()
        assertEquals(1, stopped)
    }

    @Test
    fun withoutAudienceOrPlaceTheScreenSaysWhy() {
        rule.setContent { NearlyTheme { MapScreen(PresenceUi(explained = true), PresenceSettings(audience = PresenceAudience.Nobody), emptyMap(), true, MapActions(), now) } }
        rule.onNodeWithTag("map-nobody").assertExists()
    }

    @Test
    fun withoutAPermissionTheScreenAsksForIt() {
        rule.setContent { NearlyTheme { MapScreen(PresenceUi(explained = true), PresenceSettings(), emptyMap(), false, MapActions(), now) } }
        rule.onNodeWithTag("map-location").assertExists()
    }

    @Test
    fun thePrivacyScreenChangesEverySetting() {
        var settings = PresenceSettings()
        rule.setContent { NearlyTheme { PrivacyScreen(settings, { settings = it }, {}) } }
        rule.onNodeWithTag("privacy-nobody").performClick()
        assertEquals(PresenceAudience.Nobody, settings.audience)
        rule.onNodeWithTag("privacy-friends").performClick()
        assertEquals(PresenceAudience.Friends, settings.audience)
        rule.onNodeWithTag("privacy-size-5").performClick()
        assertEquals(5, settings.cellLength)
        rule.onNodeWithTag("privacy-size-1").performClick()
        assertEquals(6, settings.cellLength)
        rule.onNodeWithTag("privacy-see").performScrollTo().performClick()
        assertEquals(false, settings.seeFriends)
        rule.onRoot().captureRoboImage("build/screenshots/17-privacy.png")
    }

    @Test
    fun theTimeUntilMidnightIsCorrect() {
        val zone = java.time.ZoneId.of("Europe/Berlin")
        val noon = java.time.ZonedDateTime.of(2027, 1, 15, 12, 0, 0, 0, zone).toEpochSecond()
        assertEquals(12 * 3600L, secondsUntilMidnight(noon, zone))
        val almost = java.time.ZonedDateTime.of(2027, 1, 15, 23, 59, 50, 0, zone).toEpochSecond()
        assertEquals(60L, secondsUntilMidnight(almost, zone))
    }
}
