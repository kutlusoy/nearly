package com.kutlusoy.nearly.chat

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SqliteChatStoreTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    private fun message(id: String, peer: String, at: Long, mine: Boolean = false, text: String = id, read: Boolean = mine) =
        ChatMessage(id, peer, mine, text, at, if (mine) MessageStatus.Sent else MessageStatus.Received, read)

    @Test
    fun messagesAreKeptInOrderAndNeverTwice() {
        val store = SqliteChatStore(app, "a1")
        assertTrue(store.add(message("m2", "p", 20)))
        assertTrue(store.add(message("m1", "p", 10)))
        assertFalse(store.add(message("m1", "p", 10, text = "changed")))
        assertEquals(listOf("m1", "m2"), store.messages("p").map { it.id })
        assertEquals("m1", store.messages("p").first().text)
        assertNull(store.message("none"))
        assertEquals("m2", store.message("m2")?.id)
    }

    @Test
    fun conversationsShowTheLastMessageAndTheUnreadCountNewestFirst() {
        val store = SqliteChatStore(app, "a2")
        store.add(message("a1", "anna", 10))
        store.add(message("a2", "anna", 30))
        store.add(message("b1", "ben", 20, mine = true))
        val list = store.conversations()
        assertEquals(listOf("anna", "ben"), list.map { it.peer })
        assertEquals("a2", list[0].last.id)
        assertEquals(2, list[0].unread)
        assertEquals(0, list[1].unread)
        assertEquals(2, store.unreadTotal())
        store.markRead("anna")
        assertEquals(0, store.unreadTotal())
    }

    @Test
    fun statusDeleteAndRemoveWork() {
        val store = SqliteChatStore(app, "a3")
        store.add(ChatMessage("s1", "p", true, "x", 5, MessageStatus.Sending))
        store.setStatus("s1", MessageStatus.Failed)
        assertEquals(MessageStatus.Failed, store.message("s1")?.status)
        store.remove("s1")
        assertNull(store.message("s1"))
        store.add(message("d1", "q", 1))
        store.deleteConversation("q")
        assertTrue(store.conversations().isEmpty())
    }

    @Test
    fun wrapsPeerNamesAndTheSyncTimeSurviveANewInstance() {
        SqliteChatStore(app, "a4").apply {
            rememberWrap("w1")
            setPeerProfile("p", PeerProfile("Paula"))
            setPeerProfile("p", PeerProfile("Paula B", "Climber", 29))
            setFollowed("p", true)
            setFollowed("q", true)
            setFollowed("q", false)
            setLastSync(1234)
            add(message("k1", "p", 1))
            close()
        }
        val again = SqliteChatStore(app, "a4")
        assertTrue(again.seenWrap("w1"))
        assertFalse(again.seenWrap("w2"))
        assertEquals("Paula B", again.peerName("p"))
        assertEquals(PeerProfile("Paula B", "Climber", 29), again.peerProfile("p"))
        assertEquals(listOf("p"), again.followed())
        assertNull(again.peerProfile("nobody"))
        assertEquals(1234L, again.lastSync())
        assertEquals(1, again.messages("p").size)
    }

    @Test
    fun everyAccountHasItsOwnDatabase() {
        SqliteChatStore(app, "x1").add(message("m", "p", 1))
        assertTrue(SqliteChatStore(app, "x2").conversations().isEmpty())
    }
}
