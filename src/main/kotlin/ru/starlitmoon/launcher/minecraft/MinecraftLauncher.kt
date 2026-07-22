package ru.starlitmoon.launcher.minecraft

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.starlitmoon.launcher.LauncherConfig
import ru.starlitmoon.launcher.LauncherVersion
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

class MinecraftLauncher(
    private val config: LauncherConfig = LauncherConfig.load(),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 180_000
            connectTimeoutMillis = 20_000
            socketTimeoutMillis = 180_000
        }
    }

    private val javaRuntimes = JavaRuntimeManager(http, config.dataDir.resolve("runtime"), json)

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
        return manifest.versions.firstOrNull { it.type == "release" }?.id ?: "26.2"
    }

    suspend fun ensureVersion(preferred: String = "", onProgress: (String) -> Unit = {}): Result<Pair<String, VersionMeta>> =
        runCatching {
            onProgress("Определение версии…")
            val id = resolveClientVersion(preferred)
            val versionDir = config.versionsDir.resolve(id)
            val versionJson = versionDir.resolve("$id.json")
            versionDir.createDirectories()
            config.librariesDir.createDirectories()
            config.assetsDir.createDirectories()

            val versionMeta = if (versionJson.exists()) {
                json.decodeFromString<VersionMeta>(versionJson.readText())
            } else {
                onProgress("Список версий…")
                val manifest = loadManifest()
                val url = manifest.versions.firstOrNull { it.id == id }?.url
                    ?: error("Версия $id недоступна")
                onProgress("Метаданные $id…")
                val meta = json.decodeFromString<VersionMeta>(http.get(url).bodyAsText())
                versionJson.writeText(json.encodeToString(VersionMeta.serializer(), meta))
                meta
            }

            val nativesDir = versionDir.resolve("natives").apply { createDirectories() }
            val libs = versionMeta.libraries.filter { it.appliesToCurrentOs() }
            libs.forEachIndexed { index, library ->
                val artifact = library.downloads?.artifact ?: return@forEachIndexed
                val libPath = if (artifact.path.isNotBlank()) {
                    config.librariesDir.resolve(artifact.path)
                } else {
                    config.librariesDir.resolve(library.name.replace(':', File.separatorChar) + ".jar")
                }
                if (!libPath.exists()) {
                    onProgress("Библиотеки ${index + 1}/${libs.size}")
                    downloadBinary(artifact.url, libPath)
                }
                if (library.isNativesForCurrentOs()) {
                    extractNatives(libPath, nativesDir)
                }
            }

            val clientJar = versionDir.resolve("$id.jar")
            val clientUrl = versionMeta.downloads?.client?.url
                ?: error("Нет ссылки на клиент")
            if (!clientJar.exists()) {
                onProgress("Скачивание клиента…")
                downloadBinary(clientUrl, clientJar)
            }

            val assetIndex = versionMeta.assetIndex ?: error("Нет индекса ассетов")
            val assetIndexFile = config.assetsDir.resolve("indexes").resolve("${assetIndex.id}.json")
            assetIndexFile.parent?.createDirectories()
            if (!assetIndexFile.exists()) {
                onProgress("Индекс ресурсов…")
                assetIndexFile.writeText(http.get(assetIndex.url).bodyAsText())
            }
            downloadAssets(assetIndexFile, onProgress)

            id to versionMeta
        }

    suspend fun launch(username: String, preferredVersion: String = "", onProgress: (String) -> Unit = {}): LaunchResult {
        val prepared = ensureVersion(preferredVersion, onProgress)
        if (prepared.isFailure) {
            return LaunchResult(false, prepared.exceptionOrNull()?.message ?: "Ошибка подготовки")
        }
        val (id, versionMeta) = prepared.getOrThrow()

        val component = versionMeta.javaVersion?.component ?: "java-runtime-epsilon"
        val requiredMajor = versionMeta.javaVersion?.majorVersion ?: 25
        val javaPath = try {
            javaRuntimes.ensureRuntime(component, onProgress).toAbsolutePath().toString()
        } catch (e: Exception) {
            val fallback = findSystemJava()?.takeIf { javaMajorVersion(it) >= requiredMajor }
            fallback ?: return LaunchResult(
                false,
                "Нужна Java $requiredMajor+. Скачивание runtime не удалось: ${e.message ?: e}",
            )
        }

        val nativesDir = config.versionsDir.resolve(id).resolve("natives")
        val classpath = buildClasspath(id, versionMeta)
        val uuid = offlineUuid(username)
        val vars = mapOf(
            "auth_player_name" to username,
            "version_name" to id,
            "game_directory" to config.gameDir.toAbsolutePath().toString(),
            "assets_root" to config.assetsDir.toAbsolutePath().toString(),
            "assets_index_name" to (versionMeta.assetIndex?.id ?: ""),
            "auth_uuid" to uuid,
            "auth_access_token" to "0",
            "clientid" to "0",
            "auth_xuid" to "0",
            "user_type" to "legacy",
            "version_type" to versionMeta.type,
            "natives_directory" to nativesDir.toAbsolutePath().toString(),
            "launcher_name" to "starlitmoon",
            "launcher_version" to LauncherVersion.CURRENT,
            "classpath" to classpath,
            "classpath_separator" to File.pathSeparator,
        )

        val jvmArgs = buildList {
            val fromMeta = resolveArgList(versionMeta.arguments?.resolvedJvm(), vars)
                .filterNot { it.startsWith("-Xms") || it.startsWith("-Xmx") }
            addAll(fromMeta)
            if (none { it.startsWith("-Djava.library.path") }) {
                add("-Djava.library.path=${nativesDir.toAbsolutePath()}")
            }
            // Config memory wins over Mojang defaults.
            add("-Xms${config.minMemoryMb}M")
            add("-Xmx${config.maxMemoryMb}M")
        }

        val gameArgs = buildList {
            val fromMeta = resolveArgList(versionMeta.arguments?.game, vars)
            if (fromMeta.isNotEmpty()) {
                addAll(fromMeta)
            } else {
                addAll(
                    listOf(
                        "--username", username,
                        "--version", id,
                        "--gameDir", vars.getValue("game_directory"),
                        "--assetsDir", vars.getValue("assets_root"),
                        "--assetIndex", vars.getValue("assets_index_name"),
                        "--uuid", uuid,
                        "--accessToken", "0",
                        "--userType", "legacy",
                        "--versionType", versionMeta.type,
                    ),
                )
            }
            if (none { it == "--quickPlayMultiplayer" || it.startsWith("--server") }) {
                add("--quickPlayMultiplayer")
                add("${config.serverHost}:25565")
            }
        }

        val command = buildList {
            add(javaPath)
            addAll(jvmArgs)
            if (jvmArgs.none { it == "-cp" || it == "-classpath" }) {
                add("-cp")
                add(classpath)
            }
            add(versionMeta.mainClass)
            addAll(gameArgs)
        }

        val logFile = config.dataDir.resolve("last-launch.log")
        return try {
            onProgress("Запуск Minecraft…")
            config.gameDir.createDirectories()
            val pb = ProcessBuilder(command)
                .directory(config.gameDir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile())
            val process = pb.start()
            Thread {
                Thread.sleep(4000)
                if (!process.isAlive) {
                    val tail = runCatching { logFile.readText().takeLast(1500) }.getOrDefault("")
                    // keep for diagnostics
                }
            }.start()
            LaunchResult(true, "Игра запущена", process)
        } catch (e: Exception) {
            LaunchResult(false, e.message ?: "Не удалось запустить")
        }
    }

    private suspend fun downloadAssets(indexFile: Path, onProgress: (String) -> Unit) {
        val root = json.parseToJsonElement(indexFile.readText()).jsonObject
        val objects = root["objects"]?.jsonObject ?: return
        val objectsDir = config.assetsDir.resolve("objects").apply { createDirectories() }
        val missing = objects.entries.mapNotNull { (_, meta) ->
            val hash = meta.jsonObject["hash"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val dest = objectsDir.resolve(hash.substring(0, 2)).resolve(hash)
            if (dest.exists()) null else hash to dest
        }
        if (missing.isEmpty()) return
        onProgress("Ресурсы 0/${missing.size}")
        val sem = Semaphore(12)
        coroutineScope {
            missing.mapIndexed { index, (hash, dest) ->
                async(Dispatchers.IO) {
                    sem.withPermit {
                        dest.parent?.createDirectories()
                        val url = "https://resources.download.minecraft.net/${hash.substring(0, 2)}/$hash"
                        runCatching { downloadBinary(url, dest) }
                        if ((index + 1) % 50 == 0) onProgress("Ресурсы ${index + 1}/${missing.size}")
                    }
                }
            }.awaitAll()
        }
        onProgress("Ресурсы готовы")
    }

    private fun resolveArgList(elements: List<JsonElement>?, vars: Map<String, String>): List<String> {
        if (elements == null) return emptyList()
        val out = mutableListOf<String>()
        for (el in elements) {
            when (el) {
                is JsonPrimitive -> out += substitute(el.content, vars)
                is JsonObject -> {
                    if (!rulesAllow(el["rules"])) continue
                    when (val value = el["value"]) {
                        is JsonPrimitive -> out += substitute(value.content, vars)
                        is JsonArray -> value.forEach { v ->
                            if (v is JsonPrimitive) out += substitute(v.content, vars)
                        }
                        else -> Unit
                    }
                }
                else -> Unit
            }
        }
        return out.filter { it.isNotBlank() && !it.contains("\${") }
    }

    private fun rulesAllow(rulesEl: JsonElement?): Boolean {
        if (rulesEl !is JsonArray || rulesEl.isEmpty()) return true
        var allowed = false
        for (rule in rulesEl) {
            val obj = rule.jsonObject
            val action = obj["action"]?.jsonPrimitive?.content ?: continue
            val os = obj["os"]?.jsonObject
            val osMatch = if (os == null) true else {
                val name = os["name"]?.jsonPrimitive?.content
                name == null || name == currentOsName()
            }
            val features = obj["features"]?.jsonObject
            val featuresOk = if (features == null) {
                true
            } else {
                features.entries.all { (k, v) ->
                    val enabled = v.jsonPrimitive.contentOrNull == "true" || v.toString() == "true"
                    when (k) {
                        "is_quick_play_multiplayer" -> enabled
                        "has_quick_plays_support" -> enabled
                        else -> !enabled
                    }
                }
            }
            if (osMatch && featuresOk) allowed = action == "allow"
        }
        return allowed
    }

    private fun substitute(template: String, vars: Map<String, String>): String {
        var s = template
        vars.forEach { (k, v) -> s = s.replace("\${$k}", v) }
        return s
    }

    private fun extractNatives(jar: Path, nativesDir: Path) {
        runCatching {
            ZipFile(jar.toFile()).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    if (entry.isDirectory) return@forEach
                    val name = entry.name.substringAfterLast('/')
                    if (name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib") || name.endsWith(".jnilib")) {
                        val out = nativesDir.resolve(name)
                        if (!out.exists()) {
                            zip.getInputStream(entry).use { input ->
                                Files.copy(input, out)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun loadManifest(): VersionManifest =
        json.decodeFromString(http.get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").bodyAsText())

    private suspend fun downloadBinary(url: String, target: Path) {
        target.parent?.createDirectories()
        target.writeBytes(http.get(url).body())
    }

    private fun buildClasspath(versionId: String, meta: VersionMeta): String {
        val paths = buildList {
            for (library in meta.libraries) {
                if (!library.appliesToCurrentOs()) continue
                // LWJGL 3 natives jars must stay on the classpath; DLLs are also extracted to natives/.
                val artifact = library.downloads?.artifact ?: continue
                if (artifact.path.isBlank()) continue
                add(config.librariesDir.resolve(artifact.path))
            }
            add(config.versionsDir.resolve(versionId).resolve("$versionId.jar"))
        }
        return paths.joinToString(File.pathSeparator) { it.toAbsolutePath().toString() }
    }

    private fun javaMajorVersion(javaBin: String): Int {
        return runCatching {
            val p = ProcessBuilder(javaBin, "-version").redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            val m = Regex("""version \"(\d+)(?:\.(\d+))?""").find(out)
                ?: Regex("""version \"1\.(\d+)""").find(out)
            val major = m?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            if (major == 1) m?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0 else major
        }.getOrDefault(0)
    }

    private fun findSystemJava(): String? {
        if (config.javaPath.isNotBlank() && File(config.javaPath).exists()) return config.javaPath
        val javaHome = System.getenv("JAVA_HOME")
        if (!javaHome.isNullOrBlank()) {
            val name = if (isWindows()) "java.exe" else "java"
            val candidate = Path.of(javaHome, "bin", name)
            if (candidate.toFile().exists()) return candidate.toString()
        }
        return runCatching {
            val p = ProcessBuilder(if (isWindows()) listOf("where", "java") else listOf("which", "java")).start()
            p.waitFor()
            p.inputStream.bufferedReader().readLine()?.trim()?.takeIf { it.isNotBlank() }
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
data class VersionManifest(val latest: LatestVersions? = null, val versions: List<VersionRef> = emptyList())

@Serializable
data class LatestVersions(val release: String? = null, val snapshot: String? = null)

@Serializable
data class VersionRef(val id: String, val url: String, val type: String = "release")

@Serializable
data class VersionMeta(
    val id: String,
    val type: String = "release",
    @SerialName("mainClass") val mainClass: String,
    val libraries: List<Library> = emptyList(),
    val downloads: ClientDownloads? = null,
    @SerialName("assetIndex") val assetIndex: AssetIndex? = null,
    val arguments: VersionArguments? = null,
    val javaVersion: JavaVersionInfo? = null,
)

@Serializable
data class VersionArguments(
    val jvm: List<JsonElement> = emptyList(),
    @SerialName("default-user-jvm") val defaultUserJvm: List<JsonElement> = emptyList(),
    val game: List<JsonElement> = emptyList(),
) {
    fun resolvedJvm(): List<JsonElement> = if (jvm.isNotEmpty()) jvm else defaultUserJvm
}

@Serializable
data class JavaVersionInfo(
    val component: String? = null,
    val majorVersion: Int? = null,
)

@Serializable
data class ClientDownloads(val client: DownloadArtifact? = null)

@Serializable
data class AssetIndex(val id: String, val url: String)

@Serializable
data class Library(
    val name: String,
    val downloads: LibraryDownloads? = null,
    val rules: List<LibraryRule>? = null,
)

@Serializable
data class LibraryDownloads(val artifact: DownloadArtifact? = null)

@Serializable
data class DownloadArtifact(val path: String = "", val url: String)

@Serializable
data class LibraryRule(val action: String, val os: LibraryOs? = null)

@Serializable
data class LibraryOs(val name: String? = null)

private fun Library.appliesToCurrentOs(): Boolean {
    val rules = rules ?: return true
    var allowed = false
    for (rule in rules) {
        val osMatch = rule.os?.name?.let { it == currentOsName() } ?: true
        if (osMatch) allowed = rule.action == "allow"
    }
    return allowed
}

private fun Library.isNativesForCurrentOs(): Boolean {
    val n = name.lowercase()
    return when (currentOsName()) {
        "windows" -> n.contains("natives-windows")
        "osx" -> n.contains("natives-macos") || n.contains("natives-osx")
        else -> n.contains("natives-linux")
    }
}

private fun currentOsName(): String = when {
    System.getProperty("os.name").lowercase().contains("win") -> "windows"
    System.getProperty("os.name").lowercase().contains("mac") -> "osx"
    else -> "linux"
}

private fun isWindows() = System.getProperty("os.name").lowercase().contains("win")
