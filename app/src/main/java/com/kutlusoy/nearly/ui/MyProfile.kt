package com.kutlusoy.nearly.ui

import android.content.SharedPreferences
import com.kutlusoy.nearly.media.BlurHash
import com.kutlusoy.nearly.nostr.CardExtras
import com.kutlusoy.nearly.nostr.CardPrompt
import com.kutlusoy.nearly.nostr.PhotoRef
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

enum class Frequency { Never, Sometimes, Often }

const val ABOUT_MAX = 500
const val INTERESTS_MAX = 10
const val INTEREST_LENGTH = 24
const val MAX_PHOTOS = 6
const val HEIGHT_MIN_CM = 100
const val HEIGHT_MAX_CM = 250
const val LINK_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@._:/#?=&%~+-"

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
    val photos: List<PhotoRef> = emptyList(),
    val pronouns: String = "",
    val orientation: Orientation? = null,
    val family: FamilyPlans? = null,
    val zodiac: Zodiac? = null,
    val diet: Diet? = null,
    val pets: String = "",
    val link: String = "",
    val prompts: List<ProfilePrompt> = emptyList(),
    val hidden: Set<String> = emptySet(),
) {
    fun withLink(text: String) = copy(link = text.filter { it in LINK_CHARS }.take(LINK_MAX))

    fun toggleHidden(field: ProfileField) = copy(hidden = if (field.key in hidden) hidden - field.key else hidden + field.key)

    fun isHidden(field: ProfileField) = field.key in hidden

    fun withNewPrompt(): MyProfile {
        if (prompts.size >= PROMPTS_MAX) return this
        val free = (0 until PROMPT_COUNT).firstOrNull { q -> prompts.none { it.q == q } } ?: return this
        return copy(prompts = prompts + ProfilePrompt(free, ""))
    }

    fun withoutPrompt(index: Int) = copy(prompts = prompts.filterIndexed { i, _ -> i != index })

    fun withAnswer(index: Int, text: String) = copy(prompts = prompts.mapIndexed { i, p -> if (i == index) p.copy(a = text.replace('\n', ' ').take(PROMPT_ANSWER_MAX)) else p })

    // The next question that no other prompt uses
    fun withNextQuestion(index: Int): MyProfile {
        val current = prompts.getOrNull(index) ?: return this
        val used = prompts.filterIndexed { i, _ -> i != index }.map { it.q }.toSet()
        val next = (1..PROMPT_COUNT).map { (current.q + it) % PROMPT_COUNT }.firstOrNull { it !in used } ?: return this
        return copy(prompts = prompts.mapIndexed { i, p -> if (i == index) p.copy(q = next) else p })
    }

    // What goes into the discovery card besides the basics. Hidden fields and empty answers are left out.
    fun cardExtras(): CardExtras {
        fun shown(field: ProfileField) = field.key !in hidden
        fun code(e: Enum<*>?, field: ProfileField) = e?.takeIf { shown(field) }?.name?.lowercase()
        return CardExtras(
            pronouns = if (shown(ProfileField.Pronouns)) pronouns.trim() else "",
            school = if (shown(ProfileField.School)) school.trim() else "",
            height = if (shown(ProfileField.Height)) heightCm else null,
            drinking = code(drinking, ProfileField.Drinking),
            smoking = code(smoking, ProfileField.Smoking),
            exercise = code(exercise, ProfileField.Exercise),
            orientation = code(orientation, ProfileField.Orientation),
            family = code(family, ProfileField.Family),
            zodiac = code(zodiac, ProfileField.Zodiac),
            diet = code(diet, ProfileField.Diet),
            pets = if (shown(ProfileField.Pets)) pets.trim() else "",
            link = if (shown(ProfileField.Link)) link.trim() else "",
            prompts = if (shown(ProfileField.Prompts)) prompts.filter { it.a.isNotBlank() }.map { CardPrompt(it.q, it.a.trim()) } else emptyList(),
        )
    }

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
            orientation != null || family != null || zodiac != null || diet != null,
            pronouns.isNotBlank() || pets.isNotBlank() || link.isNotBlank(),
            prompts.any { it.a.isNotBlank() },
            photos.isNotEmpty(),
        ).count { it }
        return filled * 100 / 14
    }

    companion object {
        private const val P = "profile."

        private inline fun <reified T : Enum<T>> byCode(code: String?): T? = code?.let { c -> enumValues<T>().firstOrNull { it.name.lowercase() == c } }

        // A new phone: the profile is built again from the own card on the relays. Fields that were hidden are not in the card and stay empty.
        fun fromCard(card: com.kutlusoy.nearly.nostr.DiscoveryCard, base: MyProfile = MyProfile()): MyProfile {
            val x = card.extras
            return base.copy(
                about = card.about.take(ABOUT_MAX),
                gender = card.gender?.let { code -> com.kutlusoy.nearly.discovery.NearbyController.GENDER_CODE.entries.firstOrNull { it.value == code }?.key },
                lookingFor = byCode<LookingFor>(card.looking),
                interests = card.interests.take(INTERESTS_MAX),
                languages = card.languages.mapNotNull { com.kutlusoy.nearly.discovery.NearbyController.languageName(it) }.toSet(),
                job = card.job,
                school = x.school,
                city = card.city,
                heightCm = x.height,
                drinking = byCode<Frequency>(x.drinking),
                smoking = byCode<Frequency>(x.smoking),
                exercise = byCode<Frequency>(x.exercise),
                photos = card.photos.take(MAX_PHOTOS),
                pronouns = x.pronouns,
                orientation = byCode<Orientation>(x.orientation),
                family = byCode<FamilyPlans>(x.family),
                zodiac = byCode<Zodiac>(x.zodiac),
                diet = byCode<Diet>(x.diet),
                pets = x.pets,
                link = x.link,
                prompts = x.prompts.map { ProfilePrompt(it.q, it.a) },
            )
        }

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
            photos = parsePhotos(prefs.getString(P + "photos", "") ?: ""),
            pronouns = (prefs.getString(P + "pronouns", "") ?: "").take(PRONOUNS_MAX),
            orientation = enumOrNull<Orientation>(prefs, "orientation"),
            family = enumOrNull<FamilyPlans>(prefs, "family"),
            zodiac = enumOrNull<Zodiac>(prefs, "zodiac"),
            diet = enumOrNull<Diet>(prefs, "diet"),
            pets = (prefs.getString(P + "pets", "") ?: "").take(PETS_MAX),
            link = (prefs.getString(P + "link", "") ?: "").filter { it in LINK_CHARS }.take(LINK_MAX),
            prompts = parsePrompts(prefs.getString(P + "prompts", "") ?: ""),
            hidden = (prefs.getString(P + "hidden", "") ?: "").split(',').filter { key -> ProfileField.values().any { it.key == key } }.toSet(),
        )

        fun promptsJson(prompts: List<ProfilePrompt>): String = buildJsonArray {
            prompts.take(PROMPTS_MAX).forEach { p -> add(buildJsonObject { put("q", p.q); put("a", p.a) }) }
        }.toString()

        fun parsePrompts(text: String): List<ProfilePrompt> {
            if (text.isBlank()) return emptyList()
            val array = runCatching { Json.parseToJsonElement(text).jsonArray }.getOrNull() ?: return emptyList()
            return array.mapNotNull { element ->
                val o = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
                val q = (o["q"] as? JsonPrimitive)?.intOrNull?.takeIf { it in 0 until PROMPT_COUNT } ?: return@mapNotNull null
                val a = (o["a"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: return@mapNotNull null
                ProfilePrompt(q, a.take(PROMPT_ANSWER_MAX))
            }.distinctBy { it.q }.take(PROMPTS_MAX)
        }

        private val HEX64 = Regex("[0-9a-f]{64}")

        fun photosJson(photos: List<PhotoRef>): String = buildJsonArray {
            photos.take(MAX_PHOTOS).forEach { p ->
                add(
                    buildJsonObject {
                        put("url", p.url); put("mime", p.mime); put("sha256", p.sha256)
                        p.width?.let { put("w", it) }
                        p.height?.let { put("h", it) }
                        p.alt?.let { put("alt", it) }
                        p.blurhash?.let { put("blurhash", it) }
                    },
                )
            }
        }.toString()

        // What was saved is read back with the same care as foreign data
        fun parsePhotos(text: String): List<PhotoRef> {
            if (text.isBlank()) return emptyList()
            val array: JsonArray = runCatching { Json.parseToJsonElement(text).jsonArray }.getOrNull() ?: return emptyList()
            return array.mapNotNull { element ->
                val o: JsonObject = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
                fun str(name: String) = (o[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
                val url = str("url")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
                val mime = str("mime")?.takeIf { it.startsWith("image/") } ?: return@mapNotNull null
                val hash = str("sha256")?.lowercase()?.takeIf { HEX64.matches(it) } ?: return@mapNotNull null
                PhotoRef(url, mime, hash, (o["w"] as? JsonPrimitive)?.intOrNull, (o["h"] as? JsonPrimitive)?.intOrNull, str("alt"), str("blurhash")?.takeIf { BlurHash.isValid(it) })
            }.take(MAX_PHOTOS)
        }

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
                .putString(P + "photos", photosJson(p.photos))
                .putString(P + "pronouns", p.pronouns)
                .putString(P + "orientation", p.orientation?.name ?: "")
                .putString(P + "family", p.family?.name ?: "")
                .putString(P + "zodiac", p.zodiac?.name ?: "")
                .putString(P + "diet", p.diet?.name ?: "")
                .putString(P + "pets", p.pets)
                .putString(P + "link", p.link)
                .putString(P + "prompts", promptsJson(p.prompts))
                .putString(P + "hidden", p.hidden.joinToString(","))
                .apply()
        }
    }
}
