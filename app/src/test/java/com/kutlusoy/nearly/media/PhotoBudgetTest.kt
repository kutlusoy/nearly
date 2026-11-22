package com.kutlusoy.nearly.media

import android.graphics.Bitmap
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PhotoBudgetTest {
    // Random pixels do not compress, so this is the worst case for the size budget
    private fun noisy(width: Int, height: Int): ByteArray {
        val random = Random(7)
        val pixels = IntArray(width * height) { 0xFF000000.toInt() or random.nextInt(0xFFFFFF) }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    @Test
    fun aLargePhotoBecomesNormalResolutionAndStaysInsideTheBudget() {
        val result = PhotoProcessor().process { ByteArrayInputStream(noisy(2400, 1600)) }
        assertTrue(maxOf(result.width, result.height) <= PhotoProcessor.MAX_SIDE, "${result.width}x${result.height}")
        assertTrue(result.jpeg.size <= PhotoProcessor.MAX_BYTES, "${result.jpeg.size} bytes")
    }

    @Test
    fun aSmallerBudgetShrinksThePhotoFurther() {
        val loose = PhotoProcessor(maxBytes = 400_000).process { ByteArrayInputStream(noisy(1200, 800)) }
        val tight = PhotoProcessor(maxBytes = 40_000).process { ByteArrayInputStream(noisy(1200, 800)) }
        assertTrue(tight.jpeg.size <= 40_000, "${tight.jpeg.size}")
        assertTrue(tight.jpeg.size < loose.jpeg.size)
        assertEquals(tight.width.toDouble() / tight.height, 1.5, 0.05)
    }

    @Test
    fun aPhotoThatCannotBeMadeSmallEnoughIsRefused() {
        assertFailsWith<IOException> { PhotoProcessor(maxBytes = 500).process { ByteArrayInputStream(noisy(900, 600)) } }
    }
}
