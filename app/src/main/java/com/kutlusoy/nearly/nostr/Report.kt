package com.kutlusoy.nearly.nostr

// NIP-56 report types. Underage has no type of its own in the NIP, it travels as illegal with a marker in the note.
enum class ReportType(val code: String) {
    Spam("spam"),
    Impersonation("impersonation"),
    Nudity("nudity"),
    Illegal("illegal"),
    Underage("illegal"),
    Abuse("profanity"),
}

object Report {
    const val KIND = 1984
    const val MAX_NOTE = 300

    fun build(session: Session, peer: String, type: ReportType, note: String, now: Long): Event {
        val clean = note.filter { !it.isISOControl() || it == '\n' }.trim().take(MAX_NOTE)
        val content = if (type == ReportType.Underage) "underage" + if (clean.isEmpty()) "" else ": $clean" else clean
        return Event.sign(session, KIND, content, listOf(listOf("p", peer, type.code)), now)
    }
}
