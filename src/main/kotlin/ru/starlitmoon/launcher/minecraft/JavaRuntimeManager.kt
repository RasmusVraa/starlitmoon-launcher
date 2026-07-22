package ru.starlitmoon.launcher.minecraft

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

class JavaRuntimeManager(
    private val http: HttpClient,
    private val runtimesDir: Path,
    private val json: Json,
) {
    suspend fun ensureRuntime(component: String, onProgress: (String) -> Unit): Path {
        val home = runtimesDir.resolve(component)
        val javaName = if (isWindows()) "java.exe" else "java"
        val javaExe = home.resolve("bin").resolve(javaName)
        if (javaExe.exists()) return javaExe

        onProgress("Загрузка Java ($component)…")
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

        onProgress("Манифест Java…")
        val manifest = json.parseToJsonElement(http.get(manifestUrl).body<String>()).jsonObject
        val files = manifest["files"]?.jsonObject ?: error("Пустой манифест Java")

        var done = 0
        val fileEntries = files.entries.toList()
        for ((relPath, metaEl) in fileEntries) {
            val meta = metaEl.jsonObject
            val type = meta["type"]?.jsonPrimitive?.content ?: continue
            val target = home.resolve(relPath.replace('/', java.io.File.separatorChar))
            if (type == "directory") {
                target.createDirectories()
            } else if (type == "file") {
                val rawUrl = meta["downloads"]?.jsonObject
                    ?.get("raw")?.jsonObject
                    ?.get("url")?.jsonPrimitive?.content
                    ?: continue
                target.parent?.createDirectories()
                if (!target.exists()) {
                    target.writeBytes(http.get(rawUrl).body<ByteArray>())
                }
                if (relPath.endsWith("java") || relPath.endsWith("java.exe")) {
                    target.toFile().setExecutable(true)
                }
            }
            done++
            if (done % 40 == 0 || done == fileEntries.size) {
                onProgress("Java $done/${fileEntries.size}")
            }
        }

        if (!javaExe.exists()) error("Java не найдена после загрузки")
        onProgress("Java готова")
        return javaExe
    }

    companion object {
        const val JAVA_RUNTIME_INDEX =
            "https://piston-meta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json"

        private fun isWindows() = System.getProperty("os.name").lowercase().contains("win")
    }
}
