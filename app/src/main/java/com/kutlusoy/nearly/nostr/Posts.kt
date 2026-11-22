package com.kutlusoy.nearly.nostr

import com.kutlusoy.nearly.media.BlurHash

data class PhotoRef(
    val url: String,
    val mime: String,
    val sha256: String,
    val width: Int? = null,
    val height: Int? = null,
    val alt: String? = null,
    val blurhash: String? = null,
)

sealed interface Post {
    val id: String
    val author: String
    val createdAt: Long
    val text: String

    data class Text(
        override val id: String,
        override val author: String,
        override val createdAt: Long,
        override val text: String,
        val hashtags: List<String>,
        val replyTo: String?,
    ) : Post

    data class Photos(
        override val id: String,
        override val author: String,
        override val createdAt: Long,
        override val text: String,
        val photos: List<PhotoRef>,
    ) : Post
}

// Text posts (kind 1), photo posts (kind 20, NIP-68) and reactions (kind 7). Photo files are uploaded elsewhere, a post only refers to them.
object PostCodec {
    const val KIND_TEXT = 1
    const val KIND_PHOTO = 20
    const val KIND_REACTION = 7
    const val MAX_TEXT = 2_000
    const val MAX_PHOTOS = 10
    private val HEX64 = Regex("[0-9a-f]{64}")

    fun text(session: Session, text: String, hashtags: List<String> = emptyList(), replyTo: String? = null, createdAt: Long = now()): Event {
        require(text.isNotBlank() && text.length <= MAX_TEXT) { "text must have 1 to $MAX_TEXT characters" }
        val tags = hashtags.map { listOf("t", it.removePrefix("#").lowercase()) }.toMutableList()
        replyTo?.let { require(HEX64.matches(it)) { "invalid event id" }; tags.add(listOf("e", it, "", "reply")) }
        return Event.sign(session, KIND_TEXT, text, tags, createdAt)
    }

    fun photo(session: Session, caption: String, photos: List<PhotoRef>, createdAt: Long = now()): Event {
        require(photos.size in 1..MAX_PHOTOS) { "a photo post has 1 to $MAX_PHOTOS photos" }
        require(caption.length <= MAX_TEXT) { "caption too long" }
        val tags = photos.map { p ->
            require(p.url.startsWith("https://") && p.mime.startsWith("image/") && HEX64.matches(p.sha256)) { "invalid photo reference" }
            buildList {
                add("imeta"); add("url ${p.url}"); add("m ${p.mime}"); add("x ${p.sha256}")
                if (p.width != null && p.height != null) add("dim ${p.width}x${p.height}")
                p.alt?.takeIf { it.isNotBlank() }?.let { add("alt $it") }
                p.blurhash?.takeIf { BlurHash.isValid(it) }?.let { add("blurhash $it") }
            }
        }
        return Event.sign(session, KIND_PHOTO, caption, tags, createdAt)
    }

    fun reaction(session: Session, target: Event, content: String = "+", createdAt: Long = now()): Event =
        Event.sign(session, KIND_REACTION, content, listOf(listOf("e", target.id), listOf("p", target.pubkey), listOf("k", target.kind.toString())), createdAt)

    fun parse(event: Event): Post? = when (event.kind) {
        KIND_TEXT -> Post.Text(
            event.id, event.pubkey, event.createdAt, event.content.take(MAX_TEXT),
            event.tagValues("t").map { it.lowercase() }.distinct().take(30),
            event.tags.firstOrNull { it.size >= 2 && it[0] == "e" && it.getOrNull(3) == "reply" }?.get(1)?.takeIf { HEX64.matches(it) },
        )
        KIND_PHOTO -> {
            val photos = event.tags.filter { it.firstOrNull() == "imeta" }.mapNotNull { parsePhoto(it) }.take(MAX_PHOTOS)
            if (photos.isEmpty()) null else Post.Photos(event.id, event.pubkey, event.createdAt, event.content.take(MAX_TEXT), photos)
        }
        else -> null
    }

    private fun parsePhoto(tag: List<String>): PhotoRef? {
        val fields = tag.drop(1).mapNotNull { entry -> entry.indexOf(' ').takeIf { it > 0 }?.let { entry.substring(0, it) to entry.substring(it + 1) } }.toMap()
        val url = fields["url"]?.takeIf { it.startsWith("https://") && it.length <= 1000 } ?: return null
        val mime = fields["m"]?.takeIf { it.startsWith("image/") } ?: return null
        val hash = fields["x"]?.lowercase()?.takeIf { HEX64.matches(it) } ?: return null
        val dim = fields["dim"]?.split("x")?.takeIf { it.size == 2 }?.let { (w, h) -> w.toIntOrNull()?.let { a -> h.toIntOrNull()?.let { b -> a to b } } }
        return PhotoRef(url, mime, hash, dim?.first, dim?.second, fields["alt"]?.take(300), fields["blurhash"]?.takeIf { BlurHash.isValid(it) })
    }

    private fun now() = System.currentTimeMillis() / 1000
}

object FeedFilters {
    // Relays limit the number of authors per filter, so the list is cut into groups
    fun following(list: FollowList, since: Long? = null, limit: Int = 50, groupSize: Int = 100): List<Filter> =
        list.follows.map { it.pubkey }.chunked(groupSize)
            .map { Filter(kinds = listOf(PostCodec.KIND_TEXT, PostCodec.KIND_PHOTO), authors = it, since = since, limit = limit) }
}
