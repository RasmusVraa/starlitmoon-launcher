package ru.starlitmoon.launcher.ui.components

import java.awt.image.BufferedImage
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Software Minecraft player model renderer (classic + slim + cape).
 * Geometry / UVs follow skinview3d / Java Edition conventions.
 * Pure JVM - no OpenGL / native deps. Used by [SkinPreview3D].
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

    /** Body part for walk-cycle pivots (skinview3d PlayerObject layout). */
    enum class Part {
        BODY,
        HEAD,
        RIGHT_ARM,
        LEFT_ARM,
        RIGHT_LEG,
        LEFT_LEG,
        CAPE,
    }

    /** Prebuilt mesh - build once per skin, reuse across frames. */
    class Mesh internal constructor(
        internal val quads: Array<Quad>,
    )

    /** Reusable color + depth buffers sized for a given output. */
    class Buffers(
        var outW: Int = 0,
        var outH: Int = 0,
        var color: IntArray = IntArray(0),
        var depth: FloatArray = FloatArray(0),
    ) {
        fun ensure(w: Int, h: Int) {
            if (w == outW && h == outH && color.size >= w * h && depth.size >= w * h) return
            outW = w
            outH = h
            color = IntArray(w * h)
            depth = FloatArray(w * h)
        }
    }

    /**
     * Limb / cape joint angles in radians - matches skinview3d WalkingAnimation
     * (progress advances by deltaSeconds * speed; site uses speed 1.15).
     */
    data class WalkPose(
        val leftLegRx: Float = 0f,
        val rightLegRx: Float = 0f,
        val leftArmRx: Float = 0f,
        val rightArmRx: Float = 0f,
        val leftArmRz: Float = 0f,
        val rightArmRz: Float = 0f,
        val headRx: Float = 0f,
        val headRy: Float = 0f,
        val capeRx: Float = (10.8 * Math.PI / 180.0).toFloat(),
    ) {
        companion object {
            fun fromProgress(progress: Float, headBobbing: Boolean = true): WalkPose {
                val t = progress * 8f
                val basicArmRz = (Math.PI * 0.02).toFloat()
                val basicCapeRx = (Math.PI * 0.06).toFloat()
                return WalkPose(
                    leftLegRx = sin(t) * 0.5f,
                    rightLegRx = sin(t + Math.PI.toFloat()) * 0.5f,
                    leftArmRx = sin(t + Math.PI.toFloat()) * 0.5f,
                    rightArmRx = sin(t) * 0.5f,
                    leftArmRz = cos(t) * 0.03f + basicArmRz,
                    rightArmRz = cos(t + Math.PI.toFloat()) * 0.03f - basicArmRz,
                    headRy = if (headBobbing) sin(t / 4f) * 0.2f else 0f,
                    headRx = if (headBobbing) sin(t / 5f) * 0.1f else 0f,
                    capeRx = sin(t / 1.5f) * 0.06f + basicCapeRx,
                )
            }
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
        // Transparent pixel at classic right-arm outer column → Alex/slim layout.
        return ((atlas.argb(50, 16) ushr 24) and 0xFF) == 0
    }

    fun buildMesh(skin: Atlas, cape: Atlas?, slim: Boolean): Mesh {
        val quads = ArrayList<Quad>(96)
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
        return render(mesh, yawDeg, pitchDeg, outW, outH, buf, WalkPose())
    }

    fun render(
        mesh: Mesh,
        yawDeg: Float,
        pitchDeg: Float,
        outW: Int,
        outH: Int,
        buffers: Buffers,
        pose: WalkPose = WalkPose(),
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

        val t0 = FloatArray(3)
        val t1 = FloatArray(3)
        val t2 = FloatArray(3)
        val t3 = FloatArray(3)
        val lx = FloatArray(4)
        val ly = FloatArray(4)
        val lz = FloatArray(4)

        fun transformInto(x: Float, y: Float, z: Float, out: FloatArray) {
            val lyLocal = y - pivotY
            // Yaw around Y, then pitch around X - orbit about torso.
            val x1 = x * cy + z * sy
            val z1 = -x * sy + z * cy
            out[0] = x1
            out[1] = lyLocal * cp - z1 * sp
            out[2] = lyLocal * sp + z1 * cp
        }

        for (q in mesh.quads) {
            applyPartTransform(q, pose, lx, ly, lz)
            transformInto(lx[0], ly[0], lz[0], t0)
            transformInto(lx[1], ly[1], lz[1], t1)
            transformInto(lx[2], ly[2], lz[2], t2)
            transformInto(lx[3], ly[3], lz[3], t3)

            val e1x = t1[0] - t0[0]
            val e1y = t1[1] - t0[1]
            val e1z = t1[2] - t0[2]
            val e2x = t2[0] - t0[0]
            val e2y = t2[1] - t0[1]
            val e2z = t2[2] - t0[2]
            // Camera looks toward +Z (depth = camDist - z). Outward CCW → nz faces camera.
            val nx = e1y * e2z - e1z * e2y
            val ny = e1z * e2x - e1x * e2z
            val nz = e1x * e2y - e1y * e2x
            if (!q.doubleSided && nz <= 0f) continue

            // Soft key light from upper-left/front.
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

    // ─── mesh builders ─────────────────────────────────────────────────────────

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
        val part: Part = Part.BODY,
    )

    /**
     * Apply joint transform into [ox]/[oy]/[oz] (4 verts).
     *
     * Limbs/head: rest-pose world space, rotated around shoulder/hip/neck pivots.
     * Cape: world-space hang (v1.9.3/1.9.5). Do NOT apply Ry(PI) here - that
     * flipped winding so only the 1px edge UV showed as a strip through the body.
     */
    private fun applyPartTransform(q: Quad, pose: WalkPose, ox: FloatArray, oy: FloatArray, oz: FloatArray) {
        when (q.part) {
            Part.CAPE -> {
                // Hang is baked in buildCape. Subtle walk sway around shoulders only.
                val baseHang = (Math.PI * 0.06).toFloat()
                val sway = pose.capeRx - baseHang
                if (kotlin.math.abs(sway) < 1e-5f) {
                    ox[0] = q.x0; oy[0] = q.y0; oz[0] = q.z0
                    ox[1] = q.x1; oy[1] = q.y1; oz[1] = q.z1
                    ox[2] = q.x2; oy[2] = q.y2; oz[2] = q.z2
                    ox[3] = q.x3; oy[3] = q.y3; oz[3] = q.z3
                } else {
                    rotatePivot(q, 0f, 24f, -2f, sway, 0f, 0f, ox, oy, oz)
                }
            }
            Part.HEAD -> rotatePivot(q, 0f, 24f, 0f, pose.headRx, pose.headRy, 0f, ox, oy, oz)
            Part.RIGHT_ARM -> rotatePivot(q, -5f, 22f, 0f, pose.rightArmRx, 0f, pose.rightArmRz, ox, oy, oz)
            Part.LEFT_ARM -> rotatePivot(q, 5f, 22f, 0f, pose.leftArmRx, 0f, pose.leftArmRz, ox, oy, oz)
            Part.RIGHT_LEG -> rotatePivot(q, -1.9f, 12f, -0.1f, pose.rightLegRx, 0f, 0f, ox, oy, oz)
            Part.LEFT_LEG -> rotatePivot(q, 1.9f, 12f, -0.1f, pose.leftLegRx, 0f, 0f, ox, oy, oz)
            Part.BODY -> {
                ox[0] = q.x0; oy[0] = q.y0; oz[0] = q.z0
                ox[1] = q.x1; oy[1] = q.y1; oz[1] = q.z1
                ox[2] = q.x2; oy[2] = q.y2; oz[2] = q.z2
                ox[3] = q.x3; oy[3] = q.y3; oz[3] = q.z3
            }
        }
    }

    /** Euler XYZ (Three.js default): R = Rz * Ry * Rx on (p - pivot), then + pivot. */
    private fun rotatePivot(
        q: Quad,
        px: Float, py: Float, pz: Float,
        rx: Float, ry: Float, rz: Float,
        ox: FloatArray, oy: FloatArray, oz: FloatArray,
    ) {
        val cx = cos(rx); val sx = sin(rx)
        val cy = cos(ry); val sy = sin(ry)
        val cz = cos(rz); val sz = sin(rz)
        fun one(i: Int, x: Float, y: Float, z: Float) {
            var lx = x - px
            var ly = y - py
            var lz = z - pz
            // Rx
            run {
                val yy = ly * cx - lz * sx
                val zz = ly * sx + lz * cx
                ly = yy; lz = zz
            }
            // Ry
            run {
                val xx = lx * cy + lz * sy
                val zz = -lx * sy + lz * cy
                lx = xx; lz = zz
            }
            // Rz
            run {
                val xx = lx * cz - ly * sz
                val yy = lx * sz + ly * cz
                lx = xx; ly = yy
            }
            ox[i] = lx + px
            oy[i] = ly + py
            oz[i] = lz + pz
        }
        one(0, q.x0, q.y0, q.z0)
        one(1, q.x1, q.y1, q.z1)
        one(2, q.x2, q.y2, q.z2)
        one(3, q.x3, q.y3, q.z3)
    }

    private fun buildPlayer(skin: Atlas, slim: Boolean, out: MutableList<Quad>) {
        val armW = if (slim) 3f else 4f
        // Feet at y=0; head top at y=32. Matches skinview3d proportions (+0.5 overlay inflate).
        box(out, skin, 0f, 24f, 0f, 8f, 8f, 8f, 0, 0, uvW = 8, uvH = 8, uvD = 8, opaqueOnly = true, part = Part.HEAD)
        box(out, skin, 0f, 24f - 0.5f, 0f, 9f, 9f, 9f, 32, 0, uvW = 8, uvH = 8, uvD = 8, opaqueOnly = false, part = Part.HEAD)

        box(out, skin, 0f, 12f, 0f, 8f, 12f, 4f, 16, 16, uvW = 8, uvH = 12, uvD = 4, opaqueOnly = true, part = Part.BODY)
        box(out, skin, 0f, 12f - 0.25f, 0f, 8.5f, 12.5f, 4.5f, 16, 32, uvW = 8, uvH = 12, uvD = 4, opaqueOnly = false, part = Part.BODY)

        // Right arm (player's right = -X). Center at -(4 + armW/2) so inner edge meets body at x=-4.
        val rArmX = -(4f + armW / 2f)
        box(out, skin, rArmX, 12f, 0f, armW, 12f, 4f, 40, 16, uvW = armW.toInt(), uvH = 12, uvD = 4, opaqueOnly = true, part = Part.RIGHT_ARM)
        box(
            out, skin, rArmX, 12f - 0.25f, 0f, armW + 0.5f, 12.5f, 4.5f, 40, 32,
            uvW = armW.toInt(), uvH = 12, uvD = 4, opaqueOnly = false, part = Part.RIGHT_ARM,
        )

        // Left arm
        val lArmX = 4f + armW / 2f
        box(out, skin, lArmX, 12f, 0f, armW, 12f, 4f, 32, 48, uvW = armW.toInt(), uvH = 12, uvD = 4, opaqueOnly = true, part = Part.LEFT_ARM)
        box(
            out, skin, lArmX, 12f - 0.25f, 0f, armW + 0.5f, 12.5f, 4.5f, 48, 48,
            uvW = armW.toInt(), uvH = 12, uvD = 4, opaqueOnly = false, part = Part.LEFT_ARM,
        )

        // Right / left legs - slight inset like skinview3d (±1.9)
        box(out, skin, -1.9f, 0f, 0f, 4f, 12f, 4f, 0, 16, uvW = 4, uvH = 12, uvD = 4, opaqueOnly = true, part = Part.RIGHT_LEG)
        box(out, skin, -1.9f, -0.25f, 0f, 4.5f, 12.5f, 4.5f, 0, 32, uvW = 4, uvH = 12, uvD = 4, opaqueOnly = false, part = Part.RIGHT_LEG)

        box(out, skin, 1.9f, 0f, 0f, 4f, 12f, 4f, 16, 48, uvW = 4, uvH = 12, uvD = 4, opaqueOnly = true, part = Part.LEFT_LEG)
        box(out, skin, 1.9f, -0.25f, 0f, 4.5f, 12.5f, 4.5f, 0, 48, uvW = 4, uvH = 12, uvD = 4, opaqueOnly = false, part = Part.LEFT_LEG)
    }

    /**
     * Standard Minecraft cape in world space (v1.9.3 / v1.9.5).
     *
     * skinview3d: BoxGeometry(10,16,1) + setCapeUVs(0,0,10,16,1) then group
     * Ry(PI) so atlas *front* (1,1)-(11,17) faces outward (-Z). Emulate that
     * with explicit faces - applying Ry(PI) to box() quads flipped winding and
     * left only the 1px edge UV visible (pink strip through the torso).
     *
     * Geometry: 10x16x1 sheet behind the back, ~10.8° hang, top at shoulders.
     */
    private fun buildCape(cape: Atlas, out: MutableList<Quad>) {
        val w = 10f
        val h = 16f
        val d = 1f
        val tilt = Math.toRadians(10.8).toFloat()
        val cosT = cos(tilt)
        val sinT = sin(tilt)
        val topY = 24f
        val attachZ = -2f
        // Inset UVs slightly so nearest sampling skips magenta gutters.
        val e = 0.001f

        fun capePoint(lx: Float, lyFromTop: Float, lzOut: Float): FloatArray {
            // lyFromTop: 0 = shoulders, h = bottom hem.
            // lzOut: 0 = outside (-Z), d = inside (toward body / +Z).
            val y = topY - lyFromTop * cosT - lzOut * sinT
            val z = attachZ - lyFromTop * sinT + lzOut * cosT
            return floatArrayOf(lx, y, z)
        }

        val x0 = -w / 2f
        val x1 = w / 2f
        val o00 = capePoint(x0, h, 0f)
        val o10 = capePoint(x1, h, 0f)
        val o11 = capePoint(x1, 0f, 0f)
        val o01 = capePoint(x0, 0f, 0f)
        val i00 = capePoint(x0, h, d)
        val i10 = capePoint(x1, h, d)
        val i11 = capePoint(x1, 0f, d)
        val i01 = capePoint(x0, 0f, d)

        // Outside (-Z): front UV (1,1)-(11,17). Winding matches body back face.
        out += quad(
            o10[0], o10[1], o10[2], o00[0], o00[1], o00[2], o01[0], o01[1], o01[2], o11[0], o11[1], o11[2],
            1f + e, 17f - e, 11f - e, 17f - e, 11f - e, 1f + e, 1f + e, 1f + e,
            cape, opaqueOnly = false, doubleSided = false, part = Part.CAPE,
        )
        // Inside (+Z): back UV (12,1)-(22,17)
        out += quad(
            i00[0], i00[1], i00[2], i10[0], i10[1], i10[2], i11[0], i11[1], i11[2], i01[0], i01[1], i01[2],
            12f + e, 17f - e, 22f - e, 17f - e, 22f - e, 1f + e, 12f + e, 1f + e,
            cape, opaqueOnly = false, doubleSided = false, part = Part.CAPE,
        )
        // Thin edges (setCapeUVs left/right/top/bottom) - solid sheet from the side.
        // Player's right (-X): atlas (0,1)-(1,17)
        out += quad(
            o00[0], o00[1], o00[2], i00[0], i00[1], i00[2], i01[0], i01[1], i01[2], o01[0], o01[1], o01[2],
            0f + e, 17f - e, 1f - e, 17f - e, 1f - e, 1f + e, 0f + e, 1f + e,
            cape, opaqueOnly = false, doubleSided = false, part = Part.CAPE,
        )
        // Player's left (+X): atlas (11,1)-(12,17)
        out += quad(
            i10[0], i10[1], i10[2], o10[0], o10[1], o10[2], o11[0], o11[1], o11[2], i11[0], i11[1], i11[2],
            11f + e, 17f - e, 12f - e, 17f - e, 12f - e, 1f + e, 11f + e, 1f + e,
            cape, opaqueOnly = false, doubleSided = false, part = Part.CAPE,
        )
        // Top (shoulders): atlas (1,0)-(11,1)
        out += quad(
            o01[0], o01[1], o01[2], i01[0], i01[1], i01[2], i11[0], i11[1], i11[2], o11[0], o11[1], o11[2],
            1f + e, 1f - e, 11f - e, 1f - e, 11f - e, 0f + e, 1f + e, 0f + e,
            cape, opaqueOnly = false, doubleSided = false, part = Part.CAPE,
        )
        // Bottom hem: atlas (11,0)-(21,1) - Minecraft flips bottom U
        out += quad(
            o00[0], o00[1], o00[2], o10[0], o10[1], o10[2], i10[0], i10[1], i10[2], i00[0], i00[1], i00[2],
            21f - e, 0f + e, 11f + e, 0f + e, 11f + e, 1f - e, 21f - e, 1f - e,
            cape, opaqueOnly = false, doubleSided = false, part = Part.CAPE,
        )
    }

    /**
     * Minecraft-style box: origin is bottom-center of the box in XZ; y is bottom.
     * UV unwrap matches skinview3d setSkinUVs / setCapeUVs (width, height, depth).
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
        doubleSided: Boolean = false,
        part: Part = Part.BODY,
    ) {
        if (!opaqueOnly && !regionHasPixels(atlas, u, v, uvW, uvH, uvD)) return

        val x0 = cx - width / 2f
        val x1 = cx + width / 2f
        val y0 = yBottom
        val y1 = yBottom + height
        val z0 = cz - depth / 2f
        val z1 = cz + depth / 2f
        val uf = u.toFloat()
        val vf = v.toFloat()
        val fw = uvW.toFloat()
        val fh = uvH.toFloat()
        val fd = uvD.toFloat()

        // skinview3d layout:
        //   top    (u+d, v) → (u+d+w, v+d)
        //   bottom (u+d+w, v) → (u+2w+d, v+d)   - UVs flipped on bottom
        //   right  (u, v+d) → …                  / player's right = our -X
        //   front  (u+d, v+d) → …
        //   left   (u+d+w, v+d) → …
        //   back   (u+d+w+d, v+d) → …
        addBoxFaces(
            out, atlas,
            x0, y0, z0, x1, y1, z1,
            frontU = uf + fd, frontV = vf + fd, frontW = fw, frontH = fh,
            backU = uf + fd + fw + fd, backV = vf + fd, backW = fw, backH = fh,
            rightU = uf, rightV = vf + fd, rightW = fd, rightH = fh,
            leftU = uf + fd + fw, leftV = vf + fd, leftW = fd, leftH = fh,
            topU = uf + fd, topV = vf, topW = fw, topH = fd,
            bottomU = uf + fd + fw, bottomV = vf, bottomW = fw, bottomH = fd,
            opaqueOnly = opaqueOnly,
            doubleSided = doubleSided,
            part = part,
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
        part: Part = Part.BODY,
    ) {
        // Inset max UV by epsilon so nearest sampling never bleeds into the next atlas column
        // (cape templates often have magenta in the unused 1px gutters).
        val e = 0.001f
        // Front (+Z)
        out += quad(
            x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1,
            frontU, frontV + frontH - e, frontU + frontW - e, frontV + frontH - e,
            frontU + frontW - e, frontV, frontU, frontV,
            atlas, opaqueOnly, doubleSided, part,
        )
        // Back (-Z)
        out += quad(
            x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0,
            backU, backV + backH - e, backU + backW - e, backV + backH - e,
            backU + backW - e, backV, backU, backV,
            atlas, opaqueOnly, doubleSided, part,
        )
        // Right (-X) - player's right
        out += quad(
            x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0,
            rightU, rightV + rightH - e, rightU + rightW - e, rightV + rightH - e,
            rightU + rightW - e, rightV, rightU, rightV,
            atlas, opaqueOnly, doubleSided, part,
        )
        // Left (+X)
        out += quad(
            x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1,
            leftU, leftV + leftH - e, leftU + leftW - e, leftV + leftH - e,
            leftU + leftW - e, leftV, leftU, leftV,
            atlas, opaqueOnly, doubleSided, part,
        )
        // Top (+Y)
        out += quad(
            x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0,
            topU, topV + topH - e, topU + topW - e, topV + topH - e,
            topU + topW - e, topV, topU, topV,
            atlas, opaqueOnly, doubleSided, part,
        )
        // Bottom (-Y) - Minecraft flips bottom UVs (skinview3d)
        out += quad(
            x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1,
            bottomU + bottomW - e, bottomV, bottomU, bottomV,
            bottomU, bottomV + bottomH - e, bottomU + bottomW - e, bottomV + bottomH - e,
            atlas, opaqueOnly, doubleSided, part,
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
        part: Part,
    ) = Quad(
        x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3,
        u0, v0, u1, v1, u2, v2, u3, v3,
        atlas, opaqueOnly, doubleSided, part,
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
        // lookY is mid-body in model space; after pivot, mid is ≈0 so offset is lookY-16 (=0).
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
