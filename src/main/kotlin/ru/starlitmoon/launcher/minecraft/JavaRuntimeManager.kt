package ru.starlitmoon.launcher.minecraft

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.writeBytes

class JavaRuntimeManager(
    private val http: HttpClient,
    private val runtimesDir: Path,
    private val json: Json,
) {
    suspend fun ensureRuntime(
        component: String,
        onProgress: (String, Float?) -> Unit,
    ): Path {
        val home = runtimesDir.resolve(component)
        val javaName = if (isWindows()) "java.exe" else "java"
        val javaExe = home.resolve("bin").resolve(javaName)
        if (javaExe.exists() && javaExe.fileSize() > 0) return javaExe

        onProgress("Загрузка Java ($component)…", 0f)
        home.createDirectories()

        val indexText = http.get(JAVA_RUNTIME_INDEX).body<String>()
        val index = json.parseToJsonElement(indexText).jsonObject
        val platformKey = when {
            isWindows() -> "windows-x64"
            System.getProperty("os.name").lowercase().contains("mac") -> "mac-os"
            else -> "linux"
        }
        val platform = index[platformKey]?.jsonObject
            ?: error("Нет Java для $platformKey")
        val entries = platform[component]?.jsonArray
            ?: error("Нет компонента $component")
        val entry = entries.first().jsonObject
        val manifestUrl = entry["manifest"]?.jsonObject?.get("url")?.jsonPrimitive?.content
            ?: error("Нет URL манифеста Java")

        onProgress("Манифест Java…", 0.02f)
        val manifest = json.parseToJsonElement(http.get(manifestUrl).body<String>()).jsonObject
        val files = manifest["files"]?.jsonObject ?: error("Пустой манифест Java")

        val downloads = mutableListOf<Pair<String, String>>()
        for ((relPath, metaEl) in files) {
            val meta = metaEl.jsonObject
            when (meta["type"]?.jsonPrimitive?.content) {
                "directory" -> {
                    home.resolve(relPath.replace('/', java.io.File.separatorChar)).createDirectories()
                }
                "file" -> {
                    val rawUrl = meta["downloads"]?.jsonObject
                        ?.get("raw")?.jsonObject
                        ?.get("url")?.jsonPrimitive?.content
                        ?: continue
                    downloads += relPath to rawUrl
                }
            }
        }

        val total = downloads.size.coerceAtLeast(1)
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        val progressMutex = Mutex()
        val sem = Semaphore(10)

        coroutineScope {
            downloads.map { (relPath, url) ->
                async(Dispatchers.IO) {
                    sem.withPermit {
                        val target = home.resolve(relPath.replace('/', java.io.File.separatorChar))
                        target.parent?.createDirectories()
                        if (!target.exists() || target.fileSize() == 0L) {
                            downloadWithRetry(url, target)
                        }
                        if (relPath.endsWith("java") || relPath.endsWith("java.exe")) {
                            target.toFile().setExecutable(true)
                        }
                        val n = done.incrementAndGet()
                        if (n % 8 == 0 || n == total) {
                            progressMutex.withLock {
                                onProgress("Java $n/$total (${n * 100 / total}%)", n.toFloat() / total)
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        if (!javaExe.exists()) error("Java не найдена после загрузки")
        onProgress("Java готова", 1f)
        return javaExe
    }

    private suspend fun downloadWithRetry(url: String, target: Path, attempts: Int = 5) {
        var last: Exception? = null
        repeat(attempts) { attempt ->
            try {
                target.writeBytes(http.get(url).body<ByteArray>())
                if (target.exists() && target.fileSize() > 0) return
            } catch (e: Exception) {
                last = e
                delay(1_000L * (attempt + 1))
            }
        }
        throw last ?: IllegalStateException("Не удалось скачать $url")
    }

    companion object {
        const val JAVA_RUNTIME_INDEX =
            "https://piston-meta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json"

        private fun isWindows() = System.getProperty("os.name").lowercase().contains("win")
    }
}
