package com.kutlusoy.nearly.network

object TorLogStatus {
    private val bootstrapLine = Regex("""Bootstrapped (\d{1,3})%""")

    // Latest bootstrap percentage in the log, null while Tor has not reported any
    fun bootstrap(lines: List<String>): Int? {
        var value: Int? = null
        for (line in lines) bootstrapLine.find(line)?.let { value = it.groupValues[1].toInt().coerceIn(0, 100) }
        return value
    }
}
