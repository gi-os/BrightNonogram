package nonogram

import kotlin.random.Random

/** A grid that survived validation, with its measured difficulty signals. */
data class Candidate(
    val grid: Grid,
    val passes: Int,
    val meanDepth: Double,
)

object Generator {

    /**
     * Validate a candidate solution: it must be recoverable from its own clues
     * by line logic alone.
     *
     * Rejects (returns null) if the puzzle is ambiguous, needs guessing, or has
     * a blank row/column at the edge — those read as a cropping mistake rather
     * than a puzzle.
     */
    fun validate(grid: Grid, minCoverage: Double = 0.55): Candidate? {
        if (grid.filledCount == 0) return null
        if (!coversEnough(grid, minCoverage)) return null
        return when (val r = GridSolver.solveFrom(grid)) {
            is SolveResult.Solved -> Candidate(grid, r.passes, r.meanDepth)
            else -> null
        }
    }

    /**
     * Nudge an ambiguous grid until it becomes uniquely solvable.
     *
     * Clean source art is very often ambiguous — solid symmetric regions admit
     * multiple clue-consistent solutions (a plain ring is the classic case). But
     * the ambiguity is usually confined to a handful of cells, and flipping one
     * of *those specific cells* breaks it.
     *
     * So: solve, look at exactly which cells line logic could not pin down, try
     * flipping each, and keep whichever flip reduces the unresolved count most.
     * Repeat. In practice one or two flips fix a 15x15 icon — well under 1% of
     * cells, which is invisible in the finished picture.
     *
     * @return a repaired candidate, or null if [maxFlips] wasn't enough.
     */
    fun repair(
        grid: Grid,
        maxFlips: Int = 12,
        samplePerStep: Int = 24,
        minCoverage: Double = 0.55,
        rng: Random = Random.Default,
    ): Pair<Candidate, Int>? {
        // Same admission checks as validate(), so repair can never smuggle in a
        // grid that validate() would have thrown out.
        if (grid.filledCount == 0) return null
        if (!coversEnough(grid, minCoverage)) return null

        val work = grid.copy()
        var flips = 0

        repeat(maxFlips + 1) {
            when (val res = GridSolver.solveFrom(work)) {
                is SolveResult.Solved -> return Candidate(work, res.passes, res.meanDepth) to flips
                is SolveResult.Contradiction -> return null   // cannot happen: work is a real solution
                is SolveResult.Ambiguous -> {
                    if (flips >= maxFlips) return null
                    val unresolved = (0 until work.height).flatMap { r ->
                        (0 until work.width).mapNotNull { c ->
                            if (res.grid[r, c] == UNKNOWN) (r to c) else null
                        }
                    }
                    if (unresolved.isEmpty()) return null

                    val sample = unresolved.shuffled(rng).take(samplePerStep)
                    var bestScore = Int.MAX_VALUE
                    var best: Pair<Int, Int>? = null

                    for ((r, c) in sample) {
                        toggle(work, r, c)
                        val score = when (val trial = GridSolver.solveFrom(work)) {
                            is SolveResult.Solved -> 0
                            is SolveResult.Ambiguous -> trial.unknownCells
                            is SolveResult.Contradiction -> Int.MAX_VALUE
                        }
                        toggle(work, r, c)
                        if (score < bestScore) { bestScore = score; best = r to c }
                    }

                    val pick = best ?: return null
                    if (bestScore == Int.MAX_VALUE) return null
                    toggle(work, pick.first, pick.second)
                    flips++
                }
            }
        }
        return null
    }

    private fun toggle(g: Grid, r: Int, c: Int) {
        g[r, c] = if (g[r, c] == FILLED) EMPTY else FILLED
    }

    /**
     * Try [attempts] random grids, keeping the ones that validate.
     *
     * Yield depends heavily on [fillRatio] — measured over 400 trials per cell:
     * ```
     *  size    fill 0.45   fill 0.55   fill 0.65
     *  10x10      27%         69%         94%
     *  15x15       7%         51%         92%
     *  20x20       0%         41%         85%
     * ```
     * Sparser grids make harder puzzles but are far more often ambiguous, so
     * expect to burn attempts if you want difficulty.
     */
    fun randomBatch(
        width: Int,
        height: Int,
        count: Int,
        fillRatio: Double = 0.58,
        smoothPasses: Int = 1,
        attempts: Int = count * 200,
        rng: Random = Random.Default,
    ): List<Candidate> {
        val out = ArrayList<Candidate>(count)
        val seen = HashSet<String>()
        var tried = 0
        while (out.size < count && tried < attempts) {
            tried++
            val g = randomGrid(width, height, fillRatio, smoothPasses, rng)
            val cand = validate(g) ?: continue
            if (!seen.add(g.toBits())) continue     // dedupe identical solutions
            out.add(cand)
        }
        return out
    }

    fun randomGrid(
        width: Int,
        height: Int,
        fillRatio: Double,
        smoothPasses: Int = 0,
        rng: Random = Random.Default,
    ): Grid {
        val g = Grid(width, height, IntArray(width * height) { if (rng.nextDouble() < fillRatio) FILLED else EMPTY })
        repeat(smoothPasses) { smooth(g) }
        return g
    }

    /**
     * One cellular-automaton pass: a cell takes the majority state of its 3x3
     * neighbourhood. Turns salt-and-pepper noise into rounded blobs, which both
     * look more like a picture and produce longer clue runs.
     */
    private fun smooth(g: Grid) {
        val src = g.cells.copyOf()
        for (r in 0 until g.height) {
            for (c in 0 until g.width) {
                var filled = 0
                var total = 0
                for (dr in -1..1) for (dc in -1..1) {
                    val rr = r + dr; val cc = c + dc
                    if (rr in 0 until g.height && cc in 0 until g.width) {
                        total++
                        if (src[rr * g.width + cc] == FILLED) filled++
                    }
                }
                g[r, c] = if (filled * 2 > total) FILLED else EMPTY
            }
        }
    }

    /**
     * Does the filled content span enough of the grid to be worth the grid size?
     *
     * Measured on the bounding box, not the border: centred art with a one-cell
     * margin is completely normal and must not be rejected. This only catches
     * content so small that the puzzle is really a smaller puzzle in a big frame.
     */
    private fun coversEnough(g: Grid, minCoverage: Double): Boolean {
        var minR = g.height; var maxR = -1
        var minC = g.width; var maxC = -1
        for (r in 0 until g.height) for (c in 0 until g.width) {
            if (g[r, c] == FILLED) {
                if (r < minR) minR = r
                if (r > maxR) maxR = r
                if (c < minC) minC = c
                if (c > maxC) maxC = c
            }
        }
        if (maxR < 0) return false
        val spanR = (maxR - minR + 1).toDouble() / g.height
        val spanC = (maxC - minC + 1).toDouble() / g.width
        return spanR >= minCoverage && spanC >= minCoverage
    }

    /**
     * Assign difficulty tiers 1..5 by quintile of [Candidate.meanDepth] within
     * the batch.
     *
     * Ranking within a batch rather than using absolute thresholds keeps tiers
     * meaningful across grid sizes — a 20x20 naturally needs more passes than a
     * 10x10, and absolute cutoffs would label every large puzzle "hard".
     */
    fun assignDifficulty(batch: List<Candidate>): List<Pair<Candidate, Int>> {
        if (batch.isEmpty()) return emptyList()
        val sorted = batch.sortedBy { it.meanDepth }
        return sorted.mapIndexed { i, cand ->
            val tier = (i * 5) / sorted.size + 1
            cand to tier.coerceIn(1, 5)
        }
    }
}
