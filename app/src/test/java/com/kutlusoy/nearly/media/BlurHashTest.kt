package com.kutlusoy.nearly.media

import com.kutlusoy.nearly.nostr.PhotoRef
import com.kutlusoy.nearly.nostr.Post
import com.kutlusoy.nearly.nostr.PostCodec
import com.kutlusoy.nearly.nostr.testSession
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlurHashTest {
    private fun solid(rgb: Int, w: Int = 16, h: Int = 16) = IntArray(w * h) { (0xff shl 24) or rgb }

    @Test
    fun hashHasTheSpecifiedLengthAndIsValid() {
        val hash = BlurHash.encode(solid(0xff0000), 16, 16)
        assertEquals(4 + 2 * 4 * 3, hash.length)
        assertTrue(BlurHash.isValid(hash))
        assertEquals(4 + 2, BlurHash.encode(solid(0x00ff00), 16, 16, 1, 1).length)
    }

    // The method is approximate by design: a solid color comes back with a slight gradient, so the average and the main color are checked
    @Test
    fun solidColorKeepsItsAverageAndItsMainColor() {
        for (color in listOf(0xff0000, 0x2060c0, 0xf0f0f0, 0x101010)) {
            val decoded = BlurHash.decode(BlurHash.encode(solid(color), 16, 16), 8, 8)
            val average = intArrayOf(
                decoded.map { (it shr 16) and 0xff }.average().toInt(),
                decoded.map { (it shr 8) and 0xff }.average().toInt(),
                decoded.map { it and 0xff }.average().toInt(),
            )
            val expected = intArrayOf((color shr 16) and 0xff, (color shr 8) and 0xff, color and 0xff)
            for (i in 0..2) assertTrue(abs(average[i] - expected[i]) <= 30, "color %06x channel %d average %d".format(color, i, average[i]))
            assertEquals(expected.indices.maxByOrNull { expected[it] }, average.indices.maxByOrNull { average[it] })
        }
    }

    @Test
    fun leftRedRightBlueKeepsTheirSides() {
        val w = 32
        val h = 16
        val pixels = IntArray(w * h) { i -> (0xff shl 24) or (if (i % w < w / 2) 0xff0000 else 0x0000ff) }
        val decoded = BlurHash.decode(BlurHash.encode(pixels, w, h), 32, 16)
        val left = decoded[8 * 32 + 3]
        val right = decoded[8 * 32 + 28]
        assertTrue(((left shr 16) and 0xff) > (left and 0xff) + 60)
        assertTrue((right and 0xff) > ((right shr 16) and 0xff) + 60)
    }

    @Test
    fun invalidHashesAreRejected() {
        assertFalse(BlurHash.isValid(""))
        assertFalse(BlurHash.isValid("LEHV6nWB2yk8pyo0adR*.7kCMdnj!"))
        assertFalse(BlurHash.isValid("LEHV6nWB2yk8pyo0adR*.7kCMdn"))
        assertTrue(BlurHash.isValid("LEHV6nWB2yk8pyo0adR*.7kCMdnj"))
        assertFailsWith<IllegalArgumentException> { BlurHash.decode("nope", 4, 4) }
        assertFailsWith<IllegalArgumentException> { BlurHash.encode(IntArray(3), 2, 2) }
    }

    @Test
    fun photoPostCarriesOnlyAValidBlurHash() {
        val session = testSession("anna")
        val hash = BlurHash.encode(solid(0x336699), 16, 16)
        val ref = PhotoRef("https://x.example/a.jpg", "image/jpeg", "ab".repeat(32), 10, 10, null, hash)
        val event = PostCodec.photo(session, "caption", listOf(ref))
        assertEquals(hash, (PostCodec.parse(event) as Post.Photos).photos.first().blurhash)
        val bad = PostCodec.photo(session, "caption", listOf(ref.copy(blurhash = "not valid!")))
        assertNull((PostCodec.parse(bad) as Post.Photos).photos.first().blurhash)
    }
}
