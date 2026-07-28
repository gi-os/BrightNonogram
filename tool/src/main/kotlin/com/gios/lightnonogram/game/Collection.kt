package com.gios.lightnonogram.game

/**
 * A generated puzzle the player has finished.
 *
 * Stored as a seed and a size, nothing more. Both the picture and the name are
 * derived from the seed, so a whole collection is a few characters per entry and
 * the grid of thumbnails is rebuilt on the fly.
 */
data class Made(val seed: Int, val size: Int) {
    fun encode(): String = "$size:$seed"

    companion object {
        fun decode(token: String): Made? {
            val parts = token.split(':')
            if (parts.size != 2) return null
            val size = parts[0].trim().toIntOrNull() ?: return null
            val seed = parts[1].trim().toIntOrNull() ?: return null
            if (size !in 4..40) return null
            return Made(seed, size)
        }
    }
}

/**
 * The player's collection of generated puzzles, newest first.
 *
 * Capped, because this lives in a single preferences string and an unbounded one
 * would grow forever. At ~14 characters an entry the cap is a few kilobytes,
 * which is far more than anyone will fill and still small enough not to care
 * about.
 */
data class MadeCollection(val entries: List<Made> = emptyList()) {

    /** Newest first, de-duplicated, trimmed to [LIMIT]. */
    fun with(made: Made): MadeCollection =
        MadeCollection((listOf(made) + entries.filter { it != made }).take(LIMIT))

    fun has(made: Made): Boolean = made in entries

    val size: Int get() = entries.size

    fun encode(): String = entries.joinToString(",") { it.encode() }

    companion object {
        const val LIMIT = 500

        fun decode(raw: String?): MadeCollection {
            if (raw.isNullOrBlank()) return MadeCollection()
            // Unparseable tokens are dropped rather than thrown on: a corrupt
            // preference should cost you one entry, not the whole collection.
            return MadeCollection(
                raw.split(',').mapNotNull { Made.decode(it) }.distinct().take(LIMIT)
            )
        }
    }
}
