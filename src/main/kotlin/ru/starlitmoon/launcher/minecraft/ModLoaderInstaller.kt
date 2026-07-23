package ru.starlitmoon.launcher.minecraft

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
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
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

/**
 * Installs Fabric / NeoForge version profiles into the shared game directory
 * so [MinecraftLauncher] can launch them instead of plain Mojang IDs.
 */
class ModLoaderInstaller(
    private val http: HttpClient,
    private val config: LauncherConfig,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    private val resolveJava: suspend (minMajor: Int) -> String,
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
        val loaderVer = loaderVersion ?: resolveLatestFabricLoader(mcVersion)
        val id = "fabric-loader-$loaderVer-$mcVersion"
        val versionDir = config.versionsDir.resolve(id).apply { createDirectories() }
        val profileFile = versionDir.resolve("$id.json")
        if (profileFile.exists()) {
            onProgress("Fabric уже установлен ($id)", 0.05f)
            return id
        }
        onProgress("Скачивание профиля Fabric $loaderVer…", 0.03f)
        val url = "https://meta.fabricmc.net/v2/versions/loader/$mcVersion/$loaderVer/profile/json"
        val body = http.get(url).bodyAsText()
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
        val nfVer = loaderVersion ?: resolveLatestNeoForge(mcVersion)
        val id = "neoforge-$nfVer"
        val profileFile = config.versionsDir.resolve(id).resolve("$id.json")
        if (profileFile.exists()) {
            onProgress("NeoForge уже установлен ($id)", 0.05f)
            return id
        }

        val cacheDir = config.dataDir.resolve("cache").apply { createDirectories() }
        val installer = cacheDir.resolve("neoforge-$nfVer-installer.jar")
        if (!installer.exists()) {
            onProgress("Скачивание установщика NeoForge $nfVer…", 0.03f)
            val url =
                "https://maven.neoforged.net/releases/net/neoforged/neoforge/$nfVer/neoforge-$nfVer-installer.jar"
            val bytes = http.get(url).readRawBytes()
            val tmp = installer.resolveSibling("${installer.fileName}.part")
            tmp.writeBytes(bytes)
            Files.move(tmp, installer, StandardCopyOption.REPLACE_EXISTING)
        }

        onProgress("Установка NeoForge $nfVer в клиент…", 0.04f)
        config.gameDir.createDirectories()
        val javaBin = resolveJava(17)
        val logFile = config.dataDir.resolve("neoforge-install.log")
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
        val code = withContextIo { pb.start().waitFor() }
        if (code != 0 || !profileFile.exists()) {
            val tip = runCatching { logFile.toFile().readText().takeLast(600) }.getOrNull().orEmpty()
            error("Установка NeoForge $nfVer не удалась (код $code). $tip")
        }
        onProgress("NeoForge установлен ($id)", 0.05f)
        return id
    }

    private suspend fun resolveLatestNeoForge(mcVersion: String): String {
        val prefix = neoForgeBranchPrefix(mcVersion)
        val body = http.get("https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge")
            .bodyAsText()
        val versions = json.decodeFromString(NeoForgeVersionsDto.serializer(), body).versions
            .filter { it.startsWith("$prefix.") }
        if (versions.isEmpty()) {
            error("Нет NeoForge для Minecraft $mcVersion (ветка $prefix)")
        }
        val stable = versions.filter { !it.contains("beta", ignoreCase = true) && !it.contains("alpha", ignoreCase = true) }
        val pool = if (stable.isNotEmpty()) stable else versions
        return pool.maxWithOrNull(compareBy({ versionKey(it).getOrElse(0) { 0 } }, { versionKey(it).getOrElse(1) { 0 } }, { versionKey(it).getOrElse(2) { 0 } }, { versionKey(it).getOrElse(3) { 0 } }, { versionKey(it).getOrElse(4) { 0 } }))
            ?: error("Нет NeoForge для Minecraft $mcVersion (ветка $prefix)")
    }

    companion object {
        /** MC 1.21.1 → 21.1, MC 26.2 → 26.2 */
        fun neoForgeBranchPrefix(mcVersion: String): String {
            val v = mcVersion.trim()
            return if (v.startsWith("1.")) v.removePrefix("1.") else v
        }

        fun versionKey(version: String): List<Int> {
            val cleaned = version.substringBefore("+").substringBefore("-beta").substringBefore("-alpha")
            return cleaned.split('.').map { it.toIntOrNull() ?: 0 }
        }
    }
}

@Serializable
private data class NeoForgeVersionsDto(
    val isSnapshot: Boolean = false,
    val versions: List<String> = emptyList(),
)

private suspend fun <T> withContextIo(block: () -> T): T =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }
