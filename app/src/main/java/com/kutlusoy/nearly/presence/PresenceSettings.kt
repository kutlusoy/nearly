package com.kutlusoy.nearly.presence

import android.content.SharedPreferences

enum class PresenceAudience { Nobody, Friends }

// What the user decided about the presence. Friends are people the user follows and who follow back.
data class PresenceSettings(
    val audience: PresenceAudience = PresenceAudience.Friends,
    val cellLength: Int = 6,
    val seeFriends: Boolean = true,
) {
    companion object {
        fun load(prefs: SharedPreferences) = PresenceSettings(
            audience = PresenceAudience.values().firstOrNull { it.name == prefs.getString("presence.audience", null) } ?: PresenceAudience.Friends,
            cellLength = prefs.getInt("presence.cell", 6).takeIf { it in 5..6 } ?: 6,
            seeFriends = prefs.getBoolean("presence.see", true),
        )

        fun save(prefs: SharedPreferences, s: PresenceSettings) {
            prefs.edit().putString("presence.audience", s.audience.name).putInt("presence.cell", s.cellLength).putBoolean("presence.see", s.seeFriends).apply()
        }
    }
}

// What the presence remembers between two starts: the friends, the people who said hello before the user followed them, the first use screen
interface PresenceMemory {
    fun friends(): Set<String>
    fun setFriends(keys: Set<String>)
    fun incoming(): Set<String>
    fun setIncoming(keys: Set<String>)
    fun explained(): Boolean
    fun setExplained()
}

class PrefsPresenceMemory(private val prefs: SharedPreferences, account: String) : PresenceMemory {
    private val friendsKey = "presence.friends.$account"
    private val incomingKey = "presence.incoming.$account"
    override fun friends(): Set<String> = prefs.getStringSet(friendsKey, emptySet())!!.toSet()
    override fun setFriends(keys: Set<String>) { prefs.edit().putStringSet(friendsKey, keys).apply() }
    override fun incoming(): Set<String> = prefs.getStringSet(incomingKey, emptySet())!!.toSet()
    override fun setIncoming(keys: Set<String>) { prefs.edit().putStringSet(incomingKey, keys).apply() }
    override fun explained() = prefs.getBoolean("presence.explained", false)
    override fun setExplained() { prefs.edit().putBoolean("presence.explained", true).apply() }
}
