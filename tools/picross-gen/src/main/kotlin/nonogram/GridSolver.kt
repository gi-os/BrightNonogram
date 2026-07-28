package nonogram

sealed class SolveResult {
    /**
     * The grid was fully determined by pure logic — no guessing, no
     * backtracking. This is the *only* result worth shipping as a puzzle.
     *
     * @param passes  full row+column sweeps needed. Rough difficulty signal.
     * @param meanDepth average sweep at which a cell got resolved. A better
     *   size-independent difficulty signal than [passes]: a puzzle where most
     *   cells fall out immediately scores near 1.0, one that unravels slowly
     *   scores higher.
     */
    data class Solved(val grid: Grid, val passes: Int, val meanDepth: Double) : SolveResult()

    /** Line logic hit a fixpoint with cells still unknown: needs guessing, or has multiple solutions. Reject. */
    data class Ambiguous(val grid: Grid, val passes: Int, val unknownCells: Int) : SolveResult()

    /** The clues are mutually unsatisfiable. */
    object Contradiction : SolveResult()
}

/**
 * Solves a whole grid by running [LineSolver] over rows and columns to a
 * fixpoint, re-queueing any line a deduction touched.
 *
 * Deliberately does **not** backtrack. A puzzle that requires guessing is a bad
 * Picross puzzle, so "line logic gets stuck" and "puzzle is rejected" are the
 * same event here.
 */
object GridSolver {

    fun solve(
        rowClues: List<IntArray>,
        colClues: List<IntArray>,
        width: Int,
        height: Int,
        maxPasses: Int = 200,
    ): SolveResult {
        val grid = Grid(width, height)
        val resolvedAt = IntArray(width * height)   // 0 = still unknown

        var rowDirty = BooleanArray(height) { true }
        var colDirty = BooleanArray(width) { true }
        var passes = 0

        while (passes < maxPasses) {
            passes++
            var changed = false

            for (r in 0 until height) {
                if (!rowDirty[r]) continue
                rowDirty[r] = false
                val before = grid.row(r)
                val after = LineSolver.solve(before, rowClues[r]) ?: return SolveResult.Contradiction
                for (c in 0 until width) {
                    if (after[c] != before[c]) {
                        grid[r, c] = after[c]
                        resolvedAt[r * width + c] = passes
                        colDirty[c] = true
                        changed = true
                    }
                }
            }

            for (c in 0 until width) {
                if (!colDirty[c]) continue
                colDirty[c] = false
                val before = grid.col(c)
                val after = LineSolver.solve(before, colClues[c]) ?: return SolveResult.Contradiction
                for (r in 0 until height) {
                    if (after[r] != before[r]) {
                        grid[r, c] = after[r]
                        resolvedAt[r * width + c] = passes
                        rowDirty[r] = true
                        changed = true
                    }
                }
            }

            if (!changed) break
        }

        val unknown = grid.cells.count { it == UNKNOWN }
        if (unknown > 0) return SolveResult.Ambiguous(grid, passes, unknown)

        val meanDepth = resolvedAt.average()
        return SolveResult.Solved(grid, passes, meanDepth)
    }

    /** Convenience: derive clues from a known solution and try to solve it back. */
    fun solveFrom(solution: Grid, maxPasses: Int = 200): SolveResult =
        solve(solution.rowClues(), solution.colClues(), solution.width, solution.height, maxPasses)
}
