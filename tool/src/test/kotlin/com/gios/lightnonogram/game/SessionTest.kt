package com.gios.lightnonogram.game

import com.gios.lightnonogram.data.PuzzleLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Continue reopens the last board the player touched, so the saved state has to
 * survive the round trip exactly — a resumed grid that quietly differs from the
 * one you left would be worse than not saving at all.
 */
class SessionTest {

    private fun boardFor(size: Int = 10, seed: Int = 4821): Board {
        val puzzle = PuzzleLibrary.forSize(size).first()
        return Board(puzzle.width, puzzle.height, puzzle.solution())
    }

    @Test
    fun `marks survive encode and decode exactly`() {
        val b = boardFor()
        // A mix of all three states, including a full auto-crossed line.
        b.beginStroke(3, 1, Tool.FILL); b.extendStroke(3, 5); b.endStroke()
        b.tap(5, 5, Tool.CROSS)
        b.tap(7, 2, Tool.FILL)

        val filled = Session.encodeMask(b.marks) { it == Mark.FILLED }
        val crossed = Session.encodeMask(b.marks) { it == Mark.CROSSED }
        val back = Session.decodeMarks(filled, crossed, b.marks.size)
        assertNotNull(back)
        assertEquals(b.marks.toList(), back.toList())
    }

    @Test
    fun `a restored board carries on from where it was left`() {
        val puzzle = PuzzleLibrary.forSize(10).first()
        val first = Board(puzzle.width, puzzle.height, puzzle.solution())
        first.beginStroke(2, 0, Tool.FILL); first.extendStroke(2, 4); first.endStroke()
        first.tap(6, 6, Tool.CROSS)

        val session = Session.of(first, puzzleId = puzzle.id, seed = null, label = null)
        val marks = Session.decodeMarks(session.filled, session.crossed, 100)
        assertNotNull(marks)

        val resumed = Board(puzzle.width, puzzle.height, puzzle.solution(), restore = marks)
        assertEquals(first.marks.toList(), resumed.marks.toList())
        assertEquals(first.filledCount, resumed.filledCount)
        // …and can still be finished.
        for (r in 0 until 10) for (c in 0 until 10) {
            if (resumed.solutionAt(r, c) && resumed.markAt(r, c) != Mark.FILLED) {
                resumed.tap(r, c, Tool.FILL)
            }
        }
        assertTrue(resumed.isSolved)
    }

    @Test
    fun `restoring does not re-run the free zero-line crosses`() {
        // A player may have deliberately cleared them; a resume must not put them
        // back and silently change the board.
        val solution = intArrayOf(1, 0, 1, 0, 0, 0, 1, 0, 1)
        val fresh = Board(3, 3, solution)
        assertEquals(Mark.CROSSED, fresh.markAt(1, 1), "fresh board crosses the [0] line")

        val cleared = Array(9) { Mark.EMPTY }
        val resumed = Board(3, 3, solution, restore = cleared)
        for (i in 0 until 9) {
            assertEquals(Mark.EMPTY, resumed.marks[i], "cell $i should have stayed empty")
        }
    }

    @Test
    fun `a campaign session round-trips through its string form`() {
        val puzzle = PuzzleLibrary.forSize(15).first()
        val b = Board(puzzle.width, puzzle.height, puzzle.solution())
        b.tap(1, 1, Tool.FILL)
        val s = Session.of(b, puzzleId = puzzle.id, seed = null, label = null)
        val back = Session.decode(s.encode())
        assertNotNull(back)
        assertEquals(s, back)
        assertEquals(puzzle.id, back.puzzleId)
        assertEquals(15, back.size)
        assertFalse(back.isGenerated)
        assertNull(back.seed)
    }

    @Test
    fun `a generated session keeps its seed and label`() {
        val b = boardFor()
        val s = Session.of(b, puzzleId = null, seed = -4821, label = "gargamel")
        val back = Session.decode(s.encode())
        assertNotNull(back)
        assertEquals(-4821, back.seed)
        assertEquals("gargamel", back.label)
        assertTrue(back.isGenerated)
        assertNull(back.puzzleId)
    }

    @Test
    fun `a label with a space does not break the separator`() {
        val b = boardFor()
        val s = Session.of(b, puzzleId = null, seed = 7, label = "light phone")
        val back = Session.decode(s.encode())
        assertNotNull(back)
        assertEquals("light phone", back.label)
        assertEquals(7, back.seed)
    }

    @Test
    fun `nonsense decodes to nothing rather than throwing`() {
        for (bad in listOf(
            null, "", "   ", "garbage", "1|c", "1|c||10|A|A|",
            "9|c|abc|10|A|A|",          // wrong version
            "1|c|abc|999|A|A|",         // implausible size
            "1|g|notanumber|10|A|A|",   // generated with a non-numeric seed
        )) {
            assertNull(Session.decode(bad), "should have rejected: $bad")
        }
    }

    @Test
    fun `a truncated mask is refused instead of half-restoring`() {
        assertNull(Session.decodeMarks("A", "A", 100), "too few bytes for 100 cells")
        assertNull(Session.decodeMarks("!!!not base64!!!", "A", 8))
        assertNotNull(Session.decodeMarks("AA==", "AA==", 8), "one byte covers 8 cells")
    }

    @Test
    fun `filled wins if a cell is somehow both`() {
        // Shouldn't happen, but the win check reads filled, so that's the bit to
        // trust if a corrupt payload sets both.
        val both = Session.decodeMarks("gA==", "gA==", 8)
        assertNotNull(both)
        assertEquals(Mark.FILLED, both[0])
    }
}
