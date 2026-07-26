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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
    private const val CONNECT_TIMEOUT_MS = 45_000
    private const val READ_TIMEOUT_MS = 120_000
    private const val PARALLEL_MIN = 1
    private const val PARALLEL_MAX = 10
    private const val PARALLEL_START = 3
    private const val DOWNLOAD_RETRIES = 5
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
        // Completed pack content only (not LFS pointers / retries).
        val completedBytes = AtomicLong(0L)
        // Bytes of in-flight final downloads (excludes discarded LFS pointers).
        val inFlightBytes = AtomicLong(0L)
        val filesDone = AtomicInteger(0)
        val filesTotal = files.size
        val progressLock = Any()
        val firstError = AtomicReference<Throwable?>(null)
        val speedTracker = SpeedTracker()
        val lastProgressAtNs = AtomicLong(0L)
        val targetParallel = AtomicInteger(PARALLEL_START.coerceAtMost(filesTotal.coerceAtLeast(1)))
        val activeWorkers = AtomicInteger(0)

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

        fun displayedBytes(): Long {
            val raw = completedBytes.get() + inFlightBytes.get()
            return totalBytes?.let { raw.coerceAtMost(it) } ?: raw
        }

        fun emit(
            message: String,
            currentFile: String? = null,
            speed: Long? = null,
            kind: ProgressEvent.Kind = ProgressEvent.Kind.Download,
        ) {
            val done = filesDone.get()
            onProgress(
                ProgressEvent(
                    message = message,
                    fraction = (0.05f + 0.90f * done.toFloat() / filesTotal).coerceIn(0.05f, 0.95f),
                    bytesDone = displayedBytes(),
                    bytesTotal = totalBytes,
                    filesDone = done,
                    filesTotal = filesTotal,
                    currentFile = currentFile,
                    speedBps = speed,
                    threads = targetParallel.get(),
                    kind = kind,
                ),
            )
        }

        fun adjustParallelism(onNetworkError: Boolean = false) {
            val cur = targetParallel.get()
            if (onNetworkError) {
                targetParallel.set((cur - 1).coerceAtLeast(PARALLEL_MIN))
                return
            }
            val speed = speedTracker.current()
            if (speed <= 0L) return
            val next = when {
                speed >= 2_500_000L && cur < PARALLEL_MAX -> cur + 1
                speed >= 1_000_000L && cur < PARALLEL_MAX -> cur + 1
                speed <= 80_000L && cur > PARALLEL_MIN -> cur - 1
                speed <= 250_000L && cur > PARALLEL_MIN + 1 -> cur - 1
                else -> cur
            }.coerceIn(PARALLEL_MIN, PARALLEL_MAX.coerceAtMost(filesTotal.coerceAtLeast(1)))
            targetParallel.set(next)
        }

        val pending = ConcurrentLinkedQueue(files)
        val pool = Executors.newFixedThreadPool(PARALLEL_MAX) {
            Thread(it, "gh-modpack-dl").apply { isDaemon = true }
        }

        fun pump() {
            while (firstError.get() == null) {
                while (true) {
                    val active = activeWorkers.get()
                    val target = targetParallel.get()
                    if (active >= target) return
                    if (activeWorkers.compareAndSet(active, active + 1)) break
                }
                val file = pending.poll()
                if (file == null) {
                    activeWorkers.decrementAndGet()
                    return
                }
                pool.execute {
                    var fileInFlight = 0L
                    try {
                        if (firstError.get() != null) return@execute
                        control.checkpoint()
                        val dest = dir.resolve(file.path).normalize()
                        if (!dest.startsWith(dir)) error("Некорректный путь в манифесте: ${file.path}")
                        dest.parent?.createDirectories()

                        val expectedSize = file.size?.takeIf { it > 0 }
                        if (!force && dest.exists() && dest.fileSize() > 0L &&
                            (expectedSize == null || dest.fileSize() == expectedSize)
                        ) {
                            filesDone.incrementAndGet()
                            completedBytes.addAndGet(expectedSize ?: dest.fileSize())
                            synchronized(progressLock) {
                                emit("Файл ${filesDone.get()}/$filesTotal", file.path, speedTracker.current())
                            }
                            return@execute
                        }

                        val customUrl = file.url?.trim()?.ifBlank { null }
                        var attempt = 0
                        while (true) {
                            try {
                                // Clear any leftover in-flight from a failed attempt.
                                if (fileInFlight != 0L) {
                                    inFlightBytes.addAndGet(-fileInFlight)
                                    fileInFlight = 0L
                                }
                                downloadGithubFile(
                                    primaryUrl = customUrl ?: fileUrlRaw(source, safeSlug, file.path),
                                    lfsUrl = if (customUrl == null) fileUrlLfs(source, safeSlug, file.path) else null,
                                    dest = dest,
                                    expectedSize = expectedSize,
                                    control = control,
                                    onChunk = { n, _, _ ->
                                        speedTracker.onBytes(n)
                                    },
                                    onProgressBytes = { delta ->
                                        if (delta != 0L) {
                                            fileInFlight += delta
                                            inFlightBytes.addAndGet(delta)
                                        }
                                        val speed = speedTracker.current()
                                        val now = System.nanoTime()
                                        val prev = lastProgressAtNs.get()
                                        if (now - prev >= 200_000_000L && lastProgressAtNs.compareAndSet(prev, now)) {
                                            synchronized(progressLock) {
                                                onProgress(
                                                    ProgressEvent(
                                                        message = "Скачивание ${file.path}",
                                                        fraction = (0.05f + 0.90f * filesDone.get().toFloat() / filesTotal)
                                                            .coerceIn(0.05f, 0.95f),
                                                        bytesDone = displayedBytes(),
                                                        bytesTotal = totalBytes,
                                                        filesDone = filesDone.get(),
                                                        filesTotal = filesTotal,
                                                        currentFile = file.path,
                                                        speedBps = speed.takeIf { it > 0 },
                                                        threads = targetParallel.get(),
                                                        kind = ProgressEvent.Kind.Download,
                                                    ),
                                                )
                                            }
                                        }
                                    },
                                )
                                break
                            } catch (e: DownloadCancelledException) {
                                throw e
                            } catch (e: Exception) {
                                if (!isTransientNetworkError(e) || attempt >= DOWNLOAD_RETRIES - 1) throw e
                                attempt++
                                adjustParallelism(onNetworkError = true)
                                synchronized(progressLock) {
                                    onProgress(
                                        ProgressEvent(
                                            message = "Повтор $attempt/$DOWNLOAD_RETRIES: ${file.path}",
                                            fraction = (0.05f + 0.90f * filesDone.get().toFloat() / filesTotal)
                                                .coerceIn(0.05f, 0.95f),
                                            bytesDone = displayedBytes(),
                                            bytesTotal = totalBytes,
                                            filesDone = filesDone.get(),
                                            filesTotal = filesTotal,
                                            currentFile = file.path,
                                            threads = targetParallel.get(),
                                            kind = ProgressEvent.Kind.Download,
                                        ),
                                    )
                                }
                                control.checkpoint()
                                Thread.sleep((400L * attempt).coerceAtMost(3_000L))
                            }
                        }
                        // Commit: replace in-flight with manifest/actual size.
                        if (fileInFlight != 0L) {
                            inFlightBytes.addAndGet(-fileInFlight)
                            fileInFlight = 0L
                        }
                        completedBytes.addAndGet(expectedSize ?: dest.fileSize())
                        filesDone.incrementAndGet()
                        adjustParallelism()
                        synchronized(progressLock) {
                            emit("Файл ${filesDone.get()}/$filesTotal", file.path, speedTracker.current())
                        }
                    } catch (e: DownloadCancelledException) {
                        firstError.compareAndSet(null, e)
                    } catch (e: Exception) {
                        firstError.compareAndSet(null, e)
                    } finally {
                        if (fileInFlight != 0L) {
                            inFlightBytes.addAndGet(-fileInFlight)
                            fileInFlight = 0L
                        }
                        activeWorkers.decrementAndGet()
                        pump()
                    }
                }
            }
        }

        try {
            repeat(PARALLEL_START.coerceAtMost(filesTotal)) { pump() }
            while (filesDone.get() < filesTotal && firstError.get() == null) {
                pump()
                Thread.sleep(50)
            }
            // Drain stragglers
            while (activeWorkers.get() > 0 && firstError.get() == null) {
                Thread.sleep(50)
            }
        } finally {
            pool.shutdownNow()
            pool.awaitTermination(30, TimeUnit.SECONDS)
        }

        firstError.get()?.let { throw it }

        marker.writeText(remoteHash)
        onProgress(
            ProgressEvent(
                "Сборка готова",
                1f,
                bytesDone = totalBytes ?: completedBytes.get(),
                bytesTotal = totalBytes,
                filesDone = filesTotal,
                filesTotal = filesTotal,
            ),
        )
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
     * Download from [primaryUrl]. If body is a Git LFS pointer, re-fetch from [lfsUrl].
     * [onProgressBytes] receives byte deltas for UI size (negative when pointer bytes are discarded).
     */
    private fun downloadGithubFile(
        primaryUrl: String,
        lfsUrl: String?,
        dest: Path,
        expectedSize: Long?,
        control: DownloadControl,
        onChunk: (n: Int, fileDone: Long, fileTotal: Long?) -> Unit,
        onProgressBytes: (delta: Long) -> Unit,
    ) {
        var counted = 0L
        downloadToPart(primaryUrl, dest, control, onChunk, onProgressBytes = { d ->
            counted += d
            onProgressBytes(d)
        })
        val part = dest.resolveSibling(dest.name + ".part")
        if (lfsUrl != null && isGitLfsPointer(part)) {
            // Discard pointer bytes from the size counter, then download the real blob.
            if (counted != 0L) onProgressBytes(-counted)
            counted = 0L
            Files.deleteIfExists(part)
            downloadToPart(lfsUrl, dest, control, onChunk, onProgressBytes = { d ->
                counted += d
                onProgressBytes(d)
            })
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
        onProgressBytes: (delta: Long) -> Unit = {},
    ) {
        val part = dest.resolveSibling(dest.name + ".part")
        var lastError: Exception? = null
        repeat(DOWNLOAD_RETRIES) { attempt ->
            control.checkpoint()
            Files.deleteIfExists(part)
            var localCounted = 0L
            val conn = openGet(url)
            try {
                val code = conn.responseCode
                if (code == 429 || code == 502 || code == 503 || code == 504) {
                    error("HTTP $code (временная ошибка)")
                }
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
                            localCounted += n
                            control.throttle(n)
                            onProgressBytes(n.toLong())
                            onChunk(n, done, total)
                        }
                    }
                }
                return
            } catch (e: DownloadCancelledException) {
                if (localCounted != 0L) onProgressBytes(-localCounted)
                Files.deleteIfExists(part)
                throw e
            } catch (e: Exception) {
                if (localCounted != 0L) onProgressBytes(-localCounted)
                Files.deleteIfExists(part)
                lastError = e
                if (!isTransientNetworkError(e) || attempt >= DOWNLOAD_RETRIES - 1) throw e
                Thread.sleep((500L * (attempt + 1)).coerceAtMost(4_000L))
            } finally {
                conn.disconnect()
            }
        }
        throw lastError ?: IllegalStateException("Не удалось скачать $url")
    }

    private fun isTransientNetworkError(e: Throwable): Boolean {
        var t: Throwable? = e
        while (t != null) {
            when (t) {
                is java.net.SocketTimeoutException,
                is java.net.ConnectException,
                is java.net.NoRouteToHostException,
                is java.net.UnknownHostException,
                is java.net.HttpRetryException,
                is java.io.InterruptedIOException,
                -> return true
            }
            val msg = (t.message ?: "").lowercase()
            if (msg.contains("timed out") ||
                msg.contains("timeout") ||
                msg.contains("getsockopt") ||
                msg.contains("connection reset") ||
                msg.contains("connection refused") ||
                msg.contains("broken pipe") ||
                msg.contains("network is unreachable") ||
                msg.contains("software caused connection abort") ||
                msg.contains("http 429") ||
                msg.contains("http 502") ||
                msg.contains("http 503") ||
                msg.contains("http 504") ||
                msg.contains("временная ошибка")
            ) {
                return true
            }
            t = t.cause
        }
        return false
    }

    private fun isGitLfsPointer(path: Path): Boolean {
        if (!path.exists()) return false
        val size = path.fileSize()
        if (size <= 0L || size > 1024L) return false
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

        fun current(): Long = synchronized(this) { ema.toLong().coerceAtLeast(0L) }
    }
}
