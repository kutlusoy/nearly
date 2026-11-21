package com.kutlusoy.nearly.nostr

data class Follow(val pubkey: String, val relay: String? = null, val petname: String? = null)

// The contact list (kind 3). It replaces the older list completely, so a change MUST start from the newest list that was read.
data class FollowList(
    val follows: List<Follow> = emptyList(),
    val otherTags: List<List<String>> = emptyList(),
    val content: String = "",
    val createdAt: Long = 0,
) {
    fun contains(pubkey: String) = follows.any { it.pubkey == pubkey }

    fun add(pubkey: String): FollowList =
        if (contains(pubkey)) this else copy(follows = follows + Follow(pubkey))

    fun remove(pubkey: String): FollowList = copy(follows = follows.filter { it.pubkey != pubkey })
}

object FollowCodec {
    const val KIND = 3
    const val MAX_FOLLOWS = 5_000
    private val HEX64 = Regex("[0-9a-f]{64}")

    fun parse(event: Event): FollowList? {
        if (event.kind != KIND) return null
        val follows = LinkedHashMap<String, Follow>()
        val other = mutableListOf<List<String>>()
        for (tag in event.tags) {
            if (tag.size >= 2 && tag[0] == "p") {
                val key = tag[1].lowercase()
                if (HEX64.matches(key) && follows.size < MAX_FOLLOWS && key !in follows) {
                    follows[key] = Follow(key, tag.getOrNull(2)?.takeIf { it.isNotEmpty() }, tag.getOrNull(3)?.takeIf { it.isNotEmpty() })
                }
            } else {
                other.add(tag)
            }
        }
        return FollowList(follows.values.toList(), other, event.content, event.createdAt)
    }

    fun sign(session: Session, list: FollowList, createdAt: Long = System.currentTimeMillis() / 1000): Event {
        val tags = list.follows.map { f ->
            buildList {
                add("p"); add(f.pubkey)
                if (f.relay != null || f.petname != null) add(f.relay.orEmpty())
                if (f.petname != null) add(f.petname)
            }
        } + list.otherTags
        // A replacement must be newer than the list it replaces, also when two changes happen in the same second
        val time = maxOf(createdAt, list.createdAt + 1)
        return Event.sign(session, KIND, list.content, tags, time)
    }
}

// For replaceable events (kinds 0 and 3) the newest event wins, with the lowest id as the tie breaker (NIP-01)
object Replaceable {
    fun newest(events: Collection<Event>): Event? =
        events.maxWithOrNull(compareBy<Event> { it.createdAt }.thenByDescending { it.id })
}
