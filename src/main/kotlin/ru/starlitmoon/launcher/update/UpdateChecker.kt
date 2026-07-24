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
    private val configProvider: () -> LauncherConfig = { LauncherConfig.load() },
    private val currentVersion: String = LauncherVersion.CURRENT,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
    }

    suspend fun checkForUpdate(): Result<UpdateInfo?> = runCatching {
        val config = configProvider()
        val owner = config.githubOwner.trim().ifBlank { "RasmusVraa" }
        val repo = config.githubRepo.trim().ifBlank { "starlitmoon-launcher" }

        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val response = client.get(url) {
            header("Accept", "application/vnd.github+json")
            header("User-Agent", "StarlitMoon-Launcher/$currentVersion")
            header("X-GitHub-Api-Version", "2022-11-28")
        }
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
        if (!isNewer(latest, current)) return@runCatching null

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

        private data class Picked(val name: String, val downloadUrl: String, val kind: UpdatePackageKind)

        private fun pickPackage(assets: List<GitHubAsset>): Picked? {
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
            val anyExe = assets.firstOrNull { it.name.endsWith(".exe", ignoreCase = true) } ?: return null
            return Picked(anyExe.name, anyExe.downloadUrl, UpdatePackageKind.SETUP)
        }
    }
}
