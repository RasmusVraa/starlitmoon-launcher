package ru.starlitmoon.launcher.util

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

/**
 * Disk cache for remote images (modpack banners, etc.).
 * Files live in ~/.starlitmoon-launcher/cache/images/
 */
object ImageDiskCache {
    private val root: Path =
        Path.of(System.getProperty("user.home"), ".starlitmoon-launcher", "cache", "images")

    fun loadOrFetch(url: String): ByteArray? {
        if (url.isBlank()) return null
        root.createDirectories()
        val file = root.resolve(keyFor(url) + extensionFor(url))
        if (file.exists() && Files.size(file) > 32) {
            return runCatching { file.readBytes() }.getOrNull()
        }
        val bytes = runCatching {
            java.net.URI(url).toURL().openStream().use { it.readBytes() }
        }.getOrNull() ?: return null
        if (bytes.size < 32) return null
        runCatching { file.writeBytes(bytes) }
        return bytes
    }

    private fun keyFor(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(40)
    }

    private fun extensionFor(url: String): String {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> ".jpg"
            path.endsWith(".webp") -> ".webp"
            path.endsWith(".gif") -> ".gif"
            else -> ".png"
        }
    }
}
