package com.kutlusoy.nearly.ui

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kutlusoy.nearly.chat.FakeArea
import com.kutlusoy.nearly.chat.FakeLink
import com.kutlusoy.nearly.chat.FakeRelay
import com.kutlusoy.nearly.chat.MemoryChatStore
import com.kutlusoy.nearly.media.PhotoBackend
import com.kutlusoy.nearly.nostr.BlossomClient
import com.kutlusoy.nearly.nostr.DeleteResult
import com.kutlusoy.nearly.nostr.PhotoRef
import com.kutlusoy.nearly.nostr.Session
import com.kutlusoy.nearly.nostr.SoftwareSecretBox
import com.kutlusoy.nearly.ui.registration.RegistrationStep
import com.kutlusoy.nearly.ui.theme.NearlyTheme
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// A photo service that needs no network: it keeps what was uploaded and what was deleted
class FakePhotoBackend : PhotoBackend {
    var fail = false
    var deleteAnswer = DeleteResult.Deleted
    val deleted = mutableListOf<PhotoRef>()
    var loads = 0
    private var next = 0

    private val png: ByteArray = ByteArrayOutputStream().also { out ->
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF3366CC.toInt()) }.compress(Bitmap.CompressFormat.PNG, 100, out)
    }.toByteArray()

    override fun upload(session: Session, open: () -> InputStream, alt: String?): PhotoRef {
        if (fail) throw IOException("no connection")
        open().use { it.readBytes() }
        val hash = BlossomClient.sha256Hex(byteArrayOf((next++).toByte()))
        return PhotoRef("https://cdn.example/$hash.jpg", "image/jpeg", hash, 1200, 1600, alt, "LEHV6nWB2yk8pyo0adR*.7kCMdnj")
    }

    override fun delete(session: Session, photo: PhotoRef): DeleteResult {
        deleted += photo
        return deleteAnswer
    }

    override fun load(photo: PhotoRef): ByteArray {
        loads++
        return png
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class PhotoFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    private fun waitFor(timeoutMs: Long = 120_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            rule.waitForIdle()
            check(System.currentTimeMillis() < end) { "timeout" }
            Thread.sleep(50)
        }
    }

    private fun phone(relay: FakeRelay, name: String, backend: PhotoBackend = FakePhotoBackend()): AppViewModel {
        val vm = AppViewModel(app, SoftwareSecretBox(), FakeLink(relay), FakeArea("u33db"), backend) { MemoryChatStore() }
        vm.openRegistration()
        vm.updateRegistration {
            it.copy(username = name, email = "$name@example.org", emailAgain = "$name@example.org", wordsSaved = true, day = "1", month = "1", year = "1990", step = RegistrationStep.Birthdate)
        }
        vm.submitRegistration()
        waitFor { vm.state.value.screen == Screen.Main }
        return vm
    }

    private fun AppViewModel.add() = addPhoto { "jpeg".byteInputStream() }

    @Test
    fun anAddedPhotoIsInTheProfileTheCardAndTheSavedData() {
        app.getSharedPreferences("nearly", Application.MODE_PRIVATE).edit().clear().commit()
        val relay = FakeRelay()
        val anna = phone(relay, "anna")
        val ben = phone(relay, "ben")
        anna.add()
        waitFor { anna.state.value.profile.photos.size == 1 && !anna.state.value.photoBusy }
        val ref = anna.state.value.profile.photos.single()
        waitFor { ben.state.value.nearby.people.firstOrNull { it.name == "anna" }?.photoRefs == listOf(ref) }
        assertEquals(1, ben.state.value.nearby.people.single { it.name == "anna" }.photos)
        assertEquals(listOf(ref), MyProfile.load(app.getSharedPreferences("nearly", Application.MODE_PRIVATE)).photos)
    }

    @Test
    fun theSeventhPhotoIsRefusedAndAFailedUploadSaysSo() {
        app.getSharedPreferences("nearly", Application.MODE_PRIVATE).edit().clear().commit()
        val backend = FakePhotoBackend()
        val anna = phone(FakeRelay(), "anna", backend)
        repeat(MAX_PHOTOS) {
            anna.add()
            waitFor { anna.state.value.profile.photos.size == it + 1 && !anna.state.value.photoBusy }
        }
        anna.add()
        assertEquals(PhotoNote.TooMany, anna.state.value.photoNote)
        assertEquals(MAX_PHOTOS, anna.state.value.profile.photos.size)

        anna.removePhoto(0)
        backend.fail = true
        anna.add()
        waitFor { anna.state.value.photoNote == PhotoNote.UploadFailed }
        assertEquals(false, anna.state.value.photoBusy)
        assertEquals(MAX_PHOTOS - 1, anna.state.value.profile.photos.size)
    }

    @Test
    fun removingAPhotoLeavesTheProfileAndTheCardAndAsksTheServerToDelete() {
        app.getSharedPreferences("nearly", Application.MODE_PRIVATE).edit().clear().commit()
        val relay = FakeRelay()
        val backend = FakePhotoBackend()
        val anna = phone(relay, "anna", backend)
        val ben = phone(relay, "ben")
        anna.add()
        waitFor { anna.state.value.profile.photos.size == 1 && !anna.state.value.photoBusy }
        val ref = anna.state.value.profile.photos.single()
        waitFor { ben.state.value.nearby.people.firstOrNull { it.name == "anna" }?.photoRefs?.size == 1 }

        anna.removePhoto(0)
        assertTrue(anna.state.value.profile.photos.isEmpty())
        waitFor { anna.state.value.photoNote == PhotoNote.Removed }
        assertEquals(listOf(ref), backend.deleted)
        waitFor { ben.state.value.nearby.people.firstOrNull { it.name == "anna" }?.photoRefs?.isEmpty() == true }

        backend.deleteAnswer = DeleteResult.Refused
        anna.add()
        waitFor { anna.state.value.profile.photos.size == 1 && !anna.state.value.photoBusy }
        anna.removePhoto(0)
        waitFor { anna.state.value.photoNote == PhotoNote.ServerMayKeep }
    }

    @Test
    fun thePhotoThatIsMadeFirstBecomesTheProfilePicture() {
        app.getSharedPreferences("nearly", Application.MODE_PRIVATE).edit().clear().commit()
        val anna = phone(FakeRelay(), "anna")
        repeat(3) {
            anna.add()
            waitFor { anna.state.value.profile.photos.size == it + 1 && !anna.state.value.photoBusy }
        }
        val third = anna.state.value.profile.photos[2]
        anna.makePhotoFirst(2)
        assertEquals(third, anna.state.value.profile.photos.first())
        assertEquals(3, anna.state.value.profile.photos.size)
    }

    @Test
    fun aLoadedPhotoIsKeptInMemory() {
        val backend = FakePhotoBackend()
        val anna = phone(FakeRelay(), "anna", backend)
        val ref = PhotoRef("https://cdn.example/x.jpg", "image/jpeg", BlossomClient.sha256Hex(byteArrayOf(42)))
        assertTrue(runBlocking { anna.loadPhoto(ref) } != null)
        assertTrue(runBlocking { anna.loadPhoto(ref) } != null)
        assertEquals(1, backend.loads)
    }

    @Test
    fun savedPhotosAreReadBackCarefully() {
        val ok = PhotoRef("https://cdn.example/a.jpg", "image/jpeg", BlossomClient.sha256Hex(byteArrayOf(1)), 10, 20, "alt", "LEHV6nWB2yk8pyo0adR*.7kCMdnj")
        assertEquals(listOf(ok), MyProfile.parsePhotos(MyProfile.photosJson(listOf(ok))))
        val text = """[{"url":"http://x/a.jpg","mime":"image/jpeg","sha256":"${ok.sha256}"},{"url":"https://x/a.jpg","mime":"text/html","sha256":"${ok.sha256}"},{"url":"https://x/a.jpg","mime":"image/jpeg","sha256":"zz"},"junk",{"url":"https://x/b.jpg","mime":"image/png","sha256":"${ok.sha256}"}]"""
        assertEquals(listOf("https://x/b.jpg"), MyProfile.parsePhotos(text).map { it.url })
        assertEquals(emptyList(), MyProfile.parsePhotos("not json"))
        assertEquals(emptyList(), MyProfile.parsePhotos(""))
    }

    @Test
    fun theEditorShowsTheGridWithAddAndRemove() {
        val refs = (1..2).map { PhotoRef("https://cdn.example/$it.jpg", "image/jpeg", BlossomClient.sha256Hex(byteArrayOf(it.toByte())), 100, 100, null, "LEHV6nWB2yk8pyo0adR*.7kCMdnj") }
        var removed = -1
        var first = -1
        var busy by androidx.compose.runtime.mutableStateOf(false)
        rule.setContent {
            NearlyTheme { EditProfileScreen(MyProfile(photos = refs), {}, {}, photoBusy = busy, photoNote = PhotoNote.ServerMayKeep, onRemovePhoto = { removed = it }, onMakeFirst = { first = it }) }
        }
        rule.onNodeWithTag("photo-0").assertExists()
        rule.onNodeWithTag("photo-1").assertExists()
        rule.onNodeWithTag("photo-add").assertExists()
        rule.onNodeWithTag("photo-note").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/24-edit-photos.png")
        rule.onNodeWithTag("photo-remove-1").performClick()
        assertEquals(1, removed)
        rule.onNodeWithTag("photo-first-1").performClick()
        assertEquals(1, first)
        busy = true
        rule.waitForIdle()
        rule.onNodeWithTag("photo-busy").assertExists()
    }

    @Test
    fun theOwnProfileShowsTheGrid() {
        val refs = (1..4).map { PhotoRef("https://cdn.example/$it.jpg", "image/jpeg", BlossomClient.sha256Hex(byteArrayOf(it.toByte())), 100, 100, null, "LEHV6nWB2yk8pyo0adR*.7kCMdnj") }
        rule.setContent {
            NearlyTheme { MainScreen(AccountInfo("anna", "npub1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq", 30), Tab.Profile, {}, {}, profile = MyProfile(photos = refs)) }
        }
        rule.onNodeWithTag("photo-grid").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/25-profile-grid.png")
    }
}
