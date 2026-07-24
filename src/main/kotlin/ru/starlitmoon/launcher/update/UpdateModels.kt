package ru.starlitmoon.launcher.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val name: String? = null,
    val body: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
)

enum class UpdatePackageKind {
    /** App folder ZIP — in-place file replace + restart (preferred). */
    ZIP,
    /** Legacy Inno Setup silent install. */
    SETUP,
}

data class UpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val releaseNotes: String,
    val releasePageUrl: String,
    val packageUrl: String?,
    val packageName: String?,
    val packageKind: UpdatePackageKind,
) {
    /** @deprecated use [packageUrl] */
    val installerUrl: String? get() = packageUrl
    /** @deprecated use [packageName] */
    val installerName: String? get() = packageName
}
