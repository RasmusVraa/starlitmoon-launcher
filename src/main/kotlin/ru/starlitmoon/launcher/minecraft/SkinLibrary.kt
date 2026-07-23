package ru.starlitmoon.launcher.minecraft

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.starlitmoon.launcher.LauncherConfig
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

@Serializable
data class SkinLibraryEntry(
    val id: String,
    val name: String,
    val fileName: String,
    val capeFileName: String? = null,
    val slim: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
)

@Serializable
private data class SkinLibraryFile(
    val skins: List<SkinLibraryEntry> = emptyList(),
    val activeId: String? = null,
)

/**
 * Local skin/cape library under `~/.starlitmoon-launcher/skins/library/`.
 */
class SkinLibrary(private val config: LauncherConfig) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val libraryDir: Path
        get() = config.skinsDir.resolve("library").also { it.createDirectories() }

    private val indexFile: Path
        get() = libraryDir.resolve("index.json")

    fun list(): List<SkinLibraryEntry> = read().skins.sortedByDescending { it.addedAt }

    fun activeId(): String? = read().activeId

    fun skinPath(entry: SkinLibraryEntry): Path = libraryDir.resolve(entry.fileName)

    fun capePath(entry: SkinLibraryEntry): Path? =
        entry.capeFileName?.let { libraryDir.resolve(it) }?.takeIf { it.exists() }

    fun activeSkinPath(): Path? {
        val data = read()
        val id = data.activeId ?: return null
        val entry = data.skins.firstOrNull { it.id == id } ?: return null
        return skinPath(entry).takeIf { it.exists() }
    }

    fun activeCapePath(): Path? {
        val data = read()
        val id = data.activeId ?: return null
        val entry = data.skins.firstOrNull { it.id == id } ?: return null
        return capePath(entry)
    }

    fun activeEntry(): SkinLibraryEntry? {
        val data = read()
        val id = data.activeId ?: return null
        return data.skins.firstOrNull { it.id == id }
    }

    fun addFromFile(source: Path, displayName: String? = null): SkinLibraryEntry {
        if (!source.exists()) error("Файл не найден")
        val bytes = Files.readAllBytes(source)
        SkinManager(config.skinsDir).validatePng(bytes)?.let { error(it) }
        val id = UUID.randomUUID().toString().take(12)
        val fileName = "$id.png"
        libraryDir.resolve(fileName).writeBytes(bytes)
        val entry = SkinLibraryEntry(
            id = id,
            name = displayName?.trim()?.ifBlank { null }
                ?: source.fileName.toString().substringBeforeLast('.'),
            fileName = fileName,
            slim = detectSlim(bytes),
        )
        val data = read()
        write(data.copy(skins = data.skins + entry, activeId = entry.id))
        return entry
    }

    fun setCape(entryId: String, capeSource: Path?) {
        val data = read()
        val idx = data.skins.indexOfFirst { it.id == entryId }
        if (idx < 0) error("Скин не найден")
        val entry = data.skins[idx]
        // remove old cape file
        entry.capeFileName?.let { runCatching { Files.deleteIfExists(libraryDir.resolve(it)) } }
        val capeName = if (capeSource != null && capeSource.exists()) {
            val bytes = Files.readAllBytes(capeSource)
            validateCape(bytes)?.let { error(it) }
            val name = "${entry.id}_cape.png"
            libraryDir.resolve(name).writeBytes(bytes)
            name
        } else {
            null
        }
        val next = entry.copy(capeFileName = capeName)
        val skins = data.skins.toMutableList().also { it[idx] = next }
        write(data.copy(skins = skins))
    }

    fun rename(entryId: String, name: String) {
        val data = read()
        val idx = data.skins.indexOfFirst { it.id == entryId }
        if (idx < 0) return
        val next = data.skins[idx].copy(name = name.trim().ifBlank { data.skins[idx].name })
        write(data.copy(skins = data.skins.toMutableList().also { it[idx] = next }))
    }

    fun remove(entryId: String) {
        val data = read()
        val entry = data.skins.firstOrNull { it.id == entryId } ?: return
        runCatching { Files.deleteIfExists(skinPath(entry)) }
        entry.capeFileName?.let { runCatching { Files.deleteIfExists(libraryDir.resolve(it)) } }
        val skins = data.skins.filterNot { it.id == entryId }
        val active = if (data.activeId == entryId) skins.firstOrNull()?.id else data.activeId
        write(SkinLibraryFile(skins, active))
    }

    fun select(entryId: String): SkinLibraryEntry {
        val data = read()
        val entry = data.skins.firstOrNull { it.id == entryId } ?: error("Скин не найден")
        write(data.copy(activeId = entryId))
        // Mirror to username.png for offline bridge / legacy path
        val dest = config.skinsDir.resolve("active.png")
        Files.copy(skinPath(entry), dest, StandardCopyOption.REPLACE_EXISTING)
        capePath(entry)?.let { cape ->
            Files.copy(cape, config.skinsDir.resolve("active_cape.png"), StandardCopyOption.REPLACE_EXISTING)
        } ?: runCatching { Files.deleteIfExists(config.skinsDir.resolve("active_cape.png")) }
        return entry
    }

    fun importActiveIfEmpty(username: String) {
        val data = read()
        if (data.skins.isNotEmpty()) return
        val legacy = sequenceOf(
            config.skinPath.trim().takeIf { it.isNotEmpty() }?.let { Path.of(it) },
            config.skinsDir.resolve("${username.trim().lowercase()}.png"),
        ).filterNotNull().firstOrNull { it.exists() } ?: return
        runCatching { addFromFile(legacy, username) }
    }

    private fun read(): SkinLibraryFile {
        if (!indexFile.exists()) return SkinLibraryFile()
        return runCatching { json.decodeFromString<SkinLibraryFile>(indexFile.readText()) }
            .getOrElse { SkinLibraryFile() }
    }

    private fun write(data: SkinLibraryFile) {
        libraryDir.createDirectories()
        indexFile.writeText(json.encodeToString(data))
    }

    companion object {
        fun validateCape(bytes: ByteArray): String? {
            val img = runCatching {
                javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes))
            }.getOrNull() ?: return "Не удалось прочитать PNG плаща"
            val ok = (img.width == 64 && img.height == 32) || (img.width == 64 && img.height == 64)
            if (!ok) return "Плащ: PNG 64×32 (или 64×64), сейчас ${img.width}×${img.height}"
            return null
        }

        fun detectSlim(bytes: ByteArray): Boolean {
            val img = runCatching {
                javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes))
            }.getOrNull() ?: return false
            // Classic: arm overlay pixel (54,20) often opaque for classic; slim uses thinner arms.
            if (img.width < 64 || img.height < 32) return false
            val px = img.getRGB(50, 16)
            val alpha = (px ushr 24) and 0xFF
            return alpha == 0
        }
    }
}
