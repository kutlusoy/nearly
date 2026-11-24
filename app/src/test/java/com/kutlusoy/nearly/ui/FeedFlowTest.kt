package com.kutlusoy.nearly.ui

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kutlusoy.nearly.chat.FakeArea
import com.kutlusoy.nearly.chat.FakeLink
import com.kutlusoy.nearly.chat.FakeRelay
import com.kutlusoy.nearly.chat.MemoryChatStore
import com.kutlusoy.nearly.feed.MemoryFeedStore
import com.kutlusoy.nearly.nostr.SoftwareSecretBox
import com.kutlusoy.nearly.ui.registration.RegistrationStep
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class FeedFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    private fun waitFor(timeoutMs: Long = 180_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            rule.waitForIdle()
            check(System.currentTimeMillis() < end) { "timeout" }
            Thread.sleep(50)
        }
    }

    private fun phone(relay: FakeRelay, name: String, photos: FakePhotoBackend = FakePhotoBackend()): AppViewModel {
        val vm = AppViewModel(app, SoftwareSecretBox(), FakeLink(relay), FakeArea("u33db"), photos, { MemoryFeedStore() }) { MemoryChatStore() }
        vm.openRegistration()
        vm.updateRegistration {
            it.copy(username = name, email = "$name@example.org", emailAgain = "$name@example.org", wordsSaved = true, day = "1", month = "1", year = "1990", step = RegistrationStep.Birthdate)
        }
        vm.submitRegistration()
        waitFor { vm.state.value.screen == Screen.Main }
        return vm
    }

    @Test
    fun aFollowerSeesPostsStoriesAndLikesGoBack() {
        val relay = FakeRelay()
        val anna = phone(relay, "anna")
        val ben = phone(relay, "ben")
        waitFor { ben.state.value.nearby.people.size == 1 }
        val annaKey = ben.state.value.nearby.people.single().key!!
        ben.toggleFollowKey(annaKey)
        anna.publishPost("Hello #nearly", listOf { "jpeg".byteInputStream() }, story = false)
        waitFor { !anna.state.value.composeBusy && anna.state.value.feed.posts.isNotEmpty() }
        waitFor { ben.state.value.feed.posts.any { it.text == "Hello #nearly" && it.photos.size == 1 } }
        val post = ben.state.value.feed.posts.single()
        ben.likePost(post.id)
        waitFor { anna.state.value.feed.posts.single().likes == 1 }
        anna.publishPost("Coffee", emptyList(), story = true)
        waitFor { ben.state.value.feed.stories.any { it.stories.any { s -> s.text == "Coffee" } } }
        anna.deletePost(anna.state.value.feed.posts.single().id)
        waitFor { anna.state.value.feed.posts.isEmpty() }
        assertTrue(anna.state.value.feed.stories.isNotEmpty(), "the story stays")
    }

    @Test
    fun aFailedUploadKeepsTheComposerOpenWithANote() {
        val relay = FakeRelay()
        val photos = FakePhotoBackend().apply { fail = true }
        val anna = phone(relay, "anna", photos)
        anna.openComposer(true)
        anna.publishPost("With photo", listOf { "jpeg".byteInputStream() }, story = false)
        waitFor { !anna.state.value.composeBusy && anna.state.value.composeNote != null }
        assertEquals(ComposeNote.UploadFailed, anna.state.value.composeNote)
        assertTrue(anna.state.value.composerOpen)
        assertTrue(anna.state.value.feed.posts.isEmpty())
    }
}
