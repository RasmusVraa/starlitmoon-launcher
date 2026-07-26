package ru.starlitmoon.launcher.update

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import ru.starlitmoon.launcher.LauncherConfig
import ru.starlitmoon.launcher.LauncherLog
import ru.starlitmoon.launcher.LauncherVersion
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.channels.UnresolvedAddressException
import java.util.concurrent.CancellationException

class UpdateChecker(
    private val configProvider: () -> LauncherConfig = { LauncherConfig.load() },
    private val currentVersion: String = LauncherVersion.CURRENT,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO) {
        expectSuccess = false
        followRedirects = true
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            // Without HttpTimeout, CIO reports connect_timeout=unknown and fails fast on flaky routes.
            connectTimeoutMillis = 30_000
            requestTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
        }
    }

    suspend fun checkForUpdate(): Result<UpdateInfo?> = runCatching {
        val config = configProvider()
        val owner = config.githubOwner.trim().ifBlank { "RasmusVraa" }
        val repo = config.githubRepo.trim().ifBlank { "starlitmoon-launcher" }

        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val response = getLatestRelease(url)
        if (response.status == HttpStatusCode.NotFound) {
            error("Репозиторий не найден: $owner/$repo")
        }
        if (response.status == HttpStatusCode.Forbidden || response.status.value == 429) {
            error("GitHub ограничил запросы (rate limit). Попробуйте позже.")
        }
        if (!response.status.isSuccess()) {
            error("GitHub API: ${response.status.value}")
        }

        val release = response.body<GitHubRelease>()
        val latest = normalizeVersion(release.tagName)
        val current = normalizeVersion(currentVersion)
        // Equal or newer local → no update. Only prompt when remote is strictly newer.
        if (!isNewer(latest, current)) {
            return@runCatching null
        }

        val pkg = pickPackage(release.assets)
            ?: return@runCatching UpdateInfo(
                currentVersion = current,
                latestVersion = latest,
                releaseNotes = release.body?.trim().orEmpty().ifBlank { release.name.orEmpty() },
                releasePageUrl = release.htmlUrl,
                packageUrl = null,
                packageName = null,
                packageKind = UpdatePackageKind.SETUP,
            )

        UpdateInfo(
            currentVersion = current,
            latestVersion = latest,
            releaseNotes = release.body?.trim().orEmpty().ifBlank { release.name.orEmpty() },
            releasePageUrl = release.htmlUrl,
            packageUrl = pkg.downloadUrl,
            packageName = pkg.name,
            packageKind = pkg.kind,
        )
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { err ->
            if (err is CancellationException) throw err
            Result.failure(Exception(friendlyNetworkError(err), err))
        },
    )

    private suspend fun getLatestRelease(url: String): HttpResponse {
        var lastError: Throwable? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return client.get(url) {
                    header("Accept", "application/vnd.github+json")
                    header("User-Agent", "StarlitMoon-Launcher/$currentVersion")
                    header("X-GitHub-Api-Version", "2022-11-28")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                val last = attempt == MAX_ATTEMPTS - 1
                if (last || !isTransientNetworkFailure(e)) throw e
                val backoffMs = BACKOFF_MS[attempt.coerceAtMost(BACKOFF_MS.lastIndex)]
                LauncherLog.warn(
                    "Update check attempt ${attempt + 1}/$MAX_ATTEMPTS failed (${e.message}); retry in ${backoffMs}ms",
                )
                delay(backoffMs)
            }
        }
        throw lastError ?: error("Не удалось проверить обновления")
    }

    fun close() = client.close()

    companion object {
        private const val MAX_ATTEMPTS = 3
        private val BACKOFF_MS = longArrayOf(1_500L, 3_500L, 7_000L)

        fun normalizeVersion(raw: String): String =
            raw.trim().removePrefix("v").removePrefix("V")

        fun isNewer(latest: String, current: String): Boolean {
            val latestParts = parseParts(latest)
            val currentParts = parseParts(current)
            val maxLen = maxOf(latestParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l != c) return l > c
            }
            return false
        }

        fun friendlyNetworkError(err: Throwable): String {
            val msg = err.message.orEmpty()
            val lower = msg.lowercase()
            return when {
                err is HttpRequestTimeoutException ||
                    err is SocketTimeoutException ||
                    lower.contains("timeout") ||
                    lower.contains("timed out") ->
                    "Не удалось связаться с GitHub (таймаут). Проверьте сеть и попробуйте позже."
                err is UnresolvedAddressException ||
                    lower.contains("unresolved") ||
                    lower.contains("unknown host") ->
                    "Не удалось разрешить api.github.com. Проверьте DNS/сеть."
                err is IOException ||
                    lower.contains("connection") ||
                    lower.contains("connect") ->
                    "Нет связи с GitHub API. Проверьте сеть и попробуйте позже."
                else -> msg.ifBlank { "Не удалось проверить обновления" }
            }
        }

        fun isTransientNetworkFailure(err: Throwable): Boolean {
            var cur: Throwable? = err
            while (cur != null) {
                when (cur) {
                    is HttpRequestTimeoutException,
                    is SocketTimeoutException,
                    is UnresolvedAddressException,
                    is IOException,
                    -> return true
                }
                val m = cur.message?.lowercase().orEmpty()
                if (m.contains("timeout") ||
                    m.contains("timed out") ||
                    m.contains("connection") ||
                    m.contains("connect") ||
                    m.contains("unresolved") ||
                    m.contains("reset") ||
                    m.contains("unreachable")
                ) {
                    return true
                }
                cur = cur.cause
            }
            return false
        }

        private fun parseParts(version: String): List<Int> =
            version.split('.', '-', '_')
                .mapNotNull { part -> part.filter(Char::isDigit).toIntOrNull() }
                .ifEmpty { listOf(0) }

        private data class Picked(val name: String, val downloadUrl: String, val kind: UpdatePackageKind)

        private fun pickPackage(assets: List<GitHubAsset>): Picked? {
            // Prefer ZIP: contents can be version-checked before apply (Setup metadata can lie).
            val zip = assets.firstOrNull { a ->
                a.name.endsWith(".zip", ignoreCase = true) &&
                    (a.name.contains("windows", ignoreCase = true) ||
                        a.name.contains("StarlitMoon", ignoreCase = true) ||
                        a.name.contains("launcher", ignoreCase = true))
            }
            if (zip != null) {
                return Picked(zip.name, zip.downloadUrl, UpdatePackageKind.ZIP)
            }
            val setup = assets.firstOrNull { a ->
                a.name.endsWith(".exe", ignoreCase = true) && a.name.contains("Setup", ignoreCase = true)
            }
            if (setup != null) {
                return Picked(setup.name, setup.downloadUrl, UpdatePackageKind.SETUP)
            }
            // Legacy Compose single-file EXE (StarlitMoonLauncher-X.Y.Z.exe, no "Setup").
            val anyExe = assets.firstOrNull { it.name.endsWith(".exe", ignoreCase = true) } ?: return null
            return Picked(anyExe.name, anyExe.downloadUrl, UpdatePackageKind.SETUP)
        }
    }
}
