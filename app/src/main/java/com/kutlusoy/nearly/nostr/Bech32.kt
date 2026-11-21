package com.kutlusoy.nearly.nostr

// Bech32 as used by NIP-19 and NIP-49, without the 90 character limit of BIP-173
object Bech32 {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val GENERATOR = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

    class Decoded(val hrp: String, val data: ByteArray)

    fun encode(hrp: String, data: ByteArray): String {
        val values = convertBits(data, 8, 5, pad = true)
        val checksum = createChecksum(hrp, values)
        return hrp + "1" + (values + checksum).map { CHARSET[it] }.joinToString("")
    }

    fun decode(text: String): Decoded {
        require(text == text.lowercase() || text == text.uppercase()) { "mixed case" }
        val lower = text.lowercase()
        val separator = lower.lastIndexOf('1')
        require(separator >= 1 && separator + 7 <= lower.length) { "invalid bech32 length" }
        val hrp = lower.substring(0, separator)
        val values = lower.substring(separator + 1).map {
            val index = CHARSET.indexOf(it)
            require(index >= 0) { "invalid character" }
            index
        }
        require(polymod(expandHrp(hrp) + values) == 1) { "invalid checksum" }
        val payload = values.dropLast(6)
        return Decoded(hrp, convertBits(payload.toIntArray(), 5, 8, pad = false).let { bits -> ByteArray(bits.size) { bits[it].toByte() } })
    }

    private fun polymod(values: List<Int>): Int {
        var chk = 1
        for (v in values) {
            val top = chk ushr 25
            chk = ((chk and 0x1ffffff) shl 5) xor v
            for (i in 0 until 5) if ((top ushr i) and 1 == 1) chk = chk xor GENERATOR[i]
        }
        return chk
    }

    private fun expandHrp(hrp: String): List<Int> = hrp.map { it.code ushr 5 } + 0 + hrp.map { it.code and 31 }

    private fun createChecksum(hrp: String, values: List<Int>): List<Int> {
        val mod = polymod(expandHrp(hrp) + values + List(6) { 0 }) xor 1
        return List(6) { (mod ushr (5 * (5 - it))) and 31 }
    }

    private fun convertBits(data: ByteArray, from: Int, to: Int, pad: Boolean): List<Int> =
        convertBits(IntArray(data.size) { data[it].toInt() and 0xff }, from, to, pad)

    private fun convertBits(data: IntArray, from: Int, to: Int, pad: Boolean): List<Int> {
        var acc = 0
        var bits = 0
        val out = ArrayList<Int>()
        val max = (1 shl to) - 1
        for (value in data) {
            require(value ushr from == 0) { "invalid value" }
            acc = (acc shl from) or value
            bits += from
            while (bits >= to) {
                bits -= to
                out.add((acc ushr bits) and max)
            }
            acc = acc and ((1 shl bits) - 1)
        }
        if (pad) {
            if (bits > 0) out.add((acc shl (to - bits)) and max)
        } else {
            require(bits < from && (acc shl (to - bits)) and max == 0) { "invalid padding" }
        }
        return out
    }
}
