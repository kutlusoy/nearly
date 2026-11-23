package com.kutlusoy.nearly.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.kutlusoy.nearly.chat.ChatMessage
import com.kutlusoy.nearly.chat.MessageStatus
import com.kutlusoy.nearly.chat.PhotoInfo
import com.kutlusoy.nearly.chat.PhotoState
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
class ChatPhotoUiTest {
    @get:Rule
    val rule = createComposeRule()

    private fun photo(id: String, mine: Boolean, state: PhotoState, once: Boolean, have: Int = 3, status: MessageStatus = if (mine) MessageStatus.Delivered else MessageStatus.Received) =
        ChatMessage(id, "peer", mine, "", 1_800_000_000, status, true, PhotoInfo("a".repeat(32), 400, 300, 50_000, once, state, have, 4))

    @Test
    fun aWaitingPhotoOffersRelayReplaceAndCancel() {
        val calls = mutableListOf<String>()
        val waiting = photo("ph:9", mine = true, state = PhotoState.Ready, once = false, status = MessageStatus.Waiting)
        rule.setContent {
            NearlyTheme {
                ConversationScreen("Anna", listOf(waiting), null, onSend = {}, onRetry = {}, onBack = {}, onCancelPhoto = { calls += "cancel:$it" }, onPhotoViaRelay = { calls += "relay:$it" })
            }
        }
        rule.onNodeWithText("Waiting. Send it through the relays or take it back.").assertExists()
        rule.onNodeWithTag("direct-toggle").assertExists()
        rule.onNodeWithTag("photo-replace").assertExists()
        rule.onNodeWithTag("photo-relay").performClick()
        rule.onNodeWithTag("photo-cancel").performClick()
        assertEquals(listOf("relay:ph:9", "cancel:ph:9"), calls)
    }

    @Test
    fun photosShowProgressAndStates() {
        val viewed = mutableListOf<String>()
        val messages = listOf(
            photo("ph:1", mine = false, state = PhotoState.Receiving, once = false),
            photo("ph:2", mine = false, state = PhotoState.Ready, once = true),
            photo("ph:3", mine = true, state = PhotoState.Gone, once = true),
        )
        rule.setContent {
            NearlyTheme {
                ConversationScreen("Anna", messages, null, onSend = {}, onRetry = {}, onBack = {}, loadPhoto = { null }, onPhotoViewed = { viewed += it })
            }
        }
        rule.onNodeWithText("Receiving photo 3 of 4").assertExists()
        rule.onNodeWithText("Photo, view once. Tap to open, it is gone afterwards.").assertExists()
        rule.onNodeWithText("Photo, view once").assertExists()
        rule.onNodeWithTag("attach-photo").assertExists()
        rule.onNodeWithTag("once-toggle").performClick()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/chat-photos.png")
        rule.onNodeWithText("Photo, view once. Tap to open, it is gone afterwards.").performClick()
        rule.onNodeWithTag("photo-viewer").assertExists()
        rule.onNodeWithTag("photo-viewer-close").performClick()
        assertEquals(listOf("ph:2"), viewed)
    }
}
