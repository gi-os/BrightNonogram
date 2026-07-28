package nonogram

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Turns source art into candidate grids.
 *
 * Pipeline: load -> box-downsample to the target grid size -> threshold to 1
 * bit. Downsampling by *averaging* (not nearest-neighbour) matters: at 15x15
 * every cell is a large chunk of the original, and averaging preserves thin
 * strokes that point-sampling would drop entirely.
 */
object ImageSource {

    /**
     * @param threshold luminance cutoff in 0..1, or null to pick one
     *   automatically with Otsu's method (recommended — source art varies
     *   wildly in exposure and a fixed cutoff blows out half of it).
     * @param invert treat light pixels as filled instead of dark ones.
     */
    fun load(
        file: File,
        width: Int,
        height: Int,
        threshold: Double? = null,
        invert: Boolean = false,
    ): Grid {
        val img = ImageIO.read(file)
            ?: throw IllegalArgumentException("not a readable image: ${file.name}")
        val lum = downsampleLuminance(img, width, height)
        val cut = threshold ?: otsu(lum)
        val cells = IntArray(width * height) { i ->
            val dark = lum[i] < cut
            if (dark != invert) FILLED else EMPTY
        }
        return Grid(width, height, cells)
    }

    /** Average luminance per output cell, in 0..1. */
    private fun downsampleLuminance(img: BufferedImage, w: Int, h: Int): DoubleArray {
        val out = DoubleArray(w * h)
        for (gy in 0 until h) {
            for (gx in 0 until w) {
                val x0 = gx * img.width / w
                val x1 = ((gx + 1) * img.width / w).coerceAtLeast(x0 + 1).coerceAtMost(img.width)
                val y0 = gy * img.height / h
                val y1 = ((gy + 1) * img.height / h).coerceAtLeast(y0 + 1).coerceAtMost(img.height)
                var sum = 0.0
                var n = 0
                for (y in y0 until y1) {
                    for (x in x0 until x1) {
                        val argb = img.getRGB(x, y)
                        val a = (argb ushr 24) and 0xFF
                        val r = (argb ushr 16) and 0xFF
                        val g = (argb ushr 8) and 0xFF
                        val b = argb and 0xFF
                        // Rec. 709 luma; composite transparent pixels onto white
                        // so PNG icons with alpha backgrounds read as "empty".
                        val luma = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
                        val alpha = a / 255.0
                        sum += luma * alpha + (1.0 - alpha)
                        n++
                    }
                }
                out[gy * w + gx] = if (n == 0) 1.0 else sum / n
            }
        }
        return out
    }

    /** Otsu's method: pick the cutoff that maximises between-class variance. */
    private fun otsu(lum: DoubleArray): Double {
        val bins = 256
        val hist = IntArray(bins)
        for (v in lum) hist[(v * (bins - 1)).toInt().coerceIn(0, bins - 1)]++
        val total = lum.size
        var sumAll = 0.0
        for (i in 0 until bins) sumAll += i * hist[i]

        var sumB = 0.0
        var wB = 0
        var best = -1.0
        var bestBin = 0
        for (i in 0 until bins) {
            wB += hist[i]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += i * hist[i]
            val mB = sumB / wB
            val mF = (sumAll - sumB) / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; bestBin = i }
        }
        // Otsu puts bins 0..bestBin in the dark class, so the cutoff sits
        // *between* bestBin and bestBin+1. Returning bestBin/255 directly is an
        // off-by-one that classifies the darkest bin as light — with flat art
        // whose dark cells are all exactly 0.0, that yields a blank grid.
        return (bestBin + 0.5) / (bins - 1)
    }
}
