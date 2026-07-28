package com.gios.lightnonogram.game

import com.gios.lightnonogram.data.PuzzleLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BoardTest {

    /** 4x4: a plus sign. */
    private fun plus(): Board = Board(
        4, 4,
        intArrayOf(
            0, 1, 1, 0,
            1, 1, 1, 1,
            1, 1, 1, 1,
            0, 1, 1, 0,
        ),
    )

    /**
     * A board whose solution is entirely filled.
     *
     * Fixtures matter here: an all-*empty* solution clues every line [0], so
     * auto-cross legitimately fires on the whole grid and swamps whatever the
     * test was trying to observe. All-filled clues every line [w], which stays
     * unsatisfied until the very last cell.
     */
    private fun open(w: Int, h: Int) = Board(w, h, IntArray(w * h) { 1 })

    // ---- clues ------------------------------------------------------------

    @Test
    fun `derives clues including the blank-line convention`() {
        val b = Board(5, 2, intArrayOf(1, 1, 0, 1, 0, 0, 0, 0, 0, 0))
        assertEquals(listOf(2, 1), b.rowClues[0])
        assertEquals(listOf(0), b.rowClues[1], "a blank line must read as [0], not []")
        assertEquals(listOf(1), b.colClues[0])
        assertEquals(listOf(0), b.colClues[2])
    }

    // ---- taps -------------------------------------------------------------

    @Test
    fun `tap toggles fill and cross independently`() {
        val b = plus()
        b.tap(0, 0, Tool.FILL)
        assertEquals(Mark.FILLED, b.markAt(0, 0))
        b.tap(0, 0, Tool.FILL)
        assertEquals(Mark.EMPTY, b.markAt(0, 0), "tapping a filled cell should clear it")
        b.tap(0, 0, Tool.CROSS)
        assertEquals(Mark.CROSSED, b.markAt(0, 0))
        b.tap(0, 0, Tool.CROSS)
        assertEquals(Mark.EMPTY, b.markAt(0, 0))
    }

    @Test
    fun `a tap always wins over an existing mark`() {
        val b = open(4, 4)
        b.tap(1, 1, Tool.CROSS)
        b.tap(1, 1, Tool.FILL)
        assertEquals(
            Mark.FILLED, b.markAt(1, 1),
            "a deliberate tap must override, or a mis-crossed cell becomes unfillable",
        )
    }

    // ---- drag -------------------------------------------------------------

    @Test
    fun `drag fills a run along a row`() {
        val b = open(6, 3)
        b.beginStroke(1, 1, Tool.FILL)
        b.extendStroke(1, 4)
        b.endStroke()
        assertEquals(Mark.EMPTY, b.markAt(1, 0))
        for (c in 1..4) assertEquals(Mark.FILLED, b.markAt(1, c), "cell $c")
        assertEquals(Mark.EMPTY, b.markAt(1, 5))
    }

    @Test
    fun `drag fills a run along a column, and backwards`() {
        val b = open(3, 6)
        b.beginStroke(4, 1, Tool.FILL)
        b.extendStroke(1, 1)
        b.endStroke()
        for (r in 1..4) assertEquals(Mark.FILLED, b.markAt(r, 1), "row $r")
        assertEquals(Mark.EMPTY, b.markAt(0, 1))
        assertEquals(Mark.EMPTY, b.markAt(5, 1))
    }

    @Test
    fun `axis lock confines a stroke to one line`() {
        val b = open(6, 6)
        b.beginStroke(2, 1, Tool.FILL)
        b.extendStroke(2, 3)       // commits to ROW 2
        b.extendStroke(5, 5)       // thumb drifts off the row
        b.endStroke()
        // The drift is projected onto row 2, never painted where the finger went.
        for (c in 1..5) assertEquals(Mark.FILLED, b.markAt(2, c), "row 2 col $c")
        assertEquals(Mark.EMPTY, b.markAt(5, 5), "no cell outside the locked row")
        assertEquals(Mark.EMPTY, b.markAt(3, 1))
        assertEquals(Mark.EMPTY, b.markAt(2, 0), "must not run past the anchor")
    }

    @Test
    fun `drift off the locked row keeps painting instead of stalling`() {
        val b = open(8, 8)
        b.beginStroke(4, 1, Tool.FILL)
        b.extendStroke(4, 2)
        b.extendStroke(3, 3)       // one row off
        b.extendStroke(5, 4)       // and the other way
        b.endStroke()
        for (c in 1..4) assertEquals(
            Mark.FILLED, b.markAt(4, c),
            "col $c: a drifting thumb must not stall the run",
        )
        assertEquals(Mark.EMPTY, b.markAt(3, 3))
        assertEquals(Mark.EMPTY, b.markAt(5, 4))
    }

    @Test
    fun `a diagonal first move commits to the dominant direction`() {
        val b = open(6, 6)
        b.beginStroke(2, 2, Tool.FILL)
        b.extendStroke(3, 5)       // dx=3, dy=1 -> ROW
        b.endStroke()
        for (c in 2..5) assertEquals(Mark.FILLED, b.markAt(2, c), "col $c")

        val v = open(6, 6)
        v.beginStroke(2, 2, Tool.FILL)
        v.extendStroke(5, 3)       // dy=3, dx=1 -> COL
        v.endStroke()
        for (r in 2..5) assertEquals(Mark.FILLED, v.markAt(r, 2), "row $r")
    }

    @Test
    fun `dragging from a filled cell erases the run`() {
        val b = open(6, 3)
        b.beginStroke(1, 0, Tool.FILL); b.extendStroke(1, 5); b.endStroke()
        b.beginStroke(1, 1, Tool.FILL); b.extendStroke(1, 3); b.endStroke()
        assertEquals(Mark.FILLED, b.markAt(1, 0))
        for (c in 1..3) assertEquals(Mark.EMPTY, b.markAt(1, c), "cell $c should be erased")
        assertEquals(Mark.FILLED, b.markAt(1, 4))
    }

    @Test
    fun `a fill drag does not destroy crosses it passes over`() {
        val b = open(6, 3)
        b.tap(1, 3, Tool.CROSS)
        b.beginStroke(1, 0, Tool.FILL); b.extendStroke(1, 5); b.endStroke()
        assertEquals(
            Mark.CROSSED, b.markAt(1, 3),
            "a careful X must survive a drag across it",
        )
        assertEquals(Mark.FILLED, b.markAt(1, 2))
        assertEquals(Mark.FILLED, b.markAt(1, 4))
    }

    @Test
    fun `a cross drag does not destroy fills it passes over`() {
        val b = open(6, 3)
        b.tap(1, 3, Tool.FILL)
        b.beginStroke(1, 0, Tool.CROSS); b.extendStroke(1, 5); b.endStroke()
        assertEquals(Mark.FILLED, b.markAt(1, 3))
        assertEquals(Mark.CROSSED, b.markAt(1, 2))
    }

    // ---- auto-cross -------------------------------------------------------

    @Test
    fun `satisfying a row auto-crosses its leftovers`() {
        // row 0 clue is [2] (cells 1,2). Fill them and the rest should cross.
        val b = plus()
        assertEquals(listOf(2), b.rowClues[0])
        b.beginStroke(0, 1, Tool.FILL); b.extendStroke(0, 2); b.endStroke()
        assertEquals(Mark.CROSSED, b.markAt(0, 0))
        assertEquals(Mark.CROSSED, b.markAt(0, 3))
    }

    @Test
    fun `undo reverses a fill together with the auto-crosses it caused`() {
        val b = plus()
        b.beginStroke(0, 1, Tool.FILL); b.extendStroke(0, 2); b.endStroke()
        assertEquals(Mark.CROSSED, b.markAt(0, 0))
        b.undo()
        assertEquals(Mark.EMPTY, b.markAt(0, 1))
        assertEquals(Mark.EMPTY, b.markAt(0, 2))
        assertEquals(
            Mark.EMPTY, b.markAt(0, 0),
            "stray auto-crosses left behind after an undo would feel broken",
        )
    }

    @Test
    fun `satisfied line reporting tracks the player's fills`() {
        val b = plus()
        assertFalse(b.isRowSatisfied(0))
        b.beginStroke(0, 1, Tool.FILL); b.extendStroke(0, 2); b.endStroke()
        assertTrue(b.isRowSatisfied(0))
        assertFalse(b.isRowSatisfied(1))
    }

    // ---- winning ----------------------------------------------------------

    @Test
    fun `win ignores crosses and rejects extra fills`() {
        val b = plus()
        assertFalse(b.isSolved)
        for (r in 0 until 4) for (c in 0 until 4) {
            if (b.solutionAt(r, c)) b.tap(r, c, Tool.FILL)
        }
        assertTrue(b.isSolved, "filled cells match; crosses must not matter")

        val extra = plus()
        for (r in 0 until 4) for (c in 0 until 4) {
            if (extra.solutionAt(r, c)) extra.tap(r, c, Tool.FILL)
        }
        extra.tap(0, 0, Tool.FILL)   // one cell too many
        assertFalse(extra.isSolved)
    }

    @Test
    fun `clear resets everything`() {
        val b = plus()
        b.tap(1, 1, Tool.FILL)
        b.clear()
        assertFalse(b.canUndo)
        assertEquals(0, b.filledCount)
    }

    @Test
    fun `lines clued zero are crossed out before the player touches anything`() {
        // row 1 and col 1 are empty in the solution -> free crosses on load
        val b = Board(3, 3, intArrayOf(1, 0, 1, 0, 0, 0, 1, 0, 1))
        assertEquals(listOf(0), b.rowClues[1])
        for (c in 0 until 3) assertEquals(Mark.CROSSED, b.markAt(1, c), "row 1 col $c")
        for (r in 0 until 3) assertEquals(Mark.CROSSED, b.markAt(r, 1), "row $r col 1")
        assertEquals(Mark.EMPTY, b.markAt(0, 0), "clued cells must be left alone")
        assertFalse(b.canUndo, "the free crosses must not be undoable")
    }

    // ---- progress ---------------------------------------------------------

    @Test
    fun `progress round-trips and dedupes`() {
        val p = Progress().with("abc").with("def").with("abc")
        assertEquals(2, p.completed.size)
        assertEquals(p.completed, Progress.decode(p.encode()).completed)
        assertEquals(Progress(), Progress.decode(null))
        assertEquals(Progress(), Progress.decode("  "))
        assertTrue(Progress.decode("a, b ,,c").has("b"))
    }

    // ---- the bundled pack -------------------------------------------------

    /**
     * Reads the pack exactly the way the app does — through [PuzzleLibrary],
     * from the compiled-in constant, not a file on disk. Testing the real shipped
     * path means a broken generator emit fails here rather than on device.
     */
    private fun bundledPack(): List<Puzzle> = PuzzleLibrary.puzzles

    @Test
    fun `bundled pack parses and every puzzle is well formed`() {
        val puzzles = bundledPack()
        assertTrue(puzzles.size >= 60, "expected the full bundled set, got ${puzzles.size}")
        assertEquals(puzzles.size, puzzles.map { it.id }.distinct().size, "duplicate puzzle ids")
        for (p in puzzles) {
            assertEquals(10, p.width, "${p.id} width")
            assertEquals(10, p.height, "${p.id} height")
            assertNotNull(p.title, "${p.id} has no title to reveal")
            val sol = p.solution()
            assertEquals(100, sol.size)
            assertTrue(sol.any { it == 1 }, "${p.title} is blank")
            assertTrue(sol.count { it == 1 } < 100, "${p.title} is completely full")
        }
    }

    /**
     * Play every bundled puzzle to completion through the real input API.
     *
     * Tapping only. This is the floor: if this fails, the game is unwinnable.
     */
    @Test
    fun `every bundled puzzle is winnable by tapping`() {
        for (p in bundledPack()) {
            val b = p.newBoard()
            for (r in 0 until b.height) for (c in 0 until b.width) {
                if (b.solutionAt(r, c)) b.tap(r, c, Tool.FILL)
            }
            assertTrue(b.isSolved, "${p.title} (${p.id}) not solved by tapping every filled cell")
        }
    }

    /**
     * Play every bundled puzzle using row drags — the way a person actually plays.
     *
     * This is the interesting one. Auto-cross can cross a cell that genuinely
     * needed filling (a satisfied run pattern isn't proof of correctness), and a
     * drag deliberately refuses to overwrite existing marks. Those two rules
     * together could strand a cell that can no longer be filled by dragging.
     *
     * The escape hatch is that an anchor tap always overrides, so this walks each
     * row as a drag and then repairs any cell auto-cross stole.
     */
    @Test
    fun `every bundled puzzle is winnable by dragging runs`() {
        var neededRepair = 0
        for (p in bundledPack()) {
            val b = p.newBoard()
            // drag each contiguous run of the solution, row by row
            for (r in 0 until b.height) {
                var c = 0
                while (c < b.width) {
                    if (!b.solutionAt(r, c)) { c++; continue }
                    var end = c
                    while (end + 1 < b.width && b.solutionAt(r, end + 1)) end++
                    b.beginStroke(r, c, Tool.FILL)
                    if (end != c) b.extendStroke(r, end)
                    b.endStroke()
                    c = end + 1
                }
            }
            if (!b.isSolved) {
                neededRepair++
                for (r in 0 until b.height) for (c in 0 until b.width) {
                    if (b.solutionAt(r, c) && b.markAt(r, c) != Mark.FILLED) b.tap(r, c, Tool.FILL)
                }
            }
            assertTrue(b.isSolved, "${p.title} (${p.id}) unwinnable even after repair taps")
        }
        println("drag playthrough: $neededRepair puzzle(s) needed a repair tap after auto-cross")
    }
}
