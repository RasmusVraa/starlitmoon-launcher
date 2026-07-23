package ru.starlitmoon.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.exists
import kotlin.math.cos
import kotlin.math.sin

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

    LaunchedEffect(skinPath) {
        atlas = null
        val p = skinPath
        if (p == null || !p.exists()) return@LaunchedEffect
        atlas = withContext(Dispatchers.IO) {
            runCatching { ImageIO.read(ByteArrayInputStream(Files.readAllBytes(p))) }.getOrNull()
        }
    }
    LaunchedEffect(capePath) {
        capeAtlas = null
        val p = capePath
        if (p == null || !p.exists()) return@LaunchedEffect
        capeAtlas = withContext(Dispatchers.IO) {
            runCatching { ImageIO.read(ByteArrayInputStream(Files.readAllBytes(p))) }.getOrNull()
        }
    }

    Box(
        modifier = modifier
            .size(previewSize)
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    yaw = (yaw + drag.x * 0.6f) % 360f
                }
            },
    ) {
        val a = atlas
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(StarlitColors.Purple.copy(alpha = 0.18f))
            if (a == null) return@Canvas
            val scale = size.minDimension / 48f
            val cx = size.width / 2f
            val cy = size.height / 2f + 6f * scale
            drawCharacter(
                atlas = a,
                capeAtlas = capeAtlas,
                slim = slim,
                yawDeg = yaw,
                cx = cx,
                cy = cy,
                scale = scale,
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
                val img = ImageIO.read(p.toFile()) ?: return@runCatching null
                val scale = (img.width / 64).coerceAtLeast(1)
                val faceImg = img.getSubimage(8 * scale, 8 * scale, 8 * scale, 8 * scale)
                val hat = runCatching {
                    img.getSubimage(40 * scale, 8 * scale, 8 * scale, 8 * scale)
                }.getOrNull()
                val out = BufferedImage(8 * scale, 8 * scale, BufferedImage.TYPE_INT_ARGB)
                val g = out.createGraphics()
                g.drawImage(faceImg, 0, 0, null)
                if (hat != null) g.drawImage(hat, 0, 0, null)
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

private fun toBitmap(img: BufferedImage): ImageBitmap {
    val baos = ByteArrayOutputStream()
    ImageIO.write(img, "PNG", baos)
    return SkiaImage.makeFromEncoded(baos.toByteArray()).toComposeImageBitmap()
}

private fun DrawScope.drawCharacter(
    atlas: BufferedImage,
    capeAtlas: BufferedImage?,
    slim: Boolean,
    yawDeg: Float,
    cx: Float,
    cy: Float,
    scale: Float,
) {
    val yaw = Math.toRadians(yawDeg.toDouble())
    val cosY = cos(yaw).toFloat()
    val sinY = sin(yaw).toFloat()
    val armW = if (slim) 3 else 4

    fun part(u: Int, v: Int, w: Int, h: Int): ImageBitmap? {
        return runCatching {
            toBitmap(atlas.getSubimage(u, v, w, h))
        }.getOrNull()
    }

    data class Quad(val bmp: ImageBitmap, val x: Float, val y: Float, val w: Float, val h: Float, val z: Float)
    val quads = mutableListOf<Quad>()

    fun addBox(
        front: ImageBitmap?,
        left: ImageBitmap?,
        right: ImageBitmap?,
        back: ImageBitmap?,
        ox: Float,
        oy: Float,
        oz: Float,
        bw: Float,
        bh: Float,
        bd: Float,
    ) {
        val rx = ox * cosY - oz * sinY
        val rz = ox * sinY + oz * cosY
        val showFront = cosY >= 0
        if (showFront && front != null) quads += Quad(front, rx - bw / 2, oy, bw, bh, rz)
        if (!showFront && back != null) quads += Quad(back, rx - bw / 2, oy, bw, bh, rz - 0.01f)
        if (sinY > 0.15f && left != null) {
            quads += Quad(left, rx - bw / 2 - bd * 0.1f, oy, bd * 0.55f, bh, rz + 0.5f)
        }
        if (sinY < -0.15f && right != null) {
            quads += Quad(right, rx + bw / 2 - bd * 0.45f, oy, bd * 0.55f, bh, rz + 0.5f)
        }
    }

    addBox(part(4, 20, 4, 12), part(0, 20, 4, 12), part(8, 20, 4, 12), part(12, 20, 4, 12), -2f, 0f, 0f, 4f, 12f, 4f)
    addBox(part(20, 52, 4, 12), part(16, 52, 4, 12), part(24, 52, 4, 12), part(28, 52, 4, 12), 2f, 0f, 0f, 4f, 12f, 4f)
    addBox(part(20, 20, 8, 12), part(16, 20, 4, 12), part(28, 20, 4, 12), part(32, 20, 8, 12), 0f, 12f, 0f, 8f, 12f, 4f)
    addBox(part(44, 20, armW, 12), part(40, 20, 4, 12), part(48, 20, 4, 12), part(52, 20, armW, 12), -(4f + armW / 2f), 12f, 0f, armW.toFloat(), 12f, 4f)
    addBox(part(36, 52, armW, 12), part(32, 52, 4, 12), part(40, 52, 4, 12), part(44, 52, armW, 12), 4f + armW / 2f, 12f, 0f, armW.toFloat(), 12f, 4f)
    addBox(part(8, 8, 8, 8), part(0, 8, 8, 8), part(16, 8, 8, 8), part(24, 8, 8, 8), 0f, 24f, 0f, 8f, 8f, 8f)
    part(40, 8, 8, 8)?.let { hat ->
        quads += Quad(hat, -4.2f, 23.6f, 8.4f, 8.4f, 1f)
    }

    if (capeAtlas != null) {
        runCatching {
            toBitmap(capeAtlas.getSubimage(1, 1, 10, 16))
        }.getOrNull()?.let { capeFront ->
            val backZ = -3f
            val rx = -backZ * sinY
            val rz = backZ * cosY
            quads += Quad(capeFront, rx - 5f, 12f, 10f, 16f, rz - 2f)
        }
    }

    quads.sortedBy { it.z }.forEach { q ->
        val w = (q.w * scale).toInt().coerceAtLeast(1)
        val h = (q.h * scale).toInt().coerceAtLeast(1)
        drawImage(
            image = q.bmp,
            dstOffset = IntOffset((cx + q.x * scale).toInt(), (cy - (q.y + q.h) * scale).toInt()),
            dstSize = IntSize(w, h),
            filterQuality = FilterQuality.None,
        )
    }
}
