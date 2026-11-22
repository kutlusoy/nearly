package com.kutlusoy.nearly.media

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign

// BlurHash: a short text (about 30 characters) that stands for a blurred version of a photo. It is shown while the real photo loads.
object BlurHash {
    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"

    fun isValid(hash: String): Boolean {
        if (hash.length < 6 || hash.length > 100 || hash.any { ALPHABET.indexOf(it) < 0 }) return false
        val size = ALPHABET.indexOf(hash[0])
        val cx = size % 9 + 1
        val cy = size / 9 + 1
        return hash.length == 4 + 2 * cx * cy
    }

    // pixels are ARGB as Android gives them, the photo should be small (32 pixels on the long side are enough)
    fun encode(pixels: IntArray, width: Int, height: Int, componentsX: Int = 4, componentsY: Int = 3): String {
        require(componentsX in 1..9 && componentsY in 1..9 && pixels.size == width * height && width > 0 && height > 0) { "invalid input" }
        val factors = ArrayList<DoubleArray>()
        for (j in 0 until componentsY) for (i in 0 until componentsX) {
            val norm = if (i == 0 && j == 0) 1.0 else 2.0
            var r = 0.0; var g = 0.0; var b = 0.0
            for (y in 0 until height) for (x in 0 until width) {
                val basis = norm * cos(PI * i * x / width) * cos(PI * j * y / height)
                val p = pixels[y * width + x]
                r += basis * toLinear((p shr 16) and 0xff)
                g += basis * toLinear((p shr 8) and 0xff)
                b += basis * toLinear(p and 0xff)
            }
            val scale = 1.0 / (width * height)
            factors.add(doubleArrayOf(r * scale, g * scale, b * scale))
        }
        val dc = factors[0]
        val ac = factors.drop(1)
        val out = StringBuilder()
        out.append(base83((componentsX - 1) + (componentsY - 1) * 9, 1))
        val maximum: Double
        if (ac.isNotEmpty()) {
            val actual = ac.maxOf { f -> f.maxOf { abs(it) } }
            val quantised = max(0, min(82, floor(actual * 166 - 0.5).toInt()))
            maximum = (quantised + 1) / 166.0
            out.append(base83(quantised, 1))
        } else {
            maximum = 1.0
            out.append(base83(0, 1))
        }
        out.append(base83((toSrgb(dc[0]) shl 16) + (toSrgb(dc[1]) shl 8) + toSrgb(dc[2]), 4))
        for (f in ac) out.append(base83(quantiseAc(f[0], maximum) * 361 + quantiseAc(f[1], maximum) * 19 + quantiseAc(f[2], maximum), 2))
        return out.toString()
    }

    // For tests and for drawing a placeholder: gives ARGB pixels
    fun decode(hash: String, width: Int, height: Int): IntArray {
        require(isValid(hash)) { "invalid blurhash" }
        val size = ALPHABET.indexOf(hash[0])
        val cx = size % 9 + 1
        val cy = size / 9 + 1
        val maximum = (ALPHABET.indexOf(hash[1]) + 1) / 166.0
        val colors = ArrayList<DoubleArray>()
        val dcValue = decode83(hash.substring(2, 6))
        colors.add(doubleArrayOf(toLinear(dcValue shr 16), toLinear((dcValue shr 8) and 0xff), toLinear(dcValue and 0xff)))
        for (i in 1 until cx * cy) {
            val v = decode83(hash.substring(4 + i * 2, 6 + i * 2))
            colors.add(doubleArrayOf(dequantiseAc(v / 361, maximum), dequantiseAc((v / 19) % 19, maximum), dequantiseAc(v % 19, maximum)))
        }
        val pixels = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            var r = 0.0; var g = 0.0; var b = 0.0
            for (j in 0 until cy) for (i in 0 until cx) {
                val basis = cos(PI * i * x / width) * cos(PI * j * y / height)
                val c = colors[j * cx + i]
                r += c[0] * basis; g += c[1] * basis; b += c[2] * basis
            }
            pixels[y * width + x] = (0xff shl 24) or (toSrgb(r) shl 16) or (toSrgb(g) shl 8) or toSrgb(b)
        }
        return pixels
    }

    private fun toLinear(value: Int): Double {
        val v = value / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun toSrgb(value: Double): Int {
        val v = max(0.0, min(1.0, value))
        return if (v <= 0.0031308) (v * 12.92 * 255 + 0.5).toInt() else ((1.055 * v.pow(1 / 2.4) - 0.055) * 255 + 0.5).toInt()
    }

    private fun quantiseAc(value: Double, maximum: Double): Int =
        max(0, min(18, floor(sign(value / maximum) * abs(value / maximum).pow(0.5) * 9 + 9.5).toInt()))

    private fun dequantiseAc(value: Int, maximum: Double): Double {
        val v = (value - 9) / 9.0
        return sign(v) * v * v * maximum
    }

    private fun base83(value: Int, length: Int): String {
        val out = CharArray(length)
        var rest = value
        for (i in length - 1 downTo 0) {
            out[i] = ALPHABET[rest % 83]
            rest /= 83
        }
        return String(out)
    }

    private fun decode83(text: String): Int = text.fold(0) { acc, c -> acc * 83 + ALPHABET.indexOf(c) }
}
