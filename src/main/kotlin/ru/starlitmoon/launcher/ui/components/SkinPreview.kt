package ru.starlitmoon.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.paint.Color
import javafx.scene.web.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import java.awt.BorderLayout
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO
import javax.swing.JPanel
import kotlin.io.path.exists

private val javafxStarted = AtomicBoolean(false)

private fun ensureJavaFx() {
    if (javafxStarted.compareAndSet(false, true)) {
        runCatching {
            Platform.startup {}
            Platform.setImplicitExit(false)
        }
    }
}

/**
 * Real WebGL 3D skin preview via skinview3d (same engine as starlit-moon.ru LK).
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
    val panel = remember {
        ensureJavaFx()
        SkinViewPanel()
    }

    DisposableEffect(Unit) {
        onDispose { /* keep shared FX toolkit alive */ }
    }

    LaunchedEffect(skinPath, capePath, slim, animated) {
        val skinData = withContext(Dispatchers.IO) { pathToDataUrl(skinPath) }
        val capeData = withContext(Dispatchers.IO) { pathToDataUrl(capePath) }
        // wait a tick for WebView document
        delay(50)
        panel.setTextures(skinData, capeData, slim, animated)
    }

    Box(
        modifier = modifier
            .size(previewSize)
            .clip(RoundedCornerShape(16.dp))
            .background(StarlitColors.Purple.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        SwingPanel(
            background = androidx.compose.ui.graphics.Color.Transparent,
            factory = { panel },
            modifier = Modifier.fillMaxSize(),
            update = { },
        )
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
                if (hat != null) {
                    var opaque = false
                    for (y in 0 until hat.height) for (x in 0 until hat.width) {
                        if (((hat.getRGB(x, y) ushr 24) and 0xFF) > 10) {
                            opaque = true
                            break
                        }
                    }
                    if (opaque) g.drawImage(hat, 0, 0, null)
                }
                g.dispose()
                val baos = ByteArrayOutputStream()
                ImageIO.write(out, "PNG", baos)
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

private fun pathToDataUrl(path: Path?): String? {
    if (path == null || !path.exists()) return null
    val bytes = Files.readAllBytes(path)
    val b64 = Base64.getEncoder().encodeToString(bytes)
    return "data:image/png;base64,$b64"
}

private class SkinViewPanel : JPanel(BorderLayout()) {
    private val fx = JFXPanel()
    @Volatile private var webView: WebView? = null
    @Volatile private var pageReady = false
    private var pending: PendingTextures? = null

    private data class PendingTextures(
        val skin: String?,
        val cape: String?,
        val slim: Boolean,
        val animated: Boolean,
    )

    init {
        isOpaque = false
        background = java.awt.Color(0, 0, 0, 0)
        fx.isOpaque = false
        add(fx, BorderLayout.CENTER)
        val latch = CountDownLatch(1)
        Platform.runLater {
            try {
                val view = WebView()
                view.isContextMenuEnabled = false
                view.setPrefSize(800.0, 800.0)
                val engine = view.engine
                engine.loadWorker.stateProperty().addListener { _, _, newState ->
                    if (newState == Worker.State.SUCCEEDED) {
                        pageReady = true
                        pending?.let { applyTextures(it) }
                        pending = null
                    }
                }
                val url = javaClass.getResource("/skinview/index.html")?.toExternalForm()
                if (url != null) engine.load(url)
                val scene = Scene(view)
                scene.fill = Color.TRANSPARENT
                fx.scene = scene
                webView = view
            } finally {
                latch.countDown()
            }
        }
        latch.await(8, TimeUnit.SECONDS)
    }

    fun setTextures(skin: String?, cape: String?, slim: Boolean, animated: Boolean) {
        val req = PendingTextures(skin, cape, slim, animated)
        Platform.runLater {
            if (!pageReady) {
                pending = req
            } else {
                applyTextures(req)
            }
        }
    }

    private fun applyTextures(req: PendingTextures) {
        val engine = webView?.engine ?: return
        val skinJs = req.skin?.let { "'${it.replace("'", "%27")}'" } ?: "null"
        val capeJs = req.cape?.let { "'${it.replace("'", "%27")}'" } ?: "null"
        val script = "window.setSkin($skinJs, $capeJs, ${req.slim}, ${req.animated})"
        runCatching { engine.executeScript(script) }
    }
}
