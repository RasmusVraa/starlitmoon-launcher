package ru.starlitmoon.launcher.ui.components

import java.awt.image.BufferedImage

/**
 * Offline cape geometry / UV checks with a synthetic 64x32 atlas
 * (distinct colors per setCapeUVs region). Run via `./gradlew verifyCape`.
 */
object CapeGeometryVerify {

    // Distinct opaque colors per cape atlas region (ARGB).
    private val FRONT = argb(0xE1, 0x1D, 0x48)
    private val BACK = argb(0x25, 0x63, 0xEB)
    private val EDGE_R = argb(0x16, 0xA3, 0x4A)
    private val EDGE_L = argb(0xEA, 0xB3, 0x08)
    private val TOP = argb(0x08, 0x91, 0xB2)
    private val BOTTOM = argb(0x7C, 0x3A, 0xED)
    private val GUTTER = argb(0xFF, 0x00, 0xFF)
    private val SKIN = argb(0x6B, 0x72, 0x80)
    private val FACE = argb(0xD1, 0xD5, 0xDB)

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    @JvmStatic
    fun main(args: Array<String>) {
        val failures = runChecks()
        if (failures.isEmpty()) {
            println("CapeGeometryVerify: OK")
            return
        }
        System.err.println("CapeGeometryVerify: FAILED")
        failures.forEach { System.err.println(" - $it") }
        kotlin.system.exitProcess(1)
    }

    fun runChecks(): List<String> {
        val failures = ArrayList<String>()
        val cape = syntheticCapeAtlas()
        val skin = syntheticSkinAtlas()
        val mesh = SkinModelRenderer.buildMesh(skin, cape, slim = false)

        val capeQuads = mesh.quads.filter { it.part == SkinModelRenderer.Part.CAPE }
        if (capeQuads.size < 2) {
            failures += "expected >=2 cape quads, got ${capeQuads.size}"
            return failures
        }

        // Inside face attaches near the back plane (~z=-1); outside hangs further back.
        val outside = capeQuads.filter { samplesRegion(it, 1, 1, 11, 17) }
        val inside = capeQuads.filter { samplesRegion(it, 12, 1, 22, 17) }
        if (outside.isEmpty()) failures += "missing outside face mapped to front UV (1,1)-(11,17)"
        if (inside.isEmpty()) failures += "missing inside face mapped to back UV (12,1)-(22,17)"
        if (outside.isNotEmpty()) {
            val outMinZ = outside.minOf { q -> minOf(q.z0, q.z1, q.z2, q.z3) }
            val outAvgZ = outside.map { avgZ(it) }.average()
            if (outAvgZ > -2.2) {
                failures += "outside face not behind torso (avgZ=$outAvgZ, want < -2.2)"
            }
            if (outMinZ > -4f) {
                failures += "cape does not hang downward/back: outMinZ=$outMinZ"
            }
            // No cape outside verts should sit in front of the torso midplane.
            val bad = outside.any { q ->
                listOf(q.z0, q.z1, q.z2, q.z3).any { it > -1.8f }
            }
            if (bad) failures += "outside face verts poke through torso (z > -1.8)"
        }
        if (outside.isNotEmpty() && inside.isNotEmpty()) {
            val outZ = outside.map { avgZ(it) }.average()
            val inZ = inside.map { avgZ(it) }.average()
            if (outZ >= inZ) {
                failures += "outside face not behind inside face (outZ=$outZ inZ=$inZ)"
            }
        }

        val backView = SkinModelRenderer.render(
            mesh, yawDeg = 180f, pitchDeg = 0f, outW = 96, outH = 128,
            buffers = SkinModelRenderer.Buffers(),
            pose = SkinModelRenderer.WalkPose(),
        )
        val backStats = colorStats(backView, 96, 128, torsoBand = true)
        if (backStats.getOrDefault(FRONT, 0) < 80) {
            failures += "back view: too few outside-face pixels (FRONT=${backStats[FRONT] ?: 0})"
        }
        if (backStats.getOrDefault(GUTTER, 0) > 30) {
            failures += "back view: magenta gutter leaking (GUTTER=${backStats[GUTTER] ?: 0})"
        }

        val sideView = SkinModelRenderer.render(
            mesh, yawDeg = 90f, pitchDeg = 0f, outW = 96, outH = 128,
            buffers = SkinModelRenderer.Buffers(),
            pose = SkinModelRenderer.WalkPose(),
        )
        val coreGutter = countColorInCore(sideView, 96, 128, GUTTER)
        if (coreGutter > 12) {
            failures += "side view: magenta strip through body core (pixels=$coreGutter)"
        }
        val sideStats = colorStats(sideView, 96, 128, torsoBand = true)
        val capeVisible = (sideStats[FRONT] ?: 0) + (sideStats[EDGE_R] ?: 0) +
            (sideStats[EDGE_L] ?: 0) + (sideStats[BACK] ?: 0)
        if (capeVisible < 8) {
            failures += "side view: cape not visible behind body (capePixels=$capeVisible)"
        }

        return failures
    }

    private fun syntheticCapeAtlas(): SkinModelRenderer.Atlas {
        val w = 64
        val h = 32
        val px = IntArray(w * h) { GUTTER }
        fun fill(u0: Int, v0: Int, u1: Int, v1: Int, color: Int) {
            for (v in v0 until v1) for (u in u0 until u1) {
                px[v * w + u] = color
            }
        }
        fill(1, 1, 11, 17, FRONT)
        fill(12, 1, 22, 17, BACK)
        fill(0, 1, 1, 17, EDGE_R)
        fill(11, 1, 12, 17, EDGE_L)
        fill(1, 0, 11, 1, TOP)
        fill(11, 0, 21, 1, BOTTOM)
        return SkinModelRenderer.Atlas(px, w, h)
    }

    private fun syntheticSkinAtlas(): SkinModelRenderer.Atlas {
        val w = 64
        val h = 64
        val px = IntArray(w * h)
        fun fill(u0: Int, v0: Int, u1: Int, v1: Int, color: Int) {
            for (v in v0 until v1) for (u in u0 until u1) {
                px[v * w + u] = color
            }
        }
        fill(0, 0, 64, 64, SKIN)
        fill(8, 8, 16, 16, FACE)
        return SkinModelRenderer.Atlas(px, w, h)
    }

    private fun samplesRegion(q: SkinModelRenderer.Quad, u0: Int, v0: Int, u1: Int, v1: Int): Boolean {
        val us = listOf(q.u0, q.u1, q.u2, q.u3)
        val vs = listOf(q.v0, q.v1, q.v2, q.v3)
        val cu = us.average()
        val cv = vs.average()
        return cu >= u0 && cu < u1 && cv >= v0 && cv < v1
    }

    private fun avgZ(q: SkinModelRenderer.Quad): Double =
        (q.z0 + q.z1 + q.z2 + q.z3) / 4.0

    private fun colorStats(
        pixels: IntArray,
        w: Int,
        h: Int,
        torsoBand: Boolean,
    ): Map<Int, Int> {
        val counts = HashMap<Int, Int>()
        val x0 = if (torsoBand) w * 3 / 8 else 0
        val x1 = if (torsoBand) w * 5 / 8 else w
        val y0 = if (torsoBand) h / 5 else 0
        val y1 = if (torsoBand) h * 3 / 4 else h
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val c = pixels[y * w + x]
                if ((c ushr 24) and 0xFF < 8) continue
                val key = nearestPalette(c)
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
        return counts
    }

    private fun countColorInCore(pixels: IntArray, w: Int, h: Int, color: Int): Int {
        var n = 0
        val x0 = w * 7 / 16
        val x1 = w * 9 / 16
        val y0 = h / 4
        val y1 = h * 5 / 8
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                if (nearestPalette(pixels[y * w + x]) == color) n++
            }
        }
        return n
    }

    private fun nearestPalette(argb: Int): Int {
        val palette = intArrayOf(FRONT, BACK, EDGE_R, EDGE_L, TOP, BOTTOM, GUTTER, SKIN, FACE)
        var best = palette[0]
        var bestD = Int.MAX_VALUE
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        for (p in palette) {
            val pr = (p ushr 16) and 0xFF
            val pg = (p ushr 8) and 0xFF
            val pb = p and 0xFF
            val d = (r - pr) * (r - pr) + (g - pg) * (g - pg) + (b - pb) * (b - pb)
            if (d < bestD) {
                bestD = d
                best = p
            }
        }
        return best
    }

    fun renderDebugPng(path: String, yawDeg: Float) {
        val mesh = SkinModelRenderer.buildMesh(syntheticSkinAtlas(), syntheticCapeAtlas(), slim = false)
        val px = SkinModelRenderer.render(
            mesh, yawDeg, 0f, 192, 256, SkinModelRenderer.Buffers(), SkinModelRenderer.WalkPose(),
        )
        val img = BufferedImage(192, 256, BufferedImage.TYPE_INT_ARGB)
        img.setRGB(0, 0, 192, 256, px, 0, 192)
        javax.imageio.ImageIO.write(img, "png", java.io.File(path))
    }
}