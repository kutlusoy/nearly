package com.kutlusoy.nearly.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import com.kutlusoy.nearly.nostr.ImageSanitizer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PhotoProcessorTest {
    private fun twoColors(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) for (x in 0 until width) bitmap.setPixel(x, y, if (x < width / 2) Color.RED else Color.BLUE)
        return bitmap
    }

    // A camera like file: JPEG with EXIF orientation and a GPS position
    private fun cameraFile(width: Int, height: Int, orientation: Int): ByteArray {
        val file = File.createTempFile("photo", ".jpg")
        file.outputStream().use { twoColors(width, height).compress(Bitmap.CompressFormat.JPEG, 95, it) }
        val exif = ExifInterface(file.absolutePath)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, "48/1,8/1,0/1")
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
        exif.setAttribute(ExifInterface.TAG_MAKE, "SecretPhoneMaker")
        exif.saveAttributes()
        return file.readBytes().also { file.delete() }
    }

    private fun decode(bytes: ByteArray): Bitmap = assertNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))

    private fun isRed(p: Int) = Color.red(p) > 150 && Color.blue(p) < 100

    private fun isBlue(p: Int) = Color.blue(p) > 150 && Color.red(p) < 100

    @Test
    fun theCameraFileHasPrivateMetadataAndTheResultHasNone() {
        val raw = cameraFile(200, 100, 1)
        assertTrue(String(raw, Charsets.ISO_8859_1).contains("SecretPhoneMaker"))
        val result = PhotoProcessor().process { ByteArrayInputStream(raw) }
        val text = String(result.jpeg, Charsets.ISO_8859_1)
        assertFalse(text.contains("SecretPhoneMaker"))
        assertFalse(ImageSanitizer.hasMetadata(result.jpeg))
        assertEquals(ImageSanitizer.inspect(result.jpeg)!!.mime, "image/jpeg")
        assertEquals(200, result.width)
        assertEquals(100, result.height)
    }

    @Test
    fun orientationSixTurnsTheLandscapeIntoAnUprightPortrait() {
        val result = PhotoProcessor().process { ByteArrayInputStream(cameraFile(200, 100, 6)) }
        assertEquals(100, result.width)
        assertEquals(200, result.height)
        val bitmap = decode(result.jpeg)
        assertEquals(100, bitmap.width)
        assertTrue(isRed(bitmap.getPixel(50, 20)), "the red left half of the stored photo is on top after turning 90 degrees clockwise")
        assertTrue(isBlue(bitmap.getPixel(50, 180)))
        assertFalse(ImageSanitizer.inspect(result.jpeg)!!.needsRotation)
    }

    @Test
    fun orientationEightAndTwoAndThree() {
        val eight = decode(PhotoProcessor().process { ByteArrayInputStream(cameraFile(200, 100, 8)) }.jpeg)
        assertTrue(isBlue(eight.getPixel(50, 20)))
        assertTrue(isRed(eight.getPixel(50, 180)))
        val two = decode(PhotoProcessor().process { ByteArrayInputStream(cameraFile(200, 100, 2)) }.jpeg)
        assertTrue(isBlue(two.getPixel(20, 50)))
        assertTrue(isRed(two.getPixel(180, 50)))
        val three = decode(PhotoProcessor().process { ByteArrayInputStream(cameraFile(200, 100, 3)) }.jpeg)
        assertTrue(isBlue(three.getPixel(20, 50)))
        assertTrue(isRed(three.getPixel(180, 50)))
    }

    @Test
    fun largePhotosAreScaledDownAndGetAThumbnailAndABlurHash() {
        val result = PhotoProcessor(maxSide = 400, thumbnailSide = 100).process { ByteArrayInputStream(cameraFile(1600, 800, 1)) }
        assertEquals(400, result.width)
        assertEquals(200, result.height)
        val thumbnail = decode(result.thumbnail)
        assertEquals(100, thumbnail.width)
        assertEquals(50, thumbnail.height)
        assertTrue(BlurHash.isValid(result.blurhash))
        assertTrue(result.jpeg.size < 400 * 200 * 4)
    }

    @Test
    fun transparentPngBecomesAJpegWithAWhiteBackground() {
        val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val result = PhotoProcessor().process { ByteArrayInputStream(png) }
        val out = decode(result.jpeg)
        assertTrue(Color.red(out.getPixel(20, 20)) > 240 && Color.green(out.getPixel(20, 20)) > 240)
    }

    @Test
    fun somethingThatIsNotAPhotoIsRefused() {
        assertFailsWith<java.io.IOException> { PhotoProcessor().process { ByteArrayInputStream("not a photo".toByteArray()) } }
    }
}
