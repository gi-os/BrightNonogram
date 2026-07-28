package nonogram

import java.io.File
import java.security.MessageDigest

data class PuzzleRecord(
    val id: String,
    val width: Int,
    val height: Int,
    val bits: String,
    val difficulty: Int,
    val passes: Int,
    val source: String?,
)

data class Pack(
    val id: String,
    val name: String,
    val version: Int,
    val license: String,
    val puzzles: List<PuzzleRecord>,
)

/**
 * Writes the JSON the app fetches. Hand-rolled rather than pulling in a JSON
 * library: the schema is fixed and tiny, and a zero-dependency generator is one
 * less thing to justify in review.
 */
object PackWriter {

    /** Stable content-addressed id, so regenerating a pack doesn't churn ids. */
    fun puzzleId(bits: String, w: Int, h: Int): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest("$w:$h:$bits".toByteArray())
        return digest.take(6).joinToString("") { "%02x".format(it) }
    }

    fun build(
        packId: String,
        name: String,
        version: Int,
        license: String,
        rated: List<Pair<Candidate, Int>>,
        sourceNames: Map<String, String> = emptyMap(),
    ): Pack {
        val puzzles = rated.map { (cand, tier) ->
            val bits = cand.grid.toBits()
            PuzzleRecord(
                id = puzzleId(bits, cand.grid.width, cand.grid.height),
                width = cand.grid.width,
                height = cand.grid.height,
                bits = bits,
                difficulty = tier,
                passes = cand.passes,
                source = sourceNames[bits],
            )
        }
        return Pack(packId, name, version, license, puzzles)
    }

    fun writePack(pack: Pack, dir: File) {
        dir.mkdirs()
        val f = File(dir, "${pack.id}.json")
        f.writeText(toJson(pack))
        println("wrote ${f.path} (${pack.puzzles.size} puzzles, ${f.length()} bytes)")
    }

    /** Regenerates index.json from every pack file present in [dir]. */
    fun writeIndex(dir: File, baseUrl: String) {
        val packs = dir.listFiles { f -> f.extension == "json" && f.name != "index.json" }
            ?.sortedBy { it.name } ?: emptyList()
        val sb = StringBuilder("[\n")
        packs.forEachIndexed { i, f ->
            val text = f.readText()
            val id = extract(text, "\"id\"")
            val name = extract(text, "\"name\"")
            val version = extract(text, "\"version\"")
            val count = Regex("\"bits\"").findAll(text).count()
            sb.append("  {\"id\":${q(id)},\"name\":${q(name)},\"version\":$version,")
            sb.append("\"count\":$count,\"url\":${q("$baseUrl/${f.name}")}}")
            sb.append(if (i == packs.size - 1) "\n" else ",\n")
        }
        sb.append("]\n")
        File(dir, "index.json").writeText(sb.toString())
        println("wrote ${dir.path}/index.json (${packs.size} packs)")
    }

    private fun extract(json: String, key: String): String {
        val m = Regex("$key\\s*:\\s*(\"([^\"]*)\"|[0-9]+)").find(json)
            ?: return ""
        return m.groupValues[2].ifEmpty { m.groupValues[1] }
    }

    private fun toJson(pack: Pack): String = buildString {
        append("{\n")
        append("  \"id\": ${q(pack.id)},\n")
        append("  \"name\": ${q(pack.name)},\n")
        append("  \"version\": ${pack.version},\n")
        append("  \"license\": ${q(pack.license)},\n")
        append("  \"puzzles\": [\n")
        pack.puzzles.forEachIndexed { i, p ->
            append("    {")
            append("\"id\":${q(p.id)},")
            append("\"w\":${p.width},\"h\":${p.height},")
            append("\"bits\":${q(p.bits)},")
            append("\"difficulty\":${p.difficulty},")
            append("\"passes\":${p.passes}")
            if (p.source != null) append(",\"source\":${q(p.source)}")
            append("}")
            append(if (i == pack.puzzles.size - 1) "\n" else ",\n")
        }
        append("  ]\n}\n")
    }

    private fun q(s: String): String = buildString {
        append('"')
        for (ch in s) when (ch) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
        }
        append('"')
    }
}
