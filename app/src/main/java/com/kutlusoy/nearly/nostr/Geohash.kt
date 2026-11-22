package com.kutlusoy.nearly.nostr

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Geohash cells. The place of a person leaves the device only as such a cell, never as coordinates.
object Geohash {
    private const val ALPHABET = "0123456789bcdefghjkmnpqrstuvwxyz"

    fun isCell(text: String, length: Int? = null): Boolean =
        text.isNotEmpty() && text.length <= 12 && (length == null || text.length == length) && text.all { it in ALPHABET }

    fun encode(lat: Double, lon: Double, length: Int): String {
        var latLo = -90.0
        var latHi = 90.0
        var lonLo = -180.0
        var lonHi = 180.0
        val out = StringBuilder()
        var bits = 0
        var value = 0
        var evenBit = true
        while (out.length < length) {
            if (evenBit) {
                val mid = (lonLo + lonHi) / 2
                if (lon >= mid) { value = value * 2 + 1; lonLo = mid } else { value *= 2; lonHi = mid }
            } else {
                val mid = (latLo + latHi) / 2
                if (lat >= mid) { value = value * 2 + 1; latLo = mid } else { value *= 2; latHi = mid }
            }
            evenBit = !evenBit
            if (++bits == 5) {
                out.append(ALPHABET[value])
                bits = 0
                value = 0
            }
        }
        return out.toString()
    }

    data class Bounds(val latLo: Double, val latHi: Double, val lonLo: Double, val lonHi: Double) {
        val centerLat get() = (latLo + latHi) / 2
        val centerLon get() = (lonLo + lonHi) / 2
    }

    fun bounds(cell: String): Bounds {
        var latLo = -90.0
        var latHi = 90.0
        var lonLo = -180.0
        var lonHi = 180.0
        var evenBit = true
        for (c in cell) {
            val v = ALPHABET.indexOf(c)
            require(v >= 0) { "not a geohash" }
            for (i in 4 downTo 0) {
                val bit = (v shr i) and 1
                if (evenBit) {
                    val mid = (lonLo + lonHi) / 2
                    if (bit == 1) lonLo = mid else lonHi = mid
                } else {
                    val mid = (latLo + latHi) / 2
                    if (bit == 1) latLo = mid else latHi = mid
                }
                evenBit = !evenBit
            }
        }
        return Bounds(latLo, latHi, lonLo, lonHi)
    }

    // The cell and the eight cells around it
    fun withNeighbors(cell: String): List<String> {
        val b = bounds(cell)
        val dLat = b.latHi - b.latLo
        val dLon = b.lonHi - b.lonLo
        val out = LinkedHashSet<String>()
        for (dy in -1..1) for (dx in -1..1) {
            val lat = b.centerLat + dy * dLat
            if (lat < -90.0 || lat > 90.0) continue
            var lon = b.centerLon + dx * dLon
            if (lon > 180.0) lon -= 360.0
            if (lon < -180.0) lon += 360.0
            out.add(encode(lat, lon, cell.length))
        }
        return out.toList()
    }

    fun distanceKm(a: String, b: String): Double {
        val x = bounds(a)
        val y = bounds(b)
        val p1 = Math.toRadians(x.centerLat)
        val p2 = Math.toRadians(y.centerLat)
        val dp = p2 - p1
        val dl = Math.toRadians(y.centerLon - x.centerLon)
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * 6371.0 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    // The cell length that covers a search radius with the cell and its eight neighbors
    fun lengthForRadius(km: Int): Int = when {
        km <= 10 -> 5
        km <= 60 -> 4
        else -> 3
    }
}
