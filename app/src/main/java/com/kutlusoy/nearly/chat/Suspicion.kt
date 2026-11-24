package com.kutlusoy.nearly.chat

// A first message with a link, an invitation to another app or a request for money is shown with a warning. Never decides alone.
object Suspicion {
    private val link = Regex("(?i)(https?://|www\\.|\\bt\\.me/|\\bwa\\.me/|\\b[a-z0-9-]{2,}\\.(com|net|org|io|me|ly|app|xyz|top|click|link|info|biz|ru|cc|gl)\\b)")
    private val words = Regex("(?iU)\\b(bitcoin|btc|crypto|usdt|paypal|western union|gift ?card|geschenkkarte|überweis\\w*|invest\\w*|send money|money|geld|verification code|bestätigungscode|iban|onlyfans|telegram|whatsapp|snapchat)\\b")

    fun isSuspicious(text: String): Boolean = link.containsMatchIn(text) || words.containsMatchIn(text)
}
