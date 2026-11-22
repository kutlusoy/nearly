package com.kutlusoy.nearly.network

object LogSanitizer {
    private val ipv4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}(?::\d{1,5})?\b""")
    private val ipv6 = Regex("""\[?[0-9a-fA-F]*:[0-9a-fA-F:]{2,}\]?(?::\d{1,5})?""")
    private val onion = Regex("""\b[a-z2-7]{16,56}\.onion(?::\d{1,5})?\b""")
    private val i2p = Regex("""\b[a-z2-7]{16,56}\.b32\.i2p(?::\d{1,5})?\b""")
    private val nostrKey = Regex("""\b(?:npub|nsec|nprofile|nevent|note)1[02-9ac-hj-np-z]{20,}\b""")
    private val path = Regex("""/(?:data|storage|sdcard|mnt)/[^\s'"]*""")

    fun clean(line: String): String {
        var out = line
        out = path.replace(out, "<path>")
        out = onion.replace(out, "<onion>")
        out = i2p.replace(out, "<i2p>")
        out = nostrKey.replace(out, "<nostr>")
        out = ipv6.replace(out) { if (it.value.contains("::") || it.value.count { c -> c == ':' } >= 5) "<ip>" else it.value }
        out = ipv4.replace(out, "<ip>")
        return out
    }

    fun clean(lines: List<String>): String = lines.joinToString("\n") { clean(it) }
}
