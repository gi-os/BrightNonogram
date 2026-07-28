package com.gios.lightnonogram.data

import com.gios.lightnonogram.game.PackReader
import com.gios.lightnonogram.game.Progress
import com.gios.lightnonogram.game.Puzzle

/**
 * The bundled puzzle sets, one per grid size.
 *
 * Free of Android imports so the tests exercise the exact path the app uses to
 * read the compiled-in packs, rather than a copy that could drift.
 *
 * Each pack is parsed at most once, on first use. The 15x15 set costs nothing if
 * a player never switches size.
 */
object PuzzleLibrary {

    /** Sizes that ship. Anything else has no pack and must be rejected upstream. */
    val sizes = listOf(10, 15)

    private val tens: List<Puzzle> by lazy { PackReader.parse(BUNDLED_PACK_10) }
    private val fifteens: List<Puzzle> by lazy { PackReader.parse(BUNDLED_PACK_15) }

    /** Easiest first — each pack is ordered by solver passes at generation time. */
    fun forSize(size: Int): List<Puzzle> = when (size) {
        10 -> tens
        15 -> fifteens
        else -> throw IllegalArgumentException("no bundled pack for size $size")
    }

    /**
     * Parse without throwing.
     *
     * This runs the moment a screen composes, and a failure would look identical
     * to any other launch crash. Returning the exception instead lets the tool
     * show what went wrong. (It has already earned its keep once: a regex that
     * the JVM accepted and Android's ICU-backed Pattern rejected.)
     */
    fun load(size: Int): Result<List<Puzzle>> = runCatching { forSize(size) }

    fun byId(id: String): Puzzle? =
        sizes.asSequence().mapNotNull { s ->
            runCatching { forSize(s) }.getOrNull()?.firstOrNull { it.id == id }
        }.firstOrNull()

    /** Position within its own pack, 1-based, for display. */
    fun numberOf(puzzle: Puzzle): Int = forSize(puzzle.width).indexOf(puzzle) + 1

    fun nextUnsolved(size: Int, progress: Progress): Puzzle? =
        forSize(size).firstOrNull { !progress.has(it.id) }
}
