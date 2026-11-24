package com.kutlusoy.nearly.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.kutlusoy.nearly.chat.ChatMessage
import com.kutlusoy.nearly.chat.ContactUi
import com.kutlusoy.nearly.chat.MessageStatus
import com.kutlusoy.nearly.nostr.ReportType
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
class SafetyUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val peer = "ab".repeat(32)

    @Test
    fun aReportNeedsATypeAndTheConsent() {
        var type by mutableStateOf<ReportType?>(null)
        var consent by mutableStateOf(false)
        rule.setContent {
            NearlyTheme {
                Column {
                    ReportForm(type, { type = it }, "", {}, consent, { consent = it })
                    androidx.compose.material3.Button(onClick = {}, enabled = type != null && consent, modifier = Modifier.testTag("report-send")) { Text("send") }
                }
            }
        }
        rule.onNodeWithTag("report-send").assertIsNotEnabled()
        rule.onNodeWithTag("report-type-spam").performClick()
        rule.onNodeWithTag("report-send").assertIsNotEnabled()
        rule.onNodeWithTag("report-consent").performClick()
        rule.onNodeWithTag("report-send").assertIsEnabled()
        assertEquals(ReportType.Spam, type)
    }

    @Test
    fun theContactPageOffersTheReportOnlyWithAReceiver() {
        rule.setContent {
            NearlyTheme {
                androidx.compose.runtime.CompositionLocalProvider(LocalReportPerson provides { _, _, _ -> }) {
                    ContactProfileScreen(ContactUi(peer, "Mia", "", null, "npub", false), onBack = {}, onMessage = {}, onToggleFollow = {})
                }
            }
        }
        rule.onNodeWithTag("report-contact").assertExists()
    }

    @Test
    fun aStrangerGetsTheBanner() {
        rule.setContent { NearlyTheme { ConversationScreen(name = "Mia", messages = emptyList(), error = null, onSend = {}, onRetry = {}, onBack = {}, stranger = true) } }
        rule.onNodeWithTag("stranger-banner").assertExists()
    }
}
