package nonogram

import java.io.File

/**
 * Reads the hand-drawn ASCII art file that becomes the bundled puzzle set.
 *
 * Format — `#` filled, `.` empty, blank lines and `#`-prefixed comment lines
 * ignored, each design introduced by `name: <id>`:
 *
 * ```
 * name: heart
 * ..........
 * .##....##.
 * ...
 * ```
 *
 * Hand-drawn pixel art beats thresholded photos at this size. A 10x10 grid has
 * 100 cells; downsampling a real image to that resolution destroys anything
 * recognisable, so the pictures have to be drawn cell by cell.
 */
object ArtFile {

    data class Design(val name: String, val grid: Grid)

    /**
     * Telling a comment from a grid row is genuinely ambiguous here: comments
     * start with '#', but so does a fully-filled row (`##########`).
     *
     * Resolution — a line is a comment only if it is a lone '#' or its second
     * character is neither '#' nor '.'. So `# note` and `#` are comments, while
     * `##########` and `..##..##..` are grid rows.
     */
    private val gridRow = Regex("^[.#]{2,}$")

    private fun isComment(line: String): Boolean {
        if (!line.startsWith("#")) return false
        if (line.length == 1) return true
        return line[1] != '#' && line[1] != '.'
    }

    fun parse(file: File): List<Design> {
        val out = ArrayList<Design>()
        val lines = file.readLines()
        var name: String? = null
        var rows = ArrayList<String>()

        fun flush(lineNo: Int) {
            val n = name ?: return
            require(rows.isNotEmpty()) { "design '$n' has no rows (line $lineNo)" }
            val h = rows.size
            val w = rows[0].length
            rows.forEachIndexed { i, r ->
                require(r.length == w) {
                    "design '$n' row ${i + 1} is ${r.length} wide, expected $w (line $lineNo)"
                }
            }
            val cells = IntArray(w * h)
            for (r in 0 until h) for (c in 0 until w) {
                cells[r * w + c] = if (rows[r][c] == '#') FILLED else EMPTY
            }
            out.add(Design(n, Grid(w, h, cells)))
            name = null
            rows = ArrayList()
        }

        lines.forEachIndexed { idx, raw ->
            val line = raw.trim()
            when {
                line.isEmpty() || isComment(line) -> Unit
                line.startsWith("name:") -> {
                    flush(idx + 1)
                    name = line.removePrefix("name:").trim()
                    require(name!!.isNotEmpty()) { "empty name on line ${idx + 1}" }
                }
                gridRow.matches(line) -> {
                    require(name != null) { "grid row before any 'name:' on line ${idx + 1}" }
                    rows.add(line)
                }
                else -> throw IllegalArgumentException("unparseable line ${idx + 1}: $raw")
            }
        }
        flush(lines.size)

        val dupes = out.groupBy { it.name }.filterValues { it.size > 1 }.keys
        require(dupes.isEmpty()) { "duplicate design names: $dupes" }
        return out
    }
}
