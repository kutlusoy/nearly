package com.kutlusoy.nearly.nostr

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

// The date of birth never leaves the device, only the age that follows from it is published
data class BirthDate(val year: Int, val month: Int, val day: Int) {
    init {
        require(month in 1..12 && day in 1..daysIn(year, month)) { "invalid date" }
    }

    fun ageOn(today: BirthDate): Int {
        var age = today.year - year
        if (today.month < month || (today.month == month && today.day < day)) age--
        return age
    }

    fun isAdultOn(today: BirthDate) = ageOn(today) >= ADULT_AGE

    companion object {
        const val ADULT_AGE = 18

        private fun daysIn(year: Int, month: Int) = when (month) {
            2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
    }
}

data class ProfileData(
    val name: String,
    val about: String = "",
    val picture: String? = null,
    val age: Int? = null,
    val languages: List<String> = emptyList(),
) {
    companion object {
        const val MAX_NAME = 40
        const val MAX_ABOUT = 500
        const val MAX_LANGUAGES = 10
        const val MIN_AGE = 18
        const val MAX_AGE = 120
    }
}

// Profile events (kind 0). Everything that comes from a relay is checked and shortened, a profile is foreign data.
object ProfileCodec {
    const val KIND = 0

    fun sign(session: Session, data: ProfileData, createdAt: Long = System.currentTimeMillis() / 1000): Event =
        Event.sign(session, KIND, content(data), emptyList(), createdAt)

    fun content(data: ProfileData): String {
        val parts = mutableListOf("\"name\":" + Event.quote(data.name), "\"about\":" + Event.quote(data.about))
        data.picture?.let { parts.add("\"picture\":" + Event.quote(it)) }
        data.age?.let { parts.add("\"age\":$it") }
        if (data.languages.isNotEmpty()) parts.add("\"languages\":" + data.languages.joinToString(",", "[", "]") { Event.quote(it) })
        return parts.joinToString(",", "{", "}")
    }

    fun parse(event: Event): ProfileData? {
        if (event.kind != KIND) return null
        val o: JsonObject = try {
            Json.parseToJsonElement(event.content).jsonObject
        } catch (e: Exception) {
            return null
        }
        val name = clean(o["name"]?.stringOrNull(), ProfileData.MAX_NAME)
        if (name.isBlank()) return null
        val about = clean(o["about"]?.stringOrNull(), ProfileData.MAX_ABOUT, keepNewlines = true)
        val picture = o["picture"]?.stringOrNull()?.takeIf { it.startsWith("https://") && it.length <= 500 }
        val age = o["age"]?.let { (it as? JsonPrimitive)?.intOrNull }?.takeIf { it in ProfileData.MIN_AGE..ProfileData.MAX_AGE }
        val languages = (o["languages"] as? JsonArray).orEmpty()
            .mapNotNull { it.stringOrNull()?.lowercase()?.takeIf { code -> LANGUAGE.matches(code) } }
            .distinct().take(ProfileData.MAX_LANGUAGES)
        return ProfileData(name, about, picture, age, languages)
    }

    private val LANGUAGE = Regex("[a-z]{2,3}")

    private fun kotlinx.serialization.json.JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    // Removes control characters and shortens, a name with line breaks or invisible characters would break lists and cards
    private fun clean(value: String?, max: Int, keepNewlines: Boolean = false): String {
        val text = (value ?: "").filter { c -> !c.isISOControl() || (keepNewlines && c == '\n') }.trim()
        return if (text.length > max) text.take(max) else text
    }
}
