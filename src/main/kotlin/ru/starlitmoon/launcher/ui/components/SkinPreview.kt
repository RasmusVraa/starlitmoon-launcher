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
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Stable Minecraft skin preview (no JavaFX / WebView).
 * Front/back body assembly + cape on back, drag horizontally to turn.
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
    var yaw by remember { mutableFloatStateOf(0f) }
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(skinPath, capePath, slim, yaw) {
        val skin = skinPath
        if (skin == null || !skin.exists()) {
            frame = null
            return@LaunchedEffect
        }
        frame = withContext(Dispatchers.IO) {
            runCatching {
                val atlas = normalizeSkin(ImageIO.read(Files.newInputStream(skin))) ?: return@runCatching null
                val cape = capePath?.takeIf { it.exists() }?.let {
                    runCatching { ImageIO.read(Files.newInputStream(it)) }.getOrNull()
                }
                toBitmap(renderBody(atlas, cape, slim, yaw, scale = 14))
            }.getOrNull()
        }
    }

    Box(
        modifier = modifier
            .size(previewSize)
            .clip(RoundedCornerShape(16.dp))
            .background(StarlitColors.Purple.copy(alpha = 0.14f))
            .pointerInput(animated) {
                if (!animated) return@pointerInput
                detectDragGestures { change, drag ->
                    change.consume()
                    yaw = (yaw + drag.x * 0.7f).let { v ->
                        var x = v % 360f
                        if (x < 0) x += 360f
                        x
                    }
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
 * Classic Minecraft launcher-style body: pixel-perfect parts on a 16×32 grid, upscaled.
 * Yaw picks front vs back; cape only on back.
 */
private fun renderBody(
    skin: BufferedImage,
    cape: BufferedImage?,
    slim: Boolean,
    yawDeg: Float,
    scale: Int,
): BufferedImage {
    val armW = if (slim) 3 else 4
    val pad = 4
    val charW = 16 + pad * 2
    val charH = 32 + 2
    val base = BufferedImage(charW, charH, BufferedImage.TYPE_INT_ARGB)
    val g = base.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)

    val yaw = ((yawDeg % 360f) + 360f) % 360f
    val back = yaw in 90f..270f
    val ox = pad

    fun draw(img: BufferedImage?, x: Int, y: Int, w: Int = img?.width ?: 0, h: Int = img?.height ?: 0) {
        if (img == null || w <= 0 || h <= 0) return
        g.drawImage(img, ox + x, y, w, h, null)
    }

    // Cape behind when showing back
    if (back && cape != null && cape.width >= 22 && cape.height >= 17) {
        runCatching { cape.getSubimage(1, 1, 10, 16) }.getOrNull()?.let { c ->
            draw(c, 3, 8, 10, 16)
        }
    }

    if (!back) {
        draw(part(skin, 4, 20, 4, 12), 4, 20)
        draw(part(skin, 20, 52, 4, 12), 8, 20)
        draw(part(skin, 4, 36, 4, 12), 4, 20)
        draw(part(skin, 4, 52, 4, 12), 8, 20)
        draw(part(skin, 20, 20, 8, 12), 4, 8)
        draw(part(skin, 20, 36, 8, 12), 4, 8)
        draw(part(skin, 44, 20, armW, 12), 4 - armW, 8)
        draw(part(skin, 36, 52, armW, 12), 12, 8)
        draw(part(skin, 44, 36, armW, 12), 4 - armW, 8)
        draw(part(skin, 52, 52, armW, 12), 12, 8)
        draw(part(skin, 8, 8, 8, 8), 4, 0)
        draw(part(skin, 40, 8, 8, 8), 4, 0)
    } else {
        draw(part(skin, 12, 20, 4, 12), 4, 20)
        draw(part(skin, 28, 52, 4, 12), 8, 20)
        draw(part(skin, 12, 36, 4, 12), 4, 20)
        draw(part(skin, 12, 52, 4, 12), 8, 20)
        draw(part(skin, 32, 20, 8, 12), 4, 8)
        draw(part(skin, 32, 36, 8, 12), 4, 8)
        draw(part(skin, 52, 20, armW, 12), 4 - armW, 8)
        draw(part(skin, 44, 52, armW, 12), 12, 8)
        draw(part(skin, 52, 36, armW, 12), 4 - armW, 8)
        draw(part(skin, 60, 52, armW, 12) ?: part(skin, 52, 52, armW, 12), 12, 8)
        draw(part(skin, 24, 8, 8, 8), 4, 0)
        draw(part(skin, 56, 8, 8, 8), 4, 0)
    }

    // Light side depth when angled toward 45°
    val side = sin(Math.toRadians(yaw.toDouble())).toFloat()
    if (!back && abs(side) > 0.35f) {
        val strip = (abs(side) * 2.5f).roundToInt().coerceIn(1, 2)
        if (side > 0) {
            draw(part(skin, 0, 8, 8, 8), 4 - strip, 0, strip, 8)
            draw(part(skin, 16, 20, 4, 12), 4 - strip, 8, strip, 12)
        } else {
            draw(part(skin, 16, 8, 8, 8), 12, 0, strip, 8)
            draw(part(skin, 28, 20, 4, 12), 12, 8, strip, 12)
        }
    }

    g.dispose()

    val outW = charW * scale
    val outH = charH * scale
    val out = BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB)
    val og = out.createGraphics()
    og.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
    og.drawImage(base, 0, 0, outW, outH, null)
    og.dispose()
    return out
}
