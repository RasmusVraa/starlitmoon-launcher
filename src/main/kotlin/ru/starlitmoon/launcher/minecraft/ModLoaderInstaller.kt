package ru.starlitmoon.launcher.minecraft

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.starlitmoon.launcher.LauncherConfig
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.writeText

/**
 * Installs Fabric / NeoForge version profiles into the shared game directory
 * so [MinecraftLauncher] can launch them instead of plain Mojang IDs.
 */
class ModLoaderInstaller(
    private val http: HttpClient,
    private val config: LauncherConfig,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    private val resolveJava: suspend (minMajor: Int, onProgress: (String, Float?) -> Unit) -> String,
) {
    suspend fun ensureLoaderProfile(
        loader: String,
        mcVersion: String,
        loaderVersion: String? = null,
        onProgress: (String, Float?) -> Unit = { _, _ -> },
    ): String {
        val mc = mcVersion.trim()
        require(mc.isNotEmpty()) { "Не указана версия Minecraft для loader" }
        return when (loader.trim().lowercase()) {
            "", "vanilla" -> mc
            "fabric" -> ensureFabric(mc, loaderVersion?.trim()?.ifBlank { null }, onProgress)
            "neoforge" -> ensureNeoForge(mc, loaderVersion?.trim()?.ifBlank { null }, onProgress)
            else -> error("Неизвестный loader: $loader (поддерживаются vanilla, fabric, neoforge)")
        }
    }

    private suspend fun ensureFabric(
        mcVersion: String,
        loaderVersion: String?,
        onProgress: (String, Float?) -> Unit,
    ): String {
        onProgress("Определение Fabric Loader…", 0.02f)
        val loaderVer = loaderVersion ?: withTimeout(45_000) {
            resolveLatestFabricLoader(mcVersion)
        }
        val id = "fabric-loader-$loaderVer-$mcVersion"
        val versionDir = config.versionsDir.resolve(id).apply { createDirectories() }
        val profileFile = versionDir.resolve("$id.json")
        if (profileFile.exists()) {
            onProgress("Fabric уже установлен ($id)", 0.05f)
            return id
        }
        onProgress("Скачивание профиля Fabric $loaderVer…", 0.03f)
        val url = "https://meta.fabricmc.net/v2/versions/loader/$mcVersion/$loaderVer/profile/json"
        val body = withTimeout(60_000) { http.get(url).bodyAsText() }
        if (body.isBlank() || !body.contains("mainClass")) {
            error("Fabric Meta не вернул профиль для $mcVersion / $loaderVer")
        }
        profileFile.writeText(body)
        onProgress("Профиль Fabric сохранён", 0.05f)
        return id
    }

    private suspend fun resolveLatestFabricLoader(mcVersion: String): String {
        val body = http.get("https://meta.fabricmc.net/v2/versions/loader/$mcVersion").bodyAsText()
        val arr = json.parseToJsonElement(body).jsonArray
        if (arr.isEmpty()) error("Нет Fabric Loader для Minecraft $mcVersion")
        val stable = arr.firstOrNull { el ->
            el.jsonObject["loader"]?.jsonObject?.get("stable")?.jsonPrimitive?.booleanOrNull == true
        }
        val pick = stable ?: arr.first()
        return pick.jsonObject["loader"]?.jsonObject?.get("version")?.jsonPrimitive?.contentOrNull
            ?: error("Не удалось прочитать версию Fabric Loader")
    }

    private suspend fun ensureNeoForge(
        mcVersion: String,
        loaderVersion: String?,
        onProgress: (String, Float?) -> Unit,
    ): String {
        onProgress("Определение NeoForge…", 0.02f)
        val nfVer = try {
            loaderVersion ?: withTimeout(45_000) {
                onProgress("Список версий NeoForge…", 0.025f)
                resolveLatestNeoForge(mcVersion)
            }
        } catch (e: Exception) {
            throw IllegalStateException(
                "Не удалось определить NeoForge для MC $mcVersion: ${e.message ?: e}. " +
                    "Проверьте доступ к maven.neoforged.net",
                e,
            )
        }
        onProgress("NeoForge $nfVer", 0.03f)
        val id = "neoforge-$nfVer"
        val profileFile = config.versionsDir.resolve(id).resolve("$id.json")
        if (profileFile.exists()) {
            onProgress("NeoForge уже установлен ($id)", 0.05f)
            return id
        }

        val cacheDir = config.dataDir.resolve("cache").apply { createDirectories() }
        val installer = cacheDir.resolve("neoforge-$nfVer-installer.jar")
        if (!installer.exists() || installer.fileSize() < 10_000L) {
            onProgress("Скачивание установщика NeoForge $nfVer…", 0.03f)
            val url =
                "https://maven.neoforged.net/releases/net/neoforged/neoforge/$nfVer/neoforge-$nfVer-installer.jar"
            val tmp = installer.resolveSibling("${installer.fileName}.part")
            runCatching { Files.deleteIfExists(tmp) }
            downloadFile(url, tmp) { read, total ->
                if (total != null && total > 0L) {
                    val pct = ((read * 100) / total).toInt().coerceIn(0, 99)
                    onProgress("Установщик NeoForge $pct%", 0.03f + 0.01f * read.toFloat() / total)
                } else if (read > 0L) {
                    onProgress("Установщик NeoForge (${formatBytes(read)})…", 0.035f)
                }
            }
            Files.move(tmp, installer, StandardCopyOption.REPLACE_EXISTING)
        }

        onProgress("Подготовка Java для установщика…", 0.04f)
        val javaBin = resolveJava(17) { msg, frac ->
            onProgress(msg, if (frac != null) 0.04f + frac * 0.01f else 0.04f)
        }

        onProgress("Установка NeoForge $nfVer в клиент…", 0.045f)
        config.gameDir.createDirectories()
        // Official installer refuses --installClient unless a vanilla launcher profile exists.
        ensureMinecraftLauncherProfiles(config.gameDir)
        val logFile = config.dataDir.resolve("neoforge-install.log")
        runCatching { Files.deleteIfExists(logFile) }
        val pb = ProcessBuilder(
            javaBin,
            "-jar",
            installer.toAbsolutePath().toString(),
            "--installClient",
            config.gameDir.toAbsolutePath().toString(),
        )
            .directory(config.gameDir.toFile())
            .redirectErrorStream(true)
            .redirectOutput(logFile.toFile())
            // Prevent installer from blocking on stdin / console prompts.
            .redirectInput(ProcessBuilder.Redirect.PIPE)

        val code = withContext(Dispatchers.IO) {
            val process = pb.start()
            runCatching { process.outputStream.close() }
            val finished = process.waitFor(12, TimeUnit.MINUTES)
            if (!finished) {
                process.destroyForcibly()
                process.waitFor(15, TimeUnit.SECONDS)
                error(
                    "Установка NeoForge $nfVer зависла (>12 мин). См. ${logFile.toAbsolutePath()}",
                )
            }
            process.exitValue()
        }
        if (code != 0 || !profileFile.exists()) {
            val tip = runCatching {
                logFile.toFile().readLines()
                    .filter { line ->
                        val l = line.lowercase()
                        l.contains("error") || l.contains("exception") ||
                            l.contains("no minecraft launcher profile") ||
                            l.contains("failed") || l.contains("не")
                    }
                    .takeLast(8)
                    .joinToString(" ")
                    .ifBlank { logFile.toFile().readText().takeLast(500) }
            }.getOrNull().orEmpty()
            error("Установка NeoForge $nfVer не удалась (код $code). $tip")
        }
        onProgress("NeoForge установлен ($id)", 0.05f)
        return id
    }

    /**
     * NeoForge client installer checks for `launcher_profiles.json` (or the Microsoft Store
     * variant) and aborts with "you need to run the launcher first" otherwise.
     */
    private fun ensureMinecraftLauncherProfiles(gameDir: java.nio.file.Path) {
        val profiles = gameDir.resolve("launcher_profiles.json")
        val msStore = gameDir.resolve("launcher_profiles_microsoft_store.json")
        if (profiles.exists() || msStore.exists()) return
        profiles.writeText(
            """
            {
              "profiles": {
                "StarlitMoon": {
                  "name": "StarlitMoon",
                  "type": "custom",
                  "created": "1970-01-01T00:00:00.000Z",
                  "lastUsed": "1970-01-01T00:00:00.000Z",
                  "icon": "Furnace",
                  "lastVersionId": "latest-release"
                }
              },
              "selectedProfile": "StarlitMoon",
              "clientToken": "00000000-0000-0000-0000-000000000000",
              "launcherVersion": {
                "name": "StarlitMoon",
                "format": 21,
                "profilesFormat": 2
              }
            }
            """.trimIndent(),
        )
    }

    /**
     * Prefer maven-metadata.xml (reliable, smaller). Fall back to JSON versions API.
     */
    private suspend fun resolveLatestNeoForge(mcVersion: String): String {
        val prefix = neoForgeBranchPrefix(mcVersion)
        val fromMeta = runCatching { resolveNeoForgeFromMavenMetadata(prefix) }.getOrNull()
        if (!fromMeta.isNullOrBlank()) return fromMeta
        return resolveNeoForgeFromJsonApi(prefix, mcVersion)
    }

    private suspend fun resolveNeoForgeFromMavenMetadata(prefix: String): String {
        val body = http.get(NEOFORGE_MAVEN_METADATA).bodyAsText()
        val versions = VERSION_TAG.findAll(body).map { it.groupValues[1] }.toList()
        return pickLatestNeoForge(versions, prefix)
            ?: error("Нет NeoForge в maven-metadata для ветки $prefix")
    }

    private suspend fun resolveNeoForgeFromJsonApi(prefix: String, mcVersion: String): String {
        val body = http.get(NEOFORGE_VERSIONS_JSON).bodyAsText()
        val versions = json.decodeFromString(NeoForgeVersionsDto.serializer(), body).versions
        return pickLatestNeoForge(versions, prefix)
            ?: error("Нет NeoForge для Minecraft $mcVersion (ветка $prefix)")
    }

    private fun pickLatestNeoForge(versions: List<String>, prefix: String): String? {
        val branch = versions.filter { it.startsWith("$prefix.") }
        if (branch.isEmpty()) return null
        val stable = branch.filter {
            !it.contains("beta", ignoreCase = true) && !it.contains("alpha", ignoreCase = true)
        }
        val pool = if (stable.isNotEmpty()) stable else branch
        return pool.maxWithOrNull(
            compareBy(
                { versionKey(it).getOrElse(0) { 0 } },
                { versionKey(it).getOrElse(1) { 0 } },
                { versionKey(it).getOrElse(2) { 0 } },
                { versionKey(it).getOrElse(3) { 0 } },
                { versionKey(it).getOrElse(4) { 0 } },
            ),
        )
    }

    private suspend fun downloadFile(
        url: String,
        target: java.nio.file.Path,
        onBytes: (read: Long, total: Long?) -> Unit,
    ) {
        withTimeout(10 * 60_000L) {
            http.prepareGet(url).execute { response ->
                val total = response.headers["Content-Length"]?.toLongOrNull()
                val channel: ByteReadChannel = response.bodyAsChannel()
                target.parent?.createDirectories()
                Files.newOutputStream(target).use { out ->
                    var read = 0L
                    var lastReport = 0L
                    while (!channel.isClosedForRead) {
                        val packet = channel.readRemaining(DEFAULT_BUFFER)
                        val chunk = packet.readByteArray()
                        if (chunk.isEmpty()) break
                        out.write(chunk)
                        read += chunk.size
                        if (read - lastReport >= 256 * 1024 || (total != null && read >= total)) {
                            onBytes(read, total)
                            lastReport = read
                        }
                    }
                    onBytes(read, total)
                }
            }
        }
        if (!target.exists() || target.fileSize() < 10_000L) {
            error("Не удалось скачать установщик NeoForge")
        }
    }

    companion object {
        private const val NEOFORGE_MAVEN_METADATA =
            "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml"
        private const val NEOFORGE_VERSIONS_JSON =
            "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge"
        private val VERSION_TAG = Regex("<version>([^<]+)</version>")
        private const val DEFAULT_BUFFER = 64 * 1024L

        /** MC 1.21.1 → 21.1, MC 26.2 → 26.2 */
        fun neoForgeBranchPrefix(mcVersion: String): String {
            val v = mcVersion.trim()
            return if (v.startsWith("1.")) v.removePrefix("1.") else v
        }

        fun versionKey(version: String): List<Int> {
            val cleaned = version.substringBefore("+").substringBefore("-beta").substringBefore("-alpha")
            return cleaned.split('.').map { it.toIntOrNull() ?: 0 }
        }

        private fun formatBytes(n: Long): String = when {
            n >= 1_048_576 -> "%.1f МБ".format(n / 1_048_576.0)
            n >= 1024 -> "${n / 1024} КБ"
            else -> "$n Б"
        }
    }
}

@Serializable
private data class NeoForgeVersionsDto(
    val isSnapshot: Boolean = false,
    val versions: List<String> = emptyList(),
)
