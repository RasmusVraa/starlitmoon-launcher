package ru.starlitmoon.launcher.minecraft

import ru.starlitmoon.launcher.api.ModpackDto
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Downloads a pack ZIP (`.minecraft`-like layout) into `~/.starlitmoon-launcher/packs/{slug}/`
 * and extracts it. Skips work when the stored sha256 marker matches the remote archive.
 */
object ModpackSync {
    private const val MARKER = ".starlit-archive.sha256"
    private const val ZIP_NAME = "pack.zip"
    /** No bytes for this long → fail (avoids eternal «Подготовка» / silent hang). */
    private const val READ_TIMEOUT_MS = 90_000
    private const val CONNECT_TIMEOUT_MS = 30_000
    /** Stale incomplete .part older than this is discarded before resume. */
    private const val STALE_PART_MS = 6L * 60L * 60L * 1000L

    fun packDir(dataDir: Path, pack: ModpackDto): Path {
        val slug = pack.slug?.trim()?.ifBlank { null }
            ?: pack.id?.trim()?.ifBlank { null }
            ?: "default"
        val safe = slug.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return dataDir.resolve("packs").resolve(safe)
    }

    /**
     * @param onProgress message + fraction 0..1 (null = indeterminate)
     * @return true if an archive was applied (or already up to date), false if pack has no ZIP.
     */
    fun syncArchive(
        dataDir: Path,
        pack: ModpackDto,
        onProgress: (String, Float?) -> Unit = { _, _ -> },
    ): Boolean {
        val archive = pack.archive
        val url = archive?.url?.trim().orEmpty()
        if (!pack.hasArchive || url.isBlank()) return false

        val dir = packDir(dataDir, pack)
        dir.createDirectories()
        val expectedSha = archive?.sha256?.trim()?.lowercase().orEmpty()
        val marker = dir.resolve(MARKER)
        if (expectedSha.isNotBlank() && marker.exists() && marker.readText().trim().lowercase() == expectedSha) {
            onProgress("Сборка уже актуальна", 1f)
            return true
        }

        val cache = dir.resolve(".cache").apply { createDirectories() }
        val zipPath = cache.resolve(ZIP_NAME)
        val expectedSize = archive?.size?.takeIf { it > 0 }
        onProgress("Подключение к архиву…", 0.01f)
        downloadTo(url, zipPath, expectedSize, onProgress)

        if (expectedSha.isNotBlank()) {
            onProgress("Проверка архива…", 0.88f)
            val actual = sha256Hex(zipPath)
            if (actual != expectedSha) {
                Files.deleteIfExists(zipPath)
                error("Контрольная сумма архива не совпала (ожидали $expectedSha)")
            }
        }

        onProgress("Распаковка сборки…", 0.90f)
        clearManagedContent(dir)
        extractZip(zipPath, dir) { done, total ->
            val frac = if (total > 0) 0.90f + 0.09f * done.toFloat() / total else 0.95f
            onProgress("Распаковка $done/$total…", frac)
        }

        if (expectedSha.isNotBlank()) {
            marker.writeText(expectedSha)
        } else {
            marker.writeText(sha256Hex(zipPath))
        }
        onProgress("Сборка готова", 1f)
        return true
    }

    private fun clearManagedContent(dir: Path) {
        val keep = setOf(".cache", MARKER, "saves", "logs", "crash-reports")
        if (!dir.exists()) return
        dir.listDirectoryEntries().forEach { child ->
            if (child.name in keep) return@forEach
            runCatching { child.toFile().deleteRecursively() }
        }
    }

    private fun downloadTo(
        url: String,
        target: Path,
        expectedSize: Long?,
        onProgress: (String, Float?) -> Unit,
    ) {
        target.parent?.createDirectories()
        val tmp = target.resolveSibling("${target.name}.part")
        var existing = if (tmp.exists()) tmp.fileSize() else 0L

        // Discard ancient incomplete parts (often a hung previous attempt).
        if (existing > 0) {
            val ageMs = System.currentTimeMillis() - tmp.toFile().lastModified()
            val hopeless = expectedSize != null && existing >= expectedSize
            val stale = ageMs > STALE_PART_MS && (expectedSize == null || existing < expectedSize * 95 / 100)
            if (hopeless || stale) {
                Files.deleteIfExists(tmp)
                existing = 0L
            }
        }

        var attempt = 0
        while (true) {
            attempt++
            try {
                downloadOnce(url, tmp, existing, expectedSize, onProgress)
                break
            } catch (e: Exception) {
                if (attempt >= 3) throw e
                onProgress("Повтор загрузки (${e.message?.take(80) ?: "ошибка"})…", 0.01f)
                // On failed resume, restart clean once.
                if (existing > 0 && attempt == 2) {
                    Files.deleteIfExists(tmp)
                    existing = 0L
                } else {
                    existing = if (tmp.exists()) tmp.fileSize() else 0L
                }
            }
        }

        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
        val finalSize = if (target.exists()) target.fileSize() else 0L
        onProgress("Архив скачан (${formatBytes(finalSize)})", 0.87f)
    }

    private fun downloadOnce(
        url: String,
        tmp: Path,
        existing: Long,
        expectedSize: Long?,
        onProgress: (String, Float?) -> Unit,
    ) {
        val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", "StarlitMoonLauncher")
            if (existing > 0) {
                setRequestProperty("Range", "bytes=$existing-")
            }
            requestMethod = "GET"
            connect()
        }

        val code = conn.responseCode
        val append = code == 206 && existing > 0
        if (code !in 200..299) {
            conn.disconnect()
            error("Не удалось скачать архив (HTTP $code)")
        }
        if (!append) {
            Files.deleteIfExists(tmp)
        }

        val totalHeader = conn.contentLengthLong.takeIf { it >= 0 }
        val total = when {
            append && expectedSize != null -> expectedSize
            append && totalHeader != null -> existing + totalHeader
            expectedSize != null -> expectedSize
            totalHeader != null -> totalHeader
            else -> -1L
        }

        var downloaded = if (append) existing else 0L
        val outOptions = if (append) {
            arrayOf(
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.WRITE,
                java.nio.file.StandardOpenOption.APPEND,
            )
        } else {
            arrayOf(
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.WRITE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
            )
        }

        try {
            BufferedInputStream(conn.inputStream).use { input ->
                Files.newOutputStream(tmp, *outOptions).use { output ->
                    val buf = ByteArray(1024 * 256)
                    var lastReport = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        downloaded += n
                        if (downloaded - lastReport >= 256 * 1024 || (total > 0 && downloaded >= total)) {
                            lastReport = downloaded
                            val frac = if (total > 0) {
                                (0.02f + 0.85f * (downloaded.toFloat() / total.toFloat())).coerceIn(0.02f, 0.87f)
                            } else {
                                null
                            }
                            val label = if (total > 0) {
                                "Скачивание ${formatBytes(downloaded)} / ${formatBytes(total)}"
                            } else {
                                "Скачивание ${formatBytes(downloaded)}"
                            }
                            onProgress(label, frac)
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }

        if (total > 0 && downloaded < total) {
            error("Архив скачан не полностью ($downloaded из $total байт)")
        }
    }

    private fun formatBytes(n: Long): String {
        if (n >= 1024L * 1024L * 1024L) return "%.2f ГБ".format(n / (1024.0 * 1024.0 * 1024.0))
        if (n >= 1024L * 1024L) return "%.1f МБ".format(n / (1024.0 * 1024.0))
        return "$n Б"
    }

    private fun extractZip(zipPath: Path, dest: Path, onEntry: (Int, Int) -> Unit = { _, _ -> }) {
        dest.createDirectories()
        val prefix = detectStripPrefix(zipPath)
        val totalEntries = runCatching {
            ZipFile(zipPath.toFile()).use { it.size() }
        }.getOrDefault(0)
        var done = 0
        ZipInputStream(Files.newInputStream(zipPath)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                var name = entry.name.replace('\\', '/')
                if (prefix.isNotEmpty() && name.startsWith(prefix)) {
                    name = name.removePrefix(prefix)
                }
                if (name.isBlank() || name.contains("..")) {
                    zis.closeEntry()
                    continue
                }
                val out = dest.resolve(name)
                if (entry.isDirectory) {
                    out.createDirectories()
                } else {
                    out.parent?.createDirectories()
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING)
                }
                zis.closeEntry()
                done++
                if (done % 25 == 0 || (totalEntries > 0 && done == totalEntries)) {
                    onEntry(done, totalEntries.coerceAtLeast(done))
                }
            }
        }
        onEntry(done, totalEntries.coerceAtLeast(done))
    }

    /** If the ZIP has a single top-level folder, strip it so mods/ land at pack root. */
    private fun detectStripPrefix(zipPath: Path): String {
        val tops = linkedSetOf<String>()
        runCatching {
            ZipFile(zipPath.toFile()).use { zip ->
                val entries = zip.entries()
                var checked = 0
                while (entries.hasMoreElements() && checked < 200) {
                    val entry = entries.nextElement()
                    checked++
                    val name = entry.name.replace('\\', '/').trimStart('/')
                    if (name.isBlank() || name.contains("..")) continue
                    val top = name.substringBefore('/')
                    if (top.isNotBlank()) tops += top
                    if (tops.size > 1) return ""
                }
            }
        }
        if (tops.size != 1) return ""
        val only = tops.first()
        val known = setOf(
            "mods", "resourcepacks", "shaderpacks", "config", "versions",
            "libraries", "assets", "saves", "options.txt",
        )
        if (only in known) return ""
        return "$only/"
    }

    private fun sha256Hex(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
