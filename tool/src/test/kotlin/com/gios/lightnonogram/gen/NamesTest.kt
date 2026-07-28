package com.gios.lightnonogram.gen

import com.gios.lightnonogram.game.Made
import com.gios.lightnonogram.game.MadeCollection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The collection stores only a seed, so the name and the picture are both
 * regenerated from it. If naming ever stopped being deterministic, every entry a
 * player had collected would silently rename itself.
 */
class NamesTest {

    @Test
    fun `a seed always yields the same name`() {
        for (seed in listOf(0, 1, 7, 4821, -3, Int.MAX_VALUE, Int.MIN_VALUE)) {
            assertEquals(Names.nameFor(seed), Names.nameFor(seed), "seed $seed drifted")
        }
    }

    @Test
    fun `names are non-empty and readable`() {
        for (seed in 1..500) {
            val n = Names.nameFor(seed)
            assertTrue(n.length in 6..48, "odd length ${n.length}: '$n'")
            assertTrue(n.trim() == n, "padded: '$n'")
            assertTrue(n.first().isUpperCase(), "should start capitalised: '$n'")
            assertTrue(!n.contains("  "), "double space: '$n'")
        }
    }

    /**
     * Seeds come from the clock, so consecutive puzzles are milliseconds apart.
     * A weak mix would name a whole session's worth of puzzles almost identically.
     */
    @Test
    fun `adjacent seeds get unrelated names`() {
        val base = 1_700_000_000
        val names = (0 until 400).map { Names.nameFor(base + it) }
        val distinct = names.distinct().size
        assertTrue(distinct > 380, "only $distinct distinct names from 400 adjacent seeds")
    }

    @Test
    fun `variety is large enough that repeats are rare`() {
        assertTrue(Names.approximateVariety > 10_000, "only ${Names.approximateVariety} combinations")
        val names = (1..3000).map { Names.nameFor(it * 7919) }
        val distinct = names.distinct().size
        assertTrue(distinct > 2700, "only $distinct distinct across 3000 seeds")
    }

    // ---- the collection itself --------------------------------------------

    @Test
    fun `collection round-trips`() {
        val c = MadeCollection().with(Made(42, 10)).with(Made(7, 15))
        assertEquals(2, c.size)
        val back = MadeCollection.decode(c.encode())
        assertEquals(c.entries, back.entries)
    }

    @Test
    fun `newest first, and re-solving does not duplicate`() {
        val c = MadeCollection().with(Made(1, 10)).with(Made(2, 10)).with(Made(1, 10))
        assertEquals(listOf(Made(1, 10), Made(2, 10)), c.entries)
        assertTrue(c.has(Made(1, 10)))
    }

    @Test
    fun `size and seed are both part of identity`() {
        val c = MadeCollection().with(Made(5, 10)).with(Made(5, 15))
        assertEquals(2, c.size, "same seed at a different size is a different piece")
    }

    @Test
    fun `collection is capped`() {
        var c = MadeCollection()
        for (i in 1..MadeCollection.LIMIT + 50) c = c.with(Made(i, 10))
        assertEquals(MadeCollection.LIMIT, c.size)
        assertEquals(Made(MadeCollection.LIMIT + 50, 10), c.entries.first(), "newest should survive")
    }

    @Test
    fun `a corrupt entry costs one piece, not the collection`() {
        val c = MadeCollection.decode("10:1,garbage,15:2,,99:3,abc:def")
        assertEquals(listOf(Made(1, 10), Made(2, 15)), c.entries)
        assertEquals(MadeCollection(), MadeCollection.decode(null))
        assertEquals(MadeCollection(), MadeCollection.decode("   "))
        assertNull(Made.decode("nonsense"))
        assertNull(Made.decode("999:1"), "an implausible size should be rejected")
    }

    @Test
    fun `every collected seed still regenerates its puzzle`() {
        // The collection is worthless if a stored seed can't be replayed.
        for (size in listOf(10, 15)) {
            var replayed = 0
            for (seed in 1..40) {
                val sol = Generate.fromSeed(seed, size) ?: continue
                assertEquals(size * size, sol.size)
                assertTrue(Generate.isUniquelySolvable(sol, size, size), "size $size seed $seed")
                replayed++
            }
            assertTrue(replayed >= 35, "only $replayed/40 seeds regenerated at size $size")
        }
    }
}
