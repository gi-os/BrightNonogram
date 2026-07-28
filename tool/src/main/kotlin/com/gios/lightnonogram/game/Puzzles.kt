package com.gios.lightnonogram.game

import java.util.Base64

/** One puzzle as it ships in the bundled pack. */
data class Puzzle(
    val id: String,
    val width: Int,
    val height: Int,
    val bits: String,
    val difficulty: Int,
    val passes: Int,
    /** Design name, e.g. "heart". Hidden until solved — it's the reveal. */
    val title: String?,
) {
    /** Decode to a 0/1 array. Cheap enough to do on demand; no need to cache. */
    fun solution(): IntArray {
        val bytes = Base64.getDecoder().decode(bits)
        val n = width * height
        require(bytes.size >= (n + 7) / 8) { "bit payload too short for ${width}x$height" }
        return IntArray(n) { i -> (bytes[i / 8].toInt() shr (7 - (i % 8))) and 1 }
    }

    fun newBoard(): Board = Board(width, height, solution())
}

/**
 * Reader for the pack JSON that `picross-gen` emits.
 *
 * Uses [MiniJson] rather than a regex. The regex version worked on the JVM and
 * threw on the device — see MiniJson's own comment for the whole story. Parsing
 * the structure properly also means a malformed pack names the field it choked
 * on instead of silently matching nothing.
 */
object PackReader {

    fun parse(json: String): List<Puzzle> {
        val root = MiniJson.parse(json).asObject("pack")
        val puzzles = root["puzzles"].asArray("pack.puzzles")
        return puzzles.mapIndexed { i, entry ->
            val where = "puzzles[$i]"
            val o = entry.asObject(where)
            Puzzle(
                id = o.string("id", where),
                width = o.int("w", where),
                height = o.int("h", where),
                bits = o.string("bits", where),
                difficulty = o.intOrDefault("difficulty", 1),
                passes = o.intOrDefault("passes", 0),
                title = o.stringOrNull("source"),
            )
        }
    }
}

/**
 * Which puzzles the player has finished.
 *
 * Stored as a comma-joined id list. Ids are content hashes from the generator,
 * so regenerating the pack doesn't wipe anyone's progress — only genuinely
 * changing a picture does.
 */
data class Progress(val completed: Set<String> = emptySet()) {

    fun with(id: String): Progress =
        if (id in completed) this else Progress(completed + id)

    fun has(id: String): Boolean = id in completed

    fun encode(): String = completed.sorted().joinToString(",")

    fun countIn(puzzles: List<Puzzle>): Int = puzzles.count { it.id in completed }

    companion object {
        fun decode(raw: String?): Progress {
            if (raw.isNullOrBlank()) return Progress()
            return Progress(raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet())
        }
    }
}
