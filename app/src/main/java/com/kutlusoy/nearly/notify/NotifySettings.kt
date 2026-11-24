package com.kutlusoy.nearly.notify

import android.content.SharedPreferences

// What the user decided about notifications. All three can be switched off.
data class NotifySettings(
    val enabled: Boolean = true,
    val showText: Boolean = true,
    val background: Boolean = true,
) {
    companion object {
        fun load(prefs: SharedPreferences) = NotifySettings(
            enabled = prefs.getBoolean("notify.enabled", true),
            showText = prefs.getBoolean("notify.text", true),
            background = prefs.getBoolean("notify.background", true),
        )

        fun save(prefs: SharedPreferences, s: NotifySettings) {
            prefs.edit().putBoolean("notify.enabled", s.enabled).putBoolean("notify.text", s.showText).putBoolean("notify.background", s.background).apply()
        }
    }
}

// What the app tells the user outside the app: the screens never talk to the system directly
interface Notifier {
    fun permitted(): Boolean

    // A new message. The name and the text are null when the user does not want them on the display.
    fun message(peer: String, name: String?, text: String?, photo: Boolean)

    fun match(peer: String, name: String?)

    fun clear(peer: String)

    fun clearAll()

    // Keeps the app receiving while it is not on screen, with a permanent notification (no Google services involved)
    fun background(on: Boolean)
}
