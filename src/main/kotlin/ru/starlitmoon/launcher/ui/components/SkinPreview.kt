package ru.starlitmoon.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.exists
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * True 3D Minecraft skin: textured cubes, perspective camera, orbit drag, walk + auto-rotate.
 */
@Composable
fun SkinPreview3D(
    skinPath: Path?,
    capePath: Path? = null,
    slim: Boolean = false,
    modifier: Modifier = Modifier,
    previewSize: Dp = 220.dp,
    animated: Boolean = true,
) {
    var yaw by remember { mutableFloatStateOf(32f) }
    var pitch by remember { mutableFloatStateOf(-10f) }
    var atlas by remember(skinPath) { mutableStateOf<BufferedImage?>(null) }
    var capeAtlas by remember(capePath) { mutableStateOf<BufferedImage?>(null) }
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    var userDragging by remember { mutableStateOf(false) }

    LaunchedEffect(skinPath) {
        atlas = null
        val p = skinPath
        if (p == null || !p.exists()) return@LaunchedEffect
        atlas = withContext(Dispatchers.IO) {
            runCatching { normalizeSkin(ImageIO.read(Files.newInputStream(p))) }.getOrNull()
        }
    }
    LaunchedEffect(capePath) {
        capeAtlas = null
        val p = capePath
        if (p == null || !p.exists()) return@LaunchedEffect
        capeAtlas = withContext(Dispatchers.IO) {
            runCatching { ImageIO.read(Files.newInputStream(p)) }.getOrNull()
        }
    }

    LaunchedEffect(atlas, capeAtlas, slim, animated) {
        val a = atlas ?: run {
            frame = null
            return@LaunchedEffect
        }
        var last = 0L
        var animT = 0f
        while (isActive) {
            withFrameNanos { now ->
                if (last == 0L) last = now
                val dt = ((now - last) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
                last = now
                if (animated) {
                    animT += dt
                    if (!userDragging) yaw = (yaw + dt * 22f) % 360f
                }
            }
            val y = yaw
            val p = pitch
            val phase = if (animated) animT * 5.2f else 0.4f
            val bmp = withContext(Dispatchers.Default) {
                runCatching {
                    toBitmap(
                        renderSkin3D(
                            skin = a,
                            cape = capeAtlas,
                            slim = slim,
                            yawDeg = y,
                            pitchDeg = p,
                            walkPhase = phase,
                            outSize = if (animated) 512 else 256,
                        ),
                    )
                }.getOrNull()
            }
            if (bmp != null) frame = bmp
            if (!animated) break
        }
    }

    Box(
        modifier = modifier
            .size(previewSize)
            .clip(RoundedCornerShape(16.dp))
            .background(StarlitColors.Purple.copy(alpha = 0.14f))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { userDragging = true },
                    onDragEnd = { userDragging = false },
                    onDragCancel = { userDragging = false },
                    onDrag = { change, drag ->
                        change.consume()
                        yaw = (yaw + drag.x * 0.55f) % 360f
                        pitch = (pitch - drag.y * 0.35f).coerceIn(-40f, 40f)
                    },
                )
            },
    ) {
        val bmp = frame
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                filterQuality = FilterQuality.None,
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
fun LocalSkinFace(
    skinPath: Path?,
    fallbackName: String,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    revision: Int = 0,
) {
    var face by remember(skinPath, revision) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(skinPath, revision) {
        face = null
        val p = skinPath
        if (p == null || !p.exists()) return@LaunchedEffect
        face = withContext(Dispatchers.IO) {
            runCatching {
                val img = normalizeSkin(ImageIO.read(p.toFile()) ?: return@runCatching null) ?: return@runCatching null
                val faceImg = img.getSubimage(8, 8, 8, 8)
                val hat = runCatching { img.getSubimage(40, 8, 8, 8) }.getOrNull()
                val out = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
                val g = out.createGraphics()
                g.drawImage(faceImg, 0, 0, null)
                if (hat != null && hasVisiblePixels(hat)) g.drawImage(hat, 0, 0, null)
                g.dispose()
                toBitmap(out)
            }.getOrNull()
        }
    }
    val bmp = face
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp)),
            filterQuality = FilterQuality.None,
            contentScale = ContentScale.FillBounds,
        )
    } else {
        NetworkAvatar(url = "", fallbackName = fallbackName, modifier = modifier, size = size)
    }
}

// ─── vectors / faces ─────────────────────────────────────────────────────────

private data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun length() = sqrt(dot(this).toDouble()).toFloat()
    fun normalized(): Vec3 {
        val l = length().coerceAtLeast(1e-6f)
        return this * (1f / l)
    }
}

private data class Face3(
    val v0: Vec3,
    val v1: Vec3,
    val v2: Vec3,
    val v3: Vec3,
    val tex: BufferedImage,
    val shade: Float,
) {
    fun center() = Vec3(
        (v0.x + v1.x + v2.x + v3.x) * 0.25f,
        (v0.y + v1.y + v2.y + v3.y) * 0.25f,
        (v0.z + v1.z + v2.z + v3.z) * 0.25f,
    )

    fun normal(): Vec3 = (v1 - v0).cross(v3 - v0).normalized()
}

private fun rotY(v: Vec3, deg: Float): Vec3 {
    val r = Math.toRadians(deg.toDouble())
    val c = cos(r).toFloat()
    val s = sin(r).toFloat()
    return Vec3(v.x * c + v.z * s, v.y, -v.x * s + v.z * c)
}

private fun rotX(v: Vec3, deg: Float): Vec3 {
    val r = Math.toRadians(deg.toDouble())
    val c = cos(r).toFloat()
    val s = sin(r).toFloat()
    return Vec3(v.x, v.y * c - v.z * s, v.y * s + v.z * c)
}

private fun addBox(
    faces: MutableList<Face3>,
    skin: BufferedImage,
    ox: Float,
    oy: Float,
    oz: Float,
    sx: Float,
    sy: Float,
    sz: Float,
    fu: Int, fv: Int, fw: Int, fh: Int,
    bu: Int, bv: Int,
    lu: Int, lv: Int, lw: Int, lh: Int,
    ru: Int, rv: Int, rw: Int, rh: Int,
    tu: Int, tv: Int, tw: Int, th: Int,
    du: Int, dv: Int,
    hingeY: Float = 0f,
    limbRotX: Float = 0f,
    inflate: Float = 0f,
) {
    val hx = sx / 2f + inflate
    val hy = sy / 2f + inflate
    val hz = sz / 2f + inflate

    fun local(x: Float, y: Float, z: Float): Vec3 {
        var p = Vec3(x, y - hingeY, z)
        p = rotX(p, limbRotX)
        p = Vec3(p.x, p.y + hingeY, p.z)
        return Vec3(p.x + ox, p.y + oy, p.z + oz)
    }

    fun tex(u: Int, v: Int, w: Int, h: Int): BufferedImage? {
        if (w <= 0 || h <= 0) return null
        if (u < 0 || v < 0 || u + w > skin.width || v + h > skin.height) return null
        val part = skin.getSubimage(u, v, w, h)
        return if (hasVisiblePixels(part)) part else null
    }

    // Corners: v0 bottom-left, v1 bottom-right, v2 top-right, v3 top-left (from outside)
    tex(fu, fv, fw, fh)?.let { // +Z front
        faces += Face3(local(-hx, -hy, hz), local(hx, -hy, hz), local(hx, hy, hz), local(-hx, hy, hz), it, 1.00f)
    }
    tex(bu, bv, fw, fh)?.let { // -Z back
        faces += Face3(local(hx, -hy, -hz), local(-hx, -hy, -hz), local(-hx, hy, -hz), local(hx, hy, -hz), it, 0.70f)
    }
    tex(lu, lv, lw, lh)?.let { // -X left
        faces += Face3(local(-hx, -hy, -hz), local(-hx, -hy, hz), local(-hx, hy, hz), local(-hx, hy, -hz), it, 0.82f)
    }
    tex(ru, rv, rw, rh)?.let { // +X right
        faces += Face3(local(hx, -hy, hz), local(hx, -hy, -hz), local(hx, hy, -hz), local(hx, hy, hz), it, 0.82f)
    }
    tex(tu, tv, tw, th)?.let { // +Y top
        faces += Face3(local(-hx, hy, hz), local(hx, hy, hz), local(hx, hy, -hz), local(-hx, hy, -hz), it, 1.08f)
    }
    tex(du, dv, tw, th)?.let { // -Y bottom
        faces += Face3(local(-hx, -hy, -hz), local(hx, -hy, -hz), local(hx, -hy, hz), local(-hx, -hy, hz), it, 0.55f)
    }
}

private fun renderSkin3D(
    skin: BufferedImage,
    cape: BufferedImage?,
    slim: Boolean,
    yawDeg: Float,
    pitchDeg: Float,
    walkPhase: Float,
    outSize: Int,
): BufferedImage {
    val out = BufferedImage(outSize, outSize, BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)

    val swing = sin(walkPhase.toDouble()).toFloat() * 32f
    val armW = if (slim) 3 else 4
    val faces = mutableListOf<Face3>()

    // Legs (pivot at top of leg = +6 local)
    addBox(faces, skin, -2f, 6f, 0f, 4f, 12f, 4f, 4, 20, 4, 12, 12, 20, 0, 20, 4, 12, 8, 20, 4, 12, 4, 16, 4, 4, 8, 16, hingeY = 6f, limbRotX = swing)
    addBox(faces, skin, 2f, 6f, 0f, 4f, 12f, 4f, 20, 52, 4, 12, 28, 52, 16, 52, 4, 12, 24, 52, 4, 12, 20, 48, 4, 4, 24, 48, hingeY = 6f, limbRotX = -swing)
    addBox(faces, skin, -2f, 6f, 0f, 4f, 12f, 4f, 4, 36, 4, 12, 12, 36, 0, 36, 4, 12, 8, 36, 4, 12, 4, 32, 4, 4, 8, 32, hingeY = 6f, limbRotX = swing, inflate = 0.3f)
    addBox(faces, skin, 2f, 6f, 0f, 4f, 12f, 4f, 4, 52, 4, 12, 12, 52, 0, 52, 4, 12, 8, 52, 4, 12, 4, 48, 4, 4, 8, 48, hingeY = 6f, limbRotX = -swing, inflate = 0.3f)

    // Body
    addBox(faces, skin, 0f, 18f, 0f, 8f, 12f, 4f, 20, 20, 8, 12, 32, 20, 16, 20, 4, 12, 28, 20, 4, 12, 20, 16, 8, 4, 28, 16)
    addBox(faces, skin, 0f, 18f, 0f, 8f, 12f, 4f, 20, 36, 8, 12, 32, 36, 16, 36, 4, 12, 28, 36, 4, 12, 20, 32, 8, 4, 28, 32, inflate = 0.3f)

    // Arms
    val aw = armW.toFloat()
    addBox(faces, skin, -(4f + aw / 2f), 18f, 0f, aw, 12f, 4f, 44, 20, armW, 12, 52 + (if (slim) -1 else 0), 20, 40, 20, 4, 12, 44 + armW, 20, 4, 12, 44, 16, armW, 4, 44 + armW, 16, hingeY = 5f, limbRotX = -swing)
    addBox(faces, skin, 4f + aw / 2f, 18f, 0f, aw, 12f, 4f, 36, 52, armW, 12, 44 + (if (slim) -1 else 0), 52, 32, 52, 4, 12, 36 + armW, 52, 4, 12, 36, 48, armW, 4, 36 + armW, 48, hingeY = 5f, limbRotX = swing)
    addBox(faces, skin, -(4f + aw / 2f), 18f, 0f, aw, 12f, 4f, 44, 36, armW, 12, 52, 36, 40, 36, 4, 12, 44 + armW, 36, 4, 12, 44, 32, armW, 4, 44 + armW, 32, hingeY = 5f, limbRotX = -swing, inflate = 0.3f)
    addBox(faces, skin, 4f + aw / 2f, 18f, 0f, aw, 12f, 4f, 52, 52, armW, 12, 60, 52, 48, 52, 4, 12, 52 + armW, 52, 4, 12, 52, 48, armW, 4, 52 + armW, 48, hingeY = 5f, limbRotX = swing, inflate = 0.3f)

    // Head + hat
    addBox(faces, skin, 0f, 28f, 0f, 8f, 8f, 8f, 8, 8, 8, 8, 24, 8, 0, 8, 8, 8, 16, 8, 8, 8, 8, 0, 8, 8, 16, 0)
    addBox(faces, skin, 0f, 28f, 0f, 8f, 8f, 8f, 40, 8, 8, 8, 56, 8, 32, 8, 8, 8, 48, 8, 8, 8, 40, 0, 8, 8, 48, 0, inflate = 0.5f)

    // Cape
    if (cape != null && cape.width >= 22 && cape.height >= 17) {
        runCatching { cape.getSubimage(1, 1, 10, 16) }.getOrNull()?.let { capeFront ->
            val capeSwing = 12f + cos(walkPhase.toDouble()).toFloat() * 10f
            val hinge = Vec3(0f, 8f, 0f)
            fun local(x: Float, y: Float, z: Float): Vec3 {
                var p = Vec3(x, y, z) - hinge
                p = rotX(p, capeSwing)
                p = p + hinge
                return Vec3(p.x, p.y + 20f, p.z - 3.5f)
            }
            faces += Face3(
                local(-5f, -8f, 0.4f), local(5f, -8f, 0.4f), local(5f, 8f, 0.4f), local(-5f, 8f, 0.4f),
                capeFront, 0.88f,
            )
        }
    }

    val camDist = 58f
    val fov = 520f
    val cx = outSize / 2f
    val cy = outSize / 2f + 28f

    fun world(v: Vec3): Vec3 {
        var p = Vec3(v.x, v.y - 18f, v.z)
        p = rotY(p, -yawDeg)
        p = rotX(p, pitchDeg)
        return Vec3(p.x, p.y, p.z + camDist)
    }

    fun project(v: Vec3): Pair<Float, Float> {
        val w = world(v)
        val z = w.z.coerceAtLeast(2f)
        return (cx + w.x * fov / z) to (cy - w.y * fov / z)
    }

    val sorted = faces.mapNotNull { face ->
        var n = face.normal()
        n = rotY(n, -yawDeg)
        n = rotX(n, pitchDeg)
        // camera looks toward -Z in view space after transform… faces with normal pointing toward camera (negative Z in world after cam)
        val viewZ = world(face.center())
        val toCam = Vec3(-viewZ.x, -viewZ.y, -viewZ.z).normalized()
        if (n.dot(toCam) < 0.02f) return@mapNotNull null
        Triple(face, viewZ.z, n.dot(toCam))
    }.sortedByDescending { it.second }

    for ((face, _, _) in sorted) {
        val (x0, y0) = project(face.v0)
        val (x1, y1) = project(face.v1)
        val (x2, y2) = project(face.v2)
        val (x3, y3) = project(face.v3)
        val tex = if (face.shade >= 0.999f) face.tex else shadeImg(face.tex, face.shade)
        drawTexturedQuad(g, tex, x0, y0, x1, y1, x2, y2, x3, y3)
    }

    g.dispose()
    return out
}

/** Texture (0,0)=top-left maps to v3; (w,0)->v2; (0,h)->v0; (w,h)->v1 */
private fun drawTexturedQuad(
    g: Graphics2D,
    tex: BufferedImage,
    x0: Float, y0: Float,
    x1: Float, y1: Float,
    x2: Float, y2: Float,
    x3: Float, y3: Float,
) {
    val tw = tex.width.toDouble()
    val th = tex.height.toDouble()
    if (tw < 1 || th < 1) return

    val at = AffineTransform(
        (x2 - x3) / tw, (y2 - y3) / tw,
        (x0 - x3) / th, (y0 - y3) / th,
        x3.toDouble(), y3.toDouble(),
    )
    val clip = Path2D.Float()
    clip.moveTo(x0, y0)
    clip.lineTo(x1, y1)
    clip.lineTo(x2, y2)
    clip.lineTo(x3, y3)
    clip.closePath()

    val oldClip = g.clip
    val oldTx = g.transform
    g.clip = clip
    g.drawImage(tex, at, null)
    g.clip = oldClip
    g.transform = oldTx
}

private fun normalizeSkin(src: BufferedImage?): BufferedImage? {
    if (src == null) return null
    val w = src.width
    val h = src.height
    if (w == 64 && h == 64) return ensureArgb(src)
    if (w == 64 && h == 32) {
        val out = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.drawImage(src, 0, 0, null)
        blit(src, out, 0, 16, 16, 16, 16, 48)
        blit(src, out, 40, 16, 16, 16, 32, 48)
        g.dispose()
        return out
    }
    if (w >= 64 && h >= 64 && w % 64 == 0 && h % 64 == 0) {
        val out = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        g.drawImage(src, 0, 0, 64, 64, null)
        g.dispose()
        return out
    }
    return ensureArgb(src)
}

private fun ensureArgb(src: BufferedImage): BufferedImage {
    if (src.type == BufferedImage.TYPE_INT_ARGB) return src
    val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    g.drawImage(src, 0, 0, null)
    g.dispose()
    return out
}

private fun blit(src: BufferedImage, dst: BufferedImage, sx: Int, sy: Int, w: Int, h: Int, dx: Int, dy: Int) {
    for (y in 0 until h) for (x in 0 until w) {
        if (sx + x >= src.width || sy + y >= src.height) continue
        if (dx + x >= dst.width || dy + y >= dst.height) continue
        dst.setRGB(dx + x, dy + y, src.getRGB(sx + x, sy + y))
    }
}

private fun hasVisiblePixels(img: BufferedImage): Boolean {
    for (y in 0 until img.height) for (x in 0 until img.width) {
        if (((img.getRGB(x, y) ushr 24) and 0xFF) > 10) return true
    }
    return false
}

private fun toBitmap(img: BufferedImage): ImageBitmap {
    val baos = ByteArrayOutputStream()
    ImageIO.write(img, "PNG", baos)
    return SkiaImage.makeFromEncoded(baos.toByteArray()).toComposeImageBitmap()
}

private fun shadeImg(src: BufferedImage, factor: Float): BufferedImage {
    val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until src.height) for (x in 0 until src.width) {
        val p = src.getRGB(x, y)
        val a = (p ushr 24) and 0xFF
        if (a == 0) {
            out.setRGB(x, y, 0)
            continue
        }
        val r = (((p ushr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val gg = (((p ushr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((p and 0xFF) * factor).toInt().coerceIn(0, 255)
        out.setRGB(x, y, (a shl 24) or (r shl 16) or (gg shl 8) or b)
    }
    return out
}
