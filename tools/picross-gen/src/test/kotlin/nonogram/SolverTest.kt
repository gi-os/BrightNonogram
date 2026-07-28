package nonogram

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The same checks [SelfCheck] runs, as a JUnit suite for `./gradlew test`.
 *
 * The property test at the bottom is the one that matters: it proves the solver
 * never invents a deduction, by comparing against exhaustive enumeration.
 */
class SolverTest {

    private fun line(vararg v: Int) = v

    @Test
    fun `overlap deduction`() {
        val got = LineSolver.solve(IntArray(10) { UNKNOWN }, intArrayOf(8))
        assertNotNull(got)
        assertEquals(
            line(-1, -1, 1, 1, 1, 1, 1, 1, -1, -1).toList(),
            got.toList(),
        )
    }

    @Test
    fun `exact fits and empties`() {
        assertEquals(
            IntArray(5) { FILLED }.toList(),
            LineSolver.solve(IntArray(5) { UNKNOWN }, intArrayOf(5))!!.toList(),
        )
        assertEquals(
            IntArray(5) { EMPTY }.toList(),
            LineSolver.solve(IntArray(5) { UNKNOWN }, intArrayOf())!!.toList(),
        )
        assertEquals(
            line(1, 0, 1, 1, 1).toList(),
            LineSolver.solve(IntArray(5) { UNKNOWN }, intArrayOf(1, 3))!!.toList(),
        )
    }

    @Test
    fun `deduces nothing when nothing is forced`() {
        assertEquals(
            IntArray(5) { UNKNOWN }.toList(),
            LineSolver.solve(IntArray(5) { UNKNOWN }, intArrayOf(1))!!.toList(),
        )
        assertEquals(
            line(-1, -1, 0, -1, -1).toList(),
            LineSolver.solve(line(-1, -1, 0, -1, -1), intArrayOf(2))!!.toList(),
        )
    }

    @Test
    fun `detects contradictions`() {
        assertNull(LineSolver.solve(IntArray(3) { UNKNOWN }, intArrayOf(4)))
        assertNull(LineSolver.solve(IntArray(5) { EMPTY }, intArrayOf(1)))
        assertNull(LineSolver.solve(line(-1, -1, 0, -1, -1), intArrayOf(3)))
    }

    @Test
    fun `known cells drive further deductions`() {
        assertEquals(
            line(0, 1, 0, 0, 0).toList(),
            LineSolver.solve(line(-1, 1, -1, -1, -1), intArrayOf(1))!!.toList(),
        )
        assertEquals(
            line(1, 1, 1, 0, 0, 0).toList(),
            LineSolver.solve(line(-1, -1, -1, 0, -1, -1), intArrayOf(3))!!.toList(),
        )
    }

    @Test
    fun `bit encoding round trips`() {
        repeat(20) { seed ->
            val g = Generator.randomGrid(17, 11, 0.5, 0, Random(seed.toLong()))
            val back = Grid.fromBits(g.toBits(), 17, 11)
            assertTrue(back.cells.contentEquals(g.cells), "seed $seed")
        }
    }

    /**
     * Regression: Otsu's cutoff used to exclude the darkest histogram bin, so
     * flat art thresholded to a blank grid at some sizes.
     */
    @Test
    fun `flat art never thresholds to a blank grid`() {
        val png = TestArt.cross()
        try {
            for (n in listOf(8, 10, 12, 15, 18, 20, 25)) {
                val ratio = ImageSource.load(png, n, n).filledCount.toDouble() / (n * n)
                assertTrue(ratio > 0.05 && ratio < 0.95, "size $n produced fill ratio $ratio")
            }
        } finally { png.delete() }
    }

    @Test
    fun `every deduction is genuinely forced`() {
        val rng = Random(7)
        repeat(400) {
            val n = listOf(4, 5, 6).random(rng)
            val truth = Grid(n, n, IntArray(n * n) { if (rng.nextDouble() < 0.5) FILLED else EMPTY })
            val rc = truth.rowClues()
            val cc = truth.colClues()

            when (val res = GridSolver.solve(rc, cc, n, n)) {
                is SolveResult.Contradiction ->
                    throw AssertionError("claimed contradiction but a solution exists")
                is SolveResult.Solved -> {
                    assertTrue(res.grid.cells.contentEquals(truth.cells), "solved to the wrong grid")
                    assertEquals(1, SelfCheck.countSolutions(rc, cc, n, n, cap = 2), "not actually unique")
                }
                is SolveResult.Ambiguous ->
                    for (i in truth.cells.indices) {
                        if (res.grid.cells[i] != UNKNOWN) {
                            assertEquals(truth.cells[i], res.grid.cells[i], "bad partial deduction")
                        }
                    }
            }
        }
    }

    @Test
    fun `repair makes ambiguous art solvable with few flips`() {
        val png = TestArt.ring()
        try {
            var repaired = 0
            for (n in listOf(10, 12, 15, 18)) {
                val g = ImageSource.load(png, n, n)
                if (Generator.validate(g) != null) continue
                val fixed = Generator.repair(g, maxFlips = 12, rng = Random(0))
                assertNotNull(fixed, "size $n could not be repaired")
                assertTrue(fixed.second <= 4, "size $n needed ${fixed.second} flips")
                assertTrue(GridSolver.solveFrom(fixed.first.grid) is SolveResult.Solved)
                repaired++
            }
            assertTrue(repaired > 0, "expected at least one ambiguous ring to exercise repair")
        } finally { png.delete() }
    }
}

private object TestArt {
    fun cross() = draw { g, s ->
        g.fillRect((s * 0.40).toInt(), (s * 0.10).toInt(), (s * 0.20).toInt(), (s * 0.80).toInt())
        g.fillRect((s * 0.10).toInt(), (s * 0.40).toInt(), (s * 0.80).toInt(), (s * 0.20).toInt())
    }

    fun ring() = draw { g, s ->
        g.fillOval((s * 0.08).toInt(), (s * 0.08).toInt(), (s * 0.84).toInt(), (s * 0.84).toInt())
        g.color = java.awt.Color.WHITE
        g.fillOval((s * 0.30).toInt(), (s * 0.30).toInt(), (s * 0.40).toInt(), (s * 0.40).toInt())
    }

    private fun draw(body: (java.awt.Graphics2D, Int) -> Unit): java.io.File {
        val s = 240
        val img = java.awt.image.BufferedImage(s, s, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = java.awt.Color.WHITE
        g.fillRect(0, 0, s, s)
        g.color = java.awt.Color.BLACK
        body(g, s)
        g.dispose()
        val f = java.io.File.createTempFile("picross-test", ".png")
        javax.imageio.ImageIO.write(img, "png", f)
        return f
    }
}
