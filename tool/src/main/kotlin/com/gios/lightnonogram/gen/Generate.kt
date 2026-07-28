package com.gios.lightnonogram.gen

import kotlin.random.Random

/**
 * On-device puzzle generation.
 *
 * A trimmed port of `tools/picross-gen` — same line-solver algorithm, but only
 * the parts needed at runtime (no image loading, no pack writing, no difficulty
 * tiering). Kept as one self-contained file with no Android imports so it
 * compiles under the Light SDK's restricted classpath and unit-tests on the JVM.
 *
 * Generation is cheap enough to do on tap. Measured on a desktop JVM, producing
 * one validated 10x10 takes ~0.2 ms including rejected attempts; even 20-50x
 * slower under ART that's a few milliseconds.
 *
 * The algorithm is documented at length in the generator; the short version is
 * that it deduces exactly the cells forced by each line and never guesses, so a
 * grid it can finish is provably solvable by logic alone.
 */
object Generate {

    const val UNKNOWN = -1
    const val EMPTY = 0
    const val FILLED = 1

    /** Bump this if the generation algorithm changes — old seeds stop reproducing. */
    const val ALGORITHM_VERSION = 1

    /**
     * The puzzle for a given seed.
     *
     * Deterministic: the same seed always yields the same picture, as long as
     * [ALGORITHM_VERSION] hasn't changed. That means "puzzle #4821" can be stored
     * as a single Int and shared as a code, with no puzzle data at all.
     *
     * @return the solution, or null if [maxAttempts] was exhausted (only
     *   realistic at low fill ratios, where most random grids are ambiguous).
     */
    fun fromSeed(
        seed: Int,
        size: Int = 10,
        fillRatio: Double = 0.58,
        smoothPasses: Int = 1,
        maxAttempts: Int = 2000,
    ): IntArray? {
        val rng = Random(seed)
        repeat(maxAttempts) {
            val g = randomGrid(size, size, fillRatio, smoothPasses, rng)
            if (isUniquelySolvable(g, size, size)) return g
        }
        return null
    }

    fun randomGrid(
        width: Int,
        height: Int,
        fillRatio: Double,
        smoothPasses: Int,
        rng: Random,
    ): IntArray {
        var cells = IntArray(width * height) { if (rng.nextDouble() < fillRatio) FILLED else EMPTY }
        repeat(smoothPasses) { cells = smooth(cells, width, height) }
        return cells
    }

    /**
     * Majority-of-neighbourhood pass. Turns salt-and-pepper noise into rounded
     * blobs, which both look less random and give longer, more satisfying clues.
     */
    private fun smooth(src: IntArray, width: Int, height: Int): IntArray {
        val out = IntArray(src.size)
        for (r in 0 until height) for (c in 0 until width) {
            var filled = 0
            var total = 0
            for (dr in -1..1) for (dc in -1..1) {
                val rr = r + dr
                val cc = c + dc
                if (rr in 0 until height && cc in 0 until width) {
                    total++
                    if (src[rr * width + cc] == FILLED) filled++
                }
            }
            out[r * width + c] = if (filled * 2 > total) FILLED else EMPTY
        }
        return out
    }

    /** Rejects blank grids, full grids, and anything line logic can't finish. */
    fun isUniquelySolvable(solution: IntArray, width: Int, height: Int): Boolean {
        val filled = solution.count { it == FILLED }
        if (filled == 0 || filled == solution.size) return false

        val rowClues = (0 until height).map { r -> runs(IntArray(width) { c -> solution[r * width + c] }) }
        val colClues = (0 until width).map { c -> runs(IntArray(height) { r -> solution[r * width + c] }) }

        val grid = IntArray(width * height) { UNKNOWN }
        var guard = 0
        while (guard++ < 200) {
            var changed = false
            for (r in 0 until height) {
                val before = IntArray(width) { c -> grid[r * width + c] }
                val after = solveLine(before, rowClues[r]) ?: return false
                for (c in 0 until width) if (after[c] != before[c]) {
                    grid[r * width + c] = after[c]; changed = true
                }
            }
            for (c in 0 until width) {
                val before = IntArray(height) { r -> grid[r * width + c] }
                val after = solveLine(before, colClues[c]) ?: return false
                for (r in 0 until height) if (after[r] != before[r]) {
                    grid[r * width + c] = after[r]; changed = true
                }
            }
            if (!changed) break
        }
        return grid.none { it == UNKNOWN }
    }

    /**
     * Optimal single-line solver: returns the line with every forced cell filled
     * in, or null if the clues can't be satisfied.
     *
     * Two O(n*k) passes — backward feasibility, then forward reachability
     * recording whether each cell *can* be filled and *can* be empty across all
     * valid placements. A cell that can only be one thing is forced.
     */
    fun solveLine(cells: IntArray, clues: List<Int>): IntArray? {
        val n = cells.size
        val k = clues.size

        val feasible = Array(n + 1) { BooleanArray(k + 1) }
        for (j in 0..k) feasible[n][j] = (j == k)
        for (i in n - 1 downTo 0) {
            for (j in k downTo 0) {
                var ok = false
                if (cells[i] != FILLED) ok = feasible[i + 1][j]
                if (!ok && j < k && fits(cells, i, clues[j])) {
                    val next = i + clues[j] + 1
                    ok = if (next > n) feasible[n][j + 1] else feasible[next][j + 1]
                }
                feasible[i][j] = ok
            }
        }
        if (!feasible[0][0]) return null

        val reach = Array(n + 1) { BooleanArray(k + 1) }
        reach[0][0] = true
        val canFill = BooleanArray(n)
        val canEmpty = BooleanArray(n)

        for (i in 0 until n) {
            for (j in 0..k) {
                if (!reach[i][j] || !feasible[i][j]) continue
                if (cells[i] != FILLED && feasible[i + 1][j]) {
                    canEmpty[i] = true
                    reach[i + 1][j] = true
                }
                if (j < k && fits(cells, i, clues[j])) {
                    val len = clues[j]
                    val next = i + len + 1
                    val tail = if (next > n) feasible[n][j + 1] else feasible[next][j + 1]
                    if (tail) {
                        for (t in i until i + len) canFill[t] = true
                        if (i + len < n) {
                            canEmpty[i + len] = true
                            reach[next][j + 1] = true
                        }
                    }
                }
            }
        }

        val out = cells.copyOf()
        for (i in 0 until n) {
            val f = canFill[i]
            val e = canEmpty[i]
            when {
                f && !e -> out[i] = FILLED
                e && !f -> out[i] = EMPTY
                !f && !e -> return null
            }
        }
        return out
    }

    private fun fits(cells: IntArray, start: Int, len: Int): Boolean {
        val n = cells.size
        if (start + len > n) return false
        for (t in start until start + len) if (cells[t] == EMPTY) return false
        if (start + len < n && cells[start + len] == FILLED) return false
        return true
    }

    /** Run lengths; a blank line yields an empty list here (clue-space, not display-space). */
    fun runs(line: IntArray): List<Int> {
        val out = ArrayList<Int>()
        var run = 0
        for (v in line) {
            if (v == FILLED) run++ else if (run > 0) { out.add(run); run = 0 }
        }
        if (run > 0) out.add(run)
        return out
    }
}
