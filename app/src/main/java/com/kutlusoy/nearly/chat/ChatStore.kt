package com.kutlusoy.nearly.chat

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

enum class MessageStatus { Sending, Sent, Failed, Received }

data class ChatMessage(val id: String, val peer: String, val mine: Boolean, val text: String, val at: Long, val status: MessageStatus, val read: Boolean = true)

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
    fun peerName(peer: String): String?
    fun setPeerName(peer: String, name: String)
}

class SqliteChatStore(context: Context, accountKey: String) : ChatStore {
    private val helper = object : SQLiteOpenHelper(context.applicationContext, "chat-$accountKey.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE messages (id TEXT PRIMARY KEY, peer TEXT NOT NULL, mine INTEGER NOT NULL, body TEXT NOT NULL, at INTEGER NOT NULL, status TEXT NOT NULL, read INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX messages_peer ON messages (peer, at)")
            db.execSQL("CREATE TABLE wraps (id TEXT PRIMARY KEY)")
            db.execSQL("CREATE TABLE peers (peer TEXT PRIMARY KEY, name TEXT NOT NULL)")
            db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
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
    override fun peerName(peer: String): String? = db.rawQuery("SELECT name FROM peers WHERE peer = ?", arrayOf(peer)).use { if (it.moveToFirst()) it.getString(0) else null }

    @Synchronized
    override fun setPeerName(peer: String, name: String) {
        db.insertWithOnConflict("peers", null, ContentValues().apply { put("peer", peer); put("name", name) }, SQLiteDatabase.CONFLICT_REPLACE)
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
