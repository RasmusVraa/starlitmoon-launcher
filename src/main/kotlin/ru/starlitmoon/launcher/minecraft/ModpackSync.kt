package ru.starlitmoon.launcher.minecraft

import ru.starlitmoon.launcher.api.ModpackDto
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
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
import kotlin.io.path.writeText

/**
 * Downloads a pack ZIP (`.minecraft`-like layout) into `~/.starlitmoon-launcher/packs/{slug}/`
 * and extracts it. Skips work when the stored sha256 marker matches the remote archive.
 *
 * Updates keep saves / options / servers / existing config / user-added mods;
 * pack ZIP settings are not applied over user files.
 */
object ModpackSync {
    private const val MARKER = ".starlit-archive.sha256"
    private const val MANAGED_MODS = ".starlit-managed-mods"
    private const val ZIP_NAME = "pack.zip"
    /** No bytes for this long → fail (avoids eternal «Подготовка» / silent hang). */
    private const val READ_TIMEOUT_MS = 90_000
    private const val CONNECT_TIMEOUT_MS = 30_000
    /** Stale incomplete .part older than this is discarded before resume. */
    private const val STALE_PART_MS = 6L * 60L * 60L * 1000L

    /**
     * ZIP entry names are often CP437/CP866 without UTF-8 flag.
     * Default UTF-8 ZipInputStream throws `malformed input off : N`.
     * ISO-8859-1 maps every byte 1:1 and never fails decoding.
     */
    private val ZIP_CHARSET: Charset = StandardCharsets.ISO_8859_1

    private fun openZip(path: Path): ZipFile = ZipFile(path.toFile(), ZIP_CHARSET)

    private fun openZipStream(path: Path): ZipInputStream =
        ZipInputStream(Files.newInputStream(path), ZIP_CHARSET)

    /**
     * Resolve a ZIP entry under [root]. Rejects `..`, NUL, absolute/drive/UNC paths, and
     * any path that normalizes outside [root] (ZIP slip).
     */
    private fun safeResolveUnder(root: Path, relative: String): Path? {
        val name = relative.replace('\\', '/').trim()
        if (name.isBlank() || name.contains("..") || name.contains('\u0000')) return null
        // Absolute / drive-letter / UNC — Path.resolve would escape the pack dir on Windows.
        if (name.startsWith("/") || name.startsWith("//") || name.matches(Regex("^[A-Za-z]:.*"))) {
            return null
        }
        val rootNorm = root.toAbsolutePath().normalize()
        val out = rootNorm.resolve(name).normalize()
        if (!out.startsWith(rootNorm)) return null
        return out
    }

    private fun readTextLenient(path: Path): String {
        val bytes = Files.readAllBytes(path)
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        return decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }
    /** Keep only worlds (+ map markers) and download cache on update. */
    private val PRESERVE_ON_UPDATE = setOf(
        ".cache",
        "saves",
        "XaeroWorldMap",
        "XaeroWaypoints",
        "xaero",
    )

    fun packDir(dataDir: Path, pack: ModpackDto): Path {
        val slug = pack.slug?.trim()?.ifBlank { null }
            ?: pack.id?.trim()?.ifBlank { null }
            ?: "default"
        val safe = slug.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return dataDir.resolve("packs").resolve(safe)
    }

    fun localArchiveSha(dataDir: Path, pack: ModpackDto): String? {
        val marker = packDir(dataDir, pack).resolve(MARKER)
        return marker.let { if (!it.exists()) null else readTextLenient(it) }
            .orEmpty().trim().lowercase().takeIf { it.isNotBlank() }
    }

    /**
     * Site ZIP archive path only. When [LauncherConfig.preferGithubModpacks] is on,
     * use [GithubModpackSync.needsUpdate] with the GitHub manifest hash instead —
     * comparing a GitHub install marker to the site ZIP sha causes a perpetual
     * «Требуется обновление» badge.
     */
    fun needsUpdate(dataDir: Path, pack: ModpackDto): Boolean {
        if (pack.hasArchive) {
            val remote = pack.archive?.sha256?.trim()?.lowercase().orEmpty()
            if (remote.isNotBlank()) {
                val local = localArchiveSha(dataDir, pack) ?: return false
                return local != remote
            }
        }
        return false
    }

    /** True if this pack was installed at least once locally. */
    fun isInstalled(dataDir: Path, pack: ModpackDto): Boolean {
        val dir = packDir(dataDir, pack)
        if (!dir.exists()) return false
        return localArchiveSha(dataDir, pack) != null ||
            dir.resolve("mods").exists() ||
            dir.listDirectoryEntries().any { it.name !in setOf(".cache") }
    }

    /** Deletes the whole local pack folder (including worlds). */
    fun deleteLocalPack(dataDir: Path, pack: ModpackDto): Boolean {
        val dir = packDir(dataDir, pack)
        if (!dir.exists()) return false
        return runCatching { dir.toFile().deleteRecursively() }.isSuccess && !dir.exists()
    }

    /**
     * @param force re-download even if local sha matches remote
     * @return true if an archive was applied (or already up to date), false if pack has no ZIP.
     */
    fun syncArchive(
        dataDir: Path,
        pack: ModpackDto,
        force: Boolean = false,
        control: DownloadControl = DownloadControl(),
        onProgress: (ProgressEvent) -> Unit = {},
    ): Boolean {
        val archive = pack.archive
        val url = archive?.url?.trim().orEmpty()
        if (!pack.hasArchive || url.isBlank()) return false

        val dir = packDir(dataDir, pack)
        dir.createDirectories()
        val expectedSha = archive?.sha256?.trim()?.lowercase().orEmpty()
        val marker = dir.resolve(MARKER)
        val cache = dir.resolve(".cache").apply { createDirectories() }
        val zipPath = cache.resolve(ZIP_NAME)

        if (force) {
            Files.deleteIfExists(marker)
            Files.deleteIfExists(zipPath)
            Files.deleteIfExists(zipPath.resolveSibling("${zipPath.name}.part"))
        }

        if (!force && expectedSha.isNotBlank() && marker.exists() &&
            readTextLenient(marker).trim().lowercase() == expectedSha
        ) {
            onProgress(ProgressEvent("Сборка уже актуальна", 1f))
            return true
        }

        val expectedSize = archive?.size?.takeIf { it > 0 }
        onProgress(ProgressEvent("Подключение к архиву…", 0.01f, bytesTotal = expectedSize, kind = ProgressEvent.Kind.Download))
        downloadTo(url, zipPath, expectedSize, control, onProgress)

        if (expectedSha.isNotBlank()) {
            onProgress(
                ProgressEvent(
                    "Проверка архива…",
                    0.88f,
                    currentFile = zipPath.name,
                    kind = ProgressEvent.Kind.Verify,
                ),
            )
            val actual = sha256Hex(zipPath)
            if (actual != expectedSha) {
                Files.deleteIfExists(zipPath)
                error("Контрольная сумма архива не совпала (ожидали $expectedSha)")
            }
        }

        onProgress(ProgressEvent("Очистка сборки…", 0.89f, kind = ProgressEvent.Kind.Extract))
        wipeExceptWorlds(dir)

        onProgress(ProgressEvent("Распаковка файлов…", 0.90f, currentFile = zipPath.name, kind = ProgressEvent.Kind.Extract))
        val zipMods = listZipModFileNames(zipPath)
        extractZip(zipPath, dir) { done, total, name ->
            val frac = if (total > 0) 0.90f + 0.05f * done.toFloat() / total else 0.93f
            onProgress(
                ProgressEvent(
                    message = "Файл $done/$total",
                    fraction = frac,
                    filesDone = done,
                    filesTotal = total,
                    currentFile = name,
                    threads = 1,
                    kind = ProgressEvent.Kind.Extract,
                ),
            )
        }
        writeManagedMods(dir, zipMods)

        onProgress(ProgressEvent("Проверка файлов…", 0.96f, kind = ProgressEvent.Kind.Verify))
        verifyExtracted(zipPath, dir) { done, total, name ->
            val frac = if (total > 0) 0.96f + 0.03f * done.toFloat() / total else 0.98f
            onProgress(
                ProgressEvent(
                    message = "Проверка $done/$total",
                    fraction = frac,
                    filesDone = done,
                    filesTotal = total,
                    currentFile = name,
                    threads = 1,
                    kind = ProgressEvent.Kind.Verify,
                ),
            )
        }

        if (expectedSha.isNotBlank()) {
            marker.writeText(expectedSha)
        } else {
            marker.writeText(sha256Hex(zipPath))
        }
        onProgress(ProgressEvent("Сборка готова", 1f, kind = ProgressEvent.Kind.Verify))
        return true
    }

    /** Wipe pack dir except worlds / map data / zip cache. */
    private fun wipeExceptWorlds(dir: Path) {
        if (!dir.exists()) return
        Files.deleteIfExists(dir.resolve(MARKER))
        Files.deleteIfExists(dir.resolve(MANAGED_MODS))
        dir.listDirectoryEntries().forEach { child ->
            if (child.name in PRESERVE_ON_UPDATE) return@forEach
            runCatching { child.toFile().deleteRecursively() }
        }
    }

    private fun writeManagedMods(packDir: Path, names: Set<String>) {
        val marker = packDir.resolve(MANAGED_MODS)
        if (names.isEmpty()) {
            Files.deleteIfExists(marker)
            return
        }
        marker.writeText(names.sorted().joinToString("\n") + "\n")
    }

    /** Basename of each file under `mods/` in the ZIP (after strip-prefix). */
    private fun listZipModFileNames(zipPath: Path): Set<String> {
        val prefix = detectStripPrefix(zipPath)
        val out = linkedSetOf<String>()
        runCatching {
            openZip(zipPath).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    var name = entry.name.replace('\\', '/').trimStart('/')
                    if (prefix.isNotEmpty() && name.startsWith(prefix)) {
                        name = name.removePrefix(prefix)
                    }
                    if (name.isBlank() || name.contains("..")) continue
                    if (!name.startsWith("mods/")) continue
                    val rest = name.removePrefix("mods/")
                    if (rest.isBlank() || rest.contains('/')) continue
                    out += rest
                }
            }
        }
        return out
    }

    private fun shouldSkipExtract(relative: String): Boolean {
        val norm = relative.trimStart('/').replace('\\', '/')
        if (norm.isBlank()) return true
        val top = norm.substringBefore('/')
        // Never replace local worlds / map data from the ZIP.
        if (top == "saves") return true
        if (top == "XaeroWorldMap" || top == "XaeroWaypoints" || top == "xaero") return true
        return false
    }

    private fun downloadTo(
        url: String,
        target: Path,
        expectedSize: Long?,
        control: DownloadControl,
        onProgress: (ProgressEvent) -> Unit,
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
                downloadOnce(url, tmp, existing, expectedSize, control, onProgress)
                break
            } catch (e: DownloadCancelledException) {
                throw e
            } catch (e: Exception) {
                if (attempt >= 3) throw e
                onProgress(
                    ProgressEvent(
                        "Повтор загрузки (${e.message?.take(80) ?: "ошибка"})…",
                        0.01f,
                        bytesTotal = expectedSize,
                    ),
                )
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
        onProgress(
            ProgressEvent(
                message = "Архив скачан (${formatBytes(finalSize)})",
                fraction = 0.87f,
                bytesDone = finalSize,
                bytesTotal = finalSize,
                currentFile = target.name,
                kind = ProgressEvent.Kind.Download,
            ),
        )
    }

    private fun downloadOnce(
        url: String,
        tmp: Path,
        existing: Long,
        expectedSize: Long?,
        control: DownloadControl,
        onProgress: (ProgressEvent) -> Unit,
    ) {
        control.checkpoint()
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
                    var speedWindowBytes = downloaded
                    var speedWindowAtNs = System.nanoTime()
                    var lastUiAtNs = 0L
                    var speedEma = 0.0
                    while (true) {
                        control.checkpoint()
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        downloaded += n
                        control.throttle(n)
                        val nowNs = System.nanoTime()
                        val sinceBytes = downloaded - speedWindowBytes
                        val dtSec = (nowNs - speedWindowAtNs) / 1_000_000_000.0
                        // Advance speed window only after enough time+bytes so МБ/с is stable.
                        if (dtSec >= 0.25 && sinceBytes >= 64 * 1024) {
                            val instant = sinceBytes / dtSec
                            speedEma = if (speedEma <= 0.0) instant else speedEma * 0.6 + instant * 0.4
                            speedWindowBytes = downloaded
                            speedWindowAtNs = nowNs
                        }
                        val uiDtSec = if (lastUiAtNs == 0L) 1.0 else (nowNs - lastUiAtNs) / 1_000_000_000.0
                        if (uiDtSec >= 0.2 || (total > 0 && downloaded >= total)) {
                            lastUiAtNs = nowNs
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
                            onProgress(
                                ProgressEvent(
                                    message = label,
                                    fraction = frac,
                                    bytesDone = downloaded,
                                    bytesTotal = total.takeIf { it > 0 },
                                    currentFile = tmp.name.removeSuffix(".part"),
                                    threads = 1,
                                    speedBps = speedEma.toLong().takeIf { it > 0 },
                                    kind = ProgressEvent.Kind.Download,
                                ),
                            )
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

    private fun extractZip(
        zipPath: Path,
        dest: Path,
        onEntry: (Int, Int, String) -> Unit = { _, _, _ -> },
    ) {
        dest.createDirectories()
        val prefix = detectStripPrefix(zipPath)
        val totalEntries = runCatching {
            openZip(zipPath).use { it.size() }
        }.getOrDefault(0)
        var done = 0
        openZipStream(zipPath).use { zis ->
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
                if (shouldSkipExtract(name)) {
                    zis.closeEntry()
                    done++
                    continue
                }
                val out = safeResolveUnder(dest, name)
                if (out == null) {
                    zis.closeEntry()
                    continue
                }
                if (entry.isDirectory) {
                    out.createDirectories()
                } else {
                    out.parent?.createDirectories()
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING)
                }
                zis.closeEntry()
                done++
                onEntry(done, totalEntries.coerceAtLeast(done), name.substringAfterLast('/').ifBlank { name })
            }
        }
        if (done > 0) {
            onEntry(done, totalEntries.coerceAtLeast(done), "")
        }
    }

    /** Verify extracted files against ZIP CRC32. */
    private fun verifyExtracted(
        zipPath: Path,
        dest: Path,
        onEntry: (Int, Int, String) -> Unit = { _, _, _ -> },
    ) {
        val prefix = detectStripPrefix(zipPath)
        val entries = mutableListOf<Pair<String, Long>>()
        openZip(zipPath).use { zip ->
            val en = zip.entries()
            while (en.hasMoreElements()) {
                val e = en.nextElement()
                if (e.isDirectory) continue
                var name = e.name.replace('\\', '/').trimStart('/')
                if (prefix.isNotEmpty() && name.startsWith(prefix)) name = name.removePrefix(prefix)
                if (name.isBlank() || name.contains("..") || shouldSkipExtract(name)) continue
                entries += name to e.crc
            }
        }
        val total = entries.size
        var done = 0
        for ((rel, expectedCrc) in entries) {
            val file = safeResolveUnder(dest, rel) ?: continue
            if (!file.exists()) {
                error("После распаковки отсутствует файл: $rel")
            }
            if (expectedCrc >= 0L) {
                val actual = crc32Of(file)
                if (actual != expectedCrc) {
                    error("Повреждён файл $rel (CRC не совпал)")
                }
            }
            done++
            if (done % 10 == 0 || done == total) {
                onEntry(done, total, rel.substringAfterLast('/'))
            }
        }
        onEntry(done, total.coerceAtLeast(done), "")
    }

    private fun crc32Of(path: Path): Long {
        val crc = java.util.zip.CRC32()
        Files.newInputStream(path).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                crc.update(buf, 0, n)
            }
        }
        return crc.value
    }

    /** If the ZIP has a single top-level folder, strip it so mods/ land at pack root. */
    private fun detectStripPrefix(zipPath: Path): String {
        val tops = linkedSetOf<String>()
        runCatching {
            openZip(zipPath).use { zip ->
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
