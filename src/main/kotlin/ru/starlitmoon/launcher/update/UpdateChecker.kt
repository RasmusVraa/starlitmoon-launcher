package ru.starlitmoon.launcher.update

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import ru.starlitmoon.launcher.LauncherConfig
import ru.starlitmoon.launcher.LauncherVersion

class UpdateChecker(
    private val config: LauncherConfig = LauncherConfig.load(),
    private val currentVersion: String = LauncherVersion.CURRENT,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
    }

    suspend fun checkForUpdate(): Result<UpdateInfo?> = runCatching {
        if (config.githubOwner.isBlank() || config.githubRepo.isBlank()) return@runCatching null

        val url = "https://api.github.com/repos/${config.githubOwner}/${config.githubRepo}/releases/latest"
        val response = client.get(url) {
            header("Accept", "application/vnd.github+json")
            header("User-Agent", "StarlitMoon-Launcher/$currentVersion")
        }
        if (response.status == HttpStatusCode.NotFound) return@runCatching null
        if (!response.status.isSuccess()) {
            error("GitHub API: ${response.status.value}")
        }

        val release = response.body<GitHubRelease>()
        val latest = normalizeVersion(release.tagName)
        val current = normalizeVersion(currentVersion)
        if (!isNewer(latest, current)) return@runCatching null

        val installer = release.assets.firstOrNull { asset ->
            asset.name.endsWith(".exe", ignoreCase = true) &&
                (asset.name.contains("launcher", ignoreCase = true) || asset.name.contains("StarlitMoon", ignoreCase = true))
        } ?: release.assets.firstOrNull { it.name.endsWith(".exe", ignoreCase = true) }

        UpdateInfo(
            currentVersion = current,
            latestVersion = latest,
            releaseNotes = release.body?.trim().orEmpty().ifBlank { release.name.orEmpty() },
            releasePageUrl = release.htmlUrl,
            installerUrl = installer?.downloadUrl,
            installerName = installer?.name,
        )
    }

    fun close() = client.close()

    companion object {
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

        private fun parseParts(version: String): List<Int> =
            version.split('.', '-', '_')
                .mapNotNull { part -> part.filter(Char::isDigit).toIntOrNull() }
                .ifEmpty { listOf(0) }
    }
}
