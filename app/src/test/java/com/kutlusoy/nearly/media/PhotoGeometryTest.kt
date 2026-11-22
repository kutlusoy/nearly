package com.kutlusoy.nearly.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhotoGeometryTest {
    @Test
    fun fitKeepsProportionsAndNeverEnlarges() {
        assertEquals(Size(1600, 1200), PhotoGeometry.fit(4000, 3000, 1600))
        assertEquals(Size(900, 1600), PhotoGeometry.fit(1800, 3200, 1600))
        assertEquals(Size(800, 600), PhotoGeometry.fit(800, 600, 1600))
        assertEquals(Size(1, 1600), PhotoGeometry.fit(1, 100_000, 1600))
        assertFailsWith<IllegalArgumentException> { PhotoGeometry.fit(0, 10, 10) }
    }

    @Test
    fun sampleSizeStaysAtLeastAsLargeAsTheTarget() {
        assertEquals(1, PhotoGeometry.sampleSize(1600, 1200, 1600))
        assertEquals(1, PhotoGeometry.sampleSize(3199, 2000, 1600))
        assertEquals(2, PhotoGeometry.sampleSize(3200, 2400, 1600))
        assertEquals(4, PhotoGeometry.sampleSize(8000, 6000, 1600))
        for (long in listOf(1700, 3300, 6500, 12_000)) assertTrue(long / PhotoGeometry.sampleSize(long, long / 2, 1600) >= 1600)
    }

    // Where the corners of the stored photo land, taken from the table of the EXIF standard
    @Test
    fun everyOrientationPutsTheCornersWhereTheStandardSays() {
        val w = 4
        val h = 2
        // top left, top right, bottom left, bottom right of the stored photo, as (x, y) in the upright photo
        val expected = mapOf(
            1 to listOf(0 to 0, 3 to 0, 0 to 1, 3 to 1),
            2 to listOf(3 to 0, 0 to 0, 3 to 1, 0 to 1),
            3 to listOf(3 to 1, 0 to 1, 3 to 0, 0 to 0),
            4 to listOf(0 to 1, 3 to 1, 0 to 0, 3 to 0),
            5 to listOf(0 to 0, 0 to 3, 1 to 0, 1 to 3),
            6 to listOf(1 to 0, 1 to 3, 0 to 0, 0 to 3),
            7 to listOf(1 to 3, 1 to 0, 0 to 3, 0 to 0),
            8 to listOf(0 to 3, 0 to 0, 1 to 3, 1 to 0),
        )
        for ((orientation, points) in expected) {
            val t = PhotoGeometry.transformFor(orientation)
            val corners = listOf(0 to 0, w - 1 to 0, 0 to h - 1, w - 1 to h - 1)
            assertEquals(points, corners.map { (x, y) -> PhotoGeometry.mapPoint(x, y, w, h, t) }, "orientation $orientation")
        }
    }

    @Test
    fun unknownOrientationCountsAsNormalAndAxesSwapOnlyForQuarterTurns() {
        assertEquals(Transform(0, false), PhotoGeometry.transformFor(0))
        assertEquals(Transform(0, false), PhotoGeometry.transformFor(99))
        assertTrue(PhotoGeometry.transformFor(6).swapsAxes)
        assertTrue(PhotoGeometry.transformFor(5).swapsAxes)
        assertFalse(PhotoGeometry.transformFor(3).swapsAxes)
    }
}
