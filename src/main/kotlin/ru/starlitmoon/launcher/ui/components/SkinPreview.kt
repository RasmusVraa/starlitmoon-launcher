package ru.starlitmoon.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * Static local 2D preview: front body of the skin + cape front beside it.
 * No network / WebGL / animation.
 */
@Composable
fun SkinPreview3D(
    skinPath: Path?,
    capePath: Path? = null,
    slim: Boolean = false,
    modifier: Modifier = Modifier,
    previewSize: Dp = 220.dp,
    @Suppress("UNUSED_PARAMETER") animated: Boolean = false,
    @Suppress("UNUSED_PARAMETER") skinUrl: String? = null,
    @Suppress("UNUSED_PARAMETER") username: String = "Steve",
    revision: Int = 0,
) {
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(skinPath, capePath, slim, revision) {
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
                toBitmap(renderFrontWithCape(atlas, cape, slim, scale = 14))
            }.getOrNull()
        }
    }

    Box(
        modifier = modifier
            .size(previewSize)
            .clip(RoundedCornerShape(16.dp))
            .background(StarlitColors.Purple.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
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
        } else {
            Text("Нет скина", color = StarlitColors.TextMuted, fontSize = 12.sp)
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

/** Front body (16×32) + optional cape front on the right. */
private fun renderFrontWithCape(
    skin: BufferedImage,
    cape: BufferedImage?,
    slim: Boolean,
    scale: Int,
): BufferedImage {
    val armW = if (slim) 3 else 4
    val pad = 2
    val bodyW = 16
    val bodyH = 32
    val capeW = 10
    val capeH = 16
    val gap = 4
    val hasCape = cape != null && cape.width >= 22 && cape.height >= 17
    val canvasW = pad * 2 + bodyW + if (hasCape) gap + capeW else 0
    val canvasH = pad * 2 + bodyH

    val base = BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB)
    val g = base.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)

    val ox = pad
    val oy = pad

    fun draw(img: BufferedImage?, x: Int, y: Int, w: Int = img?.width ?: 0, h: Int = img?.height ?: 0) {
        if (img == null || w <= 0 || h <= 0) return
        g.drawImage(img, ox + x, oy + y, w, h, null)
    }

    // Legs
    draw(part(skin, 4, 20, 4, 12), 4, 20)
    draw(part(skin, 20, 52, 4, 12), 8, 20)
    draw(part(skin, 4, 36, 4, 12), 4, 20)
    draw(part(skin, 4, 52, 4, 12), 8, 20)
    // Body
    draw(part(skin, 20, 20, 8, 12), 4, 8)
    draw(part(skin, 20, 36, 8, 12), 4, 8)
    // Arms
    draw(part(skin, 44, 20, armW, 12), 4 - armW, 8)
    draw(part(skin, 36, 52, armW, 12), 12, 8)
    draw(part(skin, 44, 36, armW, 12), 4 - armW, 8)
    draw(part(skin, 52, 52, armW, 12), 12, 8)
    // Head + hat
    draw(part(skin, 8, 8, 8, 8), 4, 0)
    draw(part(skin, 40, 8, 8, 8), 4, 0)

    if (hasCape) {
        runCatching { cape!!.getSubimage(1, 1, 10, 16) }.getOrNull()?.let { c ->
            g.drawImage(c, ox + bodyW + gap, oy + 8, capeW, capeH, null)
        }
    }

    g.dispose()

    val outW = canvasW * scale
    val outH = canvasH * scale
    val out = BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB)
    val og = out.createGraphics()
    og.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
    og.drawImage(base, 0, 0, outW, outH, null)
    og.dispose()
    return out
}
