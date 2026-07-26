package ru.starlitmoon.launcher.ui.components

import java.awt.image.BufferedImage
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Software Minecraft player model renderer (classic + slim + cape).
 * Pure JVM — no OpenGL / native deps. Used by [SkinPreview3D].
 */
internal object SkinModelRenderer {

    data class Atlas(
        val pixels: IntArray,
        val width: Int,
        val height: Int,
    ) {
        fun argb(u: Int, v: Int): Int {
            val x = ((u % width) + width) % width
            val y = ((v % height) + height) % height
            return pixels[y * width + x]
        }
    }

    fun toAtlas(img: BufferedImage): Atlas {
        val w = img.width
        val h = img.height
        val px = IntArray(w * h)
        img.getRGB(0, 0, w, h, px, 0, w)
        return Atlas(px, w, h)
    }

    fun detectSlim(atlas: Atlas): Boolean {
        if (atlas.width < 64 || atlas.height < 32) return false
        return ((atlas.argb(50, 16) ushr 24) and 0xFF) == 0
    }

    fun render(
        skin: Atlas,
        cape: Atlas?,
        slim: Boolean,
        yawDeg: Float,
        pitchDeg: Float,
        outW: Int,
        outH: Int,
        dest: IntArray = IntArray(outW * outH),
    ): IntArray {
        require(dest.size >= outW * outH)
        dest.fill(0)
        val zBuf = FloatArray(outW * outH) { Float.POSITIVE_INFINITY }

        val quads = ArrayList<Quad>(64)
        buildPlayer(skin, slim, quads)
        if (cape != null && cape.width >= 22 && cape.height >= 17) {
            buildCape(cape, quads)
        }

        val yaw = Math.toRadians(yawDeg.toDouble()).toFloat()
        val pitch = Math.toRadians(pitchDeg.toDouble()).toFloat()
        val cy = cos(yaw)
        val sy = sin(yaw)
        val cp = cos(pitch)
        val sp = sin(pitch)

        // Model center ~ mid torso; camera looks slightly down.
        val lookY = 18f
        val camDist = 52f
        val focal = min(outW, outH) * 1.35f

        fun transform(x: Float, y: Float, z: Float): FloatArray {
            // Yaw around Y, then pitch around X.
            val x1 = x * cy + z * sy
            val z1 = -x * sy + z * cy
            val y1 = y
            val y2 = y1 * cp - z1 * sp
            val z2 = y1 * sp + z1 * cp
            return floatArrayOf(x1, y2, z2)
        }

        for (q in quads) {
            val t0 = transform(q.x0, q.y0, q.z0)
            val t1 = transform(q.x1, q.y1, q.z1)
            val t2 = transform(q.x2, q.y2, q.z2)
            val t3 = transform(q.x3, q.y3, q.z3)

            // Back-face cull via projected normal (view ≈ -Z after transform).
            val e1x = t1[0] - t0[0]
            val e1y = t1[1] - t0[1]
            val e1z = t1[2] - t0[2]
            val e2x = t2[0] - t0[0]
            val e2y = t2[1] - t0[1]
            val e2z = t2[2] - t0[2]
            val nz = e1x * e2y - e1y * e2x
            // After yaw/pitch, camera looks along +Z toward model from -Z… we place
            // model at z≈0 and project with depth = camDist - z. Facing camera ⇒ nz < 0
            // for CCW winding when viewed from +Z? Our verts are CCW when looking from outside
            // along -normal. Cull if normal · viewDir <= 0. viewDir ≈ (0,0,-1) in cam space
            // where larger z is farther? We use depth = camDist - z2, so +z faces camera.
            // Facing camera: nz > 0 for CCW from outside.
            if (!q.doubleSided && nz <= 0f) continue

            rasterTri(
                dest, zBuf, outW, outH, focal, lookY, camDist,
                t0, t1, t2,
                q.u0, q.v0, q.u1, q.v1, q.u2, q.v2,
                q.atlas, q.opaqueOnly,
            )
            rasterTri(
                dest, zBuf, outW, outH, focal, lookY, camDist,
                t0, t2, t3,
                q.u0, q.v0, q.u2, q.v2, q.u3, q.v3,
                q.atlas, q.opaqueOnly,
            )
        }
        return dest
    }

    fun pixelsToImage(pixels: IntArray, w: Int, h: Int): BufferedImage {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        img.setRGB(0, 0, w, h, pixels, 0, w)
        return img
    }

    // —— mesh builders ——

    private class Quad(
        val x0: Float, val y0: Float, val z0: Float,
        val x1: Float, val y1: Float, val z1: Float,
        val x2: Float, val y2: Float, val z2: Float,
        val x3: Float, val y3: Float, val z3: Float,
        val u0: Float, val v0: Float,
        val u1: Float, val v1: Float,
        val u2: Float, val v2: Float,
        val u3: Float, val v3: Float,
        val atlas: Atlas,
        val opaqueOnly: Boolean,
        val doubleSided: Boolean = false,
    )

    private fun buildPlayer(skin: Atlas, slim: Boolean, out: MutableList<Quad>) {
        val armW = if (slim) 3f else 4f
        // Feet at y=0; head top at y=32.
        box(out, skin, 0f, 24f, 0f, 8f, 8f, 8f, 0, 0, opaqueOnly = true) // head
        box(out, skin, 0f, 24f, 0f, 8.5f, 8.5f, 8.5f, 32, 0, opaqueOnly = false) // hat

        box(out, skin, 0f, 12f, 0f, 8f, 12f, 4f, 16, 16, opaqueOnly = true) // body
        box(out, skin, 0f, 12f, 0f, 8.5f, 12.5f, 4.5f, 16, 32, opaqueOnly = false) // jacket

        // Right arm (player's right = -X)
        box(out, skin, -(4f + armW / 2f), 12f, 0f, armW, 12f, 4f, 40, 16, opaqueOnly = true, slimArm = slim)
        box(out, skin, -(4f + armW / 2f), 12f, 0f, armW + 0.5f, 12.5f, 4.5f, 40, 32, opaqueOnly = false, slimArm = slim)

        // Left arm
        box(out, skin, 4f + armW / 2f, 12f, 0f, armW, 12f, 4f, 32, 48, opaqueOnly = true, slimArm = slim)
        box(out, skin, 4f + armW / 2f, 12f, 0f, armW + 0.5f, 12.5f, 4.5f, 48, 48, opaqueOnly = false, slimArm = slim)

        // Right leg
        box(out, skin, -2f, 0f, 0f, 4f, 12f, 4f, 0, 16, opaqueOnly = true)
        box(out, skin, -2f, 0f, 0f, 4.5f, 12.5f, 4.5f, 0, 32, opaqueOnly = false)

        // Left leg
        box(out, skin, 2f, 0f, 0f, 4f, 12f, 4f, 16, 48, opaqueOnly = true)
        box(out, skin, 2f, 0f, 0f, 4.5f, 12.5f, 4.5f, 0, 48, opaqueOnly = false)
    }

    private fun buildCape(cape: Atlas, out: MutableList<Quad>) {
        // Hang behind torso, slightly below shoulders; 10×16×1 like Minecraft.
        val cx = 0f
        val cy = 8f
        val cz = 2.6f
        val w = 10f
        val h = 16f
        val d = 0.6f
        // Front of cape (faces away from camera when yaw=0 — player faces -Z… we face +Z).
        // At yaw≈25°, back of player (cape) is visible. Cape front UV (1,1), back (12,1).
        addBoxFaces(
            out, cape,
            cx - w / 2f, cy, cz - d / 2f,
            cx + w / 2f, cy + h, cz + d / 2f,
            // Custom UVs matching cape layout
            frontU = 1f, frontV = 1f, frontW = 10f, frontH = 16f,
            backU = 12f, backV = 1f, backW = 10f, backH = 16f,
            rightU = 0f, rightV = 1f, rightW = 1f, rightH = 16f,
            leftU = 11f, leftV = 1f, leftW = 1f, leftH = 16f,
            topU = 1f, topV = 0f, topW = 10f, topH = 1f,
            bottomU = 11f, bottomV = 0f, bottomW = 10f, bottomH = 1f,
            opaqueOnly = false,
            doubleSided = false,
        )
    }

    /**
     * Minecraft-style box: origin is bottom-center of the box in XZ; y is bottom.
     * UV layout uses classic skin atlas packing.
     */
    private fun box(
        out: MutableList<Quad>,
        atlas: Atlas,
        cx: Float,
        yBottom: Float,
        cz: Float,
        width: Float,
        height: Float,
        depth: Float,
        u: Int,
        v: Int,
        opaqueOnly: Boolean,
        slimArm: Boolean = false,
    ) {
        val w = width
        val h = height
        val d = depth
        // Integer UV sizes for sampling (overlays use same texel grid as base).
        val iw = when {
            slimArm && w < 4.2f -> 3
            else -> w.toInt().coerceAtLeast(1)
        }
        val ih = when {
            h >= 12f -> 12
            h >= 8f -> 8
            else -> h.toInt().coerceAtLeast(1)
        }
        val id = when {
            d >= 8f -> 8
            d >= 4f -> 4
            else -> d.toInt().coerceIn(1, 8)
        }

        if (opaqueOnly || regionHasPixels(atlas, u, v, iw, ih, id)) {
            val x0 = cx - w / 2f
            val x1 = cx + w / 2f
            val y0 = yBottom
            val y1 = yBottom + h
            val z0 = cz - d / 2f
            val z1 = cz + d / 2f
            val fu = u.toFloat()
            val fv = v.toFloat()
            val fw = iw.toFloat()
            val fh = ih.toFloat()
            val fd = id.toFloat()
            addBoxFaces(
                out, atlas,
                x0, y0, z0, x1, y1, z1,
                frontU = fu + fd, frontV = fv + fd, frontW = fw, frontH = fh,
                backU = fu + fd + fw + fd, backV = fv + fd, backW = fw, backH = fh,
                rightU = fu, rightV = fv + fd, rightW = fd, rightH = fh,
                leftU = fu + fd + fw, leftV = fv + fd, leftW = fd, leftH = fh,
                topU = fu + fd, topV = fv, topW = fw, topH = fd,
                bottomU = fu + fd + fw, bottomV = fv, bottomW = fw, bottomH = fd,
                opaqueOnly = opaqueOnly,
            )
        }
    }

    private fun regionHasPixels(atlas: Atlas, u: Int, v: Int, w: Int, h: Int, d: Int): Boolean {
        // Quick scan of front + hat-area strip for overlays.
        val maxU = u + d + w + d + w
        val maxV = v + d + h
        for (yy in v until min(maxV, atlas.height)) {
            for (xx in u until min(maxU, atlas.width)) {
                if (((atlas.argb(xx, yy) ushr 24) and 0xFF) > 10) return true
            }
        }
        return false
    }

    private fun addBoxFaces(
        out: MutableList<Quad>,
        atlas: Atlas,
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        frontU: Float, frontV: Float, frontW: Float, frontH: Float,
        backU: Float, backV: Float, backW: Float, backH: Float,
        rightU: Float, rightV: Float, rightW: Float, rightH: Float,
        leftU: Float, leftV: Float, leftW: Float, leftH: Float,
        topU: Float, topV: Float, topW: Float, topH: Float,
        bottomU: Float, bottomV: Float, bottomW: Float, bottomH: Float,
        opaqueOnly: Boolean,
        doubleSided: Boolean = false,
    ) {
        // Front (+Z)
        out += quad(
            x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1,
            frontU, frontV + frontH, frontU + frontW, frontV + frontH,
            frontU + frontW, frontV, frontU, frontV,
            atlas, opaqueOnly, doubleSided,
        )
        // Back (-Z) — wind so outward normal faces -Z
        out += quad(
            x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0,
            backU, backV + backH, backU + backW, backV + backH,
            backU + backW, backV, backU, backV,
            atlas, opaqueOnly, doubleSided,
        )
        // Right (-X)
        out += quad(
            x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0,
            rightU, rightV + rightH, rightU + rightW, rightV + rightH,
            rightU + rightW, rightV, rightU, rightV,
            atlas, opaqueOnly, doubleSided,
        )
        // Left (+X)
        out += quad(
            x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1,
            leftU, leftV + leftH, leftU + leftW, leftV + leftH,
            leftU + leftW, leftV, leftU, leftV,
            atlas, opaqueOnly, doubleSided,
        )
        // Top (+Y)
        out += quad(
            x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0,
            topU, topV + topH, topU + topW, topV + topH,
            topU + topW, topV, topU, topV,
            atlas, opaqueOnly, doubleSided,
        )
        // Bottom (-Y)
        out += quad(
            x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1,
            bottomU, bottomV + bottomH, bottomU + bottomW, bottomV + bottomH,
            bottomU + bottomW, bottomV, bottomU, bottomV,
            atlas, opaqueOnly, doubleSided,
        )
    }

    private fun quad(
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        u0: Float, v0: Float,
        u1: Float, v1: Float,
        u2: Float, v2: Float,
        u3: Float, v3: Float,
        atlas: Atlas,
        opaqueOnly: Boolean,
        doubleSided: Boolean,
    ) = Quad(
        x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3,
        u0, v0, u1, v1, u2, v2, u3, v3,
        atlas, opaqueOnly, doubleSided,
    )

    private fun rasterTri(
        dest: IntArray,
        zBuf: FloatArray,
        outW: Int,
        outH: Int,
        focal: Float,
        lookY: Float,
        camDist: Float,
        a: FloatArray,
        b: FloatArray,
        c: FloatArray,
        u0: Float, v0: Float,
        u1: Float, v1: Float,
        u2: Float, v2: Float,
        atlas: Atlas,
        opaqueOnly: Boolean,
    ) {
        fun project(p: FloatArray): FloatArray {
            val depth = camDist - p[2]
            if (depth < 0.5f) return floatArrayOf(Float.NaN, Float.NaN, depth)
            val s = focal / depth
            val sx = outW * 0.5f + p[0] * s
            val sy = outH * 0.55f - (p[1] - lookY) * s
            return floatArrayOf(sx, sy, depth)
        }

        val pa = project(a)
        val pb = project(b)
        val pc = project(c)
        if (pa[0].isNaN() || pb[0].isNaN() || pc[0].isNaN()) return

        val minX = max(0, kotlin.math.floor(min(pa[0], min(pb[0], pc[0]))).toInt())
        val maxX = min(outW - 1, kotlin.math.ceil(max(pa[0], max(pb[0], pc[0]))).toInt())
        val minY = max(0, kotlin.math.floor(min(pa[1], min(pb[1], pc[1]))).toInt())
        val maxY = min(outH - 1, kotlin.math.ceil(max(pa[1], max(pb[1], pc[1]))).toInt())
        if (minX > maxX || minY > maxY) return

        val area = edge(pa[0], pa[1], pb[0], pb[1], pc[0], pc[1])
        if (area == 0f) return
        val invArea = 1f / area

        for (y in minY..maxY) {
            val py = y + 0.5f
            for (x in minX..maxX) {
                val px = x + 0.5f
                val w0 = edge(pb[0], pb[1], pc[0], pc[1], px, py) * invArea
                val w1 = edge(pc[0], pc[1], pa[0], pa[1], px, py) * invArea
                val w2 = edge(pa[0], pa[1], pb[0], pb[1], px, py) * invArea
                if (w0 < 0f || w1 < 0f || w2 < 0f) continue

                val depth = w0 * pa[2] + w1 * pb[2] + w2 * pc[2]
                val idx = y * outW + x
                if (depth >= zBuf[idx]) continue

                val u = w0 * u0 + w1 * u1 + w2 * u2
                val v = w0 * v0 + w1 * v1 + w2 * v2
                val tex = sampleNearest(atlas, u, v)
                val alpha = (tex ushr 24) and 0xFF
                if (alpha < 8) continue
                if (opaqueOnly && alpha < 200) {
                    // Inner body: treat semi-transparent as empty (old skins).
                    continue
                }

                if (alpha >= 245) {
                    dest[idx] = tex
                    zBuf[idx] = depth
                } else {
                    // Cape / translucent overlay — SrcOver blend, still occupy z.
                    dest[idx] = blendSrcOver(tex, dest[idx])
                    zBuf[idx] = depth
                }
            }
        }
    }

    private fun edge(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Float =
        (cx - ax) * (by - ay) - (cy - ay) * (bx - ax)

    private fun sampleNearest(atlas: Atlas, u: Float, v: Float): Int {
        val ui = kotlin.math.floor(u.toDouble()).toInt()
        val vi = kotlin.math.floor(v.toDouble()).toInt()
        return atlas.argb(ui, vi)
    }

    private fun blendSrcOver(src: Int, dst: Int): Int {
        val sa = (src ushr 24) and 0xFF
        if (sa == 0) return dst
        if (sa == 255) return src
        val da = (dst ushr 24) and 0xFF
        val sr = (src ushr 16) and 0xFF
        val sg = (src ushr 8) and 0xFF
        val sb = src and 0xFF
        val dr = (dst ushr 16) and 0xFF
        val dg = (dst ushr 8) and 0xFF
        val db = dst and 0xFF
        val inv = 255 - sa
        val outA = sa + da * inv / 255
        val outR = (sr * sa + dr * inv) / 255
        val outG = (sg * sa + dg * inv) / 255
        val outB = (sb * sa + db * inv) / 255
        return (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
    }
}
