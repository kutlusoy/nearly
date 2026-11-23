package com.kutlusoy.nearly.chat

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

enum class MessageStatus { Sending, Sent, Failed, Received, Delivered }

enum class PhotoState { Receiving, Ready, Gone }

// A high resolution photo inside a chat: the bytes live in the store under the transfer id, never on a server
data class PhotoInfo(val transfer: String, val width: Int, val height: Int, val size: Int, val once: Boolean, val state: PhotoState, val have: Int, val total: Int) {
    fun encode() = listOf(transfer, width, height, size, if (once) 1 else 0, state.name, have, total).joinToString("|")

    companion object {
        fun decode(text: String?): PhotoInfo? {
            val p = text?.split("|")?.takeIf { it.size == 8 } ?: return null
            return runCatching { PhotoInfo(p[0], p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4] == "1", PhotoState.valueOf(p[5]), p[6].toInt(), p[7].toInt()) }.getOrNull()
        }
    }
}

data class ChatMessage(val id: String, val peer: String, val mine: Boolean, val text: String, val at: Long, val status: MessageStatus, val read: Boolean = true, val photo: PhotoInfo? = null)

// What is known about another person from the public profile on the relays
data class PeerProfile(val name: String, val about: String = "", val age: Int? = null)

enum class SwipeDecision { Like, Later, Skip }

// How long a swiped person stays out of the deck: a like for good, "not now" for a week, a skip for a month
object Swipes {
    fun hides(decision: String, at: Long, now: Long): Boolean = when (decision) {
        "like" -> true
        "later" -> now - at < 7 * 24 * 3600L
        "skip" -> now - at < 30 * 24 * 3600L
        else -> false
    }
}

data class Conversation(val peer: String, val last: ChatMessage, val unread: Int)

// Where the chats of one account live on the phone
interface ChatStore {
    // false when a message with this id is already there
    fun add(message: ChatMessage): Boolean
    fun remove(id: String)
    fun setStatus(id: String, status: MessageStatus)
    fun setPhoto(id: String, photo: PhotoInfo)
    fun setRead(id: String, read: Boolean)

    // Pieces of photos that are still coming in, and the finished photos
    fun addChunk(transfer: String, index: Int, data: ByteArray): Boolean
    fun chunks(transfer: String): List<Pair<Int, ByteArray>>
    fun dropChunks(transfer: String)
    fun savePhoto(transfer: String, bytes: ByteArray)
    fun photoBytes(transfer: String): ByteArray?
    fun deletePhoto(transfer: String)

    // Swipe decisions, the people who signalled interest in me (never shown), and the matches
    fun setSwipe(peer: String, decision: String, at: Long)
    fun swipes(): Map<String, Pair<String, Long>>
    fun addLikeMe(peer: String, at: Long): Boolean
    fun likedMe(peer: String): Boolean
    fun addMatch(peer: String, at: Long): Boolean
    fun matches(): List<String>
    fun removeMatch(peer: String)
    fun messages(peer: String): List<ChatMessage>
    fun message(id: String): ChatMessage?
    fun conversations(): List<Conversation>
    fun unreadTotal(): Int
    fun markRead(peer: String)
    fun deleteConversation(peer: String)

    // Gift wraps that were handled already, so a wrap that comes from several relays or again after a restart is read once
    fun seenWrap(id: String): Boolean
    fun rememberWrap(id: String)

    fun lastSync(): Long
    fun setLastSync(seconds: Long)
    fun peerProfile(peer: String): PeerProfile?
    fun setPeerProfile(peer: String, profile: PeerProfile)
    fun peerName(peer: String): String? = peerProfile(peer)?.name

    // The people the user follows, kept on this phone
    fun followed(): List<String>
    fun setFollowed(peer: String, followed: Boolean)

    // People whose messages are dropped on arrival
    fun blocked(): List<String>
    fun setBlocked(peer: String, blocked: Boolean)
    fun isBlocked(peer: String): Boolean = peer in blocked()
}

class SqliteChatStore(context: Context, accountKey: String) : ChatStore {
    private val helper = object : SQLiteOpenHelper(context.applicationContext, "chat-$accountKey.db", null, 5) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE messages (id TEXT PRIMARY KEY, peer TEXT NOT NULL, mine INTEGER NOT NULL, body TEXT NOT NULL, at INTEGER NOT NULL, status TEXT NOT NULL, read INTEGER NOT NULL, attachment TEXT)")
            db.execSQL("CREATE TABLE chunks (transfer TEXT NOT NULL, idx INTEGER NOT NULL, data BLOB NOT NULL, PRIMARY KEY (transfer, idx))")
            db.execSQL("CREATE TABLE photos (transfer TEXT PRIMARY KEY, data BLOB NOT NULL)")
            db.execSQL("CREATE INDEX messages_peer ON messages (peer, at)")
            db.execSQL("CREATE TABLE wraps (id TEXT PRIMARY KEY)")
            db.execSQL("CREATE TABLE peers (peer TEXT PRIMARY KEY, name TEXT NOT NULL, about TEXT NOT NULL DEFAULT '', age INTEGER NOT NULL DEFAULT -1)")
            db.execSQL("CREATE TABLE followed (peer TEXT PRIMARY KEY)")
            db.execSQL("CREATE TABLE blocked (peer TEXT PRIMARY KEY)")
            db.execSQL("CREATE TABLE swipes (peer TEXT PRIMARY KEY, decision TEXT NOT NULL, at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE likes_me (peer TEXT PRIMARY KEY, at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE matches (peer TEXT PRIMARY KEY, at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE peers ADD COLUMN about TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE peers ADD COLUMN age INTEGER NOT NULL DEFAULT -1")
                db.execSQL("CREATE TABLE IF NOT EXISTS followed (peer TEXT PRIMARY KEY)")
            }
            if (oldVersion < 3) db.execSQL("CREATE TABLE IF NOT EXISTS blocked (peer TEXT PRIMARY KEY)")
            if (oldVersion < 4) {
                db.execSQL("ALTER TABLE messages ADD COLUMN attachment TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS chunks (transfer TEXT NOT NULL, idx INTEGER NOT NULL, data BLOB NOT NULL, PRIMARY KEY (transfer, idx))")
                db.execSQL("CREATE TABLE IF NOT EXISTS photos (transfer TEXT PRIMARY KEY, data BLOB NOT NULL)")
            }
            if (oldVersion < 5) {
                db.execSQL("CREATE TABLE IF NOT EXISTS swipes (peer TEXT PRIMARY KEY, decision TEXT NOT NULL, at INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS likes_me (peer TEXT PRIMARY KEY, at INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS matches (peer TEXT PRIMARY KEY, at INTEGER NOT NULL)")
            }
        }
    }
    private val db get() = helper.writableDatabase

    @Synchronized
    override fun add(message: ChatMessage): Boolean {
        val values = ContentValues().apply {
            put("id", message.id)
            put("peer", message.peer)
            put("mine", if (message.mine) 1 else 0)
            put("body", message.text)
            put("at", message.at)
            put("status", message.status.name)
            put("read", if (message.read) 1 else 0)
            put("attachment", message.photo?.encode())
        }
        return db.insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    @Synchronized
    override fun remove(id: String) {
        db.delete("messages", "id = ?", arrayOf(id))
    }

    @Synchronized
    override fun setStatus(id: String, status: MessageStatus) {
        db.update("messages", ContentValues().apply { put("status", status.name) }, "id = ?", arrayOf(id))
    }

    @Synchronized
    override fun setPhoto(id: String, photo: PhotoInfo) {
        db.update("messages", ContentValues().apply { put("attachment", photo.encode()) }, "id = ?", arrayOf(id))
    }

    @Synchronized
    override fun setRead(id: String, read: Boolean) {
        db.update("messages", ContentValues().apply { put("read", if (read) 1 else 0) }, "id = ?", arrayOf(id))
    }

    @Synchronized
    override fun addChunk(transfer: String, index: Int, data: ByteArray): Boolean =
        db.insertWithOnConflict("chunks", null, ContentValues().apply { put("transfer", transfer); put("idx", index); put("data", data) }, SQLiteDatabase.CONFLICT_IGNORE) != -1L

    @Synchronized
    override fun chunks(transfer: String): List<Pair<Int, ByteArray>> =
        db.rawQuery("SELECT idx, data FROM chunks WHERE transfer = ? ORDER BY idx", arrayOf(transfer)).use { c -> buildList { while (c.moveToNext()) add(c.getInt(0) to c.getBlob(1)) } }

    @Synchronized
    override fun dropChunks(transfer: String) {
        db.delete("chunks", "transfer = ?", arrayOf(transfer))
    }

    @Synchronized
    override fun savePhoto(transfer: String, bytes: ByteArray) {
        db.insertWithOnConflict("photos", null, ContentValues().apply { put("transfer", transfer); put("data", bytes) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    override fun photoBytes(transfer: String): ByteArray? =
        db.rawQuery("SELECT data FROM photos WHERE transfer = ?", arrayOf(transfer)).use { if (it.moveToFirst()) it.getBlob(0) else null }

    @Synchronized
    override fun deletePhoto(transfer: String) {
        db.delete("photos", "transfer = ?", arrayOf(transfer))
    }

    @Synchronized
    override fun setSwipe(peer: String, decision: String, at: Long) {
        db.insertWithOnConflict("swipes", null, ContentValues().apply { put("peer", peer); put("decision", decision); put("at", at) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    override fun swipes(): Map<String, Pair<String, Long>> =
        db.rawQuery("SELECT peer, decision, at FROM swipes", null).use { c -> buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1) to c.getLong(2)) } }

    @Synchronized
    override fun addLikeMe(peer: String, at: Long): Boolean {
        val added = db.insertWithOnConflict("likes_me", null, ContentValues().apply { put("peer", peer); put("at", at) }, SQLiteDatabase.CONFLICT_IGNORE) != -1L
        if (added) db.execSQL("DELETE FROM likes_me WHERE peer NOT IN (SELECT peer FROM likes_me ORDER BY at DESC LIMIT 5000)")
        return added
    }

    @Synchronized
    override fun likedMe(peer: String): Boolean = db.rawQuery("SELECT 1 FROM likes_me WHERE peer = ?", arrayOf(peer)).use { it.moveToFirst() }

    @Synchronized
    override fun addMatch(peer: String, at: Long): Boolean =
        db.insertWithOnConflict("matches", null, ContentValues().apply { put("peer", peer); put("at", at) }, SQLiteDatabase.CONFLICT_IGNORE) != -1L

    @Synchronized
    override fun matches(): List<String> = db.rawQuery("SELECT peer FROM matches ORDER BY at", null).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    @Synchronized
    override fun removeMatch(peer: String) {
        db.delete("matches", "peer = ?", arrayOf(peer))
    }

    @Synchronized
    override fun messages(peer: String): List<ChatMessage> =
        db.rawQuery("SELECT id, peer, mine, body, at, status, read, attachment FROM messages WHERE peer = ? ORDER BY at, rowid", arrayOf(peer)).use { it.rows() }

    @Synchronized
    override fun message(id: String): ChatMessage? =
        db.rawQuery("SELECT id, peer, mine, body, at, status, read, attachment FROM messages WHERE id = ?", arrayOf(id)).use { it.rows().firstOrNull() }

    @Synchronized
    override fun conversations(): List<Conversation> {
        val peers = db.rawQuery("SELECT peer FROM messages GROUP BY peer", null).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        return peers.mapNotNull { peer ->
            val last = db.rawQuery("SELECT id, peer, mine, body, at, status, read, attachment FROM messages WHERE peer = ? ORDER BY at DESC, rowid DESC LIMIT 1", arrayOf(peer)).use { it.rows().firstOrNull() }
                ?: return@mapNotNull null
            val unread = db.rawQuery("SELECT COUNT(*) FROM messages WHERE peer = ? AND read = 0", arrayOf(peer)).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
            Conversation(peer, last, unread)
        }.sortedByDescending { it.last.at }
    }

    @Synchronized
    override fun unreadTotal(): Int = db.rawQuery("SELECT COUNT(*) FROM messages WHERE read = 0", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    @Synchronized
    override fun markRead(peer: String) {
        db.update("messages", ContentValues().apply { put("read", 1) }, "peer = ? AND read = 0", arrayOf(peer))
    }

    @Synchronized
    override fun deleteConversation(peer: String) {
        db.rawQuery("SELECT attachment FROM messages WHERE peer = ? AND attachment IS NOT NULL", arrayOf(peer)).use { c ->
            while (c.moveToNext()) PhotoInfo.decode(c.getString(0))?.let { db.delete("photos", "transfer = ?", arrayOf(it.transfer)); db.delete("chunks", "transfer = ?", arrayOf(it.transfer)) }
        }
        db.delete("messages", "peer = ?", arrayOf(peer))
    }

    @Synchronized
    override fun seenWrap(id: String): Boolean = db.rawQuery("SELECT 1 FROM wraps WHERE id = ?", arrayOf(id)).use { it.moveToFirst() }

    @Synchronized
    override fun rememberWrap(id: String) {
        db.insertWithOnConflict("wraps", null, ContentValues().apply { put("id", id) }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    @Synchronized
    override fun lastSync(): Long = meta("lastSync")?.toLongOrNull() ?: 0L

    @Synchronized
    override fun setLastSync(seconds: Long) = setMeta("lastSync", seconds.toString())

    @Synchronized
    override fun peerProfile(peer: String): PeerProfile? =
        db.rawQuery("SELECT name, about, age FROM peers WHERE peer = ?", arrayOf(peer)).use {
            if (it.moveToFirst()) PeerProfile(it.getString(0), it.getString(1), it.getInt(2).takeIf { age -> age >= 0 }) else null
        }

    @Synchronized
    override fun setPeerProfile(peer: String, profile: PeerProfile) {
        db.insertWithOnConflict(
            "peers", null,
            ContentValues().apply { put("peer", peer); put("name", profile.name); put("about", profile.about); put("age", profile.age ?: -1) },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    @Synchronized
    override fun followed(): List<String> = db.rawQuery("SELECT peer FROM followed ORDER BY rowid", null).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    @Synchronized
    override fun setFollowed(peer: String, followed: Boolean) {
        if (followed) db.insertWithOnConflict("followed", null, ContentValues().apply { put("peer", peer) }, SQLiteDatabase.CONFLICT_IGNORE)
        else db.delete("followed", "peer = ?", arrayOf(peer))
    }

    @Synchronized
    override fun blocked(): List<String> = db.rawQuery("SELECT peer FROM blocked ORDER BY rowid", null).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    @Synchronized
    override fun setBlocked(peer: String, blocked: Boolean) {
        if (blocked) db.insertWithOnConflict("blocked", null, ContentValues().apply { put("peer", peer) }, SQLiteDatabase.CONFLICT_IGNORE)
        else db.delete("blocked", "peer = ?", arrayOf(peer))
    }

    private fun meta(key: String): String? = db.rawQuery("SELECT value FROM meta WHERE key = ?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun setMeta(key: String, value: String) {
        db.insertWithOnConflict("meta", null, ContentValues().apply { put("key", key); put("value", value) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun android.database.Cursor.rows(): List<ChatMessage> = buildList {
        while (moveToNext()) {
            add(ChatMessage(getString(0), getString(1), getInt(2) == 1, getString(3), getLong(4), MessageStatus.valueOf(getString(5)), getInt(6) == 1, PhotoInfo.decode(getString(7))))
        }
    }

    fun close() = helper.close()
}
