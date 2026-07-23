package ru.starlitmoon.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import ru.starlitmoon.launcher.skin.SkinRenderApi
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.math.roundToInt

/**
 * 3D skin preview via public render APIs (VZGE + Starlight walking).
 * No JavaFX / WebGL — won't hang the UI.
 */
@Composable
fun SkinPreview3D(
    skinPath: Path?,
    @Suppress("UNUSED_PARAMETER") capePath: Path? = null,
    slim: Boolean = false,
    modifier: Modifier = Modifier,
    previewSize: Dp = 220.dp,
    animated: Boolean = true,
    skinUrl: String? = null,
    username: String = "Steve",
) {
    var yaw by remember { mutableFloatStateOf(28f) }
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var userDragging by remember { mutableStateOf(false) }

    // Load / refresh when skin or yaw changes (debounced via keyed effect)
    LaunchedEffect(skinPath, skinUrl, slim, yaw.roundToInt()) {
        loading = frame == null
        error = null
        val bytes = withContext(Dispatchers.IO) {
            val png = skinPath?.takeIf { it.exists() }?.let { runCatching { Files.readAllBytes(it) }.getOrNull() }
            val public = skinUrl?.trim()?.takeIf { it.isNotBlank() }
            // VZGE = reliable perspective 3D + yaw orbit; Starlight walking when available
            SkinRenderApi.fetchVzgeFull(public, png, slim, height = 512, yaw = yaw.roundToInt())
                ?: if (public != null) {
                    SkinRenderApi.fetchStarlightWalking(username, public, capeUrl = null, slim = slim)
                } else {
                    null
                }
        }
        if (bytes != null) {
            frame = runCatching { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
            if (frame == null) error = "Не удалось декодировать превью"
        } else {
            error = "Нет 3D-превью (сеть / скин слишком большой)"
        }
        loading = false
    }

    // Gentle auto-rotate via VZGE yaw (polite interval)
    LaunchedEffect(animated, skinPath, skinUrl) {
        if (!animated) return@LaunchedEffect
        while (isActive) {
            delay(1400)
            if (!userDragging) {
                yaw = (yaw + 18f) % 360f
            }
        }
    }

    Box(
        modifier = modifier
            .size(previewSize)
            .clip(RoundedCornerShape(16.dp))
            .background(StarlitColors.Purple.copy(alpha = 0.14f))
            .pointerInput(animated) {
                if (!animated) return@pointerInput
                detectDragGestures(
                    onDragStart = { userDragging = true },
                    onDragEnd = { userDragging = false },
                    onDragCancel = { userDragging = false },
                    onDrag = { change, drag ->
                        change.consume()
                        yaw = (yaw + drag.x * 0.55f).let { v ->
                            var x = v % 360f
                            if (x < 0) x += 360f
                            x
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val bmp = frame
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                filterQuality = FilterQuality.Medium,
                contentScale = ContentScale.Fit,
            )
        }
        if (loading && bmp == null) {
            CircularProgressIndicator(color = StarlitColors.Gold, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }
        if (!loading && bmp == null && error != null) {
            Text(error!!, color = StarlitColors.TextMuted, fontSize = 11.sp)
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
                val img = javax.imageio.ImageIO.read(p.toFile()) ?: return@runCatching null
                val scale = (img.width / 64).coerceAtLeast(1)
                val faceImg = img.getSubimage(8 * scale, 8 * scale, 8 * scale, 8 * scale)
                val hat = runCatching {
                    img.getSubimage(40 * scale, 8 * scale, 8 * scale, 8 * scale)
                }.getOrNull()
                val out = java.awt.image.BufferedImage(8 * scale, 8 * scale, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                val g = out.createGraphics()
                g.drawImage(faceImg, 0, 0, null)
                if (hat != null) g.drawImage(hat, 0, 0, null)
                g.dispose()
                val baos = java.io.ByteArrayOutputStream()
                javax.imageio.ImageIO.write(out, "PNG", baos)
                SkiaImage.makeFromEncoded(baos.toByteArray()).toComposeImageBitmap()
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
