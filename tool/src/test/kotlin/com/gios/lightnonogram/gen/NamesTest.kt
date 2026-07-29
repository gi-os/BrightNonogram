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

    // ---- typed seeds ------------------------------------------------------

    /**
     * A player can type any number, so the awkward values are the interesting
     * ones: negatives, zero and the ends of the Int range all have to produce a
     * real puzzle rather than an empty grid or a crash.
     */
    @Test
    fun `awkward typed seeds still make real puzzles`() {
        val seeds = listOf(0, 1, -1, 7, -4821, 999_999_999, Int.MAX_VALUE, Int.MIN_VALUE)
        for (size in listOf(10, 15)) {
            for (seed in seeds) {
                val sol = Generate.fromSeed(seed, size)
                assertTrue(sol != null, "seed $seed produced nothing at size $size")
                assertEquals(size * size, sol!!.size)
                val filled = sol.count { it == Generate.FILLED }
                assertTrue(filled in 1 until sol.size, "seed $seed at $size is blank or full")
                assertTrue(
                    Generate.isUniquelySolvable(sol, size, size),
                    "seed $seed at size $size is not uniquely solvable",
                )
                assertTrue(Names.nameFor(seed).isNotBlank(), "seed $seed has no name")
            }
        }
    }

    @Test
    fun `a typed seed reproduces the same puzzle at the same size, and differs across sizes`() {
        val seed = 4821
        assertTrue(Generate.fromSeed(seed, 10)!!.contentEquals(Generate.fromSeed(seed, 10)!!))
        assertTrue(Generate.fromSeed(seed, 15)!!.contentEquals(Generate.fromSeed(seed, 15)!!))
        assertEquals(100, Generate.fromSeed(seed, 10)!!.size)
        assertEquals(225, Generate.fromSeed(seed, 15)!!.size)
    }

    // ---- word seeds, Minecraft-style --------------------------------------

    /**
     * Minecraft hashes the seed box when it isn't a number, which is why
     * "gargamel" is a world people can pass around. String.hashCode is specified
     * by the JDK, so the same word gives the same puzzle on any device — the whole
     * point of a shareable seed.
     */
    @Test
    fun `a word is a seed, and always the same seed`() {
        assertEquals("gargamel".hashCode(), Names.seedFromText("gargamel"))
        assertEquals(Names.seedFromText("gargamel"), Names.seedFromText("  gargamel  "))
        assertEquals(Names.seedFromText("gargamel"), Names.seedFromText("gargamel"))
        // Case and spelling matter, as they do in Minecraft.
        assertTrue(Names.seedFromText("gargamel") != Names.seedFromText("Gargamel"))
    }

    @Test
    fun `digits are read as a number, not hashed`() {
        assertEquals(4821, Names.seedFromText("4821"))
        assertEquals(-7, Names.seedFromText("-7"))
        assertEquals(0, Names.seedFromText("0"))
        // Too big for an Int, so it falls back to the hash rather than failing.
        assertEquals("99999999999".hashCode(), Names.seedFromText("99999999999"))
    }

    @Test
    fun `blank text is not a seed`() {
        assertNull(Names.seedFromText(""))
        assertNull(Names.seedFromText("   "))
    }

    @Test
    fun `words produce real puzzles at both sizes`() {
        for (word in listOf("gargamel", "basil", "alex", "light phone", "a", "404 not a number")) {
            val seed = Names.seedFromText(word)!!
            for (size in listOf(10, 15)) {
                val sol = Generate.fromSeed(seed, size)
                assertTrue(sol != null, "'$word' produced nothing at size $size")
                assertTrue(
                    Generate.isUniquelySolvable(sol!!, size, size),
                    "'$word' at $size is not uniquely solvable",
                )
            }
        }
    }

    // ---- labels ------------------------------------------------------------

    @Test
    fun `a typed word is kept as the label and survives the round trip`() {
        val m = Made(Names.seedFromText("gargamel")!!, 10, "gargamel")
        val back = MadeCollection().with(m).encode()
        assertEquals(listOf(m), MadeCollection.decode(back).entries)
    }

    @Test
    fun `labels are sanitised so they cannot break the encoding`() {
        // Colons and commas are the separators; neither may survive.
        assertEquals("ab", Made.sanitizeLabel("a:b"))
        assertEquals("ab", Made.sanitizeLabel("a,b"))
        assertEquals("hello world", Made.sanitizeLabel("  hello   world  "))
        assertEquals("keep-this_one", Made.sanitizeLabel("keep-this_one"))
        assertNull(Made.sanitizeLabel(":::"))
        assertNull(Made.sanitizeLabel("   "))
        assertNull(Made.sanitizeLabel(null))
        assertTrue(Made.sanitizeLabel("x".repeat(200))!!.length <= Made.MAX_LABEL)
    }

    @Test
    fun `a label with separators in it still decodes to one entry`() {
        val nasty = Made(5, 10, Made.sanitizeLabel("a:b,c"))
        val encoded = MadeCollection().with(nasty).encode()
        val back = MadeCollection.decode(encoded)
        assertEquals(1, back.size, "encoded as: '$encoded'")
        assertEquals(5, back.entries.first().seed)
    }

    @Test
    fun `identity ignores the label, so replaying does not duplicate a tile`() {
        val c = MadeCollection()
            .with(Made(9, 10, "gargamel"))
            .with(Made(9, 10, null))
        assertEquals(1, c.size, "same seed and size is the same piece")
        assertTrue(c.has(Made(9, 10)))
    }

    @Test
    fun `entries without a label still decode, as older ones have none`() {
        val back = MadeCollection.decode("10:1,15:2:some name")
        assertEquals(2, back.size)
        assertNull(back.entries[0].label)
        assertEquals("some name", back.entries[1].label)
    }
}
