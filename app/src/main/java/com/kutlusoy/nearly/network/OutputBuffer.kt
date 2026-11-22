package com.kutlusoy.nearly.network

import java.util.ArrayDeque

class OutputBuffer(private val capacity: Int = 1_000) {
    private val lines = ArrayDeque<String>()

    fun add(line: String) {
        synchronized(lines) {
            if (lines.size >= capacity) lines.removeFirst()
            lines.addLast(line)
        }
    }

    fun clear() {
        synchronized(lines) { lines.clear() }
    }

    fun snapshot(): List<String> = synchronized(lines) { lines.toList() }

    fun tail(count: Int): List<String> = synchronized(lines) { lines.toList().takeLast(count) }
}
