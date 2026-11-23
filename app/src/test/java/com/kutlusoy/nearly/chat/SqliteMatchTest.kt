package com.kutlusoy.nearly.chat

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SqliteMatchTest {
    private val store = SqliteChatStore(ApplicationProvider.getApplicationContext<Application>(), "match1")

    @Test
    fun swipesLikesAndMatchesAreKept() {
        store.setSwipe("a", "later", 10)
        store.setSwipe("a", "like", 20)
        assertEquals(mapOf("a" to ("like" to 20L)), store.swipes())
        assertTrue(store.addLikeMe("b", 5))
        assertFalse(store.addLikeMe("b", 6))
        assertTrue(store.likedMe("b"))
        assertFalse(store.likedMe("c"))
        assertTrue(store.addMatch("a", 30))
        assertFalse(store.addMatch("a", 31))
        assertEquals(listOf("a"), store.matches())
        store.removeMatch("a")
        assertEquals(emptyList(), store.matches())
    }

    @Test
    fun onlyTheNewestFiveThousandSignalsAreKept() {
        repeat(5002) { store.addLikeMe("p$it", it.toLong()) }
        assertFalse(store.likedMe("p0"))
        assertTrue(store.likedMe("p5001"))
    }
}
