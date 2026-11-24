package com.kutlusoy.nearly.notify

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kutlusoy.nearly.AppCore
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

class FakeNotifier(var allowed: Boolean = true) : Notifier {
    data class Message(val peer: String, val name: String?, val text: String?, val photo: Boolean)
    val messages = mutableListOf<Message>()
    val matches = mutableListOf<Pair<String, String?>>()
    val cleared = mutableListOf<String>()
    var clearedAll = 0
    var background: Boolean? = null
    override fun permitted() = allowed
    override fun message(peer: String, name: String?, text: String?, photo: Boolean) { messages += Message(peer, name, text, photo) }
    override fun match(peer: String, name: String?) { matches += peer to name }
    override fun clear(peer: String) { cleared += peer }
    override fun clearAll() { clearedAll++ }
    override fun background(on: Boolean) { background = on }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotifyTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val manager get() = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val peer = "ab".repeat(32)

    @AfterTest
    fun reset() = AppCore.install(null)

    @Test
    fun aMessageShowsTheNameAndTheText() {
        AndroidNotifier(app).message(peer, "Mia", "See you at 8?", false)
        val n = shadowOf(manager).allNotifications.single()
        assertEquals("Mia", n.extras.getString("android.title"))
        assertEquals("See you at 8?", n.extras.getCharSequence("android.text").toString())
        assertNotNull(n.contentIntent)
        assertNotNull(n.publicVersion, "the lock screen can show a version without the name and the text")
        assertEquals("New message", n.publicVersion.extras.getCharSequence("android.text").toString())
    }

    @Test
    fun withoutTheNameAndTheTextOnlyThatSomethingArrivedIsShown() {
        AndroidNotifier(app).message(peer, null, null, false)
        val n = shadowOf(manager).allNotifications.single()
        assertEquals("Nearly", n.extras.getString("android.title"))
        assertEquals("New message", n.extras.getCharSequence("android.text").toString())
    }

    @Test
    fun aPhotoSaysPhotoAndAMatchSaysMatch() {
        val notifier = AndroidNotifier(app)
        notifier.message(peer, "Mia", null, true)
        notifier.match("cd".repeat(32), "Ben")
        val texts = shadowOf(manager).allNotifications.map { it.extras.getCharSequence("android.text").toString() }
        assertTrue("Photo" in texts)
        assertTrue(texts.any { it.contains("Ben") })
    }

    @Test
    fun clearingRemovesTheNotificationOfAPerson() {
        val notifier = AndroidNotifier(app)
        notifier.message(peer, "Mia", "Hi", false)
        notifier.message("cd".repeat(32), "Ben", "Hello", false)
        notifier.clear(peer)
        assertEquals(1, shadowOf(manager).allNotifications.size)
        notifier.clearAll()
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test
    fun withoutThePermissionNothingIsPosted() {
        shadowOf(manager).setNotificationsEnabled(false)
        val notifier = AndroidNotifier(app)
        assertFalse(notifier.permitted())
        notifier.message(peer, "Mia", "Hi", false)
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test
    fun theChannelsExist() {
        AndroidNotifier(app).ensureChannels()
        val ids = manager.notificationChannels.map { it.id }
        assertTrue("messages" in ids && "receiving" in ids)
    }

    @Test
    fun backgroundReceivingStartsAndStopsTheService() {
        val notifier = AndroidNotifier(app)
        notifier.background(true)
        assertEquals(ReceiveService::class.java.name, shadowOf(app).nextStartedService.component?.className)
        notifier.background(false)
        assertEquals(ReceiveService::class.java.name, shadowOf(app).nextStoppedService.component?.className)
    }

    @Test
    fun theServiceRunsInTheForegroundWithAPermanentNotification() {
        val controller = Robolectric.buildService(ReceiveService::class.java)
        val service = controller.create().get()
        // the app state of the process is opened by the service, here a ready one
        AppCore.install(com.kutlusoy.nearly.ui.AppViewModel(app, com.kutlusoy.nearly.nostr.SoftwareSecretBox(), com.kutlusoy.nearly.chat.FakeLink(com.kutlusoy.nearly.chat.FakeRelay()), com.kutlusoy.nearly.chat.FakeArea("u33db"), com.kutlusoy.nearly.ui.FakePhotoBackend(), { com.kutlusoy.nearly.feed.MemoryFeedStore() }, FakeNotifier()) { com.kutlusoy.nearly.chat.MemoryChatStore() })
        service.onStartCommand(null, 0, 1)
        assertTrue(shadowOf(service).isLastForegroundNotificationAttached)
        val n = shadowOf(service).lastForegroundNotification
        assertEquals("Nearly is receiving messages", n.extras.getString("android.title"))
    }
}
