package com.kutlusoy.nearly.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import com.kutlusoy.nearly.nostr.ImageSanitizer
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class ProcessedPhoto(val jpeg: ByteArray, val width: Int, val height: Int, val thumbnail: ByteArray, val blurhash: String)

// Turns a photo of the camera or the gallery into what Nearly uploads: upright, scaled down, as JPEG, without any metadata,
// plus a small thumbnail and a BlurHash. The photo is opened several times, so the caller passes a function that opens it.
// Public photos stay small on purpose: normal resolution and a size budget. High resolution photos are for the chat only.
class PhotoProcessor(
    private val maxSide: Int = MAX_SIDE,
    private val thumbnailSide: Int = 320,
    private val quality: Int = 72,
    private val maxBytes: Int = MAX_BYTES,
) {
    fun process(open: () -> InputStream): ProcessedPhoto {
        val orientation = open().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("this is not a photo")
        // Decode at a smaller size when the photo is large, a 50 megapixel photo would not fit into memory otherwise
        val options = BitmapFactory.Options().apply { inSampleSize = PhotoGeometry.sampleSize(bounds.outWidth, bounds.outHeight, maxSide) }
        val decoded = open().use { BitmapFactory.decodeStream(it, null, options) } ?: throw IOException("the photo could not be read")
        val upright = upright(decoded, PhotoGeometry.transformFor(orientation))
        val (photo, jpeg) = withinBudget(flatten(scaled(upright, maxSide)))
        val thumbnail = scaled(photo, thumbnailSide)
        val hashSource = scaled(photo, 32)
        val pixels = IntArray(hashSource.width * hashSource.height).also { hashSource.getPixels(it, 0, hashSource.width, 0, 0, hashSource.width, hashSource.height) }
        return ProcessedPhoto(
            jpeg = guard(jpeg),
            width = photo.width,
            height = photo.height,
            thumbnail = guard(compress(thumbnail, 75)),
            blurhash = BlurHash.encode(pixels, hashSource.width, hashSource.height),
        )
    }

    // Lowers the quality, and then the size, until the file fits into the budget
    private fun withinBudget(start: Bitmap): Pair<Bitmap, ByteArray> {
        var bitmap = start
        var q = quality
        while (true) {
            val bytes = compress(bitmap, q)
            if (bytes.size <= maxBytes) return bitmap to bytes
            if (q > MIN_QUALITY) {
                q = (q - 8).coerceAtLeast(MIN_QUALITY)
            } else {
                val side = (maxOf(bitmap.width, bitmap.height) * 0.85f).toInt()
                if (side < MIN_SIDE) throw IOException("the photo cannot be made small enough")
                bitmap = scaled(bitmap, side)
            }
        }
    }

    private fun upright(source: Bitmap, t: Transform): Bitmap {
        if (t.rotationDegrees == 0 && !t.flipHorizontal) return source
        val matrix = Matrix()
        if (t.rotationDegrees != 0) matrix.postRotate(t.rotationDegrees.toFloat())
        if (t.flipHorizontal) matrix.postScale(-1f, 1f)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun scaled(source: Bitmap, side: Int): Bitmap {
        val size = PhotoGeometry.fit(source.width, source.height, side)
        return if (size.width == source.width && size.height == source.height) source else Bitmap.createScaledBitmap(source, size.width, size.height, true)
    }

    // JPEG has no transparency, a transparent area becomes white
    private fun flatten(source: Bitmap): Bitmap {
        if (!source.hasAlpha()) return source
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawColor(Color.WHITE); drawBitmap(source, 0f, 0f, null) }
        return out
    }

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) throw IOException("the photo could not be encoded")
        return out.toByteArray()
    }

    // The result of this class must be clean and upright. If it is not, nothing is uploaded.
    private fun guard(jpeg: ByteArray): ByteArray {
        val info = ImageSanitizer.inspect(jpeg) ?: throw IOException("the encoder produced an unusable file")
        if (info.needsRotation) throw IOException("the encoder kept an orientation")
        return if (ImageSanitizer.hasMetadata(jpeg)) ImageSanitizer.strip(jpeg) else jpeg
    }

    companion object {
        // Normal resolution and a small file: about 1080 pixels on the long side and at most 150 KB
        const val MAX_SIDE = 1080
        const val MAX_BYTES = 150_000
        private const val MIN_QUALITY = 40
        private const val MIN_SIDE = 400
    }
}
