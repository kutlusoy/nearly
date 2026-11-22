package com.kutlusoy.nearly.ui

import android.content.SharedPreferences

enum class Frequency { Never, Sometimes, Often }

const val ABOUT_MAX = 500
const val INTERESTS_MAX = 10
const val INTEREST_LENGTH = 24
const val HEIGHT_MIN_CM = 100
const val HEIGHT_MAX_CM = 250

val LANGUAGES = listOf(
    "English", "Deutsch", "Français", "Español", "Italiano", "Português", "Nederlands", "Polski", "Türkçe", "Svenska",
    "Dansk", "Norsk", "Suomi", "Čeština", "Română", "Magyar", "Ελληνικά", "Русский", "Українська",
)

val INTEREST_SUGGESTIONS = listOf(
    "Travel", "Hiking", "Climbing", "Cycling", "Running", "Yoga", "Swimming", "Cooking", "Baking", "Coffee", "Wine", "Music",
    "Concerts", "Dancing", "Photography", "Film", "Books", "Board games", "Gaming", "Art", "Dogs", "Cats", "Plants", "Coding",
)

// What I tell about myself. It is the same set of fields other people see in the profile view.
data class MyProfile(
    val about: String = "",
    val gender: Gender? = null,
    val lookingFor: LookingFor? = null,
    val interests: List<String> = emptyList(),
    val languages: Set<String> = emptySet(),
    val job: String = "",
    val school: String = "",
    val city: String = "",
    val heightCm: Int? = null,
    val drinking: Frequency? = null,
    val smoking: Frequency? = null,
    val exercise: Frequency? = null,
) {
    fun withAbout(text: String) = copy(about = text.take(ABOUT_MAX))

    fun withInterest(raw: String): MyProfile {
        val text = raw.trim().replace(Regex("\\s+"), " ").take(INTEREST_LENGTH)
        if (text.isEmpty() || interests.size >= INTERESTS_MAX || interests.any { it.equals(text, ignoreCase = true) }) return this
        return copy(interests = interests + text)
    }

    fun withoutInterest(text: String) = copy(interests = interests.filterNot { it.equals(text, ignoreCase = true) })

    fun toggleInterest(text: String): MyProfile =
        if (interests.any { it.equals(text, ignoreCase = true) }) withoutInterest(text) else withInterest(text)

    fun toggleLanguage(language: String) = copy(languages = if (language in languages) languages - language else languages + language)

    fun withHeight(text: String): MyProfile {
        val digits = text.filter { it.isDigit() }.take(3)
        val value = digits.toIntOrNull()
        return copy(heightCm = value?.takeIf { it in HEIGHT_MIN_CM..HEIGHT_MAX_CM })
    }

    // Share of the fields that are filled in, for the hint on the profile
    fun completeness(): Int {
        val filled = listOf(
            about.isNotBlank(), gender != null, lookingFor != null, interests.isNotEmpty(), languages.isNotEmpty(),
            job.isNotBlank(), school.isNotBlank(), city.isNotBlank(), heightCm != null,
            drinking != null || smoking != null || exercise != null,
        ).count { it }
        return filled * 100 / 10
    }

    companion object {
        private const val P = "profile."

        private inline fun <reified T : Enum<T>> enumOrNull(prefs: SharedPreferences, key: String): T? =
            enumValues<T>().firstOrNull { it.name == prefs.getString(P + key, null) }

        fun load(prefs: SharedPreferences): MyProfile = MyProfile(
            about = (prefs.getString(P + "about", "") ?: "").take(ABOUT_MAX),
            gender = enumOrNull<Gender>(prefs, "gender"),
            lookingFor = enumOrNull<LookingFor>(prefs, "lookingFor"),
            interests = (prefs.getString(P + "interests", "") ?: "").split('\n').filter { it.isNotBlank() }.take(INTERESTS_MAX),
            languages = (prefs.getString(P + "languages", "") ?: "").split(',').filter { it in LANGUAGES }.toSet(),
            job = prefs.getString(P + "job", "") ?: "",
            school = prefs.getString(P + "school", "") ?: "",
            city = prefs.getString(P + "city", "") ?: "",
            heightCm = prefs.getInt(P + "height", -1).takeIf { it in HEIGHT_MIN_CM..HEIGHT_MAX_CM },
            drinking = enumOrNull<Frequency>(prefs, "drinking"),
            smoking = enumOrNull<Frequency>(prefs, "smoking"),
            exercise = enumOrNull<Frequency>(prefs, "exercise"),
        )

        fun save(prefs: SharedPreferences, p: MyProfile) {
            prefs.edit()
                .putString(P + "about", p.about)
                .putString(P + "gender", p.gender?.name ?: "")
                .putString(P + "lookingFor", p.lookingFor?.name ?: "")
                .putString(P + "interests", p.interests.joinToString("\n"))
                .putString(P + "languages", p.languages.joinToString(","))
                .putString(P + "job", p.job)
                .putString(P + "school", p.school)
                .putString(P + "city", p.city)
                .putInt(P + "height", p.heightCm ?: -1)
                .putString(P + "drinking", p.drinking?.name ?: "")
                .putString(P + "smoking", p.smoking?.name ?: "")
                .putString(P + "exercise", p.exercise?.name ?: "")
                .apply()
        }
    }
}
