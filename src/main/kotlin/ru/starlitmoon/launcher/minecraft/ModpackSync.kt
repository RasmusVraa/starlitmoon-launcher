package ru.starlitmoon.launcher.minecraft

import ru.starlitmoon.launcher.api.ModpackDto
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
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

    fun packDir(dataDir: Path, pack: ModpackDto): Path {
        val slug = pack.slug?.trim()?.ifBlank { null }
            ?: pack.id?.trim()?.ifBlank { null }
            ?: "default"
        val safe = slug.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return dataDir.resolve("packs").resolve(safe)
    }

    /**
     * @return true if an archive was applied (or already up to date), false if pack has no ZIP.
     */
    fun syncArchive(
        dataDir: Path,
        pack: ModpackDto,
        onProgress: (String) -> Unit = {},
    ): Boolean {
        val archive = pack.archive
        val url = archive?.url?.trim().orEmpty()
        if (!pack.hasArchive || url.isBlank()) return false

        val dir = packDir(dataDir, pack)
        dir.createDirectories()
        val expectedSha = archive?.sha256?.trim()?.lowercase().orEmpty()
        val marker = dir.resolve(MARKER)
        if (expectedSha.isNotBlank() && marker.exists() && marker.readText().trim().lowercase() == expectedSha) {
            onProgress("Сборка уже актуальна")
            return true
        }

        val cache = dir.resolve(".cache").apply { createDirectories() }
        val zipPath = cache.resolve(ZIP_NAME)
        onProgress("Скачивание архива сборки…")
        downloadTo(url, zipPath)

        if (expectedSha.isNotBlank()) {
            val actual = sha256Hex(zipPath)
            if (actual != expectedSha) {
                error("Контрольная сумма архива не совпала (ожидали $expectedSha)")
            }
        }

        onProgress("Распаковка сборки…")
        clearManagedContent(dir)
        extractZip(zipPath, dir)

        if (expectedSha.isNotBlank()) {
            marker.writeText(expectedSha)
        } else {
            marker.writeText(sha256Hex(zipPath))
        }
        onProgress("Сборка готова")
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

    private fun downloadTo(url: String, target: Path) {
        target.parent?.createDirectories()
        val tmp = target.resolveSibling("${target.name}.part")
        URI(url).toURL().openStream().use { input ->
            Files.copy(input, tmp, StandardCopyOption.REPLACE_EXISTING)
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun extractZip(zipPath: Path, dest: Path) {
        dest.createDirectories()
        val prefix = detectStripPrefix(zipPath)
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
            }
        }
    }

    /** If the ZIP has a single top-level folder, strip it so mods/ land at pack root. */
    private fun detectStripPrefix(zipPath: Path): String {
        val tops = linkedSetOf<String>()
        ZipInputStream(Files.newInputStream(zipPath)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val name = entry.name.replace('\\', '/').trimStart('/')
                zis.closeEntry()
                if (name.isBlank() || name.contains("..")) continue
                val top = name.substringBefore('/')
                if (top.isNotBlank()) tops += top
                if (tops.size > 1) return ""
            }
        }
        if (tops.size != 1) return ""
        val only = tops.first()
        // Don't strip if the only top entry is itself a known .minecraft folder.
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
