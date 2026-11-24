package com.kutlusoy.nearly.feed

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.kutlusoy.nearly.nostr.Event

enum class PostState { Sending, Sent, Failed }

// Posts, stories and reactions on this phone. The feed opens from here at once and the relays refresh it.
interface FeedStore {
    fun add(event: Event, state: PostState = PostState.Sent): Boolean
    fun get(id: String): Event?
    fun events(): List<Event>
    fun remove(id: String)
    fun state(id: String): PostState
    fun setState(id: String, state: PostState)

    // The own likes: post id to the id of the reaction event
    fun liked(): Map<String, String>
    fun setLike(target: String, reaction: String?)

    // Likes of other people on the own posts
    fun addReaction(id: String, target: String, reactor: String): Boolean
    fun removeReaction(id: String)
    fun reactions(target: String): Int

    fun seen(): Set<String>
    fun markSeen(id: String)
    fun prune(keep: Int = MAX_EVENTS)

    companion object {
        const val MAX_EVENTS = 600
    }
}

class SqliteFeedStore(context: Context, accountKey: String) : FeedStore {
    private val helper = object : SQLiteOpenHelper(context.applicationContext, "feed-$accountKey.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE posts (id TEXT PRIMARY KEY, author TEXT NOT NULL, kind INTEGER NOT NULL, at INTEGER NOT NULL, json TEXT NOT NULL, state TEXT NOT NULL)")
            db.execSQL("CREATE INDEX posts_at ON posts (at)")
            db.execSQL("CREATE TABLE liked (target TEXT PRIMARY KEY, reaction TEXT NOT NULL)")
            db.execSQL("CREATE TABLE reactions (id TEXT PRIMARY KEY, target TEXT NOT NULL, reactor TEXT NOT NULL)")
            db.execSQL("CREATE TABLE seen (id TEXT PRIMARY KEY)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    private val db get() = helper.writableDatabase

    @Synchronized
    override fun add(event: Event, state: PostState): Boolean {
        val values = ContentValues().apply {
            put("id", event.id); put("author", event.pubkey); put("kind", event.kind); put("at", event.createdAt)
            put("json", event.toJson()); put("state", state.name)
        }
        return db.insertWithOnConflict("posts", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    @Synchronized
    override fun get(id: String): Event? =
        db.rawQuery("SELECT json FROM posts WHERE id = ?", arrayOf(id)).use { c -> if (c.moveToFirst()) runCatching { Event.parse(c.getString(0)) }.getOrNull() else null }

    @Synchronized
    override fun events(): List<Event> =
        db.rawQuery("SELECT json FROM posts ORDER BY at DESC", null).use { c -> buildList { while (c.moveToNext()) runCatching { Event.parse(c.getString(0)) }.getOrNull()?.let { add(it) } } }

    @Synchronized
    override fun remove(id: String) {
        db.delete("posts", "id = ?", arrayOf(id))
        db.delete("reactions", "target = ?", arrayOf(id))
    }

    @Synchronized
    override fun state(id: String): PostState =
        db.rawQuery("SELECT state FROM posts WHERE id = ?", arrayOf(id)).use { c -> if (c.moveToFirst()) runCatching { PostState.valueOf(c.getString(0)) }.getOrDefault(PostState.Sent) else PostState.Sent }

    @Synchronized
    override fun setState(id: String, state: PostState) {
        db.update("posts", ContentValues().apply { put("state", state.name) }, "id = ?", arrayOf(id))
    }

    @Synchronized
    override fun liked(): Map<String, String> =
        db.rawQuery("SELECT target, reaction FROM liked", null).use { c -> buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) } }

    @Synchronized
    override fun setLike(target: String, reaction: String?) {
        if (reaction == null) db.delete("liked", "target = ?", arrayOf(target))
        else db.insertWithOnConflict("liked", null, ContentValues().apply { put("target", target); put("reaction", reaction) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    override fun addReaction(id: String, target: String, reactor: String): Boolean =
        db.insertWithOnConflict("reactions", null, ContentValues().apply { put("id", id); put("target", target); put("reactor", reactor) }, SQLiteDatabase.CONFLICT_IGNORE) != -1L

    @Synchronized
    override fun removeReaction(id: String) {
        db.delete("reactions", "id = ?", arrayOf(id))
    }

    @Synchronized
    override fun reactions(target: String): Int =
        db.rawQuery("SELECT COUNT(DISTINCT reactor) FROM reactions WHERE target = ?", arrayOf(target)).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    @Synchronized
    override fun seen(): Set<String> = db.rawQuery("SELECT id FROM seen", null).use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }

    @Synchronized
    override fun markSeen(id: String) {
        db.insertWithOnConflict("seen", null, ContentValues().apply { put("id", id) }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    @Synchronized
    override fun prune(keep: Int) {
        db.execSQL("DELETE FROM posts WHERE id NOT IN (SELECT id FROM posts ORDER BY at DESC LIMIT $keep)")
        db.execSQL("DELETE FROM reactions WHERE target NOT IN (SELECT id FROM posts)")
        db.execSQL("DELETE FROM seen WHERE id NOT IN (SELECT id FROM posts)")
    }
}
