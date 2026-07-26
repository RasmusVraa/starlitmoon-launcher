package ru.starlitmoon.launcher

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
data class LauncherConfig(
    val apiBaseUrl: String = "https://starlit-moon.ru",
    val serverHost: String = "play.starlit-moon.ru",
    val defaultMcVersion: String = "26.2",
    val defaultMaxPlayers: Int = 100,
    val minecraftVersionId: String = "26.2",
    val javaPath: String = "",
    val memoryAuto: Boolean = true,
    val minMemoryMb: Int = 2048,
    val maxMemoryMb: Int = 4096,
    val fullscreen: Boolean = false,
    /** Безрамочный режим (окно на весь экран без рамки), как BorderlessMinecraft. */
    val borderlessFullscreen: Boolean = false,
    val autoJoinServer: Boolean = false,
    val keepLauncherOpen: Boolean = true,
    val autoLogin: Boolean = false,
    val savePassword: Boolean = false,
    val vsync: Boolean = true,
    /** Пустая строка = ~/.starlitmoon-launcher/game */
    val gamePath: String = "",
    val skinPath: String = "",
    val skinTextureUrl: String = "",
    val selectedModpackId: String = "",
    val githubOwner: String = "RasmusVraa",
    val githubRepo: String = "starlitmoon-launcher",
    /** Репозиторий сборок (пофайлово): packs/{slug}/manifest.json */
    val modpackGithubOwner: String = "RasmusVraa",
    val modpackGithubRepo: String = "starlitmoon-modpacks",
    val modpackGithubRef: String = "main",
    /**
     * Legacy config field — always treated as true.
     * Players always sync packs from GitHub (`modpackGithubOwner`/`modpackGithubRepo`); site ZIP is admin-only.
     */
    val preferGithubModpacks: Boolean = true,
    /**
     * Local clone of starlitmoon-modpacks for admin «Опубликовать в GitHub».
     * Empty = auto-detect sibling `../starlitmoon-modpacks` or env `STARLIT_MODPACKS_REPO`.
     */
    val modpackLocalRepoPath: String = "",
    /** 0 = без лимита. КБ/с для скачивания сборок. */
    val downloadSpeedLimitKBps: Int = 0,
    val sidebarExpanded: Boolean = false,
    val checkUpdatesOnStart: Boolean = true,
    /** Показывать статус лаунчера в Discord (Rich Presence). */
    val discordRpcEnabled: Boolean = true,
    /** Анимации UI: переходы страниц, кнопки, раскрытия. */
    val animationsEnabled: Boolean = true,
) {
    val dataDir: Path
        get() = Path.of(System.getProperty("user.home"), ".starlitmoon-launcher")

    val skinsDir: Path
        get() = dataDir.resolve("skins")

    val packsDir: Path
        get() = dataDir.resolve("packs")

    val gameDir: Path
        get() = if (gamePath.isNotBlank()) Path.of(gamePath) else dataDir.resolve("game")

    val assetsDir: Path
        get() = gameDir.resolve("assets")

    val versionsDir: Path
        get() = gameDir.resolve("versions")

    val librariesDir: Path
        get() = gameDir.resolve("libraries")

    fun resolvedMinMemoryMb(): Int =
        if (memoryAuto) (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toInt().coerceIn(1024, 2048)
            .coerceAtMost(resolvedMaxMemoryMb())
        else minMemoryMb

    fun resolvedMaxMemoryMb(): Int {
        if (!memoryAuto) return maxMemoryMb.coerceIn(1024, 32768)
        val coresHint = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val suggested = (2048 + coresHint * 512).coerceIn(2048, 8192)
        return suggested.coerceAtMost(maxMemoryMb.coerceAtLeast(2048))
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }

        fun load(): LauncherConfig {
            val dir = Path.of(System.getProperty("user.home"), ".starlitmoon-launcher")
            dir.createDirectories()
            val file = dir.resolve("config.json")
            if (!file.exists()) {
                val defaults = LauncherConfig()
                file.writeText(json.encodeToString(defaults))
                return defaults
            }
            val loaded = runCatching {
                json.decodeFromString<LauncherConfig>(file.readText())
            }.getOrElse { LauncherConfig() }
            var next = loaded
            if (next.minecraftVersionId == "1.21.4") {
                next = next.copy(minecraftVersionId = "26.2", defaultMcVersion = "26.2")
            }
            // Old default pointed at a non-existent org → update checks silently 404'd.
            if (next.githubOwner.equals("starlit-moon", ignoreCase = true) || next.githubOwner.isBlank()) {
                next = next.copy(githubOwner = "RasmusVraa")
            }
            if (next.githubRepo.isBlank()) {
                next = next.copy(githubRepo = "starlitmoon-launcher")
            }
            if (next.modpackGithubOwner.isBlank()) {
                next = next.copy(modpackGithubOwner = "RasmusVraa")
            }
            if (next.modpackGithubRepo.isBlank()) {
                next = next.copy(modpackGithubRepo = "starlitmoon-modpacks")
            }
            if (next.modpackGithubRef.isBlank()) {
                next = next.copy(modpackGithubRef = "main")
            }
            // Players always pull packs from GitHub — force-enable for existing configs.
            if (!next.preferGithubModpacks) {
                next = next.copy(preferGithubModpacks = true)
            }
            if (next != loaded) save(next)
            return next
        }

        fun save(config: LauncherConfig) {
            val dir = config.dataDir
            dir.createDirectories()
            dir.resolve("config.json").writeText(json.encodeToString(config))
        }
    }
}
