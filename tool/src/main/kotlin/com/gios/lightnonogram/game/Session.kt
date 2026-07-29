package com.gios.lightnonogram.game

import java.util.Base64

/**
 * A puzzle left half-finished, so Continue can pick it up.
 *
 * One slot, not one per puzzle. "Continue" means the thing you were last doing,
 * and a single slot gives that for free — whichever board you touched most
 * recently is the one that's saved, campaign or generated.
 *
 * Marks are stored as two bit-per-cell masks rather than one array of three-state
 * values. A 15x15 board is 225 cells, so each mask is 29 bytes, and the pair
 * base64s to about 80 characters — small enough to sit in a preference and be
 * rewritten after every stroke.
 */
data class Session(
    /** Non-null for a bundled puzzle. */
    val puzzleId: String?,
    /** Non-null for a generated one. */
    val seed: Int?,
    val size: Int,
    val label: String?,
    val filled: String,
    val crossed: String,
) {
    val isGenerated: Boolean get() = seed != null

    fun encode(): String = listOf(
        VERSION,
        if (isGenerated) "g" else "c",
        (puzzleId ?: seed?.toString()).orEmpty(),
        size.toString(),
        filled,
        crossed,
        label.orEmpty(),
    ).joinToString(SEP)

    companion object {
        private const val VERSION = "1"

        /**
         * Field separator. Base64 uses A–Z a–z 0–9 + / =, ids are hex, and labels
         * are sanitised to letters, digits, spaces, hyphens and underscores — so a
         * pipe can't occur inside any field.
         */
        private const val SEP = "|"

        fun decode(raw: String?): Session? {
            if (raw.isNullOrBlank()) return null
            val p = raw.split(SEP)
            if (p.size < 7 || p[0] != VERSION) return null
            val size = p[3].toIntOrNull() ?: return null
            if (size !in 4..40) return null
            val generated = p[1] == "g"
            val key = p[2]
            if (key.isEmpty()) return null
            return Session(
                puzzleId = if (generated) null else key,
                seed = if (generated) key.toIntOrNull() ?: return null else null,
                size = size,
                label = p[6].ifEmpty { null },
                filled = p[4],
                crossed = p[5],
            )
        }

        /** Capture a board mid-solve. */
        fun of(
            board: Board,
            puzzleId: String?,
            seed: Int?,
            label: String?,
        ): Session = Session(
            puzzleId = puzzleId,
            seed = seed,
            size = board.width,
            label = label,
            filled = encodeMask(board.marks) { it == Mark.FILLED },
            crossed = encodeMask(board.marks) { it == Mark.CROSSED },
        )

        fun encodeMask(marks: Array<Mark>, predicate: (Mark) -> Boolean): String {
            val bytes = ByteArray((marks.size + 7) / 8)
            for (i in marks.indices) {
                if (predicate(marks[i])) {
                    bytes[i / 8] = (bytes[i / 8].toInt() or (0x80 ushr (i % 8))).toByte()
                }
            }
            return Base64.getEncoder().encodeToString(bytes)
        }

        /**
         * Rebuild marks from the two masks.
         *
         * Returns null on anything that doesn't fit, rather than throwing: a
         * corrupt session should cost the player their place, not the launch.
         * FILLED wins if both bits are somehow set — it's the state that decides
         * the win, so it's the one worth preserving.
         */
        fun decodeMarks(filled: String, crossed: String, cells: Int): Array<Mark>? {
            val f = runCatching { Base64.getDecoder().decode(filled) }.getOrNull() ?: return null
            val c = runCatching { Base64.getDecoder().decode(crossed) }.getOrNull() ?: return null
            val needed = (cells + 7) / 8
            if (f.size < needed || c.size < needed) return null
            return Array(cells) { i ->
                val bit = 0x80 ushr (i % 8)
                when {
                    f[i / 8].toInt() and bit != 0 -> Mark.FILLED
                    c[i / 8].toInt() and bit != 0 -> Mark.CROSSED
                    else -> Mark.EMPTY
                }
            }
        }
    }
}
