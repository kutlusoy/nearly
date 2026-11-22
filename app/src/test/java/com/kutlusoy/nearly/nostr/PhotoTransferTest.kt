package com.kutlusoy.nearly.nostr

import com.kutlusoy.nearly.chat.newSession
import com.kutlusoy.nearly.chat.hex
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoTransferTest {
    private val anna = newSession()
    private val ben = newSession()
    private fun photo(size: Int) = Random(size).nextBytes(size)

    private fun received(bytes: ByteArray, meta: PhotoMeta): List<PhotoChunk> =
        PhotoTransfer.wraps(anna, ben.hex(), meta, bytes).map { wrap ->
            val rumor = assertNotNull(GiftWrap.unwrap(ben, wrap)).rumor
            assertEquals(PhotoTransfer.KIND_CHUNK, rumor.kind)
            assertNotNull(PhotoTransfer.parseChunk(rumor))
        }

    @Test
    fun aPhotoIsCutIntoWrapsThatFitTheRelayLimits() {
        val bytes = photo(PhotoTransfer.MAX_BYTES)
        val meta = PhotoTransfer.meta(bytes, 2048, 1536, once = false)
        assertEquals(PhotoTransfer.MAX_CHUNKS, meta.total)
        val wraps = PhotoTransfer.wraps(anna, ben.hex(), meta, bytes)
        assertEquals(meta.total, wraps.size)
        val biggest = wraps.maxOf { it.toJson().toByteArray().size }
        assertTrue(biggest < 60_000, "the biggest wrap has $biggest bytes")
        assertTrue(wraps.all { it.kind == GiftWrap.KIND_WRAP && it.isValid() && it.tagValues("p") == listOf(ben.hex()) })
    }

    @Test
    fun theReceiverGetsTheSamePhotoBackInAnyOrderAndWithRepeats() {
        val bytes = photo(95_000)
        val meta = PhotoTransfer.meta(bytes, 1200, 800, once = true)
        val chunks = received(bytes, meta)
        assertEquals(5, chunks.size)
        assertTrue(chunks.all { it.meta == meta }, "every chunk carries the same description")
        val assembler = PhotoAssembler(meta)
        for (chunk in chunks.shuffled(Random(3)) + chunks.take(2)) assembler.add(chunk)
        assertEquals(5, assembler.have)
        assertTrue(assembler.result().contentEquals(bytes))
        assertTrue(meta.once)
    }

    @Test
    fun aMissingChunkOrAWrongHashGivesNothing() {
        val bytes = photo(60_000)
        val meta = PhotoTransfer.meta(bytes, 100, 100, false)
        val chunks = received(bytes, meta)
        val partial = PhotoAssembler(meta)
        chunks.drop(1).forEach { partial.add(it) }
        assertNull(partial.result())
        val forged = PhotoAssembler(meta)
        chunks.forEachIndexed { i, c -> forged.add(if (i == 1) PhotoChunk(meta, 1, ByteArray(c.data.size) { 9 }) else c) }
        assertNull(forged.result(), "the hash of the whole photo does not fit")
        assertEquals(false, PhotoAssembler(meta).add(PhotoChunk(meta.copy(transfer = "a".repeat(32)), 0, chunks[0].data)))
    }

    @Test
    fun badChunksAreDropped() {
        val bytes = photo(30_000)
        val meta = PhotoTransfer.meta(bytes, 100, 100, false)
        val good = Rumor(anna.hex(), 1, PhotoTransfer.KIND_CHUNK, listOf(listOf("p", ben.hex()), listOf("i", meta.transfer), listOf("x", meta.sha256), listOf("n", "0"), listOf("c", "2"), listOf("size", "30000"), listOf("dim", "100x100")), java.util.Base64.getEncoder().encodeToString(bytes.copyOf(20_000)))
        assertNotNull(PhotoTransfer.parseChunk(good))
        fun tag(name: String, value: String) = good.copy(tags = good.tags.map { if (it[0] == name) listOf(name, value) else it })
        assertNull(PhotoTransfer.parseChunk(tag("n", "2")), "index out of range")
        assertNull(PhotoTransfer.parseChunk(tag("n", "-1")))
        assertNull(PhotoTransfer.parseChunk(tag("c", "3")), "the number of chunks does not fit the size")
        assertNull(PhotoTransfer.parseChunk(tag("size", (PhotoTransfer.MAX_BYTES + 1).toString())))
        assertNull(PhotoTransfer.parseChunk(tag("i", "short")))
        assertNull(PhotoTransfer.parseChunk(tag("x", "zz")))
        assertNull(PhotoTransfer.parseChunk(tag("dim", "100000x5")))
        assertNull(PhotoTransfer.parseChunk(good.copy(content = "not base64 !!")))
        assertNull(PhotoTransfer.parseChunk(good.copy(content = java.util.Base64.getEncoder().encodeToString(ByteArray(100)))), "wrong chunk length")
        assertNull(PhotoTransfer.parseChunk(good.copy(kind = 14)))
    }

    @Test
    fun aReceiptNamesTheTransfer() {
        val meta = PhotoTransfer.meta(photo(10), 10, 10, false)
        val wrap = PhotoTransfer.receipt(ben, anna.hex(), meta.transfer)
        val rumor = assertNotNull(GiftWrap.unwrap(anna, wrap)).rumor
        assertEquals(meta.transfer, PhotoTransfer.parseReceipt(rumor))
        assertNull(PhotoTransfer.parseReceipt(rumor.copy(kind = 14)))
    }

    @Test
    fun sizeAndShapeAreChecked() {
        kotlin.test.assertFailsWith<IllegalArgumentException> { PhotoTransfer.meta(ByteArray(0), 1, 1, false) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { PhotoTransfer.meta(ByteArray(PhotoTransfer.MAX_BYTES + 1), 1, 1, false) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { PhotoTransfer.meta(ByteArray(5), 0, 1, false) }
        assertEquals(1, PhotoTransfer.chunkCount(1))
        assertEquals(1, PhotoTransfer.chunkCount(PhotoTransfer.CHUNK_BYTES))
        assertEquals(2, PhotoTransfer.chunkCount(PhotoTransfer.CHUNK_BYTES + 1))
    }
}
