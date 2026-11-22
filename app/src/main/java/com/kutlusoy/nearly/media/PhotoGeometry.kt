package com.kutlusoy.nearly.media

data class Size(val width: Int, val height: Int)

// How a stored photo must be turned to look upright: first rotate clockwise, then mirror horizontally
data class Transform(val rotationDegrees: Int, val flipHorizontal: Boolean) {
    val swapsAxes: Boolean get() = rotationDegrees == 90 || rotationDegrees == 270
}

object PhotoGeometry {
    // Keeps the proportions, never makes a photo larger
    fun fit(width: Int, height: Int, maxSide: Int): Size {
        require(width > 0 && height > 0 && maxSide > 0) { "invalid size" }
        val longSide = maxOf(width, height)
        if (longSide <= maxSide) return Size(width, height)
        val scale = maxSide.toDouble() / longSide
        return Size(maxOf(1, Math.round(width * scale).toInt()), maxOf(1, Math.round(height * scale).toInt()))
    }

    // The decoder can read a photo at 1/2, 1/4 and so on, which saves memory. The long side stays at least targetSide.
    fun sampleSize(width: Int, height: Int, targetSide: Int): Int {
        require(width > 0 && height > 0 && targetSide > 0) { "invalid size" }
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= targetSide) sample *= 2
        return sample
    }

    // EXIF orientation 1 to 8, see the table of the EXIF standard. Unknown values count as normal.
    fun transformFor(orientation: Int): Transform = when (orientation) {
        2 -> Transform(0, true)
        3 -> Transform(180, false)
        4 -> Transform(180, true)
        5 -> Transform(90, true)
        6 -> Transform(90, false)
        7 -> Transform(270, true)
        8 -> Transform(270, false)
        else -> Transform(0, false)
    }

    // Where a pixel of the stored photo lands in the upright photo
    fun mapPoint(x: Int, y: Int, width: Int, height: Int, t: Transform): Pair<Int, Int> {
        val (rx, ry) = when (t.rotationDegrees) {
            90 -> (height - 1 - y) to x
            180 -> (width - 1 - x) to (height - 1 - y)
            270 -> y to (width - 1 - x)
            else -> x to y
        }
        val newWidth = if (t.swapsAxes) height else width
        return (if (t.flipHorizontal) newWidth - 1 - rx else rx) to ry
    }
}
