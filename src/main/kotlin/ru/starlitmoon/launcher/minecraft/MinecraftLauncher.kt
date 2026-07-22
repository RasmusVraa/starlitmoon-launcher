package ru.starlitmoon.launcher.minecraft

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
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
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 120_000
        }
    }

    data class LaunchResult(
        val success: Boolean,
        val message: String,
        val process: Process? = null,
    )

    suspend fun resolveClientVersion(preferred: String = ""): String {
        val manifest = loadManifest()
        val candidates = listOf(
            preferred,
            config.minecraftVersionId,
            config.defaultMcVersion,
            manifest.latest?.release.orEmpty(),
        ).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        for (id in candidates) {
            if (manifest.versions.any { it.id == id }) return id
        }
        return manifest.versions.firstOrNull { it.type == "release" }?.id
            ?: candidates.firstOrNull()
            ?: "26.2"
    }

    suspend fun ensureVersion(preferred: String = "", onProgress: (String) -> Unit = {}): Result<Unit> = runCatching {
        onProgress("Определение версии…")
        val id = resolveClientVersion(preferred)
        val versionDir = config.versionsDir.resolve(id)
        val versionJson = versionDir.resolve("$id.json")
        if (versionJson.exists() && versionDir.resolve("$id.jar").exists()) {
            onProgress("Клиент $id готов")
            return@runCatching
        }

        versionDir.createDirectories()
        config.librariesDir.createDirectories()
        config.assetsDir.createDirectories()

        onProgress("Загрузка списка версий…")
        val manifest = loadManifest()
        val versionUrl = manifest.versions.firstOrNull { it.id == id }?.url
            ?: error("Версия $id недоступна")

        onProgress("Метаданные $id…")
        val versionMeta = json.decodeFromString<VersionMeta>(http.get(versionUrl).bodyAsText())
        versionJson.writeText(json.encodeToString(VersionMeta.serializer(), versionMeta))

        val libs = versionMeta.libraries.filter { it.appliesToCurrentOs() && it.downloads?.artifact != null }
        libs.forEachIndexed { index, library ->
            val artifact = library.downloads!!.artifact!!
            val libPath = config.librariesDir.resolve(artifact.path)
            if (!libPath.exists()) {
                onProgress("Библиотеки ${index + 1}/${libs.size}")
                downloadBinary(artifact.url, libPath)
            }
        }

        val clientJar = versionDir.resolve("$id.jar")
        val clientUrl = versionMeta.downloads?.client?.url
            ?: error("В метаданных нет ссылки на клиент")
        if (!clientJar.exists()) {
            onProgress("Скачивание клиента $id…")
            downloadBinary(clientUrl, clientJar)
        }

        val assetIndex = versionMeta.assetIndex
            ?: error("Нет индекса ассетов")
        val assetIndexFile = config.assetsDir.resolve("indexes").resolve("${assetIndex.id}.json")
        assetIndexFile.parent?.createDirectories()
        if (!assetIndexFile.exists()) {
            onProgress("Индекс ресурсов…")
            assetIndexFile.writeText(http.get(assetIndex.url).bodyAsText())
        }
        onProgress("Готово")
    }

    suspend fun launch(username: String, preferredVersion: String = "", onProgress: (String) -> Unit = {}): LaunchResult {
        val prepare = ensureVersion(preferredVersion, onProgress)
        if (prepare.isFailure) {
            return LaunchResult(false, prepare.exceptionOrNull()?.message ?: "Ошибка загрузки версии")
        }
        val id = resolveClientVersion(preferredVersion)

        val java = findJava() ?: return LaunchResult(
            false,
            "Java не найдена. Укажите путь в настройках или установите JDK 17+",
        )

        val versionJson = config.versionsDir.resolve(id).resolve("$id.json")
        val versionMeta = json.decodeFromString<VersionMeta>(versionJson.readText())
        val assetIndexId = versionMeta.assetIndex?.id ?: return LaunchResult(false, "Нет индекса ассетов")
        val classpath = buildClasspath(id, versionMeta)
        val uuid = offlineUuid(username)
        val gameDir = config.gameDir.toAbsolutePath().toString()
        val assetsDir = config.assetsDir.toAbsolutePath().toString()
        val nativesDir = config.versionsDir.resolve(id).resolve("natives").apply { createDirectories() }

        val jvmArgs = listOf(
            "-Xms${config.minMemoryMb}M",
            "-Xmx${config.maxMemoryMb}M",
            "-Djava.library.path=${nativesDir.toAbsolutePath()}",
            "-Dminecraft.launcher.brand=starlitmoon",
            "-Dminecraft.launcher.version=${ru.starlitmoon.launcher.LauncherVersion.CURRENT}",
        )

        val gameArgs = listOf(
            "--username", username,
            "--version", id,
            "--gameDir", gameDir,
            "--assetsDir", assetsDir,
            "--assetIndex", assetIndexId,
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
            onProgress("Запуск…")
            val process = ProcessBuilder(command)
                .directory(config.gameDir.toFile())
                .inheritIO()
                .start()
            LaunchResult(true, "Игра запущена", process)
        } catch (e: Exception) {
            LaunchResult(false, e.message ?: "Не удалось запустить игру")
        }
    }

    private suspend fun loadManifest(): VersionManifest {
        val text = http.get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").bodyAsText()
        return json.decodeFromString(text)
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
            val name = if (System.getProperty("os.name").contains("Windows")) "java.exe" else "java"
            val candidate = Path.of(javaHome, "bin", name)
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
    val latest: LatestVersions? = null,
    val versions: List<VersionRef> = emptyList(),
)

@Serializable
data class LatestVersions(
    val release: String? = null,
    val snapshot: String? = null,
)

@Serializable
data class VersionRef(
    val id: String,
    val url: String,
    val type: String = "release",
)

@Serializable
data class VersionMeta(
    val id: String,
    val type: String = "release",
    @SerialName("mainClass") val mainClass: String,
    val libraries: List<Library> = emptyList(),
    val downloads: ClientDownloads? = null,
    @SerialName("assetIndex") val assetIndex: AssetIndex? = null,
)

@Serializable
data class ClientDownloads(
    val client: DownloadArtifact? = null,
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
    val path: String = "",
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

private fun currentOsName(): String = when {
    System.getProperty("os.name").lowercase().contains("win") -> "windows"
    System.getProperty("os.name").lowercase().contains("mac") -> "osx"
    else -> "linux"
}
