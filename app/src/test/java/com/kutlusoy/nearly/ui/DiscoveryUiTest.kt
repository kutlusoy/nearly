package com.kutlusoy.nearly.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kutlusoy.nearly.ui.theme.NearlyTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class DiscoveryUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val log = mutableListOf<Pair<String, SwipeDirection>>()

    @Test
    fun theProfileCanBeReadBeforeDecidingAndTheButtonsStillDecide() {
        rule.setContent { NearlyTheme { SwipeScreen(people = SamplePeople, onSwiped = { p, d -> log += p.id to d }) } }
        rule.onNodeWithContentDescription("View profile").performClick()
        rule.onNodeWithText("About").assertExists()
        rule.onNodeWithText("Looking for").assertExists()
        rule.onNodeWithText("Interests").assertExists()
        rule.onNodeWithText("Climbing").assertExists()
        rule.onNodeWithText("Something serious").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/11-profile-detail.png")

        rule.onNodeWithContentDescription("Close").performClick()
        rule.onNodeWithTag("card").assertExists()
        assertEquals(emptyList(), log)

        rule.onNodeWithContentDescription("View profile").performClick()
        rule.onNodeWithText("About").assertExists()
        rule.onNodeWithContentDescription("Like").performClick()
        rule.waitUntil(5_000) { log.size == 1 }
        assertEquals(listOf("mia" to SwipeDirection.Like), log)
        rule.onNodeWithText("Jonas").assertExists()
        rule.onNodeWithText("About").assertDoesNotExist()
    }

    @Test
    fun whenNoOneFitsTheDeckSaysSoAndLeadsToTheSettings() {
        var opened = 0
        val none = DiscoveryFilter(showMe = setOf(Gender.Man), lookingFor = setOf(LookingFor.Fun))
        rule.setContent { NearlyTheme { SwipeScreen(people = SamplePeople, filter = none, onFilters = { opened++ }) } }
        rule.onNodeWithText("No one fits your settings").assertExists()
        rule.onNodeWithText("Change settings").performClick()
        rule.onNodeWithContentDescription("Discovery settings").performClick()
        assertEquals(2, opened)
    }

    @Test
    fun theFilterChangesWhoIsInTheDeck() {
        val women = DiscoveryFilter(showMe = setOf(Gender.Woman))
        rule.setContent { NearlyTheme { SwipeScreen(people = SamplePeople, filter = women) } }
        rule.onNodeWithText("Mia").assertExists()
        rule.onNodeWithContentDescription("Skip").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithText("Lea").assertExists() }.isSuccess }
    }

    @Test
    fun everyControlOfTheDiscoverySettingsChangesTheFilter() {
        var filter by mutableStateOf(DiscoveryFilter())
        rule.setContent { NearlyTheme { DiscoveryScreen(filter, { filter = it }, {}) } }

        rule.onNodeWithText("Men").performClick()
        assertEquals(setOf(Gender.Man), filter.showMe)
        rule.onNodeWithText("Women").performClick()
        assertEquals(setOf(Gender.Man, Gender.Woman), filter.showMe)
        rule.onNodeWithText("Everyone").performClick()
        assertEquals(Gender.values().toSet(), filter.showMe)

        rule.onNodeWithText("Something serious").performClick()
        rule.onNodeWithText("Just for fun").performClick()
        assertEquals(setOf(LookingFor.Serious, LookingFor.Fun), filter.lookingFor)
        rule.onNodeWithText("Anything").performClick()
        assertEquals(emptySet(), filter.lookingFor)

        rule.onNodeWithText("Show people a bit further away when no one fits").performScrollTo().performClick()
        assertEquals(false, filter.furtherAway)
        rule.onNodeWithText("Show people slightly outside this range when no one fits").performScrollTo().performClick()
        assertEquals(false, filter.slightlyOutsideAge)
        rule.onNodeWithText("Only people active in the last 7 days").performScrollTo().performClick()
        assertEquals(true, filter.recentlyActiveOnly)
        rule.onNodeWithText("Show me in Swipe").performScrollTo().performClick()
        assertEquals(false, filter.visible)
        rule.onNodeWithText("Show my distance").performScrollTo().performClick()
        assertEquals(false, filter.showDistance)

        rule.onNodeWithText("Reset").performClick()
        assertEquals(DiscoveryFilter(), filter)
    }

    @Test
    fun theChoiceIsKeptForTheNextStart() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("nearly", Application.MODE_PRIVATE).edit().clear().commit()
        val chosen = DiscoveryFilter(
            showMe = setOf(Gender.Man, Gender.NonBinary),
            lookingFor = setOf(LookingFor.Friends, LookingFor.Unsure),
            maxDistanceKm = 80,
            ageMin = 24,
            ageMax = 39,
            furtherAway = false,
            slightlyOutsideAge = false,
            recentlyActiveOnly = true,
            visible = false,
            showDistance = false,
        )
        AppViewModel(app).updateDiscovery(chosen)
        assertEquals(chosen, AppViewModel(app).state.value.discovery)
        app.getSharedPreferences("nearly", Application.MODE_PRIVATE).edit().clear().commit()
        assertEquals(DiscoveryFilter(), AppViewModel(app).state.value.discovery)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1750dp-xxhdpi")
class DiscoveryShotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun shot(name: String, dark: Boolean, filter: DiscoveryFilter = DiscoveryFilter()) {
        rule.setContent { NearlyTheme(dark = dark) { DiscoveryScreen(filter, {}, {}) } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test
    fun light() = shot("12-discovery", dark = false)

    @Test
    fun dark() = shot("12-discovery-dark", dark = true, filter = DiscoveryFilter(showMe = setOf(Gender.Woman), lookingFor = setOf(LookingFor.Serious, LookingFor.Open), maxDistanceKm = 60, ageMin = 25, ageMax = 38))
}
