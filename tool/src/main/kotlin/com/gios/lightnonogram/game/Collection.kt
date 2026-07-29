package com.gios.lightnonogram.game

/**
 * A generated puzzle the player has finished.
 *
 * Normally only the seed and size are stored — the picture and the name are both
 * derived from the seed, so an entry costs a handful of characters.
 *
 * [label] is the exception: when a player types a word rather than a number, the
 * word is what they'll remember the puzzle by, so it's worth the extra bytes to
 * keep it. Null means "use the generated name".
 */
data class Made(val seed: Int, val size: Int, val label: String? = null) {

    fun encode(): String =
        if (label == null) "$size:$seed" else "$size:$seed:$label"

    companion object {
        /** Longest label kept. Long enough for a word or two, short enough to display. */
        const val MAX_LABEL = 24

        /**
         * Strip anything that would collide with the separators this format uses,
         * or with a single line of UI. Returns null if nothing usable is left.
         */
        fun sanitizeLabel(raw: String?): String? {
            if (raw == null) return null
            // Built by hand rather than with a Regex: patterns in this module are
            // banned outright since an ICU-only rejection can't be caught by a
            // JVM test. See MiniJson for the crash that taught us that.
            val out = StringBuilder(MAX_LABEL)
            var lastWasSpace = true          // also trims the leading space
            for (ch in raw) {
                val keep = ch.isLetterOrDigit() || ch == '-' || ch == '_'
                when {
                    keep -> { out.append(ch); lastWasSpace = false }
                    ch.isWhitespace() -> {
                        if (!lastWasSpace && out.length < MAX_LABEL) out.append(' ')
                        lastWasSpace = true
                    }
                }
                if (out.length >= MAX_LABEL) break
            }
            return out.toString().trim().ifEmpty { null }
        }

        fun decode(token: String): Made? {
            // Split into at most 3 so a label can't be broken by anything that
            // survived sanitising.
            val parts = token.split(':', limit = 3)
            if (parts.size < 2) return null
            val size = parts[0].trim().toIntOrNull() ?: return null
            val seed = parts[1].trim().toIntOrNull() ?: return null
            if (size !in 4..40) return null
            val label = if (parts.size == 3) sanitizeLabel(parts[2]) else null
            return Made(seed, size, label)
        }
    }
}

/**
 * The player's collection of generated puzzles, newest first.
 *
 * Capped, because this lives in a single preferences string and an unbounded one
 * would grow forever. At a few dozen characters an entry the cap is a few
 * kilobytes — far more than anyone will fill, and small enough not to care about.
 */
data class MadeCollection(val entries: List<Made> = emptyList()) {

    /**
     * Newest first, de-duplicated, trimmed to [LIMIT].
     *
     * Identity is the seed and size, not the label: re-solving the same puzzle
     * shouldn't add a second tile just because it was reached a different way.
     */
    fun with(made: Made): MadeCollection = MadeCollection(
        (listOf(made) + entries.filterNot { it.seed == made.seed && it.size == made.size })
            .take(LIMIT)
    )

    fun has(made: Made): Boolean =
        entries.any { it.seed == made.seed && it.size == made.size }

    val size: Int get() = entries.size

    fun encode(): String = entries.joinToString(",") { it.encode() }

    companion object {
        const val LIMIT = 500

        fun decode(raw: String?): MadeCollection {
            if (raw.isNullOrBlank()) return MadeCollection()
            // Unparseable tokens are dropped rather than thrown on: a corrupt
            // preference should cost you one entry, not the whole collection.
            val parsed = raw.split(',').mapNotNull { Made.decode(it) }
            val seen = LinkedHashMap<Pair<Int, Int>, Made>()
            for (m in parsed) seen.putIfAbsent(m.seed to m.size, m)
            return MadeCollection(seen.values.toList().take(LIMIT))
        }
    }
}
