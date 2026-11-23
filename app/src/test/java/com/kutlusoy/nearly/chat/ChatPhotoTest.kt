package com.kutlusoy.nearly.chat

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kutlusoy.nearly.nostr.Images
import com.kutlusoy.nearly.nostr.PhotoTransfer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatPhotoTest {
    private val relay = FakeRelay()
    private val anna = newSession()
    private val ben = newSession()
    private val annaStore = MemoryChatStore()
    private val benStore = MemoryChatStore()
    private val annaChat = ChatService(relay, anna, annaStore, background = { it.run() }, chunkTimeoutSeconds = 1).also { it.start() }
    private fun benChat(accept: (String) -> Boolean = { true }) = ChatService(relay, ben, benStore, background = { it.run() }, acceptPhotos = accept, chunkTimeoutSeconds = 1).also { it.start() }

    private fun big(size: Int): ByteArray {
        val base = Images.jpeg(exif = false, width = 2000, height = 1500)
        return base.copyOfRange(0, base.size - 2) + ByteArray(size) { (it % 200 + 1).toByte() } + byteArrayOf(0xFF.toByte(), 0xD9.toByte())
    }

    @Test
    fun aPhotoArrivesInPiecesAndTheSenderLearnsAboutIt() {
        val bytes = big(70_000)
        benChat()
        val sent = annaChat.sendPhoto(ben.hex(), bytes, 2000, 1500, once = false) as SendResult.Queued
        val mine = annaStore.message(sent.message.id)!!
        assertEquals(MessageStatus.Delivered, mine.status)
        assertEquals(PhotoState.Ready, mine.photo!!.state)
        val theirs = benStore.message(sent.message.id)!!
        assertEquals(PhotoState.Ready, theirs.photo!!.state)
        assertEquals(4, theirs.photo!!.total)
        assertEquals(false, theirs.read)
        assertContentEquals(bytes, benChat().photoBytes(theirs.id))
        assertEquals(4 + 1, relay.events.size, "four chunks and one receipt, no copy for the sender")
        assertEquals(emptyList(), benStore.chunks(theirs.photo!!.transfer))
    }

    @Test
    fun metadataOfTheReceivedPhotoIsRemoved() {
        val withExif = Images.jpeg(exif = true)
        benChat()
        val sent = annaChat.sendPhoto(ben.hex(), withExif, 640, 480, once = false) as SendResult.Queued
        val got = benStore.photoBytes(sent.message.photo!!.transfer)!!
        assertTrue(String(got, Charsets.ISO_8859_1).contains("GPS").not())
    }

    @Test
    fun aPhotoThatCanBeSeenOnceIsGoneAfterTheViewerClosed() {
        val chat = benChat()
        val sent = annaChat.sendPhoto(ben.hex(), big(30_000), 2000, 1500, once = true) as SendResult.Queued
        val id = sent.message.id
        assertEquals(PhotoState.Gone, annaStore.message(id)!!.photo!!.state, "the sender keeps no copy after delivery")
        assertNull(annaStore.photoBytes(sent.message.photo!!.transfer))
        assertNotNull(chat.photoBytes(id))
        chat.photoViewed(id)
        assertNull(chat.photoBytes(id))
        assertNull(benStore.photoBytes(sent.message.photo!!.transfer))
        assertEquals(PhotoState.Gone, benStore.message(id)!!.photo!!.state)
        annaChat.photoViewed(id)
        assertEquals(MessageStatus.Delivered, annaStore.message(id)!!.status)
    }

    @Test
    fun strangersAreDropped() {
        benChat { false }
        annaChat.sendPhoto(ben.hex(), big(30_000), 2000, 1500, false)
        assertTrue(benStore.conversations().isEmpty())
        assertEquals(MessageStatus.Sent, annaStore.messages(ben.hex()).single().status)
    }

    @Test
    fun aBlockedSenderIsDropped() {
        benStore.setBlocked(anna.hex(), true)
        benChat()
        annaChat.sendPhoto(ben.hex(), big(30_000), 2000, 1500, false)
        assertTrue(benStore.conversations().isEmpty())
    }

    @Test
    fun aFileThatIsNoJpegIsThrownAway() {
        benChat()
        annaChat.sendPhoto(ben.hex(), ByteArray(30_000) { 7 }, 100, 100, false)
        assertTrue(benStore.conversations().isEmpty())
        assertEquals(2 + 0, relay.events.size, "two chunks, no receipt")
    }

    @Test
    fun aFailedPhotoCanBeSentAgainFromTheStore() {
        val chat = benChat()
        relay.accept = false
        val sent = annaChat.sendPhoto(ben.hex(), big(30_000), 2000, 1500, false) as SendResult.Queued
        assertEquals(MessageStatus.Failed, annaStore.message(sent.message.id)!!.status)
        relay.accept = true
        annaChat.retry(sent.message.id)
        assertEquals(MessageStatus.Delivered, annaStore.message(sent.message.id)!!.status)
        assertNotNull(chat.photoBytes(sent.message.id))
    }

    @Test
    fun aWaitingPhotoLeavesOnlyWhenTheSenderSendsItThroughTheRelays() {
        val chat = benChat()
        val sent = annaChat.sendPhoto(ben.hex(), big(30_000), 2000, 1500, false, directFirst = true) as SendResult.Queued
        val id = sent.message.id
        assertEquals(MessageStatus.Waiting, annaStore.message(id)!!.status)
        assertTrue(relay.events.isEmpty(), "nothing leaves while the photo waits")
        assertNotNull(annaStore.photoBytes(sent.message.photo!!.transfer))
        annaChat.sendViaRelay(id)
        assertEquals(MessageStatus.Delivered, annaStore.message(id)!!.status)
        assertNotNull(chat.photoBytes(id))
    }

    @Test
    fun aWaitingPhotoCanBeTakenBackWithoutAnyTraffic() {
        val sent = annaChat.sendPhoto(ben.hex(), big(30_000), 2000, 1500, false, directFirst = true) as SendResult.Queued
        assertTrue(annaChat.cancelPhoto(sent.message.id))
        assertNull(annaStore.message(sent.message.id))
        assertNull(annaStore.photoBytes(sent.message.photo!!.transfer))
        assertTrue(relay.events.isEmpty())
        assertEquals(false, annaChat.cancelPhoto(sent.message.id))
    }

    @Test
    fun aPhotoOnTheWayIsTakenBackAndTheReceiverDropsThePieces() {
        benChat()
        val bytes = big(70_000)
        val meta = PhotoTransfer.meta(bytes, 2000, 1500, false)
        relay.deliverNew(PhotoTransfer.wrapChunk(anna, ben.hex(), meta, 0, bytes))
        relay.deliverNew(PhotoTransfer.wrapChunk(anna, ben.hex(), meta, 1, bytes))
        val id = "ph:${meta.transfer}"
        assertEquals(PhotoState.Receiving, benStore.message(id)!!.photo!!.state)
        annaStore.add(ChatMessage(id, ben.hex(), true, "", 1, MessageStatus.Sending, true, PhotoInfo(meta.transfer, 2000, 1500, bytes.size, false, PhotoState.Ready, 2, meta.total)))
        annaStore.savePhoto(meta.transfer, bytes)
        assertTrue(annaChat.cancelPhoto(id))
        assertNull(benStore.message(id), "the pieces are gone on the other phone")
        assertEquals(emptyList(), benStore.chunks(meta.transfer))
        relay.deliverNew(PhotoTransfer.wrapChunk(anna, ben.hex(), meta, 2, bytes))
        assertNull(benStore.message(id), "a late piece does not start the photo again")
    }

    @Test
    fun aPhotoCanBeReplacedByAnother() {
        val first = annaChat.sendPhoto(ben.hex(), big(30_000), 2000, 1500, false, directFirst = true) as SendResult.Queued
        val second = annaChat.replacePhoto(first.message.id, big(40_000), 2000, 1500, true, directFirst = true) as SendResult.Queued
        assertNull(annaStore.message(first.message.id))
        assertEquals(MessageStatus.Waiting, annaStore.message(second.message.id)!!.status)
        assertEquals(true, annaStore.message(second.message.id)!!.photo!!.once)
        assertEquals(1, annaStore.messages(ben.hex()).size)
    }

    @Test
    fun aPersonCanOnlyHaveFewTransfersOpen() {
        benChat()
        repeat(5) { n ->
            val bytes = big(50_000 + n)
            val meta = PhotoTransfer.meta(bytes, 100, 100, false)
            relay.deliverNew(PhotoTransfer.wrapChunk(anna, ben.hex(), meta, 0, bytes))
        }
        assertEquals(ChatService.MAX_OPEN_TRANSFERS, benStore.messages(anna.hex()).size)
    }

    @Test
    fun tooBigPhotosAreRefused() {
        assertEquals(SendResult.PhotoTooBig, annaChat.sendPhoto(ben.hex(), ByteArray(PhotoTransfer.MAX_BYTES + 1), 10, 10, false))
        assertEquals(SendResult.ToSelf, annaChat.sendPhoto(anna.hex(), ByteArray(10), 10, 10, false))
    }

    @Test
    fun theStoreKeepsPhotosAndPieces() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = SqliteChatStore(app, "photo1")
        val info = PhotoInfo("a".repeat(32), 100, 80, 500, true, PhotoState.Receiving, 1, 3)
        store.add(ChatMessage("ph:x", "p", false, "", 5, MessageStatus.Received, true, info))
        assertEquals(info, store.message("ph:x")!!.photo)
        assertTrue(store.addChunk(info.transfer, 1, byteArrayOf(1, 2)))
        assertEquals(false, store.addChunk(info.transfer, 1, byteArrayOf(9)))
        assertTrue(store.addChunk(info.transfer, 0, byteArrayOf(3)))
        assertEquals(listOf(0, 1), store.chunks(info.transfer).map { it.first })
        val whole = ByteArray(1_200_000) { it.toByte() }
        store.savePhoto(info.transfer, whole)
        assertContentEquals(whole, store.photoBytes(info.transfer))
        store.setPhoto("ph:x", info.copy(state = PhotoState.Ready))
        store.setRead("ph:x", false)
        assertEquals(1, store.unreadTotal())
        store.deleteConversation("p")
        assertNull(store.photoBytes(info.transfer))
        assertEquals(emptyList(), store.chunks(info.transfer))
    }
}
