package com.kutlusoy.nearly.chat

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

enum class MessageStatus { Sending, Sent, Failed, Received }

data class ChatMessage(val id: String, val peer: String, val mine: Boolean, val text: String, val at: Long, val status: MessageStatus, val read: Boolean = true)

// What is known about another person from the public profile on the relays
data class PeerProfile(val name: String, val about: String = "", val age: Int? = null)

data class Conversation(val peer: String, val last: ChatMessage, val unread: Int)

// Where the chats of one account live on the phone
interface ChatStore {
    // false when a message with this id is already there
    fun add(message: ChatMessage): Boolean
    fun remove(id: String)
    fun setStatus(id: String, status: MessageStatus)
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
    private val helper = object : SQLiteOpenHelper(context.applicationContext, "chat-$accountKey.db", null, 3) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE messages (id TEXT PRIMARY KEY, peer TEXT NOT NULL, mine INTEGER NOT NULL, body TEXT NOT NULL, at INTEGER NOT NULL, status TEXT NOT NULL, read INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX messages_peer ON messages (peer, at)")
            db.execSQL("CREATE TABLE wraps (id TEXT PRIMARY KEY)")
            db.execSQL("CREATE TABLE peers (peer TEXT PRIMARY KEY, name TEXT NOT NULL, about TEXT NOT NULL DEFAULT '', age INTEGER NOT NULL DEFAULT -1)")
            db.execSQL("CREATE TABLE followed (peer TEXT PRIMARY KEY)")
            db.execSQL("CREATE TABLE blocked (peer TEXT PRIMARY KEY)")
            db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE peers ADD COLUMN about TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE peers ADD COLUMN age INTEGER NOT NULL DEFAULT -1")
                db.execSQL("CREATE TABLE IF NOT EXISTS followed (peer TEXT PRIMARY KEY)")
            }
            if (oldVersion < 3) db.execSQL("CREATE TABLE IF NOT EXISTS blocked (peer TEXT PRIMARY KEY)")
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
    override fun messages(peer: String): List<ChatMessage> =
        db.rawQuery("SELECT id, peer, mine, body, at, status, read FROM messages WHERE peer = ? ORDER BY at, rowid", arrayOf(peer)).use { it.rows() }

    @Synchronized
    override fun message(id: String): ChatMessage? =
        db.rawQuery("SELECT id, peer, mine, body, at, status, read FROM messages WHERE id = ?", arrayOf(id)).use { it.rows().firstOrNull() }

    @Synchronized
    override fun conversations(): List<Conversation> {
        val peers = db.rawQuery("SELECT peer FROM messages GROUP BY peer", null).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        return peers.mapNotNull { peer ->
            val last = db.rawQuery("SELECT id, peer, mine, body, at, status, read FROM messages WHERE peer = ? ORDER BY at DESC, rowid DESC LIMIT 1", arrayOf(peer)).use { it.rows().firstOrNull() }
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
            add(ChatMessage(getString(0), getString(1), getInt(2) == 1, getString(3), getLong(4), MessageStatus.valueOf(getString(5)), getInt(6) == 1))
        }
    }

    fun close() = helper.close()
}
