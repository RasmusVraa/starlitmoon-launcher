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
import kotlinx.serialization.json.booleanOrNull
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
            requestTimeoutMillis = 300_000
            connectTimeoutMillis = 60_000
            socketTimeoutMillis = 300_000
        }
    }

    private val javaRuntimes = JavaRuntimeManager(http, config.dataDir.resolve("runtime"), json)

    data class LaunchResult(
        val success: Boolean,
        val message: String,
        val process: Process? = null,
        val skinBridge: OfflineSkinBridge? = null,
    )

    data class PreparedVersion(
        val id: String,
        val meta: VersionMeta,
        /** Каталог/имя jar клиента (обычно ванильный parent при inheritsFrom). */
        val clientJarId: String,
    )

    private val loaderInstaller = ModLoaderInstaller(
        http = http,
        config = config,
        json = json,
        resolveJava = { minMajor, onProgress ->
            findSystemJava()?.takeIf { javaMajorVersion(it) >= minMajor }
                ?: runCatching {
                    javaRuntimes.ensureRuntime("java-runtime-epsilon", onProgress)
                        .toAbsolutePath().toString()
                        .takeIf { javaMajorVersion(it) >= minMajor }
                }.getOrNull()
                ?: error("Нужна Java $minMajor+ для установки модлоадера")
        },
    )

    /**
     * Устанавливает профиль Fabric/NeoForge при необходимости и возвращает id версии для запуска.
     */
    suspend fun resolveLaunchVersionId(
        mcVersion: String,
        loader: String = "vanilla",
        loaderVersion: String? = null,
        onProgress: (String, Float?) -> Unit = { _, _ -> },
    ): String = loaderInstaller.ensureLoaderProfile(loader, mcVersion, loaderVersion, onProgress)

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
            if (isInstalledProfile(id)) return id
        }
        return manifest.versions.firstOrNull { it.type == "release" }?.id ?: "26.2"
    }

    private fun isInstalledProfile(id: String): Boolean =
        config.versionsDir.resolve(id).resolve("$id.json").exists()

    suspend fun ensureVersion(preferred: String = "", onProgress: (String, Float?) -> Unit = { _, _ -> }): Result<PreparedVersion> =
        runCatching {
            onProgress("Определение версии…", 0.01f)
            val id = resolveEnsureId(preferred)
            config.librariesDir.createDirectories()
            config.assetsDir.createDirectories()

            onProgress("Метаданные $id…", 0.05f)
            val chain = loadVersionChain(id, onProgress)
            val (merged, clientJarId) = mergeVersionChain(chain)
            val versionDir = config.versionsDir.resolve(id).apply { createDirectories() }
            val nativesDir = versionDir.resolve("natives").apply { createDirectories() }

            val libs = merged.libraries.filter { it.appliesToCurrentOs() }
            val totalLibs = libs.size.coerceAtLeast(1)
            val libSem = Semaphore(12)
            coroutineScope {
                libs.mapIndexed { index, library ->
                    async(Dispatchers.IO) {
                        libSem.withPermit {
                            val artifact = library.resolvedArtifact() ?: return@withPermit
                            val libPath = if (artifact.path.isNotBlank()) {
                                config.librariesDir.resolve(artifact.path)
                            } else {
                                config.librariesDir.resolve(library.name.replace(':', File.separatorChar) + ".jar")
                            }
                            if (!libPath.exists()) {
                                downloadBinaryWithRetry(artifact.url, libPath)
                            }
                            if (library.isNativesForCurrentOs()) {
                                extractNatives(libPath, nativesDir)
                            }
                            if ((index + 1) % 5 == 0 || index + 1 == totalLibs) {
                                val frac = 0.08f + 0.32f * (index + 1).toFloat() / totalLibs
                                onProgress("Библиотеки ${index + 1}/$totalLibs (${(index + 1) * 100 / totalLibs}%)", frac)
                            }
                        }
                    }
                }.awaitAll()
            }

            val clientDir = config.versionsDir.resolve(clientJarId).apply { createDirectories() }
            val clientJar = clientDir.resolve("$clientJarId.jar")
            val clientUrl = merged.downloads?.client?.url
                ?: error("Нет ссылки на клиентский jar (профиль $id → $clientJarId)")
            if (!clientJar.exists()) {
                onProgress("Скачивание клиента $clientJarId…", 0.42f)
                downloadBinaryWithRetry(clientUrl, clientJar)
            }
            onProgress("Клиент готов", 0.45f)

            val assetIndex = merged.assetIndex ?: error("Нет индекса ассетов")
            val assetIndexFile = config.assetsDir.resolve("indexes").resolve("${assetIndex.id}.json")
            assetIndexFile.parent?.createDirectories()
            if (!assetIndexFile.exists()) {
                onProgress("Индекс ресурсов…", 0.46f)
                assetIndexFile.writeText(http.get(assetIndex.url).bodyAsText())
            }
            downloadAssets(assetIndexFile, onProgress)

            PreparedVersion(id, merged, clientJarId)
        }

    private suspend fun resolveEnsureId(preferred: String): String {
        val raw = preferred.trim()
        if (raw.isNotEmpty() && (isLoaderProfileId(raw) || isInstalledProfile(raw))) return raw
        return resolveClientVersion(raw)
    }

    private fun isLoaderProfileId(id: String): Boolean =
        id.startsWith("fabric-loader-") || id.startsWith("neoforge-") || id.contains("-forge-")

    private suspend fun loadVersionChain(id: String, onProgress: (String, Float?) -> Unit): List<VersionMeta> {
        val chain = mutableListOf<VersionMeta>()
        val seen = linkedSetOf<String>()
        var current = id
        while (true) {
            check(seen.add(current)) { "Цикл inheritsFrom около $current" }
            chain += loadOrFetchVersionMeta(current, onProgress)
            val parent = chain.last().inheritsFrom?.trim().orEmpty()
            if (parent.isEmpty()) break
            current = parent
        }
        return chain
    }

    private suspend fun loadOrFetchVersionMeta(id: String, onProgress: (String, Float?) -> Unit): VersionMeta {
        val versionDir = config.versionsDir.resolve(id).apply { createDirectories() }
        val versionJson = versionDir.resolve("$id.json")
        if (versionJson.exists()) {
            return json.decodeFromString(versionJson.readText())
        }
        onProgress("Список версий (Mojang)…", 0.03f)
        val manifest = loadManifest()
        val url = manifest.versions.firstOrNull { it.id == id }?.url
            ?: error("Версия $id недоступна (нет локального профиля и нет в манифесте Mojang)")
        onProgress("Метаданные $id…", 0.05f)
        val meta = json.decodeFromString<VersionMeta>(http.get(url).bodyAsText())
        versionJson.writeText(json.encodeToString(VersionMeta.serializer(), meta))
        return meta
    }

    private fun mergeVersionChain(chain: List<VersionMeta>): Pair<VersionMeta, String> {
        require(chain.isNotEmpty())
        val child = chain.first()
        // Parent → child; child wins on the same Maven key (avoids Duplicate key in NeoForge BootstrapLauncher).
        val libraries = LinkedHashMap<String, Library>()
        for (meta in chain.asReversed()) {
            for (lib in meta.libraries) {
                libraries[libraryDedupKey(lib.name)] = lib
            }
        }
        val jvm = chain.asReversed().flatMap { it.arguments?.resolvedJvm().orEmpty() }
        val game = chain.asReversed().flatMap { it.arguments?.game.orEmpty() }
        val merged = VersionMeta(
            id = child.id,
            type = child.type,
            mainClass = child.mainClass,
            libraries = libraries.values.toList(),
            downloads = chain.firstNotNullOfOrNull { it.downloads },
            assetIndex = chain.firstNotNullOfOrNull { it.assetIndex },
            arguments = VersionArguments(jvm = jvm, game = game),
            javaVersion = chain.firstNotNullOfOrNull { it.javaVersion },
            inheritsFrom = null,
        )
        val jarId = chain.asReversed().firstOrNull { it.downloads?.client?.url != null }?.id
            ?: chain.last().id
        return merged to jarId
    }

    /** group:artifact[:classifier] — version ignored so NeoForge overrides vanilla duplicates. */
    private fun libraryDedupKey(name: String): String {
        val parts = name.split(':')
        return when {
            parts.size >= 4 -> "${parts[0]}:${parts[1]}:${parts[3]}"
            parts.size >= 2 -> "${parts[0]}:${parts[1]}"
            else -> name
        }
    }

    /**
     * @param instanceDir каталог инстанса (сборка): mods/config и т.п.
     * Версии/библиотеки/ассеты остаются в общем [LauncherConfig.gameDir].
     * @param loader fabric / neoforge / vanilla — при fabric/neoforge ставится профиль лоадера.
     */
    suspend fun launch(
        username: String,
        preferredVersion: String = "",
        instanceDir: Path? = null,
        loader: String = "vanilla",
        loaderVersion: String? = null,
        skinFile: Path? = null,
        onProgress: (String, Float?) -> Unit = { _, _ -> },
    ): LaunchResult {
        val mcVersion = preferredVersion.ifBlank { config.minecraftVersionId }
        val versionId = try {
            resolveLaunchVersionId(mcVersion, loader, loaderVersion, onProgress)
        } catch (e: Exception) {
            return LaunchResult(false, e.message ?: "Не удалось подготовить модлоадер")
        }
        val prepared = ensureVersion(versionId, onProgress)
        if (prepared.isFailure) {
            return LaunchResult(false, prepared.exceptionOrNull()?.message ?: "Ошибка подготовки")
        }
        val preparedVersion = prepared.getOrThrow()
        val id = preparedVersion.id
        val versionMeta = preparedVersion.meta
        val clientJarId = preparedVersion.clientJarId

        val component = versionMeta.javaVersion?.component ?: "java-runtime-epsilon"
        val requiredMajor = versionMeta.javaVersion?.majorVersion ?: 25
        val javaPath = try {
            javaRuntimes.ensureRuntime(component) { msg, frac ->
                val mapped = if (frac != null) 0.72f + frac * 0.22f else null
                onProgress(msg, mapped)
            }.toAbsolutePath().toString()
        } catch (e: Exception) {
            val fallback = findSystemJava()?.takeIf { javaMajorVersion(it) >= requiredMajor }
            fallback ?: return LaunchResult(
                false,
                "Нужна Java $requiredMajor+. Скачивание runtime не удалось: ${e.message ?: e}",
            )
        }

        val gameDirectory = (instanceDir ?: config.gameDir).also { it.createDirectories() }
        val nativesDir = config.versionsDir.resolve(id).resolve("natives")
        val classpath = buildClasspath(clientJarId, versionMeta)
        val uuid = offlineUuid(username)
        val freshConfig = runCatching { LauncherConfig.load() }.getOrDefault(config)
            val resolvedSkin = sequenceOf(
                skinFile,
                freshConfig.skinsDir.resolve("active.png"),
                freshConfig.skinPath.trim().takeIf { it.isNotEmpty() }?.let { Path.of(it) },
                freshConfig.skinsDir.resolve("${username.trim().lowercase()}.png"),
                config.skinsDir.resolve("${username.trim().lowercase()}.png"),
            ).filterNotNull().firstOrNull { it.exists() }
        var skinError: String? = null
        val skinBridge = try {
            OfflineSkinBridge.startIfNeeded(
                cacheDir = freshConfig.dataDir.resolve("cache"),
                username = username,
                uuidDashed = uuid,
                skinFile = resolvedSkin,
                capeFile = sequenceOf(
                    freshConfig.skinsDir.resolve("active_cape.png"),
                    config.skinsDir.resolve("active_cape.png"),
                ).firstOrNull { it.exists() },
            )
        } catch (e: Exception) {
            skinError = e.message ?: e.toString()
            null
        }
        val vars = mapOf(
            "auth_player_name" to username,
            "version_name" to id,
            "game_directory" to gameDirectory.toAbsolutePath().toString(),
            "assets_root" to config.assetsDir.toAbsolutePath().toString(),
            "assets_index_name" to (versionMeta.assetIndex?.id ?: ""),
            "auth_uuid" to uuid,
            "auth_access_token" to "0",
            "clientid" to "0",
            "auth_xuid" to "0",
            "user_type" to "legacy",
            "version_type" to versionMeta.type,
            "natives_directory" to nativesDir.toAbsolutePath().toString(),
            "library_directory" to config.librariesDir.toAbsolutePath().toString(),
            "launcher_name" to "starlitmoon",
            "launcher_version" to LauncherVersion.CURRENT,
            "classpath" to classpath,
            "classpath_separator" to File.pathSeparator,
        )

        val jvmArgs = buildList {
            skinBridge?.agentArg?.let { add(it) }
            val fromMeta = resolveArgList(versionMeta.arguments?.resolvedJvm(), vars)
                .filterNot { it.startsWith("-Xms") || it.startsWith("-Xmx") }
            addAll(fromMeta)
            if (none { it.startsWith("-Djava.library.path") }) {
                add("-Djava.library.path=${nativesDir.toAbsolutePath()}")
            }
            add("-Xms${config.resolvedMinMemoryMb()}M")
            add("-Xmx${config.resolvedMaxMemoryMb()}M")
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
            // Drop demo / quick-play leftovers (never enabled in this launcher).
            removeAll { it == "--demo" }
            removeFlagAndValue("--quickPlayPath")
            removeFlagAndValue("--quickPlaySingleplayer")
            removeFlagAndValue("--quickPlayMultiplayer")
            removeFlagAndValue("--quickPlayRealms")
            if (config.fullscreen && none { it == "--fullscreen" }) {
                add("--fullscreen")
            }
            // Never auto-join server — player connects from multiplayer list.
            removeFlagAndValue("--server")
            removeFlagAndValue("--port")
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
            onProgress("Запуск Minecraft…", 0.98f)
            val preamble = buildString {
                appendLine(command.joinToString("\n"))
                appendLine()
                appendLine("skinFile=${resolvedSkin?.toAbsolutePath()}")
                appendLine("skinAgent=${skinBridge != null}")
                if (skinError != null) appendLine("skinError=$skinError")
                appendLine()
                appendLine("--- game output ---")
            }
            logFile.writeText(preamble)
            val pb = ProcessBuilder(command)
                .directory(gameDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
            val process = pb.start()
            onProgress("Игра запущена", 1f)
            LaunchResult(true, "Игра запущена", process, skinBridge)
        } catch (e: Exception) {
            skinBridge?.close()
            LaunchResult(false, e.message ?: "Не удалось запустить")
        }
    }

    private suspend fun downloadAssets(indexFile: Path, onProgress: (String, Float?) -> Unit) {
        val root = json.parseToJsonElement(indexFile.readText()).jsonObject
        val objects = root["objects"]?.jsonObject ?: return
        val objectsDir = config.assetsDir.resolve("objects").apply { createDirectories() }
        val missing = objects.entries.mapNotNull { (_, meta) ->
            val hash = meta.jsonObject["hash"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val dest = objectsDir.resolve(hash.substring(0, 2)).resolve(hash)
            if (dest.exists()) null else hash to dest
        }
        if (missing.isEmpty()) {
            onProgress("Ресурсы готовы", 0.70f)
            return
        }
        onProgress("Ресурсы 0/${missing.size} (0%)", 0.46f)
        val total = missing.size
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        val sem = Semaphore(16)
        coroutineScope {
            missing.map { (hash, dest) ->
                async(Dispatchers.IO) {
                    sem.withPermit {
                        dest.parent?.createDirectories()
                        val url = "https://resources.download.minecraft.net/${hash.substring(0, 2)}/$hash"
                        runCatching { downloadBinaryWithRetry(url, dest) }
                        val n = done.incrementAndGet()
                        if (n % 40 == 0 || n == total) {
                            val pct = n * 100 / total
                            onProgress("Ресурсы $n/$total ($pct%)", 0.46f + 0.24f * n.toFloat() / total)
                        }
                    }
                }
            }.awaitAll()
        }
        onProgress("Ресурсы готовы", 0.70f)
    }

    private fun MutableList<String>.removeFlagAndValue(flag: String) {
        var i = indexOf(flag)
        while (i >= 0) {
            removeAt(i)
            // Only drop the following token if it is a value, not another flag.
            if (i < size && !this[i].startsWith("-")) {
                removeAt(i)
            }
            i = indexOf(flag)
        }
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
        // Drop unresolved templates and orphan flags whose value was a template.
        val filtered = mutableListOf<String>()
        var i = 0
        val raw = out.filter { it.isNotBlank() }
        while (i < raw.size) {
            val cur = raw[i]
            val next = raw.getOrNull(i + 1)
            if (next != null && next.contains("\${")) {
                i += 2
                continue
            }
            if (cur.contains("\${")) {
                i++
                continue
            }
            filtered += cur
            i++
        }
        return filtered
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
            // Launcher never enables quick-play / demo features → require exact match to false defaults.
            val featuresOk = if (features == null) {
                true
            } else {
                features.entries.all { (_, v) ->
                    val want = v.jsonPrimitive.booleanOrNull
                        ?: (v.jsonPrimitive.contentOrNull?.equals("true", ignoreCase = true) == true)
                    val have = false
                    want == have
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

    private suspend fun downloadBinaryWithRetry(url: String, target: Path, attempts: Int = 4) {
        var last: Exception? = null
        repeat(attempts) { attempt ->
            try {
                downloadBinary(url, target)
                if (target.exists()) return
            } catch (e: Exception) {
                last = e
                kotlinx.coroutines.delay(800L * (attempt + 1))
            }
        }
        throw last ?: IllegalStateException("Не удалось скачать $url")
    }

    private fun buildClasspath(clientJarId: String, meta: VersionMeta): String {
        val paths = LinkedHashSet<String>()
        for (library in meta.libraries) {
            if (!library.appliesToCurrentOs()) continue
            val artifact = library.resolvedArtifact() ?: continue
            if (artifact.path.isBlank()) continue
            paths += config.librariesDir.resolve(artifact.path).toAbsolutePath().normalize().toString()
        }
        // Forge/NeoForge BootstrapLauncher already provides module `minecraft` via libraries.
        // Putting versions/1.21.1/1.21.1.jar on -cp creates automatic module `_1._21._1` and crashes:
        // "Modules minecraft and _1._21._1 export package net.minecraft.data…"
        if (shouldIncludeVanillaClientJar(meta)) {
            paths += config.versionsDir.resolve(clientJarId).resolve("$clientJarId.jar")
                .toAbsolutePath().normalize().toString()
        }
        return paths.joinToString(File.pathSeparator)
    }

    private fun shouldIncludeVanillaClientJar(meta: VersionMeta): Boolean {
        val main = meta.mainClass.lowercase()
        if (main.contains("bootstraplauncher")) return false
        val id = meta.id.lowercase()
        if (id.startsWith("neoforge-")) return false
        if (id.startsWith("forge-") || id.contains("-forge-")) return false
        return true
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
    val inheritsFrom: String? = null,
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
    /** Maven base URL (Fabric Meta style), used when [downloads] is absent. */
    val url: String? = null,
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

private fun Library.resolvedArtifact(): DownloadArtifact? {
    downloads?.artifact?.let { art ->
        if (art.url.isNotBlank()) {
            val path = art.path.ifBlank { mavenPathFromName(name) ?: "" }
            return art.copy(path = path)
        }
    }
    val path = mavenPathFromName(name) ?: return null
    val base = url?.trimEnd('/') ?: "https://libraries.minecraft.net"
    return DownloadArtifact(path = path, url = "$base/$path")
}

/** `group:artifact:version` or `group:artifact:version:classifier` → Maven relative path. */
private fun mavenPathFromName(name: String): String? {
    val parts = name.split(':')
    if (parts.size < 3) return null
    val group = parts[0].replace('.', '/')
    val artifact = parts[1]
    val version = parts[2]
    val classifier = parts.getOrNull(3)
    val file = if (classifier.isNullOrBlank()) {
        "$artifact-$version.jar"
    } else {
        "$artifact-$version-$classifier.jar"
    }
    return "$group/$artifact/$version/$file"
}

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
