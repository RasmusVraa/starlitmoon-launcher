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
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.exists
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Rotatable Minecraft skin preview.
 * Renders a crisp pixel character (front + side depth) via Java2D, similar to site LK look.
 */
@Composable
fun SkinPreview3D(
    skinPath: Path?,
    capePath: Path? = null,
    slim: Boolean = false,
    modifier: Modifier = Modifier,
    previewSize: Dp = 220.dp,
) {
    var yaw by remember { mutableFloatStateOf(28f) }
    var atlas by remember(skinPath) { mutableStateOf<BufferedImage?>(null) }
    var capeAtlas by remember(capePath) { mutableStateOf<BufferedImage?>(null) }
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }

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
    LaunchedEffect(atlas, capeAtlas, slim, yaw) {
        val a = atlas ?: run {
            frame = null
            return@LaunchedEffect
        }
        frame = withContext(Dispatchers.Default) {
            runCatching { toBitmap(renderCharacter(a, capeAtlas, slim, yaw, pixelScale = 12)) }.getOrNull()
        }
    }

    Box(
        modifier = modifier
            .size(previewSize)
            .clip(RoundedCornerShape(16.dp))
            .background(StarlitColors.Purple.copy(alpha = 0.16f))
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    yaw = (yaw + drag.x * 0.6f).let { if (it > 180f) it - 360f else if (it < -180f) it + 360f else it }
                }
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

private fun normalizeSkin(src: BufferedImage?): BufferedImage? {
    if (src == null) return null
    val w = src.width
    val h = src.height
    if (w == 64 && h == 64) return ensureArgb(src)
    if (w == 64 && h == 32) {
        val out = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.drawImage(src, 0, 0, null)
        // legacy → 1.8 left limb slots
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

private fun part(skin: BufferedImage, u: Int, v: Int, w: Int, h: Int): BufferedImage? {
    if (u < 0 || v < 0 || u + w > skin.width || v + h > skin.height) return null
    val p = skin.getSubimage(u, v, w, h)
    return if (hasVisiblePixels(p)) p else null
}

/**
 * Front/side hybrid: body parts on a 16×32 grid, upscaled with nearest-neighbor.
 * Yaw blends front↔side for a light 3D turntable (no broken free-orbit mesh).
 */
private fun renderCharacter(
    skin: BufferedImage,
    cape: BufferedImage?,
    slim: Boolean,
    yawDeg: Float,
    pixelScale: Int,
): BufferedImage {
    val armW = if (slim) 3 else 4
    val charW = 16 + 4 // padding for side extrusion
    val charH = 32
    val base = BufferedImage(charW, charH, BufferedImage.TYPE_INT_ARGB)
    val g = base.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)

    val yaw = ((yawDeg % 360f) + 360f) % 360f
    // 0 = front, 90 = left, 180 = back, 270 = right
    val useBack = yaw in 90f..270f
    val sideAmt = sin(Math.toRadians(yaw.toDouble())).toFloat() // -1..1, positive = show left side bias
    val frontAmt = cos(Math.toRadians(yaw.toDouble())).toFloat().coerceAtLeast(0f)

    val ox = 2 // left padding in base canvas

    fun drawScaled(img: BufferedImage?, x: Int, y: Int, w: Int, h: Int, shade: Float = 1f) {
        if (img == null) return
        val src = if (shade >= 0.999f) img else shadeImg(img, shade)
        g.drawImage(src, ox + x, y, w, h, null)
    }

    // —— Legs ——
    if (!useBack) {
        drawScaled(part(skin, 4, 20, 4, 12), 4, 20, 4, 12)
        drawScaled(part(skin, 20, 52, 4, 12), 8, 20, 4, 12)
        drawScaled(part(skin, 4, 36, 4, 12), 4, 20, 4, 12) // overlay
        drawScaled(part(skin, 4, 52, 4, 12), 8, 20, 4, 12)
    } else {
        drawScaled(part(skin, 12, 20, 4, 12), 4, 20, 4, 12)
        drawScaled(part(skin, 28, 52, 4, 12), 8, 20, 4, 12)
        drawScaled(part(skin, 12, 36, 4, 12), 4, 20, 4, 12)
        drawScaled(part(skin, 12, 52, 4, 12), 8, 20, 4, 12)
    }

    // —— Body ——
    if (!useBack) {
        drawScaled(part(skin, 20, 20, 8, 12), 4, 8, 8, 12)
        drawScaled(part(skin, 20, 36, 8, 12), 4, 8, 8, 12)
    } else {
        drawScaled(part(skin, 32, 20, 8, 12), 4, 8, 8, 12)
        drawScaled(part(skin, 32, 36, 8, 12), 4, 8, 8, 12)
    }

    // —— Arms ——
    if (!useBack) {
        drawScaled(part(skin, 44, 20, armW, 12), 4 - armW, 8, armW, 12)
        drawScaled(part(skin, 36, 52, armW, 12), 12, 8, armW, 12)
        drawScaled(part(skin, 44, 36, armW, 12), 4 - armW, 8, armW, 12)
        drawScaled(part(skin, 52, 52, armW, 12), 12, 8, armW, 12)
    } else {
        drawScaled(part(skin, 52, 20, armW, 12), 4 - armW, 8, armW, 12)
        drawScaled(part(skin, 44, 52, armW, 12), 12, 8, armW, 12)
        drawScaled(part(skin, 52, 36, armW, 12), 4 - armW, 8, armW, 12)
        drawScaled(part(skin, 60, 52, armW, 12).let { it ?: part(skin, 52, 52, armW, 12) }, 12, 8, armW, 12)
    }

    // —— Head ——
    if (!useBack) {
        drawScaled(part(skin, 8, 8, 8, 8), 4, 0, 8, 8)
        drawScaled(part(skin, 40, 8, 8, 8), 4, 0, 8, 8)
    } else {
        drawScaled(part(skin, 24, 8, 8, 8), 4, 0, 8, 8)
        drawScaled(part(skin, 56, 8, 8, 8), 4, 0, 8, 8)
    }

    // —— Side depth strips when angled ——
    if (abs(sideAmt) > 0.15f && frontAmt > 0.05f) {
        val sideShade = 0.72f
        val strip = (abs(sideAmt) * 3f).roundToInt().coerceIn(1, 3)
        if (sideAmt > 0) {
            // show character's right side on the left of sprite (viewer sees left face)
            drawScaled(part(skin, 0, 8, 8, 8), 4 - strip, 0, strip, 8, sideShade) // head left
            drawScaled(part(skin, 16, 20, 4, 12), 4 - strip, 8, strip, 12, sideShade) // body
            drawScaled(part(skin, 40, 20, 4, 12), 4 - armW - strip, 8, strip, 12, sideShade)
        } else {
            drawScaled(part(skin, 16, 8, 8, 8), 12, 0, strip, 8, sideShade)
            drawScaled(part(skin, 28, 20, 4, 12), 12, 8, strip, 12, sideShade)
            drawScaled(part(skin, 48, 20, 4, 12), 12 + armW, 8, strip, 12, sideShade)
        }
    }

    // Cape behind when mostly front-facing
    if (cape != null && !useBack && frontAmt > 0.3f && cape.width >= 22 && cape.height >= 17) {
        runCatching { cape.getSubimage(1, 1, 10, 16) }.getOrNull()?.let { c ->
            drawScaled(c, 3, 8, 10, 16, 0.85f)
        }
    }

    g.dispose()

    val outW = charW * pixelScale
    val outH = charH * pixelScale
    val out = BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB)
    val og = out.createGraphics()
    og.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
    og.drawImage(base, 0, 0, outW, outH, null)
    og.dispose()
    return out
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
