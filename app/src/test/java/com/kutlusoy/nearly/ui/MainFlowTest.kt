package com.kutlusoy.nearly.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kutlusoy.nearly.ui.theme.NearlyTheme
import com.kutlusoy.nearly.ui.theme.ThemeMode
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
class MainFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val log = mutableListOf<Pair<String, SwipeDirection>>()

    private fun deck() = rule.setContent {
        NearlyTheme { SwipeScreen(onSwiped = { person, direction -> log += person.id to direction }) }
    }

    @Test
    fun theButtonsAndTheGesturesDecideWhatHappensToACard() {
        deck()
        rule.onNodeWithText("Mia").assertExists()
        rule.onNodeWithContentDescription("Like").performClick()
        rule.waitUntil(5_000) { log.size == 1 }
        rule.onNodeWithText("Jonas").assertExists()

        rule.onNodeWithContentDescription("Not now").performClick()
        rule.waitUntil(5_000) { log.size == 2 }

        rule.onNodeWithTag("card").performTouchInput { swipeRight() }
        rule.waitUntil(5_000) { log.size == 3 }
        rule.onNodeWithTag("card").performTouchInput { swipeLeft() }
        rule.waitUntil(5_000) { log.size == 4 }
        rule.onNodeWithTag("card").performTouchInput { swipeUp() }
        rule.waitUntil(5_000) { log.size == 5 }

        assertEquals(
            listOf("mia" to SwipeDirection.Like, "jonas" to SwipeDirection.Later, "lea" to SwipeDirection.Like, "omar" to SwipeDirection.Later, "sofia" to SwipeDirection.Skip),
            log,
        )
    }

    @Test
    fun theDeckEndsWithAnEmptyStateAndCanStartAgain() {
        deck()
        repeat(SamplePeople.size) { n ->
            rule.onNodeWithContentDescription("Skip").performClick()
            rule.waitUntil(5_000) { log.size == n + 1 }
        }
        rule.onNodeWithText("No one left nearby").assertExists()
        rule.onNodeWithText("Show the sample profiles again").performClick()
        rule.onNodeWithText("Mia").assertExists()
    }

    @Test
    fun aHalfDraggedCardShowsItsStamp() {
        deck()
        rule.onNodeWithTag("card").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(width * 0.2f, 0f))
        }
        rule.waitForIdle()
        rule.onNodeWithText("LIKE").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/08-main-swipe-dragging.png")
    }

    @Test
    fun theThemeIsLightUntilTheUserChoosesAndTheChoiceIsKept() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("nearly", Application.MODE_PRIVATE).edit().clear().commit()
        val first = AppViewModel(app)
        assertEquals(ThemeMode.Light, first.state.value.themeMode)
        first.setTheme(ThemeMode.Dark)
        assertEquals(ThemeMode.Dark, AppViewModel(app).state.value.themeMode)
        first.setTheme(ThemeMode.Light)
    }
}
