package com.gios.lightnonogram.gen

import com.gios.lightnonogram.game.Board
import com.gios.lightnonogram.game.Tool
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GenerateTest {

    // ---- line solver ------------------------------------------------------

    @Test
    fun `line solver makes only forced deductions`() {
        assertEquals(
            listOf(-1, -1, 1, 1, 1, 1, 1, 1, -1, -1),
            Generate.solveLine(IntArray(10) { Generate.UNKNOWN }, listOf(8))!!.toList(),
        )
        assertEquals(
            List(5) { Generate.FILLED },
            Generate.solveLine(IntArray(5) { Generate.UNKNOWN }, listOf(5))!!.toList(),
        )
        assertEquals(
            List(5) { Generate.EMPTY },
            Generate.solveLine(IntArray(5) { Generate.UNKNOWN }, emptyList())!!.toList(),
        )
        assertEquals(
            List(5) { Generate.UNKNOWN },
            Generate.solveLine(IntArray(5) { Generate.UNKNOWN }, listOf(1))!!.toList(),
            "a single clue in 5 cells forces nothing",
        )
        assertNull(Generate.solveLine(IntArray(3) { Generate.UNKNOWN }, listOf(4)))
        assertEquals(
            listOf(1, 1, 1, 0, 0, 0),
            Generate.solveLine(intArrayOf(-1, -1, -1, 0, -1, -1), listOf(3))!!.toList(),
            "a known empty splits the line; only one segment fits",
        )
    }

    // ---- determinism ------------------------------------------------------

    @Test
    fun `the same seed always produces the same puzzle`() {
        for (seed in listOf(1, 7, 42, 1234, 99999)) {
            val a = Generate.fromSeed(seed)
            val b = Generate.fromSeed(seed)
            assertNotNull(a, "seed $seed produced nothing")
            assertNotNull(b)
            assertTrue(
                a.contentEquals(b),
                "seed $seed is not deterministic — puzzle codes would be worthless",
            )
        }
    }

    @Test
    fun `different seeds mostly produce different puzzles`() {
        val seen = (1..200).mapNotNull { Generate.fromSeed(it) }.map { it.joinToString("") }
        assertTrue(seen.size >= 190, "too many seeds failed to generate: ${seen.size}/200")
        val distinct = seen.distinct().size
        assertTrue(distinct >= seen.size - 5, "only $distinct distinct puzzles from ${seen.size} seeds")
    }

    // ---- the core guarantee -----------------------------------------------

    @Test
    fun `every generated puzzle is uniquely solvable by logic`() {
        var made = 0
        for (seed in 1..400) {
            val sol = Generate.fromSeed(seed) ?: continue
            made++
            assertEquals(100, sol.size)
            assertTrue(
                Generate.isUniquelySolvable(sol, 10, 10),
                "seed $seed produced a puzzle that isn't uniquely solvable",
            )
            val filled = sol.count { it == Generate.FILLED }
            assertTrue(filled in 1..99, "seed $seed is blank or full")
        }
        assertTrue(made >= 380, "expected nearly every seed to yield a puzzle, got $made/400")
    }

    @Test
    fun `rejects blank and full grids outright`() {
        assertFalse(Generate.isUniquelySolvable(IntArray(100), 10, 10))
        assertFalse(Generate.isUniquelySolvable(IntArray(100) { 1 }, 10, 10))
    }

    @Test
    fun `hard settings still terminate or give up cleanly`() {
        // Fill 0.45 at 10x10 has a low yield; the point is that it either returns
        // a valid puzzle or null, never hangs and never returns something broken.
        var ok = 0
        for (seed in 1..40) {
            val sol = Generate.fromSeed(seed, fillRatio = 0.45, smoothPasses = 0, maxAttempts = 400)
            if (sol != null) {
                assertTrue(Generate.isUniquelySolvable(sol, 10, 10), "seed $seed")
                ok++
            }
        }
        assertTrue(ok > 0, "hard settings produced nothing at all in 40 seeds")
    }

    // ---- end to end -------------------------------------------------------

    @Test
    fun `generated puzzles are playable and winnable on a real board`() {
        for (seed in 1..60) {
            val sol = Generate.fromSeed(seed) ?: continue
            val board = Board(10, 10, sol)

            // Clue lists the Board derives must match the generator's own view.
            for (r in 0 until 10) {
                val expected = Generate.runs(IntArray(10) { c -> sol[r * 10 + c] })
                    .ifEmpty { listOf(0) }
                assertEquals(expected, board.rowClues[r], "seed $seed row $r")
            }

            // Play it by dragging each run, as a person would.
            for (r in 0 until 10) {
                var c = 0
                while (c < 10) {
                    if (!board.solutionAt(r, c)) { c++; continue }
                    var end = c
                    while (end + 1 < 10 && board.solutionAt(r, end + 1)) end++
                    board.beginStroke(r, c, Tool.FILL)
                    if (end != c) board.extendStroke(r, end)
                    board.endStroke()
                    c = end + 1
                }
            }
            assertTrue(board.isSolved, "seed $seed not solved by dragging its own runs")
        }
    }

    @Test
    fun `smoothing produces longer clue runs than raw noise`() {
        fun meanRunLength(smooth: Int): Double {
            var runs = 0
            var cells = 0
            for (seed in 1..60) {
                val g = Generate.randomGrid(10, 10, 0.58, smooth, Random(seed.toLong()))
                for (r in 0 until 10) {
                    val rr = Generate.runs(IntArray(10) { c -> g[r * 10 + c] })
                    runs += rr.size
                    cells += rr.sum()
                }
            }
            return if (runs == 0) 0.0 else cells.toDouble() / runs
        }
        val noisy = meanRunLength(0)
        val smoothed = meanRunLength(1)
        assertTrue(
            smoothed > noisy,
            "smoothing should lengthen runs (noisy=$noisy smoothed=$smoothed)",
        )
    }
}
