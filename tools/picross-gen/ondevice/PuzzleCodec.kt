package com.yourname.picross.data

import java.util.Base64

/**
 * On-device counterpart to the generator's encoding. Drop this into your Light
 * tool module.
 *
 * Deliberately free of Android imports so it compiles under the Light SDK's
 * restricted classpath and can be unit-tested on the JVM without an emulator.
 *
 * Clues are **derived at load time, never stored**. A 20x20 solution is 50
 * bytes; its clue lists would be several times that, and they would be one more
 * thing that could disagree with the solution.
 */
object PuzzleCodec {

    const val EMPTY = 0
    const val FILLED = 1

    /** Decode a Base64 row-major bitmask into a `w*h` array of EMPTY/FILLED. */
    fun decodeBits(bits: String, width: Int, height: Int): IntArray {
        val bytes = Base64.getDecoder().decode(bits)
        val n = width * height
        require(bytes.size >= (n + 7) / 8) { "bit payload too short for ${width}x$height" }
        return IntArray(n) { i ->
            if ((bytes[i / 8].toInt() shr (7 - (i % 8))) and 1 == 1) FILLED else EMPTY
        }
    }

    /** Row clues, top to bottom. */
    fun rowClues(solution: IntArray, width: Int, height: Int): List<List<Int>> =
        (0 until height).map { r ->
            runLengths(IntArray(width) { c -> solution[r * width + c] })
        }

    /** Column clues, left to right. */
    fun colClues(solution: IntArray, width: Int, height: Int): List<List<Int>> =
        (0 until width).map { c ->
            runLengths(IntArray(height) { r -> solution[r * width + c] })
        }

    /**
     * Consecutive filled runs. An empty line yields `[0]` rather than `[]` so
     * the UI has something to render in the clue gutter — Picross convention is
     * to show a single 0 for a blank line.
     */
    private fun runLengths(line: IntArray): List<Int> {
        val out = ArrayList<Int>()
        var run = 0
        for (cell in line) {
            if (cell == FILLED) run++ else if (run > 0) { out.add(run); run = 0 }
        }
        if (run > 0) out.add(run)
        return if (out.isEmpty()) listOf(0) else out
    }

    /**
     * Has the player won?
     *
     * Compares only *filled* cells. Cross-marks are a personal bookkeeping aid,
     * so a board is complete when the filled set matches, regardless of how the
     * player annotated the rest. Checking marks too is a classic bug that makes
     * a visually-correct board refuse to register as solved.
     */
    fun isSolved(playerFilled: BooleanArray, solution: IntArray): Boolean {
        if (playerFilled.size != solution.size) return false
        for (i in solution.indices) {
            if (playerFilled[i] != (solution[i] == FILLED)) return false
        }
        return true
    }
}
