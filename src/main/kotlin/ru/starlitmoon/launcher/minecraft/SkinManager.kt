package ru.starlitmoon.launcher.minecraft

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

/**
 * Валидация и локальный кеш скина. Загрузка на сервер — через [ru.starlitmoon.launcher.api.StarlitApiClient.uploadSkin].
 */
class SkinManager(
    private val skinsDir: Path,
) {
    data class PreparedSkin(
        val localPath: Path,
        val dataUrl: String,
        val bytes: ByteArray,
    )

    fun skinFileFor(username: String): Path =
        skinsDir.resolve("${username.trim().lowercase()}.png")

    fun validatePng(bytes: ByteArray): String? {
        val img = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
            ?: return "Не удалось прочитать PNG"
        val w = img.width
        val h = img.height
        val ok =
            (w == 64 && (h == 64 || h == 32)) ||
                (w == 128 && h == 128)
        if (!ok) return "Нужен скин PNG 64×64 (или 64×32 / 128×128), сейчас ${w}×${h}"
        return null
    }

    fun prepareFromFile(username: String, sourceFile: Path): PreparedSkin {
        if (!sourceFile.exists()) error("Файл не найден")
        val bytes = Files.readAllBytes(sourceFile)
        validatePng(bytes)?.let { error(it) }

        skinsDir.createDirectories()
        val dest = skinFileFor(username)
        dest.writeBytes(bytes)

        val b64 = Base64.getEncoder().encodeToString(bytes)
        return PreparedSkin(
            localPath = dest,
            dataUrl = "data:image/png;base64,$b64",
            bytes = bytes,
        )
    }

    fun close() = Unit
}
