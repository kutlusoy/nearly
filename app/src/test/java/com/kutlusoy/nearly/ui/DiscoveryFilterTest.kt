package com.kutlusoy.nearly.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiscoveryFilterTest {
    private fun ids(f: DiscoveryFilter) = f.deck(SamplePeople).map { it.id }

    @Test
    fun theDefaultShowsEveryoneNearestFirstAndTheFarOnesAtTheEnd() {
        assertEquals(listOf("mia", "jonas", "lea", "omar", "sofia", "nico", "alex", "ben", "elif", "marta"), ids(DiscoveryFilter()))
        assertFalse(DiscoveryFilter().matches(SamplePeople.first { it.id == "elif" }))
    }

    @Test
    fun theGenderFilterAlwaysKeepsOneChoice() {
        var f = DiscoveryFilter().withShowMe(Gender.Man).withShowMe(Gender.NonBinary)
        assertEquals(setOf(Gender.Woman), f.showMe)
        assertEquals(f, f.withShowMe(Gender.Woman))
        assertTrue(ids(f).all { id -> SamplePeople.first { it.id == id }.gender == Gender.Woman })
        f = f.withShowMe(Gender.Man)
        assertEquals(setOf(Gender.Woman, Gender.Man), f.showMe)
    }

    @Test
    fun whatSomeoneIsLookingForIsAFilterAndNothingSelectedMeansAnything() {
        assertEquals(10, ids(DiscoveryFilter()).size)
        val serious = DiscoveryFilter(furtherAway = false, slightlyOutsideAge = false).withLookingFor(LookingFor.Serious)
        assertEquals(listOf("mia", "nico"), ids(serious))
        val both = serious.withLookingFor(LookingFor.Fun)
        assertEquals(listOf("mia", "sofia", "nico"), ids(both))
        assertEquals(listOf("mia", "nico"), ids(both.withLookingFor(LookingFor.Fun)))
    }

    @Test
    fun distanceAndAgeLimitsAreStrictWhenTheStretchIsOff() {
        val strict = DiscoveryFilter(furtherAway = false, slightlyOutsideAge = false)
        assertEquals(listOf("mia", "jonas"), ids(strict.withDistance(2)))
        assertEquals(listOf("lea", "sofia"), ids(strict.withAge(18, 26)))
    }

    @Test
    fun peopleJustOutsideTheLimitsComeAfterTheOnesThatFit() {
        val f = DiscoveryFilter(maxDistanceKm = 3, ageMin = 18, ageMax = 28)
        assertEquals(listOf("mia", "lea", "jonas", "omar", "sofia", "nico"), ids(f))
    }

    @Test
    fun theStretchOnlyReachesTwiceTheDistanceAndFiveYearsOfAge() {
        val order = ids(DiscoveryFilter(maxDistanceKm = 20, ageMin = 25, ageMax = 29))
        assertTrue("jonas" in order && "nico" in order)
        assertFalse("elif" in order, "35 years is more than 5 years above 29")
        assertFalse("marta" in order, "45 km is more than twice 20 km")
        assertFalse("ben" in order)
    }

    @Test
    fun onlyRecentlyActivePeopleCanBeRequested() {
        val order = ids(DiscoveryFilter(recentlyActiveOnly = true))
        assertTrue("mia" in order && "elif" in order)
        assertFalse("nico" in order || "ben" in order)
    }

    @Test
    fun theAgeRangeNeverGoesBelowEighteenOrCrossesItself() {
        assertEquals(18 to 18, DiscoveryFilter().withAge(10, 12).let { it.ageMin to it.ageMax })
        assertEquals(40 to 40, DiscoveryFilter().withAge(40, 30).let { it.ageMin to it.ageMax })
        assertEquals(160, DiscoveryFilter().withDistance(1000).maxDistanceKm)
        assertEquals(1, DiscoveryFilter().withDistance(0).maxDistanceKm)
    }
}
