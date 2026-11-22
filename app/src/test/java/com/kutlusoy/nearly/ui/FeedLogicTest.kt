package com.kutlusoy.nearly.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedLogicTest {
    @Test
    fun theFeedShowsPeopleNearbyAndTheOnesYouFollowNoMatterTheDistance() {
        val none = feedPosts(SamplePosts, emptySet()).map { it.person.id }
        assertTrue("mia" in none && "marta" in none)
        assertFalse("paul" in none, "120 km is not nearby")
        val following = feedPosts(SamplePosts, setOf("paul")).map { it.person.id }
        assertTrue("paul" in following)
    }

    @Test
    fun theNewestPostComesFirst() {
        val minutes = feedPosts(SamplePosts, setOf("paul")).map { it.minutesAgo }
        assertEquals(minutes.sorted(), minutes)
    }

    @Test
    fun nearbyPeopleAreSuggestedUntilYouFollowThemNearestFirst() {
        val suggested = nearbyPeople(SamplePeople, emptySet()).map { it.id }
        assertEquals("mia", suggested.first())
        assertFalse("paul" in suggested)
        assertFalse("mia" in nearbyPeople(SamplePeople, setOf("mia")).map { it.id })
        assertEquals(suggested.size - 1, nearbyPeople(SamplePeople, setOf("mia")).size)
    }

    @Test
    fun theFeedIgnoresTheDiscoverySettings() {
        val strictMen = DiscoveryFilter(showMe = setOf(Gender.Man), maxDistanceKm = 1, furtherAway = false)
        assertTrue(strictMen.deck(SamplePeople).size < nearbyPeople(SamplePeople, emptySet()).size)
    }
}

class MyProfileTest {
    @Test
    fun interestsAreTrimmedUniqueLimitedAndToggle() {
        var p = MyProfile().withInterest("  Climbing  ").withInterest("climbing").withInterest("  ")
        assertEquals(listOf("Climbing"), p.interests)
        p = p.toggleInterest("Climbing")
        assertEquals(emptyList(), p.interests)
        repeat(15) { p = p.withInterest("Hobby $it") }
        assertEquals(INTERESTS_MAX, p.interests.size)
        assertEquals(INTEREST_LENGTH, MyProfile().withInterest("x".repeat(50)).interests.single().length)
    }

    @Test
    fun aboutAndHeightAreLimited() {
        assertEquals(ABOUT_MAX, MyProfile().withAbout("a".repeat(900)).about.length)
        assertEquals(178, MyProfile().withHeight("178").heightCm)
        assertEquals(null, MyProfile().withHeight("50").heightCm)
        assertEquals(null, MyProfile().withHeight("").heightCm)
        assertEquals(180, MyProfile().withHeight("18x0").heightCm)
    }

    @Test
    fun theCompletenessGrowsWithTheFields() {
        assertEquals(0, MyProfile().completeness())
        val p = MyProfile(about = "Hi", gender = Gender.Woman, interests = listOf("Tea"), languages = setOf("English"))
        assertEquals(40, p.completeness())
        val all = p.copy(lookingFor = LookingFor.Serious, job = "x", school = "y", city = "z", heightCm = 170, drinking = Frequency.Never)
        assertEquals(100, all.completeness())
    }
}
