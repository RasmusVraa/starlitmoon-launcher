package ru.starlitmoon.launcher.minecraft

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.starlitmoon.launcher.api.ModpackDto
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.name
import kotlin.io.path.writeText

/**
 * Per-file modpack install from a GitHub repo layout:
 *
 *   packs/{slug}/manifest.json
 *   packs/{slug}/mods/...
 *   packs/{slug}/config/...
 *
 * Raw URLs: raw.githubusercontent.com/{owner}/{repo}/{ref}/packs/{slug}/...
 */
object GithubModpackSync {
    private const val MARKER = ".starlit-archive.sha256"
    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val READ_TIMEOUT_MS = 90_000

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Manifest(
        val sha256: String? = null,
        val version: String? = null,
        val name: String? = null,
        /** vanilla / fabric / neoforge / forge — for bare or GitHub-only packs. */
        val loader: String? = null,
        val mcVersion: String? = null,
        val loaderVersion: String? = null,
        val files: List<ManifestFile> = emptyList(),
    )

    data class SyncResult(
        val applied: Boolean,
        val loader: String? = null,
        val mcVersion: String? = null,
        val loaderVersion: String? = null,
        val name: String? = null,
    )

    @Serializable
    data class ManifestFile(
        val path: String,
        val sha256: String? = null,
        val size: Long? = null,
        val url: String? = null,
    )

    data class GithubSource(
        val owner: String,
        val repo: String,
        val ref: String = "main",
    )

    fun manifestUrl(source: GithubSource, slug: String): String =
        "https://raw.githubusercontent.com/${source.owner}/${source.repo}/${source.ref}/packs/$slug/manifest.json"

    /** Cache-busted manifest URL — raw.githubusercontent.com often serves stale branch tips. */
    private fun manifestUrlFresh(source: GithubSource, slug: String): String =
        manifestUrl(source, slug) + "?t=${System.currentTimeMillis()}"

    /** Non-LFS files (configs, txt) live on raw; LFS jars need media. */
    fun fileUrlRaw(source: GithubSource, slug: String, relativePath: String): String {
        val encoded = encodeGithubPath(relativePath)
        return "https://raw.githubusercontent.com/${source.owner}/${source.repo}/${source.ref}/packs/$slug/$encoded"
    }

    fun fileUrlLfs(source: GithubSource, slug: String, relativePath: String): String {
        val encoded = encodeGithubPath(relativePath)
        return "https://media.githubusercontent.com/media/${source.owner}/${source.repo}/${source.ref}/packs/$slug/$encoded"
    }

    /** Encode each path segment (spaces, Cyrillic, % in names like dim%0). */
    private fun encodeGithubPath(relativePath: String): String =
        relativePath.trim().trimStart('/').split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { segment ->
                URLEncoder.encode(segment, StandardCharsets.UTF_8)
                    .replace("+", "%20")
            }

    @Deprecated("Use fileUrlRaw / fileUrlLfs", ReplaceWith("fileUrlLfs(source, slug, relativePath)"))
    fun fileUrl(source: GithubSource, slug: String, relativePath: String): String =
        fileUrlLfs(source, slug, relativePath)

    fun fetchManifest(source: GithubSource, slug: String, control: DownloadControl): Manifest {
        control.checkpoint()
        val url = manifestUrlFresh(source, slug)
        val conn = openGet(url)
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                error("Манифест GitHub недоступен (HTTP $code): $url")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return json.decodeFromString<Manifest>(body)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Sync pack files from GitHub. Empty [Manifest.files] is allowed (loader-only / bare pack).
     */
    fun sync(
        dataDir: Path,
        pack: ModpackDto,
        source: GithubSource,
        force: Boolean = false,
        control: DownloadControl = DownloadControl(),
        onProgress: (ProgressEvent) -> Unit = {},
    ): SyncResult {
        val slug = pack.slug?.trim()?.ifBlank { null }
            ?: pack.id?.trim()?.ifBlank { null }
            ?: return SyncResult(false)
        val safeSlug = slug.replace(Regex("[^a-zA-Z0-9._-]"), "_")

        onProgress(ProgressEvent("Загрузка манифеста GitHub…", 0.02f, kind = ProgressEvent.Kind.Download))
        val manifest = fetchManifest(source, safeSlug, control)
        val meta = SyncResult(
            applied = true,
            loader = manifest.loader?.trim()?.ifBlank { null },
            mcVersion = manifest.mcVersion?.trim()?.ifBlank { null },
            loaderVersion = manifest.loaderVersion?.trim()?.ifBlank { null },
            name = manifest.name?.trim()?.ifBlank { null },
        )
        val remoteHash = manifest.sha256?.trim()?.lowercase().orEmpty()
            .ifBlank { contentFingerprint(manifest) }

        val dir = ModpackSync.packDir(dataDir, pack)
        dir.createDirectories()
        val marker = dir.resolve(MARKER)

        if (!force && remoteHash.isNotBlank() && marker.exists() &&
            Files.readString(marker).trim().lowercase() == remoteHash
        ) {
            onProgress(ProgressEvent("Сборка уже актуальна", 1f))
            return meta
        }

        val files = manifest.files.map {
            it.copy(path = it.path.trim().trimStart('/').trimEnd('"').trim())
        }
            .filter { it.path.isNotBlank() && !it.path.contains("..") && !it.path.contains('\u0000') }
        val totalBytes = files.sumOf { it.size?.coerceAtLeast(0) ?: 0L }.takeIf { it > 0 }
        var doneBytes = 0L
        var filesDone = 0
        val filesTotal = files.size

        if (files.isEmpty()) {
            marker.writeText(remoteHash.ifBlank { "empty-loader-only" })
            onProgress(
                ProgressEvent(
                    "Файлов сборки нет — только Minecraft / лоадер",
                    1f,
                    filesDone = 0,
                    filesTotal = 0,
                ),
            )
            return meta
        }

        for (file in files) {
            control.checkpoint()
            val dest = dir.resolve(file.path).normalize()
            if (!dest.startsWith(dir)) error("Некорректный путь в манифесте: ${file.path}")
            dest.parent?.createDirectories()

            val expected = file.sha256?.trim()?.lowercase().orEmpty()
            if (!force && expected.isNotBlank() && dest.exists()) {
                if (sha256Matches(dest, expected)) {
                    filesDone++
                    doneBytes += file.size?.coerceAtLeast(0) ?: dest.fileSize()
                    onProgress(
                        ProgressEvent(
                            message = "Файл $filesDone/$filesTotal",
                            fraction = (0.05f + 0.90f * filesDone.toFloat() / filesTotal).coerceIn(0.05f, 0.95f),
                            bytesDone = doneBytes,
                            bytesTotal = totalBytes,
                            filesDone = filesDone,
                            filesTotal = filesTotal,
                            currentFile = file.path,
                            kind = ProgressEvent.Kind.Download,
                        ),
                    )
                    continue
                }
            }

            val customUrl = file.url?.trim()?.ifBlank { null }
            val rawUrl = fileUrlRaw(source, safeSlug, file.path)
            val lfsUrl = fileUrlLfs(source, safeSlug, file.path)
            downloadGithubFile(
                primaryUrl = customUrl ?: rawUrl,
                lfsUrl = if (customUrl == null) lfsUrl else null,
                dest = dest,
                expectedSha = expected.takeIf { it.isNotBlank() },
                expectedSize = file.size?.takeIf { it > 0 },
                control = control,
                onChunk = { n, fileDone, fileTotal ->
                    val overallDone = doneBytes + fileDone
                    val overallTotal = totalBytes ?: (doneBytes + (fileTotal ?: fileDone))
                    onProgress(
                        ProgressEvent(
                            message = "Скачивание ${file.path}",
                            fraction = (0.05f + 0.90f * (filesDone + if ((fileTotal ?: 0) > 0) fileDone.toFloat() / fileTotal!! else 0f) / filesTotal)
                                .coerceIn(0.05f, 0.95f),
                            bytesDone = overallDone,
                            bytesTotal = overallTotal.takeIf { it > 0 },
                            filesDone = filesDone,
                            filesTotal = filesTotal,
                            currentFile = file.path,
                            speedBps = null,
                            kind = ProgressEvent.Kind.Download,
                        ),
                    )
                },
            )
            filesDone++
            doneBytes += file.size?.takeIf { it > 0 } ?: dest.fileSize()
            onProgress(
                ProgressEvent(
                    message = "Файл $filesDone/$filesTotal",
                    fraction = (0.05f + 0.90f * filesDone.toFloat() / filesTotal).coerceIn(0.05f, 0.95f),
                    bytesDone = doneBytes,
                    bytesTotal = totalBytes,
                    filesDone = filesDone,
                    filesTotal = filesTotal,
                    currentFile = file.path,
                    kind = ProgressEvent.Kind.Download,
                ),
            )
        }

        onProgress(ProgressEvent("Проверка файлов…", 0.96f, kind = ProgressEvent.Kind.Verify))
        var verified = 0
        for (file in files) {
            control.checkpoint()
            val expected = file.sha256?.trim()?.lowercase().orEmpty()
            if (expected.isBlank()) {
                verified++
                continue
            }
            val dest = dir.resolve(file.path)
            if (!dest.exists() || !sha256Matches(dest, expected)) {
                error("Контрольная сумма не совпала: ${file.path}")
            }
            verified++
            onProgress(
                ProgressEvent(
                    message = "Проверка $verified/$filesTotal",
                    fraction = (0.96f + 0.03f * verified.toFloat() / filesTotal).coerceIn(0.96f, 0.99f),
                    filesDone = verified,
                    filesTotal = filesTotal,
                    currentFile = file.path,
                    kind = ProgressEvent.Kind.Verify,
                ),
            )
        }

        marker.writeText(remoteHash)
        onProgress(ProgressEvent("Сборка готова", 1f, filesDone = filesTotal, filesTotal = filesTotal))
        return meta
    }

    private fun contentFingerprint(manifest: Manifest): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (f in manifest.files.sortedBy { it.path }) {
            digest.update(f.path.toByteArray())
            digest.update((f.sha256 ?: "").toByteArray())
            digest.update((f.size ?: 0L).toString().toByteArray())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Download from [primaryUrl] (usually raw.githubusercontent.com). If the body is a Git LFS
     * pointer, re-fetch the real blob from [lfsUrl] (media.githubusercontent.com).
     */
    private fun downloadGithubFile(
        primaryUrl: String,
        lfsUrl: String?,
        dest: Path,
        expectedSha: String?,
        expectedSize: Long?,
        control: DownloadControl,
        onChunk: (n: Int, fileDone: Long, fileTotal: Long?) -> Unit,
    ) {
        downloadToPart(primaryUrl, dest, control, onChunk)
        val part = dest.resolveSibling(dest.name + ".part")
        if (lfsUrl != null && isGitLfsPointer(part)) {
            Files.deleteIfExists(part)
            downloadToPart(lfsUrl, dest, control, onChunk)
        }
        if (expectedSha != null) {
            if (!sha256Matches(part, expectedSha)) {
                Files.deleteIfExists(part)
                error("SHA-256 не совпал для ${dest.name}")
            }
        }
        if (expectedSize != null && part.exists()) {
            val size = part.fileSize()
            // Soft size check: text files may differ by CRLF vs LF vs manifest.
            if (size < 1024 && expectedSize > 4096) {
                Files.deleteIfExists(part)
                error("Скачан LFS-указатель вместо файла: ${dest.name} (нужен media.githubusercontent.com)")
            }
        }
        Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING)
    }

    /** Exact match, or match after CRLF↔LF (GitHub raw serves LF; Windows trees often CRLF). */
    private fun sha256Matches(path: Path, expected: String): Boolean {
        val want = expected.trim().lowercase()
        if (sha256Hex(path) == want) return true
        val bytes = Files.readAllBytes(path)
        if (bytes.isEmpty() || bytes.size > 8 * 1024 * 1024) return false
        // Only rewrite newlines for small/text-ish payloads (not jar/zip magic).
        if (bytes.size >= 4) {
            val b0 = bytes[0].toInt() and 0xff
            val b1 = bytes[1].toInt() and 0xff
            if (b0 == 0x50 && b1 == 0x4b) return false // PK zip/jar
            if (b0 == 0x1f && b1 == 0x8b) return false // gzip
        }
        val asLf = stripCr(bytes)
        if (asLf.size != bytes.size && sha256Bytes(asLf) == want) return true
        val asCrlf = lfToCrlf(bytes)
        if (asCrlf.size != bytes.size && sha256Bytes(asCrlf) == want) return true
        return false
    }

    private fun stripCr(bytes: ByteArray): ByteArray {
        var cr = 0
        for (b in bytes) if (b == '\r'.code.toByte()) cr++
        if (cr == 0) return bytes
        val out = ByteArray(bytes.size - cr)
        var j = 0
        for (b in bytes) if (b != '\r'.code.toByte()) out[j++] = b
        return out
    }

    private fun lfToCrlf(bytes: ByteArray): ByteArray {
        var lf = 0
        var i = 0
        while (i < bytes.size) {
            if (bytes[i] == '\n'.code.toByte() && (i == 0 || bytes[i - 1] != '\r'.code.toByte())) lf++
            i++
        }
        if (lf == 0) return bytes
        val out = ByteArray(bytes.size + lf)
        var j = 0
        i = 0
        while (i < bytes.size) {
            val b = bytes[i]
            if (b == '\n'.code.toByte() && (i == 0 || bytes[i - 1] != '\r'.code.toByte())) {
                out[j++] = '\r'.code.toByte()
            }
            out[j++] = b
            i++
        }
        return out
    }

    private fun sha256Bytes(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(bytes)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun downloadToPart(
        url: String,
        dest: Path,
        control: DownloadControl,
        onChunk: (n: Int, fileDone: Long, fileTotal: Long?) -> Unit,
    ) {
        val part = dest.resolveSibling(dest.name + ".part")
        Files.deleteIfExists(part)
        val conn = openGet(url)
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("Не удалось скачать $url (HTTP $code)")
            val total = conn.contentLengthLong.takeIf { it > 0 }
            var done = 0L
            BufferedInputStream(conn.inputStream).use { input ->
                Files.newOutputStream(part).use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        control.checkpoint()
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        done += n
                        control.throttle(n)
                        onChunk(n, done, total)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun isGitLfsPointer(path: Path): Boolean {
        if (!path.exists()) return false
        val size = path.fileSize()
        if (size <= 0L || size > 1024L) return false
        val head = Files.readString(path).trimStart()
        return head.startsWith("version https://git-lfs.github.com/spec/v1")
    }

    private fun openGet(url: String): HttpURLConnection {
        val conn = URI.create(url).toURL().openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.useCaches = false
        conn.defaultUseCaches = false
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "StarlitMoonLauncher")
        conn.setRequestProperty("Accept", "*/*")
        conn.setRequestProperty("Cache-Control", "no-cache")
        conn.setRequestProperty("Pragma", "no-cache")
        return conn
    }

    private fun sha256Hex(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
