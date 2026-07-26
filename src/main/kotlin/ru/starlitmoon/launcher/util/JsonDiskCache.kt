package ru.starlitmoon.launcher.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Disk + in-memory JSON cache under ~/.starlitmoon-launcher/cache/http/
 * Used for players list, profiles, comments, etc.
 */
object JsonDiskCache {
    const val TTL_PLAYERS_LIST_MS: Long = 15L * 60_000
    const val TTL_PROFILE_MS: Long = 30L * 60_000
    const val TTL_COMMENTS_MS: Long = 10L * 60_000

    /** Allow showing stale data while a network refresh runs. */
    const val MAX_STALE_MS: Long = 24L * 60 * 60_000

    private val root: Path =
        Path.of(System.getProperty("user.home"), ".starlitmoon-launcher", "cache", "http")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val memory = ConcurrentHashMap<String, MemEntry>()

    @Serializable
    private data class Envelope(
        val cachedAt: Long,
        val ttlMs: Long,
        val payload: String,
    )

    private data class MemEntry(
        val cachedAt: Long,
        val ttlMs: Long,
        val payload: String,
    )

    data class Hit<T>(
        val value: T,
        /** True when age is within [ttlMs]. Stale hits may still be shown while refreshing. */
        val fresh: Boolean,
        val ageMs: Long,
    )

    fun <T> get(key: String, serializer: KSerializer<T>, maxStaleMs: Long = MAX_STALE_MS): Hit<T>? {
        if (key.isBlank()) return null
        val now = System.currentTimeMillis()
        memory[key]?.let { mem ->
            val age = now - mem.cachedAt
            if (age in 0 until maxStaleMs) {
                val value = runCatching { json.decodeFromString(serializer, mem.payload) }.getOrNull()
                    ?: return@let
                return Hit(value, fresh = age <= mem.ttlMs, ageMs = age)
            }
        }
        root.createDirectories()
        val file = root.resolve(fileName(key))
        if (!file.exists()) return null
        val envelope = runCatching { json.decodeFromString(Envelope.serializer(), file.readText()) }.getOrNull()
            ?: return null
        val age = now - envelope.cachedAt
        if (age < 0 || age >= maxStaleMs) return null
        val value = runCatching { json.decodeFromString(serializer, envelope.payload) }.getOrNull()
            ?: return null
        memory[key] = MemEntry(envelope.cachedAt, envelope.ttlMs, envelope.payload)
        return Hit(value, fresh = age <= envelope.ttlMs, ageMs = age)
    }

    fun <T> put(key: String, value: T, serializer: KSerializer<T>, ttlMs: Long) {
        if (key.isBlank()) return
        val payload = runCatching { json.encodeToString(serializer, value) }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        memory[key] = MemEntry(now, ttlMs, payload)
        root.createDirectories()
        val envelope = Envelope(cachedAt = now, ttlMs = ttlMs, payload = payload)
        runCatching {
            root.resolve(fileName(key)).writeText(json.encodeToString(Envelope.serializer(), envelope))
        }
    }

    fun invalidate(key: String) {
        memory.remove(key)
        runCatching {
            val file = root.resolve(fileName(key))
            if (file.exists()) Files.deleteIfExists(file)
        }
    }

    private fun fileName(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(40) + ".json"
    }
}
