package ru.starlitmoon.launcher.util

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

/**
 * Disk + in-memory cache for remote images (avatars, skins, capes, modpack banners).
 * Files live in ~/.starlitmoon-launcher/cache/images/
 */
object ImageDiskCache {
    /** Avatars / skins / capes — rarely change; keep for a week. */
    const val TTL_TEXTURE_MS: Long = 7L * 24 * 60 * 60_000
    /** Generic UI images (banners, bank art). */
    const val TTL_IMAGE_MS: Long = 2L * 24 * 60 * 60_000

    private val root: Path =
        Path.of(System.getProperty("user.home"), ".starlitmoon-launcher", "cache", "images")

    private val memory = ConcurrentHashMap<String, ByteArray>()

    fun loadOrFetch(url: String, ttlMs: Long = TTL_IMAGE_MS): ByteArray? {
        if (url.isBlank()) return null
        memory[url]?.let { return it }
        root.createDirectories()
        val file = root.resolve(keyFor(url) + extensionFor(url))
        if (file.exists() && Files.size(file) > 32 && !isExpired(file, ttlMs)) {
            return runCatching { file.readBytes() }.getOrNull()?.also { memory[url] = it }
        }
        val bytes = runCatching {
            java.net.URI(url).toURL().openStream().use { it.readBytes() }
        }.getOrNull() ?: run {
            // Network failed — return stale disk entry if present.
            if (file.exists() && Files.size(file) > 32) {
                return runCatching { file.readBytes() }.getOrNull()?.also { memory[url] = it }
            }
            return null
        }
        if (bytes.size < 32) return null
        runCatching { file.writeBytes(bytes) }
        memory[url] = bytes
        return bytes
    }

    /** Ensures [url] is cached on disk and returns the local path (for skin/cape preview). */
    fun cachedPath(url: String, ttlMs: Long = TTL_TEXTURE_MS): Path? {
        loadOrFetch(url, ttlMs) ?: return null
        val file = root.resolve(keyFor(url) + extensionFor(url))
        return file.takeIf { it.exists() && Files.size(it) > 32 }
    }

    fun peekBytes(url: String): ByteArray? = memory[url]

    private fun isExpired(file: Path, ttlMs: Long): Boolean {
        if (ttlMs <= 0) return false
        val modified = runCatching { Files.getLastModifiedTime(file).toMillis() }.getOrNull() ?: return true
        return System.currentTimeMillis() - modified > ttlMs
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
