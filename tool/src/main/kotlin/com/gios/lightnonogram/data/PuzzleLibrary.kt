package com.gios.lightnonogram.data

import com.gios.lightnonogram.game.PackReader
import com.gios.lightnonogram.game.Progress
import com.gios.lightnonogram.game.Puzzle

/**
 * The bundled puzzle set.
 *
 * Kept in its own file, free of Android imports, so it can be unit-tested
 * directly — the tests then exercise the exact same path the app uses to read
 * the compiled-in pack, rather than a copy that could drift.
 *
 * Parsed once and cached: 69 entries is nothing, but re-parsing on every screen
 * entry would be waste for no gain.
 */
object PuzzleLibrary {

    /** Easiest first — the pack is ordered by solver passes at generation time. */
    val puzzles: List<Puzzle> by lazy { PackReader.parse(BUNDLED_PACK_JSON) }

    fun byId(id: String): Puzzle? = puzzles.firstOrNull { it.id == id }

    /** Position in the set, 1-based, for display. */
    fun numberOf(puzzle: Puzzle): Int = puzzles.indexOf(puzzle) + 1

    /** Next unsolved puzzle, or null once the player has finished everything. */
    fun nextUnsolved(progress: Progress): Puzzle? = puzzles.firstOrNull { !progress.has(it.id) }
}
