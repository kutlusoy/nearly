package com.kutlusoy.nearly.ui

import android.content.SharedPreferences

enum class Gender { Woman, Man, NonBinary }

enum class LookingFor { Serious, Open, Casual, Fun, Friends, Unsure }

const val AGE_MIN = 18
const val AGE_MAX = 99
const val DISTANCE_MIN_KM = 1
const val DISTANCE_MAX_KM = 160

// Who appears in Swipe and on the Map. The Feed ignores it, it always shows the people you follow.
data class DiscoveryFilter(
    val showMe: Set<Gender> = Gender.values().toSet(),
    val lookingFor: Set<LookingFor> = emptySet(),
    val maxDistanceKm: Int = 25,
    val ageMin: Int = AGE_MIN,
    val ageMax: Int = 50,
    val furtherAway: Boolean = true,
    val slightlyOutsideAge: Boolean = true,
    val recentlyActiveOnly: Boolean = false,
    val visible: Boolean = true,
    val showDistance: Boolean = true,
) {
    private fun fits(p: Person, stretch: Boolean): Boolean {
        if (p.gender !in showMe) return false
        if (lookingFor.isNotEmpty() && p.lookingFor !in lookingFor) return false
        if (recentlyActiveOnly && p.activeDaysAgo > ACTIVE_DAYS) return false
        val distanceOk = p.distanceKm <= maxDistanceKm || (stretch && furtherAway && p.distanceKm <= maxDistanceKm * 2)
        val ageOk = p.age in ageMin..ageMax || (stretch && slightlyOutsideAge && p.age in (ageMin - AGE_SLACK)..(ageMax + AGE_SLACK))
        return distanceOk && ageOk
    }

    fun matches(p: Person): Boolean = fits(p, stretch = false)

    // The people that fit first, nearest first, then the ones just outside the limits if the person allows it
    fun deck(people: List<Person>): List<Person> {
        val strict = people.filter { fits(it, false) }.sortedBy { it.distanceKm }
        val stretched = people.filter { !fits(it, false) && fits(it, true) }.sortedBy { it.distanceKm }
        return strict + stretched
    }

    fun withShowMe(gender: Gender): DiscoveryFilter {
        val next = if (gender in showMe) showMe - gender else showMe + gender
        return if (next.isEmpty()) this else copy(showMe = next)
    }

    fun withLookingFor(goal: LookingFor): DiscoveryFilter =
        copy(lookingFor = if (goal in lookingFor) lookingFor - goal else lookingFor + goal)

    fun withAge(min: Int, max: Int): DiscoveryFilter {
        val low = min.coerceIn(AGE_MIN, AGE_MAX)
        return copy(ageMin = low, ageMax = max.coerceIn(low, AGE_MAX))
    }

    fun withDistance(km: Int): DiscoveryFilter = copy(maxDistanceKm = km.coerceIn(DISTANCE_MIN_KM, DISTANCE_MAX_KM))

    companion object {
        const val ACTIVE_DAYS = 7
        const val AGE_SLACK = 5

        private const val P = "discovery."

        fun load(prefs: SharedPreferences): DiscoveryFilter {
            val d = DiscoveryFilter()
            fun <T : Enum<T>> names(key: String, all: Array<T>, fallback: Set<T>): Set<T> {
                val raw = prefs.getString(P + key, null) ?: return fallback
                return raw.split(',').mapNotNull { n -> all.firstOrNull { it.name == n } }.toSet()
            }
            val showMe = names("showMe", Gender.values(), d.showMe).ifEmpty { d.showMe }
            return DiscoveryFilter(
                showMe = showMe,
                lookingFor = names("lookingFor", LookingFor.values(), d.lookingFor),
                maxDistanceKm = prefs.getInt(P + "distance", d.maxDistanceKm).coerceIn(DISTANCE_MIN_KM, DISTANCE_MAX_KM),
                ageMin = prefs.getInt(P + "ageMin", d.ageMin).coerceIn(AGE_MIN, AGE_MAX),
                ageMax = prefs.getInt(P + "ageMax", d.ageMax).coerceIn(AGE_MIN, AGE_MAX),
                furtherAway = prefs.getBoolean(P + "furtherAway", d.furtherAway),
                slightlyOutsideAge = prefs.getBoolean(P + "ageSlack", d.slightlyOutsideAge),
                recentlyActiveOnly = prefs.getBoolean(P + "recent", d.recentlyActiveOnly),
                visible = prefs.getBoolean(P + "visible", d.visible),
                showDistance = prefs.getBoolean(P + "showDistance", d.showDistance),
            ).let { it.withAge(it.ageMin, it.ageMax) }
        }

        fun save(prefs: SharedPreferences, f: DiscoveryFilter) {
            prefs.edit()
                .putString(P + "showMe", f.showMe.joinToString(",") { it.name })
                .putString(P + "lookingFor", f.lookingFor.joinToString(",") { it.name })
                .putInt(P + "distance", f.maxDistanceKm)
                .putInt(P + "ageMin", f.ageMin)
                .putInt(P + "ageMax", f.ageMax)
                .putBoolean(P + "furtherAway", f.furtherAway)
                .putBoolean(P + "ageSlack", f.slightlyOutsideAge)
                .putBoolean(P + "recent", f.recentlyActiveOnly)
                .putBoolean(P + "visible", f.visible)
                .putBoolean(P + "showDistance", f.showDistance)
                .apply()
        }
    }
}
