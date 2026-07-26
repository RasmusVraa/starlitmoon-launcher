package ru.starlitmoon.launcher.minecraft

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.starlitmoon.launcher.api.ModpackDto
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
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
    /** Parallel file downloads (GitHub rate-limits gently; 8 is a good balance). */
    private const val PARALLELISM = 8
    private val LFS_POINTER_PREFIX =
        "version https://git-lfs.github.com/spec/v1".toByteArray(StandardCharsets.US_ASCII)

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
     * Downloads up to [PARALLELISM] files at once. SHA-256 is not verified (size used to skip
     * already-present files).
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
        val doneBytes = AtomicLong(0L)
        val filesDone = AtomicInteger(0)
        val filesTotal = files.size
        val progressLock = Any()
        val firstError = AtomicReference<Throwable?>(null)
        val speedTracker = SpeedTracker()
        val lastProgressAtNs = AtomicLong(0L)

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

        fun emit(
            message: String,
            currentFile: String? = null,
            speed: Long? = null,
            kind: ProgressEvent.Kind = ProgressEvent.Kind.Download,
        ) {
            val done = filesDone.get()
            val bytes = doneBytes.get()
            onProgress(
                ProgressEvent(
                    message = message,
                    fraction = (0.05f + 0.90f * done.toFloat() / filesTotal).coerceIn(0.05f, 0.95f),
                    bytesDone = bytes,
                    bytesTotal = totalBytes,
                    filesDone = done,
                    filesTotal = filesTotal,
                    currentFile = currentFile,
                    speedBps = speed,
                    threads = PARALLELISM,
                    kind = kind,
                ),
            )
        }

        val pool = Executors.newFixedThreadPool(PARALLELISM.coerceAtMost(filesTotal.coerceAtLeast(1))) {
            Thread(it, "gh-modpack-dl").apply { isDaemon = true }
        }
        val futures = ArrayList<Future<*>>(files.size)
        try {
            for (file in files) {
                futures += pool.submit {
                    if (firstError.get() != null) return@submit
                    try {
                        control.checkpoint()
                        val dest = dir.resolve(file.path).normalize()
                        if (!dest.startsWith(dir)) error("Некорректный путь в манифесте: ${file.path}")
                        dest.parent?.createDirectories()

                        val expectedSize = file.size?.takeIf { it > 0 }
                        if (!force && dest.exists() && dest.fileSize() > 0L &&
                            (expectedSize == null || dest.fileSize() == expectedSize)
                        ) {
                            filesDone.incrementAndGet()
                            doneBytes.addAndGet(expectedSize ?: dest.fileSize())
                            synchronized(progressLock) {
                                emit("Файл ${filesDone.get()}/$filesTotal", file.path, speedTracker.current())
                            }
                            return@submit
                        }

                        val customUrl = file.url?.trim()?.ifBlank { null }
                        val rawUrl = fileUrlRaw(source, safeSlug, file.path)
                        val lfsUrl = fileUrlLfs(source, safeSlug, file.path)
                        downloadGithubFile(
                            primaryUrl = customUrl ?: rawUrl,
                            lfsUrl = if (customUrl == null) lfsUrl else null,
                            dest = dest,
                            expectedSize = expectedSize,
                            control = control,
                            onChunk = { n, _, _ ->
                                doneBytes.addAndGet(n.toLong())
                                val speed = speedTracker.onBytes(n)
                                val now = System.nanoTime()
                                val prev = lastProgressAtNs.get()
                                val shouldEmit = now - prev >= 200_000_000L &&
                                    lastProgressAtNs.compareAndSet(prev, now)
                                if (shouldEmit) {
                                    synchronized(progressLock) {
                                        onProgress(
                                            ProgressEvent(
                                                message = "Скачивание ${file.path}",
                                                fraction = (0.05f + 0.90f * filesDone.get().toFloat() / filesTotal)
                                                    .coerceIn(0.05f, 0.95f),
                                                bytesDone = doneBytes.get(),
                                                bytesTotal = totalBytes,
                                                filesDone = filesDone.get(),
                                                filesTotal = filesTotal,
                                                currentFile = file.path,
                                                speedBps = speed,
                                                threads = PARALLELISM,
                                                kind = ProgressEvent.Kind.Download,
                                            ),
                                        )
                                    }
                                }
                            },
                        )
                        filesDone.incrementAndGet()
                        synchronized(progressLock) {
                            emit("Файл ${filesDone.get()}/$filesTotal", file.path, speedTracker.current())
                        }
                    } catch (e: DownloadCancelledException) {
                        firstError.compareAndSet(null, e)
                    } catch (e: Exception) {
                        firstError.compareAndSet(null, e)
                    }
                }
            }
            for (f in futures) {
                runCatching { f.get() }
            }
        } finally {
            pool.shutdownNow()
        }

        firstError.get()?.let { throw it }

        marker.writeText(remoteHash)
        onProgress(ProgressEvent("Сборка готова", 1f, filesDone = filesTotal, filesTotal = filesTotal))
        return meta
    }

    private fun contentFingerprint(manifest: Manifest): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
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
        if (expectedSize != null && part.exists()) {
            val size = part.fileSize()
            if (size < 1024 && expectedSize > 4096) {
                Files.deleteIfExists(part)
                error("Скачан LFS-указатель вместо файла: ${dest.name}")
            }
        }
        Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING)
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
        // Do not use Files.readString — small binaries throw MalformedInputException ("Input length = 1").
        val bytes = Files.readAllBytes(path)
        val prefix = LFS_POINTER_PREFIX
        if (bytes.size < prefix.size) return false
        for (i in prefix.indices) {
            if (bytes[i] != prefix[i]) return false
        }
        return true
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

    /** Thread-safe smoothed download speed. */
    private class SpeedTracker {
        private var windowBytes = 0L
        private var windowAtNs = System.nanoTime()
        private var ema = 0.0

        fun onBytes(n: Int): Long = synchronized(this) {
            if (n > 0) windowBytes += n
            val now = System.nanoTime()
            val dt = (now - windowAtNs) / 1_000_000_000.0
            if (dt >= 0.25 && windowBytes >= 16 * 1024) {
                val instant = windowBytes / dt
                ema = if (ema <= 0) instant else ema * 0.55 + instant * 0.45
                windowBytes = 0L
                windowAtNs = now
            }
            ema.toLong().coerceAtLeast(0L)
        }

        fun current(): Long = synchronized(this) { ema.toLong().coerceAtLeast(0L).takeIf { it > 0 } ?: 0L }
    }
}
