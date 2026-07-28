package nonogram

import kotlin.random.Random

/**
 * Correctness harness for the solver, runnable as `picross-gen selfcheck`.
 *
 * The interesting property is not "does it solve puzzles" but **"is every
 * deduction it makes actually forced?"** A solver that over-deduces silently
 * produces packs full of unsolvable puzzles, and you would not find out until a
 * user got stuck. So this cross-checks the line solver against exhaustive
 * enumeration on small grids.
 */
object SelfCheck {

    fun run(trials: Int = 500, seed: Long = 7L): Boolean {
        var failures = 0

        failures += unit("overlap [8] in 10 forces cells 2..7") {
            LineSolver.solve(IntArray(10) { UNKNOWN }, intArrayOf(8))
                .contentEqualsOrNull(intArrayOf(-1, -1, 1, 1, 1, 1, 1, 1, -1, -1))
        }
        failures += unit("[5] in 5 fills the line") {
            LineSolver.solve(IntArray(5) { UNKNOWN }, intArrayOf(5))
                .contentEqualsOrNull(IntArray(5) { FILLED })
        }
        failures += unit("no clues empties the line") {
            LineSolver.solve(IntArray(5) { UNKNOWN }, intArrayOf())
                .contentEqualsOrNull(IntArray(5) { EMPTY })
        }
        failures += unit("[1,3] in 5 is an exact fit") {
            LineSolver.solve(IntArray(5) { UNKNOWN }, intArrayOf(1, 3))
                .contentEqualsOrNull(intArrayOf(1, 0, 1, 1, 1))
        }
        failures += unit("[1] in 5 forces nothing") {
            LineSolver.solve(IntArray(5) { UNKNOWN }, intArrayOf(1))
                .contentEqualsOrNull(IntArray(5) { UNKNOWN })
        }
        failures += unit("[4] in 3 is a contradiction") {
            LineSolver.solve(IntArray(3) { UNKNOWN }, intArrayOf(4)) == null
        }
        failures += unit("clue against an all-empty line is a contradiction") {
            LineSolver.solve(IntArray(5) { EMPTY }, intArrayOf(1)) == null
        }
        failures += unit("a known FILLED cell pins [1] in 5") {
            LineSolver.solve(intArrayOf(-1, 1, -1, -1, -1), intArrayOf(1))
                .contentEqualsOrNull(intArrayOf(0, 1, 0, 0, 0))
        }
        failures += unit("a known EMPTY splits the line, only one segment fits [3]") {
            LineSolver.solve(intArrayOf(-1, -1, -1, 0, -1, -1), intArrayOf(3))
                .contentEqualsOrNull(intArrayOf(1, 1, 1, 0, 0, 0))
        }
        failures += unit("two viable segments force nothing") {
            LineSolver.solve(intArrayOf(-1, -1, 0, -1, -1), intArrayOf(2))
                .contentEqualsOrNull(intArrayOf(-1, -1, 0, -1, -1))
        }
        failures += unit("round-trips through the bit encoding") {
            val g = Generator.randomGrid(13, 7, 0.5, 0, Random(1))
            Grid.fromBits(g.toBits(), 13, 7).cells.contentEquals(g.cells)
        }

        // Regression: Otsu used to return a cutoff that excluded the darkest
        // bin, so flat art whose dark cells are exactly 0.0 thresholded to a
        // completely blank grid — silently, at some sizes but not others.
        failures += unit("flat black-on-white art never thresholds to a blank grid") {
            val png = syntheticCross()
            try {
                listOf(8, 10, 12, 15, 18, 20, 25).all { n ->
                    val g = ImageSource.load(png, n, n)
                    val ratio = g.filledCount.toDouble() / (n * n)
                    ratio > 0.05 && ratio < 0.95
                }
            } finally { png.delete() }
        }

        // --- property test against exhaustive enumeration --------------------
        val rng = Random(seed)
        var solved = 0
        var ambiguous = 0
        var wrongDeduction = 0
        var falseUnique = 0
        var falseContradiction = 0

        repeat(trials) {
            val n = listOf(4, 5, 6).random(rng)
            val truth = Grid(n, n, IntArray(n * n) { if (rng.nextDouble() < 0.5) FILLED else EMPTY })
            val rc = truth.rowClues()
            val cc = truth.colClues()

            when (val res = GridSolver.solve(rc, cc, n, n)) {
                is SolveResult.Contradiction -> falseContradiction++   // truth exists, so this is always a bug
                is SolveResult.Solved -> {
                    solved++
                    if (!res.grid.cells.contentEquals(truth.cells)) wrongDeduction++
                    if (countSolutions(rc, cc, n, n, cap = 2) != 1) falseUnique++
                }
                is SolveResult.Ambiguous -> {
                    ambiguous++
                    // partial deductions must still agree with a real solution
                    for (i in truth.cells.indices) {
                        if (res.grid.cells[i] != UNKNOWN && res.grid.cells[i] != truth.cells[i]) {
                            wrongDeduction++
                        }
                    }
                }
            }
        }

        failures += unit("$trials random grids: no false contradictions") { falseContradiction == 0 }
        failures += unit("$trials random grids: every deduction agrees with truth") { wrongDeduction == 0 }
        failures += unit("'Solved' always means exhaustively unique") { falseUnique == 0 }
        println("   (solved=$solved ambiguous=$ambiguous)")

        println(if (failures == 0) "\nALL PASS" else "\n$failures FAILED")
        return failures == 0
    }

    /** A hard-edged black cross on white — the pathological case for Otsu. */
    private fun syntheticCross(): java.io.File {
        val s = 240
        val img = java.awt.image.BufferedImage(s, s, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = java.awt.Color.WHITE
        g.fillRect(0, 0, s, s)
        g.color = java.awt.Color.BLACK
        g.fillRect((s * 0.40).toInt(), (s * 0.10).toInt(), (s * 0.20).toInt(), (s * 0.80).toInt())
        g.fillRect((s * 0.10).toInt(), (s * 0.40).toInt(), (s * 0.80).toInt(), (s * 0.20).toInt())
        g.dispose()
        val f = java.io.File.createTempFile("picross-selfcheck", ".png")
        javax.imageio.ImageIO.write(img, "png", f)
        return f
    }

    private inline fun unit(name: String, body: () -> Boolean): Int {
        val ok = try { body() } catch (e: Throwable) { println("   threw: $e"); false }
        println((if (ok) "PASS  " else "FAIL  ") + name)
        return if (ok) 0 else 1
    }

    private fun IntArray?.contentEqualsOrNull(other: IntArray): Boolean =
        this != null && this.contentEquals(other)

    /** Every arrangement of [clues] in a line of length [n]. */
    fun enumerateLines(n: Int, clues: IntArray): List<IntArray> {
        val out = ArrayList<IntArray>()
        fun rec(pos: Int, ci: Int, acc: IntArray) {
            if (ci == clues.size) {
                val done = acc.copyOf()
                for (t in pos until n) done[t] = EMPTY
                out.add(done); return
            }
            val len = clues[ci]
            var start = pos
            while (start + len <= n) {
                val next = acc.copyOf()
                for (t in pos until start) next[t] = EMPTY
                for (t in start until start + len) next[t] = FILLED
                if (start + len < n) {
                    next[start + len] = EMPTY
                    rec(start + len + 1, ci + 1, next)
                } else if (ci + 1 == clues.size) {
                    out.add(next)
                }
                start++
            }
        }
        rec(0, 0, IntArray(n) { UNKNOWN })
        return out
    }

    /** Exhaustive solution count, capped at [cap]. Only viable for tiny grids. */
    fun countSolutions(
        rowClues: List<IntArray>,
        colClues: List<IntArray>,
        w: Int,
        h: Int,
        cap: Int = 2,
    ): Int {
        val options = rowClues.map { enumerateLines(w, it) }
        var count = 0
        val chosen = ArrayList<IntArray>()

        fun columnsStillViable(): Boolean {
            val r = chosen.size
            for (c in 0 until w) {
                val partial = IntArray(h) { i -> if (i < r) chosen[i][c] else UNKNOWN }
                if (LineSolver.solve(partial, colClues[c]) == null) return false
            }
            return true
        }

        fun rec() {
            if (count >= cap) return
            if (chosen.size == h) { count++; return }
            for (cand in options[chosen.size]) {
                chosen.add(cand)
                if (columnsStillViable()) rec()
                chosen.removeAt(chosen.size - 1)
                if (count >= cap) return
            }
        }
        rec()
        return count
    }
}
