package nonogram

import java.util.Base64

/** Cell states. A puzzle grid is complete when no cell is [UNKNOWN]. */
const val UNKNOWN = -1
const val EMPTY = 0
const val FILLED = 1

/**
 * A row-major nonogram grid.
 *
 * Used both as a *solution* (every cell EMPTY or FILLED) and as a *working
 * board* during solving (cells may be UNKNOWN).
 */
class Grid(
    val width: Int,
    val height: Int,
    val cells: IntArray = IntArray(width * height) { UNKNOWN },
) {
    init {
        require(width > 0 && height > 0) { "grid must be non-empty" }
        require(cells.size == width * height) { "cells size != width*height" }
    }

    operator fun get(r: Int, c: Int): Int = cells[r * width + c]
    operator fun set(r: Int, c: Int, v: Int) { cells[r * width + c] = v }

    fun row(r: Int): IntArray = IntArray(width) { c -> this[r, c] }
    fun col(c: Int): IntArray = IntArray(height) { r -> this[r, c] }

    fun setRow(r: Int, v: IntArray) { for (c in 0 until width) this[r, c] = v[c] }
    fun setCol(c: Int, v: IntArray) { for (r in 0 until height) this[r, c] = v[r] }

    val isComplete: Boolean get() = cells.none { it == UNKNOWN }
    val filledCount: Int get() = cells.count { it == FILLED }

    fun copy(): Grid = Grid(width, height, cells.copyOf())

    fun rowClues(): List<IntArray> = (0 until height).map { deriveClues(row(it)) }
    fun colClues(): List<IntArray> = (0 until width).map { deriveClues(col(it)) }

    /**
     * Row-major bitmask, one bit per cell, MSB-first within each byte,
     * Base64-encoded. A 20x20 puzzle is 400 bits = 50 bytes = 68 chars.
     */
    fun toBits(): String {
        val bytes = ByteArray((cells.size + 7) / 8)
        for (i in cells.indices) {
            if (cells[i] == FILLED) {
                bytes[i / 8] = (bytes[i / 8].toInt() or (0x80 ushr (i % 8))).toByte()
            }
        }
        return Base64.getEncoder().encodeToString(bytes)
    }

    /** ASCII art, for eyeballing generator output in the terminal. */
    fun render(): String = buildString {
        for (r in 0 until height) {
            for (c in 0 until width) {
                append(
                    when (this@Grid[r, c]) {
                        FILLED -> "##"
                        EMPTY -> ". "
                        else -> "? "
                    }
                )
            }
            append('\n')
        }
    }

    companion object {
        fun fromBits(bits: String, width: Int, height: Int): Grid {
            val bytes = Base64.getDecoder().decode(bits)
            val g = Grid(width, height, IntArray(width * height) { EMPTY })
            for (i in 0 until width * height) {
                val bit = (bytes[i / 8].toInt() shr (7 - (i % 8))) and 1
                g.cells[i] = if (bit == 1) FILLED else EMPTY
            }
            return g
        }
    }
}

/** Run-length clue list for a single line. An all-empty line yields `[]`. */
fun deriveClues(line: IntArray): IntArray {
    val out = ArrayList<Int>()
    var run = 0
    for (cell in line) {
        if (cell == FILLED) {
            run++
        } else if (run > 0) {
            out.add(run); run = 0
        }
    }
    if (run > 0) out.add(run)
    return out.toIntArray()
}
