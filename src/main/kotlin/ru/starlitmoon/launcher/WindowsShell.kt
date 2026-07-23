package ru.starlitmoon.launcher

import com.sun.jna.Native
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.Image
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * Windows taskbar: set AppUserModelID before UI, and multi-size frame icons.
 */
object WindowsShell {
    const val APP_USER_MODEL_ID = "StarlitMoon.Launcher"

    fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true)

    /** Must run before the first UI window. */
    fun applyAppUserModelId() {
        if (!isWindows()) return
        runCatching {
            Shell32Ex.INSTANCE.SetCurrentProcessExplicitAppUserModelID(APP_USER_MODEL_ID)
        }
    }

    fun loadWindowIconImages(): List<Image> {
        val src = runCatching {
            object {}.javaClass.getResourceAsStream("/icon.png")?.use { ImageIO.read(it) }
        }.getOrNull() ?: return emptyList()
        return listOf(16, 32, 48, 64, 128, 256).map { size -> scaleIcon(src, size) }
    }

    private fun scaleIcon(src: BufferedImage, size: Int): BufferedImage {
        val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(
            java.awt.RenderingHints.KEY_INTERPOLATION,
            java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC,
        )
        g.setRenderingHint(
            java.awt.RenderingHints.KEY_ANTIALIASING,
            java.awt.RenderingHints.VALUE_ANTIALIAS_ON,
        )
        g.drawImage(src, 0, 0, size, size, null)
        g.dispose()
        return out
    }

    @Suppress("FunctionName")
    private interface Shell32Ex : StdCallLibrary {
        fun SetCurrentProcessExplicitAppUserModelID(appId: String): WinNT.HRESULT

        companion object {
            val INSTANCE: Shell32Ex = Native.load(
                "shell32",
                Shell32Ex::class.java,
                W32APIOptions.DEFAULT_OPTIONS,
            )
        }
    }
}
