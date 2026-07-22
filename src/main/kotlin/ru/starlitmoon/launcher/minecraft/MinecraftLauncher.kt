package ru.starlitmoon.launcher.minecraft

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.starlitmoon.launcher.LauncherConfig
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class MinecraftLauncher(
    private val config: LauncherConfig = LauncherConfig.load(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient(CIO)

    data class LaunchResult(
        val success: Boolean,
        val message: String,
        val process: Process? = null,
    )

    suspend fun ensureVersion(versionId: String): Result<Unit> = runCatching {
        val id = versionId.ifBlank { config.minecraftVersionId.ifBlank { config.defaultMcVersion } }
        val versionDir = config.versionsDir.resolve(id)
        val versionJson = versionDir.resolve("$id.json")
        if (versionJson.exists()) return@runCatching

        versionDir.createDirectories()
        config.librariesDir.createDirectories()
        config.assetsDir.createDirectories()

        val manifest = http.get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").body<VersionManifest>()
        val versionUrl = manifest.versions.firstOrNull { it.id == id }?.url
            ?: throw IllegalStateException(
                "Версия «$id» не найдена в Mojang manifest. " +
                    "Укажите корректный minecraftVersionId в ~/.starlitmoon-launcher/config.json",
            )

        val versionMeta = http.get(versionUrl).body<VersionMeta>()
        versionJson.writeText(json.encodeToString(VersionMeta.serializer(), versionMeta))

        for (library in versionMeta.libraries) {
            if (!library.appliesToCurrentOs()) continue
            val artifact = library.downloads?.artifact ?: continue
            val libPath = config.librariesDir.resolve(artifact.path)
            if (!libPath.exists()) {
                downloadBinary(artifact.url, libPath)
            }
        }

        val clientJar = versionDir.resolve("$id.jar")
        val clientDownload = versionMeta.downloads.client
        if (!clientJar.exists()) {
            downloadBinary(clientDownload.url, clientJar)
        }

        val assetIndex = versionMeta.assetIndex
        val assetIndexFile = config.assetsDir.resolve("indexes").resolve("${assetIndex.id}.json")
        assetIndexFile.parent?.createDirectories()
        if (!assetIndexFile.exists()) {
            assetIndexFile.writeText(http.get(assetIndex.url).bodyAsText())
        }
    }

    suspend fun launch(username: String, versionId: String): LaunchResult {
        val id = versionId.ifBlank { config.minecraftVersionId.ifBlank { config.defaultMcVersion } }
        ensureVersion(id).onFailure { return LaunchResult(false, it.message ?: "Ошибка загрузки версии") }

        val java = findJava() ?: return LaunchResult(
            false,
            "Java не найдена. Установите JDK 17+ или укажите javaPath в config.json",
        )

        val versionJson = config.versionsDir.resolve(id).resolve("$id.json")
        val versionMeta = json.decodeFromString<VersionMeta>(versionJson.readText())
        val classpath = buildClasspath(id, versionMeta)
        val uuid = offlineUuid(username)
        val gameDir = config.gameDir.toAbsolutePath().toString()
        val assetsDir = config.assetsDir.toAbsolutePath().toString()
        val nativesDir = config.versionsDir.resolve(id).resolve("natives").apply { createDirectories() }

        val jvmArgs = mutableListOf(
            "-Xms${config.minMemoryMb}M",
            "-Xmx${config.maxMemoryMb}M",
            "-Djava.library.path=${nativesDir.toAbsolutePath()}",
            "-Dminecraft.launcher.brand=starlitmoon",
            "-Dminecraft.launcher.version=1.0.0",
        )

        val gameArgs = mutableListOf(
            "--username", username,
            "--version", id,
            "--gameDir", gameDir,
            "--assetsDir", assetsDir,
            "--assetIndex", versionMeta.assetIndex.id,
            "--uuid", uuid,
            "--accessToken", "0",
            "--userType", "legacy",
            "--versionType", versionMeta.type,
            "--quickPlayMultiplayer", "${config.serverHost}:25565",
        )

        val command = buildList {
            add(java)
            addAll(jvmArgs)
            add("-cp")
            add(classpath)
            add(versionMeta.mainClass)
            addAll(gameArgs)
        }

        return try {
            val process = ProcessBuilder(command)
                .directory(config.gameDir.toFile())
                .inheritIO()
                .start()
            LaunchResult(true, "Minecraft запущен", process)
        } catch (e: Exception) {
            LaunchResult(false, e.message ?: "Не удалось запустить игру")
        }
    }

    private suspend fun downloadBinary(url: String, target: Path) {
        target.parent?.createDirectories()
        val bytes = http.get(url).body<ByteArray>()
        Files.write(target, bytes)
    }

    private fun buildClasspath(versionId: String, meta: VersionMeta): String {
        val paths = buildList {
            for (library in meta.libraries) {
                if (!library.appliesToCurrentOs()) continue
                val artifact = library.downloads?.artifact ?: continue
                add(config.librariesDir.resolve(artifact.path))
            }
            add(config.versionsDir.resolve(versionId).resolve("$versionId.jar"))
        }
        return paths.joinToString(File.pathSeparator) { it.toAbsolutePath().toString() }
    }

    private fun findJava(): String? {
        if (config.javaPath.isNotBlank()) {
            val file = File(config.javaPath)
            if (file.exists()) return config.javaPath
        }
        val javaHome = System.getenv("JAVA_HOME")
        if (!javaHome.isNullOrBlank()) {
            val candidate = Path.of(javaHome, "bin", if (System.getProperty("os.name").contains("Windows")) "java.exe" else "java")
            if (candidate.toFile().exists()) return candidate.toString()
        }
        return runCatching {
            val process = ProcessBuilder("where", "java").start()
            process.waitFor()
            if (process.exitValue() != 0) return null
            process.inputStream.bufferedReader().readLine()?.trim()?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun offlineUuid(name: String): String {
        val md = MessageDigest.getInstance("MD5")
        md.update("OfflinePlayer:$name".toByteArray(Charsets.UTF_8))
        val digest = md.digest()
        digest[6] = (digest[6].toInt() and 0x0f or 0x30).toByte()
        digest[8] = (digest[8].toInt() and 0x3f or 0x80).toByte()
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun close() = http.close()
}

@Serializable
data class VersionManifest(
    val versions: List<VersionRef> = emptyList(),
)

@Serializable
data class VersionRef(
    val id: String,
    val url: String,
)

@Serializable
data class VersionMeta(
    val id: String,
    val type: String = "release",
    @SerialName("mainClass") val mainClass: String,
    val libraries: List<Library> = emptyList(),
    val downloads: ClientDownloads,
    @SerialName("assetIndex") val assetIndex: AssetIndex,
)

@Serializable
data class ClientDownloads(
    val client: DownloadArtifact,
)

@Serializable
data class AssetIndex(
    val id: String,
    val url: String,
)

@Serializable
data class Library(
    val name: String,
    val downloads: LibraryDownloads? = null,
    val rules: List<LibraryRule>? = null,
)

@Serializable
data class LibraryDownloads(
    val artifact: DownloadArtifact? = null,
)

@Serializable
data class DownloadArtifact(
    val path: String,
    val url: String,
)

@Serializable
data class LibraryRule(
    val action: String,
    val os: LibraryOs? = null,
)

@Serializable
data class LibraryOs(
    val name: String? = null,
)

private fun Library.appliesToCurrentOs(): Boolean {
    val rules = rules ?: return true
    var allowed = false
    for (rule in rules) {
        val osMatch = rule.os?.name?.let { it == currentOsName() } ?: true
        if (osMatch) allowed = rule.action == "allow"
    }
    return allowed
}

private fun currentOsName(): String = when (System.getProperty("os.name").lowercase()) {
    "windows" -> "windows"
    "mac os x", "macos" -> "osx"
    else -> "linux"
}
