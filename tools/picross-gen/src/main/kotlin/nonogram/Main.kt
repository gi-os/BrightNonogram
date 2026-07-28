package nonogram

import java.io.File
import kotlin.random.Random
import kotlin.system.exitProcess

private const val USAGE = """
picross-gen — generate uniquely-solvable nonogram packs

  random   Generate puzzles from filtered random grids
    --size N            grid size (square)                 [15]
    --width N --height N  non-square grid
    --count N           puzzles to keep                    [100]
    --fill F            target fill ratio 0..1             [0.58]
    --smooth N          CA smoothing passes (0 = noisy)    [1]
    --seed N            RNG seed for reproducible packs    [0]
    --pack-id ID        pack identifier                    [random-WxH]
    --name S            human-readable pack name
    --out DIR           output directory                   [packs]

  image    Generate puzzles by thresholding source art
    --in DIR            directory of png/jpg/gif files      (required)
    --size N            grid size (square)                 [15]
    --threshold F       fixed luminance cutoff (else Otsu)
    --invert            treat light pixels as filled
    --repair N          max cell flips to fix ambiguity, 0=off [12]
    --seed N            RNG seed for repair                 [0]
    --pack-id ID        pack identifier                    [icons-WxH]
    --name S            human-readable pack name
    --out DIR           output directory                   [packs]

  index    Rebuild index.json from packs in --out
    --out DIR           pack directory                     [packs]
    --base-url URL      URL prefix packs are served from    (required)

  selfcheck  Cross-check the solver against exhaustive enumeration
    --trials N          random grids to test               [500]
    --seed N            RNG seed                           [7]

  common
    --license SPDX      license recorded in the pack       [CC0-1.0]
    --version N         pack version, bump to trigger sync [1]
    --preview N         print N solutions as ASCII art     [0]
"""

fun main(args: Array<String>) {
    if (args.isEmpty()) { println(USAGE); exitProcess(1) }
    val cmd = args[0]
    val a = Args(args.drop(1))

    val outDir = File(a.str("out") ?: "packs")
    val license = a.str("license") ?: "CC0-1.0"
    val version = a.int("version") ?: 1
    val preview = a.int("preview") ?: 0

    when (cmd) {
        "random" -> {
            val size = a.int("size") ?: 15
            val w = a.int("width") ?: size
            val h = a.int("height") ?: size
            val count = a.int("count") ?: 100
            val fill = a.dbl("fill") ?: 0.58
            val smooth = a.int("smooth") ?: 1
            val seed = a.int("seed") ?: 0
            val packId = a.str("pack-id") ?: "random-${w}x$h"
            val name = a.str("name") ?: "Random ${w}x$h"

            val t0 = System.currentTimeMillis()
            val batch = Generator.randomBatch(w, h, count, fill, smooth, rng = Random(seed.toLong()))
            report(batch, count, System.currentTimeMillis() - t0)
            if (batch.isEmpty()) exitProcess(1)

            val rated = Generator.assignDifficulty(batch)
            PackWriter.writePack(
                PackWriter.build(packId, name, version, license, rated), outDir
            )
            showPreview(rated, preview)
        }

        "image" -> {
            val inDir = File(a.str("in") ?: run { println(USAGE); exitProcess(1) })
            val size = a.int("size") ?: 15
            val w = a.int("width") ?: size
            val h = a.int("height") ?: size
            val threshold = a.dbl("threshold")
            val invert = a.flag("invert")
            val packId = a.str("pack-id") ?: "icons-${w}x$h"
            val name = a.str("name") ?: "Icons ${w}x$h"

            val files = inDir.listFiles { f ->
                f.extension.lowercase() in setOf("png", "jpg", "jpeg", "gif", "bmp")
            }?.sortedBy { it.name } ?: emptyList()
            if (files.isEmpty()) { System.err.println("no images in ${inDir.path}"); exitProcess(1) }

            val maxFlips = a.int("repair") ?: 12
            val rng = Random((a.int("seed") ?: 0).toLong())

            val kept = ArrayList<Candidate>()
            val sources = HashMap<String, String>()
            val rejected = ArrayList<String>()
            var repairedCount = 0

            for (f in files) {
                val grid = try {
                    ImageSource.load(f, w, h, threshold, invert)
                } catch (e: Exception) {
                    rejected.add("${f.name}: ${e.message}"); continue
                }
                var cand = Generator.validate(grid)
                if (cand == null && maxFlips > 0) {
                    val fixed = Generator.repair(grid, maxFlips, rng = rng)
                    if (fixed != null) {
                        cand = fixed.first
                        repairedCount++
                        println("  repair  ${f.name}: ${fixed.second} cell(s) flipped")
                    }
                }
                if (cand == null) { rejected.add("${f.name}: could not be made uniquely solvable"); continue }
                kept.add(cand)
                sources[cand.grid.toBits()] = f.nameWithoutExtension
            }

            println("images: ${files.size}  kept: ${kept.size} (repaired $repairedCount)  rejected: ${rejected.size}")
            rejected.take(20).forEach { println("  reject  $it") }
            if (rejected.size > 20) println("  ... and ${rejected.size - 20} more")
            if (kept.isEmpty()) exitProcess(1)

            val rated = Generator.assignDifficulty(kept)
            PackWriter.writePack(
                PackWriter.build(packId, name, version, license, rated, sources), outDir
            )
            showPreview(rated, preview)
        }

        "index" -> {
            val base = a.str("base-url") ?: run { println(USAGE); exitProcess(1) }
            PackWriter.writeIndex(outDir, base.trimEnd('/'))
        }

        "selfcheck" -> {
            val ok = SelfCheck.run(a.int("trials") ?: 500, (a.int("seed") ?: 7).toLong())
            exitProcess(if (ok) 0 else 1)
        }

        else -> { println(USAGE); exitProcess(1) }
    }
}

private fun report(batch: List<Candidate>, wanted: Int, ms: Long) {
    println("kept ${batch.size}/$wanted in ${ms}ms")
    if (batch.isEmpty()) {
        System.err.println(
            "nothing survived validation — raise --fill (sparser grids are usually ambiguous) " +
                "or raise the attempt budget by lowering --count"
        )
        return
    }
    val passes = batch.map { it.passes }.sorted()
    println(
        "passes  min=${passes.first()}  p50=${passes[passes.size / 2]}  max=${passes.last()}"
    )
}

private fun showPreview(rated: List<Pair<Candidate, Int>>, n: Int) {
    if (n <= 0) return
    rated.takeLast(n).forEach { (cand, tier) ->
        println("\ndifficulty $tier  passes ${cand.passes}  depth %.2f".format(cand.meanDepth))
        print(cand.grid.render())
    }
}

/** Minimal `--key value` / `--flag` parser. */
private class Args(argv: List<String>) {
    private val map = HashMap<String, String>()
    private val flags = HashSet<String>()

    init {
        var i = 0
        while (i < argv.size) {
            val tok = argv[i]
            if (tok.startsWith("--")) {
                val key = tok.removePrefix("--")
                val next = argv.getOrNull(i + 1)
                if (next != null && !next.startsWith("--")) {
                    map[key] = next; i += 2
                } else {
                    flags.add(key); i++
                }
            } else i++
        }
    }

    fun str(k: String): String? = map[k]
    fun int(k: String): Int? = map[k]?.toIntOrNull()
    fun dbl(k: String): Double? = map[k]?.toDoubleOrNull()
    fun flag(k: String): Boolean = k in flags
}
