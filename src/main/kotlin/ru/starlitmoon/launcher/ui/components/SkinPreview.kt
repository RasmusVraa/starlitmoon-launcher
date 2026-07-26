package ru.starlitmoon.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap as SkiaBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.ImageInfo
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import java.awt.AlphaComposite
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.exists
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Interactive 3D skin preview: full player body from PNG (classic + slim), optional cape,
 * drag to rotate. Software-rasterized — no WebGL / native deps.
 *
 * Yaw updates on the UI thread immediately; rasterization runs on Default with conflated
 * frames so drag stays smooth without blocking scroll when the gesture is vertical.
 */
@Composable
fun SkinPreview3D(
    skinPath: Path?,
    capePath: Path? = null,
    slim: Boolean? = null,
    modifier: Modifier = Modifier,
    previewSize: Dp = 220.dp,
    @Suppress("UNUSED_PARAMETER") animated: Boolean = false,
    @Suppress("UNUSED_PARAMETER") skinUrl: String? = null,
    @Suppress("UNUSED_PARAMETER") username: String = "Steve",
    revision: Int = 0,
    showHint: Boolean = true,
) {
    var yaw by remember { mutableFloatStateOf(28f) }
    var pitch by remember { mutableFloatStateOf(-8f) }
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    var loaded by remember { mutableStateOf<LoadedSkin?>(null) }
    val density = LocalDensity.current
    val touchSlop = with(density) { 8.dp.toPx() }

    LaunchedEffect(skinPath, capePath, slim, revision) {
        val skin = skinPath
        if (skin == null || !skin.exists()) {
            loaded = null
            frame = null
            return@LaunchedEffect
        }
        loaded = withContext(Dispatchers.IO) {
            runCatching {
                val atlasImg = normalizeSkin(ImageIO.read(Files.newInputStream(skin))) ?: return@runCatching null
                val skinAtlas = SkinModelRenderer.toAtlas(atlasImg)
                val capeAtlas = capePath?.takeIf { it.exists() }?.let {
                    runCatching {
                        SkinModelRenderer.toAtlas(ensureArgb(ImageIO.read(Files.newInputStream(it)) ?: return@runCatching null))
                    }.getOrNull()
                }
                val isSlim = slim ?: SkinModelRenderer.detectSlim(skinAtlas)
                val mesh = SkinModelRenderer.buildMesh(skinAtlas, capeAtlas, isSlim)
                LoadedSkin(mesh, isSlim)
            }.getOrNull()
        }
        if (loaded == null) frame = null
    }

    LaunchedEffect(loaded, previewSize) {
        val data = loaded ?: run {
            frame = null
            return@LaunchedEffect
        }
        // Sensible software-raster res: sharp enough, cheap enough for ~30–60fps drag.
        val outW = (previewSize.value * 0.85f).roundToInt().coerceIn(140, 280)
        val outH = (outW * 1.22f).roundToInt()
        val buffers = SkinModelRenderer.Buffers(outW, outH)
        val bgra = ByteArray(outW * outH * 4)

        snapshotFlow { yaw to pitch }
            .distinctUntilChanged()
            .conflate()
            .collect { (y, p) ->
                val bmp = withContext(Dispatchers.Default) {
                    runCatching {
                        val pixels = SkinModelRenderer.render(
                            mesh = data.mesh,
                            yawDeg = y,
                            pitchDeg = p,
                            outW = outW,
                            outH = outH,
                            buffers = buffers,
                        )
                        argbPixelsToBitmap(pixels, outW, outH, bgra)
                    }.getOrNull()
                }
                if (bmp != null) frame = bmp
            }
    }

    Box(
        modifier = modifier
            .size(previewSize)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF161C30),
                        Color(0xFF0A0E1C),
                    ),
                ),
            )
            .border(1.dp, Color(0x33788CDC), RoundedCornerShape(16.dp))
            .pointerInput(touchSlop) {
                // Horizontal-dominant drag rotates; vertical-dominant gestures pass through to page scroll.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var totalX = 0f
                    var totalY = 0f
                    var rotating = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        val delta = change.positionChange()
                        if (!rotating) {
                            totalX += delta.x
                            totalY += delta.y
                            if (abs(totalX) > touchSlop || abs(totalY) > touchSlop) {
                                if (abs(totalX) >= abs(totalY) * 1.15f) {
                                    rotating = true
                                    change.consume()
                                    yaw += totalX * 0.55f
                                    pitch = (pitch - totalY * 0.28f).coerceIn(-35f, 35f)
                                } else {
                                    // Vertical scroll wins — stop claiming this gesture.
                                    break
                                }
                            }
                        } else {
                            change.consume()
                            yaw += delta.x * 0.55f
                            pitch = (pitch - delta.y * 0.28f).coerceIn(-35f, 35f)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            StarlitColors.Gold.copy(alpha = 0.10f),
                            StarlitColors.Purple.copy(alpha = 0.06f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        val bmp = frame
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(6.dp),
                filterQuality = FilterQuality.None,
                contentScale = ContentScale.Fit,
            )
        } else {
            Text("Нет скина", color = StarlitColors.TextMuted, fontSize = 12.sp)
        }
        if (showHint && bmp != null) {
            Text(
                "Свайп — поворот",
                color = StarlitColors.TextMuted.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
            )
        }
    }
}

private data class LoadedSkin(
    val mesh: SkinModelRenderer.Mesh,
    val slim: Boolean,
)

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
                bufferedToBitmap(out)
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
    try {
        g.composite = AlphaComposite.Src
        g.drawImage(src, 0, 0, null)
    } finally {
        g.dispose()
    }
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

/** Fast ARGB int[] → Compose ImageBitmap via Skia (no PNG encode/decode). */
private fun argbPixelsToBitmap(pixels: IntArray, w: Int, h: Int, bgraScratch: ByteArray): ImageBitmap {
    require(bgraScratch.size >= w * h * 4)
    val buf = ByteBuffer.wrap(bgraScratch).order(ByteOrder.LITTLE_ENDIAN)
    buf.clear()
    val n = w * h
    for (i in 0 until n) {
        val p = pixels[i]
        // BGRA_8888 little-endian
        buf.put((p and 0xFF).toByte())
        buf.put(((p ushr 8) and 0xFF).toByte())
        buf.put(((p ushr 16) and 0xFF).toByte())
        buf.put(((p ushr 24) and 0xFF).toByte())
    }
    val info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
    val skBitmap = SkiaBitmap()
    skBitmap.allocPixels(info)
    skBitmap.installPixels(info, bgraScratch, w * 4)
    return SkiaImage.makeFromBitmap(skBitmap).toComposeImageBitmap()
}

private fun bufferedToBitmap(img: BufferedImage): ImageBitmap {
    val w = img.width
    val h = img.height
    val pixels = IntArray(w * h)
    img.getRGB(0, 0, w, h, pixels, 0, w)
    return argbPixelsToBitmap(pixels, w, h, ByteArray(w * h * 4))
}
