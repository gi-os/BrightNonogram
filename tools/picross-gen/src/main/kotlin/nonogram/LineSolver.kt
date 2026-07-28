package nonogram

/**
 * Optimal single-line nonogram solver.
 *
 * Given a line's current known/unknown cells and its clues, this deduces
 * *every* cell whose value is forced by that line in isolation — no more, no
 * less. It never guesses.
 *
 * How it works, in two O(n*k) passes:
 *
 *  1. **Feasibility (backward).** `feasible[i][j]` answers "can clues `j..k-1`
 *     be laid out in cells `i..n-1` without contradicting a known cell?"
 *
 *  2. **Reachability (forward).** Walk the states reachable from `(0, 0)` that
 *     are also feasible. Every transition taken tells us something concrete: a
 *     "leave blank" transition proves cell `i` *can* be empty; a "place block
 *     j here" transition proves those cells *can* be filled.
 *
 * A cell that can only ever be filled is forced FILLED; one that can only ever
 * be empty is forced EMPTY; one that can be neither means the line is
 * unsatisfiable.
 *
 * This "can it be X in *some* valid arrangement" formulation is what makes the
 * solver optimal — a simpler leftmost/rightmost overlap check would miss
 * deductions that arise from already-known cells splitting the line.
 */
object LineSolver {

    /**
     * @return the line with all forced cells filled in, or `null` if the clues
     *   cannot be satisfied given the known cells.
     */
    fun solve(cells: IntArray, clues: IntArray): IntArray? {
        val n = cells.size
        val k = clues.size

        // --- pass 1: backward feasibility -----------------------------------
        // feasible[i][j] = clues[j until k] fit in cells[i until n]
        val feasible = Array(n + 1) { BooleanArray(k + 1) }
        for (j in 0..k) feasible[n][j] = (j == k)

        for (i in n - 1 downTo 0) {
            for (j in k downTo 0) {
                var ok = false
                // option A: leave cell i blank
                if (cells[i] != FILLED) ok = feasible[i + 1][j]
                // option B: start block j at cell i
                if (!ok && j < k) {
                    val len = clues[j]
                    if (fitsBlockAt(cells, i, len)) {
                        val next = i + len + 1
                        ok = if (next > n) feasible[n][j + 1] else feasible[next][j + 1]
                    }
                }
                feasible[i][j] = ok
            }
        }

        if (!feasible[0][0]) return null

        // --- pass 2: forward reachability, marking possible cell states ------
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

                if (j < k) {
                    val len = clues[j]
                    if (fitsBlockAt(cells, i, len)) {
                        val next = i + len + 1
                        val tailOk =
                            if (next > n) feasible[n][j + 1] else feasible[next][j + 1]
                        if (tailOk) {
                            for (t in i until i + len) canFill[t] = true
                            if (i + len < n) {
                                canEmpty[i + len] = true   // the mandatory gap
                                reach[next][j + 1] = true
                            }
                        }
                    }
                }
            }
        }

        // --- collapse possibilities into deductions --------------------------
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

    /** Can a run of [len] sit at [start] — in bounds, no known EMPTY inside, no FILLED butting up after? */
    private fun fitsBlockAt(cells: IntArray, start: Int, len: Int): Boolean {
        val n = cells.size
        if (start + len > n) return false
        for (t in start until start + len) if (cells[t] == EMPTY) return false
        if (start + len < n && cells[start + len] == FILLED) return false
        return true
    }
}
