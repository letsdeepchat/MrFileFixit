@file:Suppress("MagicNumber") // Patch sizes and search counts are tuning values, named where they matter.

package com.mrfilefixit.core.domain.image

import kotlin.random.Random

/**
 * Fills a hole in a picture from its surroundings, on the phone, with no model and no network: exemplar-based
 * synthesis (in the spirit of Efros and Leung's texture synthesis, with Ashikhmin's coherence search). The hole is
 * filled from its edge inwards; each pixel takes the colour of the known pixel whose 7 × 7 neighbourhood best
 * matches what is already around it. Candidates are the continuations of the neighbours' sources (which keeps
 * lines, edges and textures going) plus random known pixels. Plain backgrounds, walls, sky, grass, skin and
 * paper come out clean; a large object in front of a detailed scene can look smeared.
 */
object Inpainting {
    private const val PATCH_RADIUS = 3
    private const val RANDOM_CANDIDATES = 48
    private const val NO_SOURCE = -1

    /**
     * Replaces the pixels of [argb] (row-major, [width] × [height]) where [mask] is true. Pixels outside the mask
     * are never changed. Does nothing when nothing is masked or nothing is known.
     */
    fun fill(argb: IntArray, width: Int, height: Int, mask: BooleanArray, random: Random = Random(SEED)) {
        require(argb.size == width * height && mask.size == argb.size) { "Pixels and mask must be width × height" }
        val knownPixels = (argb.indices).filter { !mask[it] }
        if (knownPixels.isEmpty() || knownPixels.size == argb.size) return
        val original = BooleanArray(argb.size) { !mask[it] }
        val filled = original.copyOf()
        val source = IntArray(argb.size) { if (original[it]) it else NO_SOURCE }
        fillOrder(width, height, original).forEach { p ->
            val best = bestSource(argb, width, height, p, original, filled, source, knownPixels, random)
            argb[p] = if (best >= 0) argb[best] else averageAround(argb, width, height, p, filled)
            source[p] = best
            filled[p] = true
        }
    }

    /** Masked pixels from the edge of the hole inwards (breadth-first from the known pixels). */
    internal fun fillOrder(width: Int, height: Int, known: BooleanArray): IntArray {
        val seen = known.copyOf()
        val queue = IntArray(known.size)
        var head = 0
        var tail = 0
        val order = IntArray(known.count { !it })
        var n = 0
        for (p in known.indices) {
            if (!known[p]) continue
            val x = p % width
            val y = p / width
            if (neighbours4(x, y, width, height).any { !known[it] }) queue[tail++] = p
        }
        while (head < tail) {
            val p = queue[head++]
            for (q in neighbours4(p % width, p / width, width, height)) {
                if (!seen[q]) {
                    seen[q] = true
                    order[n++] = q
                    queue[tail++] = q
                }
            }
        }
        return order.copyOf(n)
    }

    @Suppress("LongParameterList")
    private fun bestSource(
        argb: IntArray,
        width: Int,
        height: Int,
        p: Int,
        original: BooleanArray,
        filled: BooleanArray,
        source: IntArray,
        known: List<Int>,
        random: Random,
    ): Int {
        val px = p % width
        val py = p / width
        var best = NO_SOURCE
        var bestScore = Long.MAX_VALUE
        val tryCandidate = { c: Int ->
            if (c >= 0 && original[c]) {
                val score = patchDistance(argb, width, height, px, py, c % width, c / width, original, filled)
                if (score < bestScore) {
                    bestScore = score
                    best = c
                }
            }
        }
        // Coherence: continue the source of each filled or known neighbour.
        for (dy in -1..1) {
            for (dx in -1..1) {
                val qx = px + dx
                val qy = py + dy
                if ((dx == 0 && dy == 0) || qx !in 0 until width || qy !in 0 until height) continue
                val q = qy * width + qx
                val s = source[q]
                if (!filled[q] || s < 0) continue
                val sx = s % width - dx
                val sy = s / width - dy
                if (sx in 0 until width && sy in 0 until height) tryCandidate(sy * width + sx)
            }
        }
        repeat(RANDOM_CANDIDATES) { tryCandidate(known[random.nextInt(known.size)]) }
        return best
    }

    /**
     * Mean squared colour difference between the patch around (px, py) and the one around (cx, cy), over the
     * offsets where the target side is already known or filled and the candidate side is original. Patches with
     * nothing to compare score worst.
     */
    @Suppress("LongParameterList")
    private fun patchDistance(
        argb: IntArray,
        width: Int,
        height: Int,
        px: Int,
        py: Int,
        cx: Int,
        cy: Int,
        original: BooleanArray,
        filled: BooleanArray,
    ): Long {
        var sum = 0L
        var count = 0
        for (dy in -PATCH_RADIUS..PATCH_RADIUS) {
            val ty = py + dy
            val sy = cy + dy
            if (ty !in 0 until height || sy !in 0 until height) continue
            for (dx in -PATCH_RADIUS..PATCH_RADIUS) {
                val tx = px + dx
                val sx = cx + dx
                if (tx !in 0 until width || sx !in 0 until width) continue
                val t = ty * width + tx
                val s = sy * width + sx
                if (!filled[t] || !original[s]) continue
                sum += colourDistance(argb[t], argb[s])
                count++
            }
        }
        return if (count == 0) Long.MAX_VALUE else sum * PRECISION / count
    }

    private fun colourDistance(a: Int, b: Int): Int {
        val dr = (a shr 16 and 0xFF) - (b shr 16 and 0xFF)
        val dg = (a shr 8 and 0xFF) - (b shr 8 and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return dr * dr + dg * dg + db * db
    }

    /** Fallback when no candidate fits: the average of the filled neighbours. */
    private fun averageAround(argb: IntArray, width: Int, height: Int, p: Int, filled: BooleanArray): Int {
        var r = 0
        var g = 0
        var b = 0
        var a = 0
        var n = 0
        val px = p % width
        val py = p / width
        for (dy in -1..1) {
            for (dx in -1..1) {
                val x = px + dx
                val y = py + dy
                if (x !in 0 until width || y !in 0 until height || !filled[y * width + x]) continue
                val c = argb[y * width + x]
                a += c ushr 24
                r += c shr 16 and 0xFF
                g += c shr 8 and 0xFF
                b += c and 0xFF
                n++
            }
        }
        if (n == 0) return argb[p]
        return (a / n shl 24) or (r / n shl 16) or (g / n shl 8) or (b / n)
    }

    private fun neighbours4(x: Int, y: Int, width: Int, height: Int): List<Int> = buildList(4) {
        if (x > 0) add(y * width + x - 1)
        if (x < width - 1) add(y * width + x + 1)
        if (y > 0) add((y - 1) * width + x)
        if (y < height - 1) add((y + 1) * width + x)
    }

    private const val PRECISION = 16L
    private const val SEED = 20_261_121
}
