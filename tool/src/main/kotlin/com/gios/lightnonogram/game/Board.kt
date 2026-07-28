package com.gios.lightnonogram.game

/** What the player has put in a cell. Crosses are bookkeeping and never checked for a win. */
enum class Mark { EMPTY, FILLED, CROSSED }

/** Which tool a stroke is using. */
enum class Tool { FILL, CROSS }

private enum class Axis { NONE, ROW, COL }

/**
 * Mutable play state for one puzzle.
 *
 * Free of Android imports on purpose: all the rules that decide whether the
 * game feels good live here, and they're worth unit-testing without an emulator.
 *
 * "Forgiving" rules — a wrong fill is never flagged or punished. You win when
 * the filled cells match the solution, however you got there.
 */
class Board(
    val width: Int,
    val height: Int,
    private val solution: IntArray,
    /**
     * Cross out lines whose clue is already satisfied, and lines clued 0 up
     * front. On by default because it removes a lot of dull tapping, but some
     * players want the grid to hold only marks they put there themselves — so
     * it's a setting rather than a rule.
     */
    private val autoCross: Boolean = true,
) {
    init {
        require(solution.size == width * height) { "solution size != width*height" }
    }

    val marks = Array(width * height) { Mark.EMPTY }

    val rowClues: List<List<Int>> = (0 until height).map { r ->
        runs(IntArray(width) { c -> solution[r * width + c] })
    }
    val colClues: List<List<Int>> = (0 until width).map { c ->
        runs(IntArray(height) { r -> solution[r * width + c] })
    }

    init {
        // A line clued [0] is known-empty the moment you read it, so cross it
        // out up front. Costs the player nothing and saves a row of dull taps —
        // most of the bundled pictures have blank border rows.
        if (autoCross) crossSatisfiedLines(record = false)
    }

    // ---- stroke state -----------------------------------------------------

    private var anchor = -1
    private var anchorWas = Mark.EMPTY
    private var target = Mark.EMPTY
    private var axis = Axis.NONE
    private var active = false

    /** Cells changed by the in-flight (or last) stroke, for undo. */
    private val strokeBefore = HashMap<Int, Mark>()
    private val undoStack = ArrayList<Map<Int, Mark>>()

    fun markAt(r: Int, c: Int): Mark = marks[r * width + c]
    fun solutionAt(r: Int, c: Int): Boolean = solution[r * width + c] == 1

    // ---- input ------------------------------------------------------------

    /**
     * Start a stroke on a cell.
     *
     * The *anchor* decides whether this stroke paints or erases: pressing a cell
     * that already holds what the tool would apply means you meant to clear it.
     * That single rule gives tap-to-toggle and drag-to-erase-a-run for free.
     */
    fun beginStroke(r: Int, c: Int, tool: Tool) {
        val i = r * width + c
        anchor = i
        anchorWas = marks[i]
        axis = Axis.NONE
        active = true
        strokeBefore.clear()

        val wanted = if (tool == Tool.FILL) Mark.FILLED else Mark.CROSSED
        target = if (marks[i] == wanted) Mark.EMPTY else wanted

        // The anchor always changes — a deliberate tap must never be ignored.
        apply(i, target)
    }

    /**
     * Continue a stroke onto another cell.
     *
     * Two things make this feel right on a small screen:
     *
     * 1. **Axis lock.** The first cell you move to fixes the stroke to that row
     *    or column, and everything off it is ignored. Without this, a thumb
     *    dragging across 5 mm cells scribbles diagonally through cells you never
     *    meant to touch.
     *
     * 2. **Protecting existing marks.** A drag only writes into cells that are
     *    still EMPTY — or, when erasing, cells matching what the anchor held. So
     *    dragging fill across a row never destroys crosses you carefully placed,
     *    and dragging crosses never wipes out fills.
     */
    fun extendStroke(r: Int, c: Int) {
        if (!active) return
        val i = r * width + c
        if (i == anchor) return

        val ar = anchor / width
        val ac = anchor % width

        if (axis == Axis.NONE) {
            axis = when {
                r == ar && c != ac -> Axis.ROW
                c == ac && r != ar -> Axis.COL
                // Diagonal first move: commit to whichever direction moved more.
                kotlin.math.abs(c - ac) >= kotlin.math.abs(r - ar) -> Axis.ROW
                else -> Axis.COL
            }
        }

        // Project the touch onto the locked axis rather than discarding it. A
        // thumb on a 5 mm cell drifts constantly; if drifting one row off killed
        // the stroke, painting would stall mid-run and feel broken. So a locked
        // row cares only about the column you've reached, and vice versa.
        when (axis) {
            Axis.ROW -> {
                val step = if (c > ac) 1 else -1
                var k = ac
                while (k != c + step) { maybeApply(ar * width + k); k += step }
            }
            Axis.COL -> {
                val step = if (r > ar) 1 else -1
                var k = ar
                while (k != r + step) { maybeApply(k * width + ac); k += step }
            }
            Axis.NONE -> Unit
        }
    }

    /**
     * Finish a stroke: run auto-cross, then bank the whole thing as one undo step.
     *
     * Order matters. Auto-crosses fold into the same undo entry as the fill that
     * triggered them, so undoing a fill doesn't leave orphaned X marks behind.
     */
    fun endStroke() {
        if (!active) return
        active = false
        if (autoCross) crossSatisfiedLines(record = true)
        if (strokeBefore.isNotEmpty()) undoStack.add(HashMap(strokeBefore))
    }

    /** Convenience for a single tap. */
    fun tap(r: Int, c: Int, tool: Tool) {
        beginStroke(r, c, tool)
        endStroke()
    }

    private fun maybeApply(i: Int) {
        val cur = marks[i]
        val writable = cur == Mark.EMPTY || (target == Mark.EMPTY && cur == anchorWas)
        if (writable && cur != target) apply(i, target)
    }

    private fun apply(i: Int, m: Mark) {
        if (marks[i] == m) return
        strokeBefore.putIfAbsent(i, marks[i])
        marks[i] = m
    }

    // ---- undo -------------------------------------------------------------

    val canUndo: Boolean get() = undoStack.isNotEmpty()

    fun undo() {
        val last = undoStack.removeLastOrNull() ?: return
        for ((i, m) in last) marks[i] = m
    }

    fun clear() {
        for (i in marks.indices) marks[i] = Mark.EMPTY
        undoStack.clear()
        strokeBefore.clear()
        active = false
        if (autoCross) crossSatisfiedLines(record = false)   // restore the free [0]-line crosses
    }

    // ---- derived state ----------------------------------------------------

    /**
     * Does this row's filled pattern match its clue?
     *
     * Used to dim satisfied clues, which is the single cheapest readability win
     * on a small screen — it tells you where not to look.
     */
    fun isRowSatisfied(r: Int): Boolean =
        runs(IntArray(width) { c -> if (markAt(r, c) == Mark.FILLED) 1 else 0 }) == rowClues[r]

    fun isColSatisfied(c: Int): Boolean =
        runs(IntArray(height) { r -> if (markAt(r, c) == Mark.FILLED) 1 else 0 }) == colClues[c]

    /**
     * Cross out the leftovers in any line whose clue is already satisfied.
     *
     * Removes a lot of dull tapping. It can occasionally cross a cell that
     * actually needed filling — a satisfied run pattern isn't proof of
     * correctness — but since nothing here punishes mistakes, the player just
     * undoes it. Worth the trade.
     */
    private fun crossSatisfiedLines(record: Boolean) {
        fun cross(i: Int) {
            if (marks[i] != Mark.EMPTY) return
            if (record) strokeBefore.putIfAbsent(i, Mark.EMPTY)
            marks[i] = Mark.CROSSED
        }
        for (r in 0 until height) {
            if (!isRowSatisfied(r)) continue
            for (c in 0 until width) cross(r * width + c)
        }
        for (c in 0 until width) {
            if (!isColSatisfied(c)) continue
            for (r in 0 until height) cross(r * width + c)
        }
    }

    /** Filled cells match the solution. Crosses are ignored entirely. */
    val isSolved: Boolean
        get() {
            for (i in solution.indices) {
                if ((marks[i] == Mark.FILLED) != (solution[i] == 1)) return false
            }
            return true
        }

    val filledCount: Int get() = marks.count { it == Mark.FILLED }
    val solutionFilledCount: Int get() = solution.count { it == 1 }

    companion object {
        /** Run lengths of a 0/1 line; a blank line reads as `[0]` per Picross convention. */
        fun runs(line: IntArray): List<Int> {
            val out = ArrayList<Int>()
            var run = 0
            for (v in line) {
                if (v == 1) run++ else if (run > 0) { out.add(run); run = 0 }
            }
            if (run > 0) out.add(run)
            return if (out.isEmpty()) listOf(0) else out
        }
    }
}
