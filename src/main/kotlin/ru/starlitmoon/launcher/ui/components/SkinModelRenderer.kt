package ru.starlitmoon.launcher.ui.components

import java.awt.image.BufferedImage
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Software Minecraft player model renderer (classic + slim + cape).
 * Geometry / UVs follow skinview3d / Java Edition conventions.
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

    /** Prebuilt mesh — build once per skin, reuse across frames. */
    class Mesh internal constructor(
        internal val quads: Array<Quad>,
    )

    /** Reusable color + depth buffers sized for a given output. */
    class Buffers(
        var outW: Int,
        var outH: Int,
        var color: IntArray = IntArray(outW * outH),
        var depth: FloatArray = FloatArray(outW * outH),
    ) {
        fun ensure(w: Int, h: Int) {
            if (w == outW && h == outH && color.size >= w * h && depth.size >= w * h) return
            outW = w
            outH = h
            color = IntArray(w * h)
            depth = FloatArray(w * h)
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
        // Transparent pixel at classic right-arm outer column ⇒ Alex/slim layout.
        return ((atlas.argb(50, 16) ushr 24) and 0xFF) == 0
    }

    fun buildMesh(skin: Atlas, cape: Atlas?, slim: Boolean): Mesh {
        val quads = ArrayList<Quad>(72)
        buildPlayer(skin, slim, quads)
        if (cape != null && cape.width >= 22 && cape.height >= 17) {
            buildCape(cape, quads)
        }
        return Mesh(quads.toTypedArray())
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
        val mesh = buildMesh(skin, cape, slim)
        val buf = Buffers(outW, outH, dest, FloatArray(outW * outH))
        return render(mesh, yawDeg, pitchDeg, outW, outH, buf)
    }

    fun render(
        mesh: Mesh,
        yawDeg: Float,
        pitchDeg: Float,
        outW: Int,
        outH: Int,
        buffers: Buffers,
    ): IntArray {
        buffers.ensure(outW, outH)
        val dest = buffers.color
        val zBuf = buffers.depth
        val n = outW * outH
        dest.fill(0, 0, n)
        zBuf.fill(Float.POSITIVE_INFINITY, 0, n)

        val yaw = Math.toRadians(yawDeg.toDouble()).toFloat()
        val pitch = Math.toRadians(pitchDeg.toDouble()).toFloat()
        val cy = cos(yaw)
        val sy = sin(yaw)
        val cp = cos(pitch)
        val sp = sin(pitch)

        // Model: feet at y=0, head top at y=32. Pivot at mid-body for natural orbit.
        val pivotY = 16f
        val lookY = 16f
        val camDist = 48f
        val focal = min(outW, outH) * 1.55f

        // Scratch for transformed verts (avoid per-vert allocations).
        val t0 = FloatArray(3)
        val t1 = FloatArray(3)
        val t2 = FloatArray(3)
        val t3 = FloatArray(3)

        fun transformInto(x: Float, y: Float, z: Float, out: FloatArray) {
            val ly = y - pivotY
            // Yaw around Y, then pitch around X — orbit about torso.
            val x1 = x * cy + z * sy
            val z1 = -x * sy + z * cy
            out[0] = x1
            out[1] = ly * cp - z1 * sp
            out[2] = ly * sp + z1 * cp
        }

        for (q in mesh.quads) {
            transformInto(q.x0, q.y0, q.z0, t0)
            transformInto(q.x1, q.y1, q.z1, t1)
            transformInto(q.x2, q.y2, q.z2, t2)
            transformInto(q.x3, q.y3, q.z3, t3)

            val e1x = t1[0] - t0[0]
            val e1y = t1[1] - t0[1]
            val e1z = t1[2] - t0[2]
            val e2x = t2[0] - t0[0]
            val e2y = t2[1] - t0[1]
            val e2z = t2[2] - t0[2]
            // Camera looks toward +Z (depth = camDist - z). Outward CCW ⇒ +nz faces camera.
            val nx = e1y * e2z - e1z * e2y
            val ny = e1z * e2x - e1x * e2z
            val nz = e1x * e2y - e1y * e2x
            if (!q.doubleSided && nz <= 0f) continue

            // Soft key light from upper-left-front.
            val nLen = max(1e-4f, kotlin.math.sqrt(nx * nx + ny * ny + nz * nz))
            val ln = (nx * -0.35f + ny * 0.55f + nz * 0.75f) / nLen
            val shade = (0.72f + 0.28f * max(0f, ln)).coerceIn(0.55f, 1.05f)

            rasterTri(
                dest, zBuf, outW, outH, focal, lookY, camDist, shade,
                t0, t1, t2,
                q.u0, q.v0, q.u1, q.v1, q.u2, q.v2,
                q.atlas, q.opaqueOnly,
            )
            rasterTri(
                dest, zBuf, outW, outH, focal, lookY, camDist, shade,
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

    internal class Quad(
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
        // Feet at y=0; head top at y=32. Matches skinview3d proportions (+0.5 overlay inflate).
        box(out, skin, 0f, 24f, 0f, 8f, 8f, 8f, 0, 0, uvW = 8, uvH = 8, uvD = 8, opaqueOnly = true)
        box(out, skin, 0f, 24f - 0.5f, 0f, 9f, 9f, 9f, 32, 0, uvW = 8, uvH = 8, uvD = 8, opaqueOnly = false)

        box(out, skin, 0f, 12f, 0f, 8f, 12f, 4f, 16, 16, uvW = 8, uvH = 12, uvD = 4, opaqueOnly = true)
        box(out, skin, 0f, 12f - 0.25f, 0f, 8.5f, 12.5f, 4.5f, 16, 32, uvW = 8, uvH = 12, uvD = 4, opaqueOnly = false)

        // Right arm (player's right = -X). Center at -(4 + armW/2) so inner edge meets body at x=-4.
        val rArmX = -(4f + armW / 2f)
        box(out, skin, rArmX, 12f, 0f, armW, 12f, 4f, 40, 16, uvW = armW.toInt(), uvH = 12, uvD = 4, opaqueOnly = true)
        box(
            out, skin, rArmX, 12f - 0.25f, 0f, armW + 0.5f, 12.5f, 4.5f, 40, 32,
            uvW = armW.toInt(), uvH = 12, uvD = 4, opaqueOnly = false,
        )

        // Left arm
        val lArmX = 4f + armW / 2f
        box(out, skin, lArmX, 12f, 0f, armW, 12f, 4f, 32, 48, uvW = armW.toInt(), uvH = 12, uvD = 4, opaqueOnly = true)
        box(
            out, skin, lArmX, 12f - 0.25f, 0f, armW + 0.5f, 12.5f, 4.5f, 48, 48,
            uvW = armW.toInt(), uvH = 12, uvD = 4, opaqueOnly = false,
        )

        // Right / left legs — slight inset like skinview3d (±1.9)
        box(out, skin, -1.9f, 0f, 0f, 4f, 12f, 4f, 0, 16, uvW = 4, uvH = 12, uvD = 4, opaqueOnly = true)
        box(out, skin, -1.9f, -0.25f, 0f, 4.5f, 12.5f, 4.5f, 0, 32, uvW = 4, uvH = 12, uvD = 4, opaqueOnly = false)

        box(out, skin, 1.9f, 0f, 0f, 4f, 12f, 4f, 16, 48, uvW = 4, uvH = 12, uvD = 4, opaqueOnly = true)
        box(out, skin, 1.9f, -0.25f, 0f, 4.5f, 12.5f, 4.5f, 0, 48, uvW = 4, uvH = 12, uvD = 4, opaqueOnly = false)
    }

    private fun buildCape(cape: Atlas, out: MutableList<Quad>) {
        // Match skinview3d CapeObject + PlayerObject:
        // Box 10x16x1 with setCapeUVs(0,0,10,16,1); Euler XYZ Rx(10.8deg) then Ry(PI);
        // group at (0, 8, -2) in skinview = our feet-at-0 coords (0, 24, -2);
        // mesh local offset (0, -8, 0.5) -> bottom y=-16, cz=0.5.
        // After Ry(PI), atlas front (1,1)-(11,17) faces world -Z (outside / visible art).
        val local = ArrayList<Quad>(6)
        box(
            local, cape,
            cx = 0f, yBottom = -16f, cz = 0.5f,
            width = 10f, height = 16f, depth = 1f,
            u = 0, v = 0, uvW = 10, uvH = 16, uvD = 1,
            opaqueOnly = false,
        )
        if (local.isEmpty()) return

        val tilt = Math.toRadians(10.8).toFloat()
        val cosX = cos(tilt)
        val sinX = sin(tilt)

        fun xform(x: Float, y: Float, z: Float): FloatArray {
            val y1 = y * cosX - z * sinX
            val z1 = y * sinX + z * cosX
            return floatArrayOf(-x, y1 + 24f, -z1 - 2f)
        }

        for (q in local) {
            val p0 = xform(q.x0, q.y0, q.z0)
            val p1 = xform(q.x1, q.y1, q.z1)
            val p2 = xform(q.x2, q.y2, q.z2)
            val p3 = xform(q.x3, q.y3, q.z3)
            out += quad(
                p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], p2[0], p2[1], p2[2], p3[0], p3[1], p3[2],
                q.u0, q.v0, q.u1, q.v1, q.u2, q.v2, q.u3, q.v3,
                q.atlas, q.opaqueOnly, q.doubleSided,
            )
        }
    }
    /**
     * Minecraft-style box: origin is bottom-center of the box in XZ; y is bottom.
     * UV unwrap matches skinview3d setSkinUVs(u, v, width, height, depth).
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
        uvW: Int,
        uvH: Int,
        uvD: Int,
        opaqueOnly: Boolean,
    ) {
        if (!opaqueOnly && !regionHasPixels(atlas, u, v, uvW, uvH, uvD)) return

        val x0 = cx - width / 2f
        val x1 = cx + width / 2f
        val y0 = yBottom
        val y1 = yBottom + height
        val z0 = cz - depth / 2f
        val z1 = cz + depth / 2f
        val fu = u.toFloat()
        val fv = v.toFloat()
        val fw = uvW.toFloat()
        val fh = uvH.toFloat()
        val fd = uvD.toFloat()

        // skinview3d layout:
        //   top    (u+d, v) … (u+d+w, v+d)
        //   bottom (u+d+w, v) … (u+d+2w, v+d)  — UVs flipped on bottom
        //   right  (u, v+d) …           // player's right / our −X
        //   front  (u+d, v+d) …
        //   left   (u+d+w, v+d) …
        //   back   (u+d+w+d, v+d) …
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

    private fun regionHasPixels(atlas: Atlas, u: Int, v: Int, w: Int, h: Int, d: Int): Boolean {
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
        // Back (−Z)
        out += quad(
            x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0,
            backU, backV + backH, backU + backW, backV + backH,
            backU + backW, backV, backU, backV,
            atlas, opaqueOnly, doubleSided,
        )
        // Right (−X) — player's right
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
        // Bottom (−Y) — Minecraft flips bottom UVs (skinview3d)
        out += quad(
            x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1,
            bottomU + bottomW, bottomV, bottomU, bottomV,
            bottomU, bottomV + bottomH, bottomU + bottomW, bottomV + bottomH,
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
        shade: Float,
        a: FloatArray,
        b: FloatArray,
        c: FloatArray,
        u0: Float, v0: Float,
        u1: Float, v1: Float,
        u2: Float, v2: Float,
        atlas: Atlas,
        opaqueOnly: Boolean,
    ) {
        // Keep world-space inputs intact (shared across both tris of a quad).
        val d0 = camDist - a[2]
        val d1 = camDist - b[2]
        val d2 = camDist - c[2]
        if (d0 < 0.5f || d1 < 0.5f || d2 < 0.5f) return
        val s0 = focal / d0
        val s1 = focal / d1
        val s2 = focal / d2
        // lookY is mid-body in model space; after pivot, mid is ~0 so offset is lookY-16 (=0).
        val yOff = lookY - 16f
        val ax = outW * 0.5f + a[0] * s0
        val ay = outH * 0.52f - (a[1] - yOff) * s0
        val bx = outW * 0.5f + b[0] * s1
        val by = outH * 0.52f - (b[1] - yOff) * s1
        val cx = outW * 0.5f + c[0] * s2
        val cy = outH * 0.52f - (c[1] - yOff) * s2

        val minX = max(0, kotlin.math.floor(min(ax, min(bx, cx))).toInt())
        val maxX = min(outW - 1, kotlin.math.ceil(max(ax, max(bx, cx))).toInt())
        val minY = max(0, kotlin.math.floor(min(ay, min(by, cy))).toInt())
        val maxY = min(outH - 1, kotlin.math.ceil(max(ay, max(by, cy))).toInt())
        if (minX > maxX || minY > maxY) return

        val area = edge(ax, ay, bx, by, cx, cy)
        if (area == 0f) return
        val invArea = 1f / area
        val applyShade = shade < 0.999f || shade > 1.001f

        for (y in minY..maxY) {
            val py = y + 0.5f
            for (x in minX..maxX) {
                val px = x + 0.5f
                val w0 = edge(bx, by, cx, cy, px, py) * invArea
                val w1 = edge(cx, cy, ax, ay, px, py) * invArea
                val w2 = edge(ax, ay, bx, by, px, py) * invArea
                if (w0 < 0f || w1 < 0f || w2 < 0f) continue

                val depth = w0 * d0 + w1 * d1 + w2 * d2
                val idx = y * outW + x
                if (depth >= zBuf[idx]) continue

                val u = w0 * u0 + w1 * u1 + w2 * u2
                val v = w0 * v0 + w1 * v1 + w2 * v2
                var tex = sampleNearest(atlas, u, v)
                val alpha = (tex ushr 24) and 0xFF
                if (alpha < 8) continue
                if (opaqueOnly && alpha < 200) continue

                if (applyShade && alpha >= 245) {
                    tex = shadeArgb(tex, shade)
                }

                if (alpha >= 245) {
                    dest[idx] = tex
                    zBuf[idx] = depth
                } else {
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

    private fun shadeArgb(argb: Int, shade: Float): Int {
        val a = (argb ushr 24) and 0xFF
        val r = (((argb ushr 16) and 0xFF) * shade).toInt().coerceIn(0, 255)
        val g = (((argb ushr 8) and 0xFF) * shade).toInt().coerceIn(0, 255)
        val b = ((argb and 0xFF) * shade).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
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
