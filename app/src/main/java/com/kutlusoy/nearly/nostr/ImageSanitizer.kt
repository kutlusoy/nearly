package com.kutlusoy.nearly.nostr

import java.io.ByteArrayOutputStream

data class ImageInfo(val mime: String, val width: Int, val height: Int, val needsRotation: Boolean)

// Checks and cleans photo files before they are uploaded. The Android layer decodes, rotates and re-encodes a photo first,
// this is the last guard: it finds the real type by the first bytes, reads the size, and removes metadata (EXIF, GPS, comments, XMP).
object ImageSanitizer {
    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val PNG_KEEP = setOf("IHDR", "PLTE", "IDAT", "IEND", "tRNS", "gAMA", "cHRM", "sRGB", "iCCP")

    fun inspect(bytes: ByteArray): ImageInfo? = when {
        isJpeg(bytes) -> inspectJpeg(bytes)
        isPng(bytes) -> inspectPng(bytes)
        else -> null
    }

    // The same file without metadata. The picture data is not touched, so there is no loss of quality.
    fun strip(bytes: ByteArray): ByteArray = when {
        isJpeg(bytes) -> stripJpeg(bytes)
        isPng(bytes) -> stripPng(bytes)
        else -> throw IllegalArgumentException("unsupported image format")
    }

    fun hasMetadata(bytes: ByteArray): Boolean = !strip(bytes).contentEquals(bytes)

    private fun isJpeg(b: ByteArray) = b.size > 4 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte()

    private fun isPng(b: ByteArray) = b.size > 8 && PNG_SIGNATURE.indices.all { b[it] == PNG_SIGNATURE[it] }

    private fun u16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xff) shl 8) or (b[o + 1].toInt() and 0xff)

    private fun u32(b: ByteArray, o: Int) = (u16(b, o).toLong() shl 16 or u16(b, o + 2).toLong()).toInt()

    private class Segment(val marker: Int, val start: Int, val end: Int)

    // Segments up to the start of the scan. After the scan marker the rest of the file is picture data.
    private fun jpegSegments(b: ByteArray): Pair<List<Segment>, Int> {
        val segments = mutableListOf<Segment>()
        var pos = 2
        while (pos + 4 <= b.size) {
            if (b[pos] != 0xFF.toByte()) throw IllegalArgumentException("damaged jpeg")
            val marker = b[pos + 1].toInt() and 0xff
            if (marker == 0xFF) { pos++; continue }
            if (marker == 0xDA) return segments to pos
            if (marker == 0xD9) return segments to pos
            val length = u16(b, pos + 2)
            if (length < 2 || pos + 2 + length > b.size) throw IllegalArgumentException("damaged jpeg")
            segments.add(Segment(marker, pos, pos + 2 + length))
            pos += 2 + length
        }
        throw IllegalArgumentException("damaged jpeg")
    }

    private fun startsWith(b: ByteArray, at: Int, text: String) =
        at + text.length <= b.size && text.indices.all { b[at + it] == text[it].code.toByte() }

    private fun keepJpegSegment(b: ByteArray, s: Segment): Boolean = when (s.marker) {
        0xE0 -> startsWith(b, s.start + 4, "JFIF\u0000")
        0xE2 -> startsWith(b, s.start + 4, "ICC_PROFILE\u0000")
        0xEE -> startsWith(b, s.start + 4, "Adobe")
        in 0xE1..0xEF, 0xFE -> false
        else -> true
    }

    private fun stripJpeg(b: ByteArray): ByteArray {
        val (segments, scanAt) = jpegSegments(b)
        val out = ByteArrayOutputStream(b.size)
        out.write(b, 0, 2)
        for (s in segments) if (keepJpegSegment(b, s)) out.write(b, s.start, s.end - s.start)
        out.write(b, scanAt, b.size - scanAt)
        return out.toByteArray()
    }

    private fun inspectJpeg(b: ByteArray): ImageInfo? = try {
        val (segments, _) = jpegSegments(b)
        val sof = segments.firstOrNull { it.marker in 0xC0..0xCF && it.marker != 0xC4 && it.marker != 0xC8 && it.marker != 0xCC } ?: return null
        val height = u16(b, sof.start + 5)
        val width = u16(b, sof.start + 7)
        val orientation = segments.firstOrNull { it.marker == 0xE1 && startsWith(b, it.start + 4, "Exif\u0000\u0000") }?.let { exifOrientation(b, it) } ?: 1
        if (width <= 0 || height <= 0) null else ImageInfo("image/jpeg", width, height, orientation != 1)
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun exifOrientation(b: ByteArray, s: Segment): Int {
        val tiff = s.start + 10
        if (tiff + 8 > s.end) return 1
        val little = b[tiff] == 'I'.code.toByte()
        fun r16(o: Int) = if (little) (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8) else u16(b, o)
        fun r32(o: Int) = if (little) r16(o) or (r16(o + 2) shl 16) else (u16(b, o) shl 16) or u16(b, o + 2)
        val ifd = tiff + r32(tiff + 4)
        if (ifd < tiff || ifd + 2 > s.end) return 1
        val count = r16(ifd)
        for (i in 0 until count) {
            val entry = ifd + 2 + i * 12
            if (entry + 12 > s.end) break
            if (r16(entry) == 0x0112) return r16(entry + 8)
        }
        return 1
    }

    private fun pngChunks(b: ByteArray): List<Triple<String, Int, Int>> {
        val chunks = mutableListOf<Triple<String, Int, Int>>()
        var pos = 8
        while (pos + 12 <= b.size) {
            val length = u32(b, pos)
            if (length < 0 || pos + 12 + length > b.size) throw IllegalArgumentException("damaged png")
            chunks.add(Triple(String(b, pos + 4, 4, Charsets.ISO_8859_1), pos, pos + 12 + length))
            pos += 12 + length
        }
        return chunks
    }

    private fun stripPng(b: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(b.size)
        out.write(b, 0, 8)
        for ((type, start, end) in pngChunks(b)) if (type in PNG_KEEP) out.write(b, start, end - start)
        return out.toByteArray()
    }

    private fun inspectPng(b: ByteArray): ImageInfo? = try {
        val ihdr = pngChunks(b).firstOrNull { it.first == "IHDR" } ?: return null
        val width = u32(b, ihdr.second + 8)
        val height = u32(b, ihdr.second + 12)
        if (width <= 0 || height <= 0) null else ImageInfo("image/png", width, height, false)
    } catch (e: IllegalArgumentException) {
        null
    }
}
