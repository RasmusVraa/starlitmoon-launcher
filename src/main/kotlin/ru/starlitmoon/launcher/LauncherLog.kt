package ru.starlitmoon.launcher

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.fileSize

/** Append-only launcher log under ~/.starlitmoon-launcher/launcher.log */
object LauncherLog {
    private val lock = Any()
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private const val MAX_BYTES = 2L * 1024 * 1024

    fun file(dataDir: Path = defaultDataDir()): Path = dataDir.resolve("launcher.log")

    fun gameLaunchFile(dataDir: Path = defaultDataDir()): Path = dataDir.resolve("last-launch.log")

    fun info(message: String, dataDir: Path = defaultDataDir()) = append("INFO", message, dataDir)

    fun warn(message: String, dataDir: Path = defaultDataDir()) = append("WARN", message, dataDir)

    fun error(message: String, dataDir: Path = defaultDataDir()) = append("ERROR", message, dataDir)

    fun readText(dataDir: Path = defaultDataDir(), maxChars: Int = 400_000): String {
        val path = file(dataDir)
        if (!path.exists()) return "Лог лаунчера пока пуст."
        return runCatching {
            val text = Files.readString(path, StandardCharsets.UTF_8)
            if (text.length <= maxChars) text else text.takeLast(maxChars)
        }.getOrElse { "Не удалось прочитать лог: ${it.message}" }
    }

    fun readGameLaunchText(dataDir: Path = defaultDataDir(), maxChars: Int = 400_000): String {
        val path = gameLaunchFile(dataDir)
        if (!path.exists()) return "Лог запуска игры пока пуст. Запустите Minecraft из лаунчера."
        return runCatching {
            val text = Files.readString(path, StandardCharsets.UTF_8)
            if (text.length <= maxChars) text else text.takeLast(maxChars)
        }.getOrElse { "Не удалось прочитать лог: ${it.message}" }
    }

    fun clear(dataDir: Path = defaultDataDir()) {
        synchronized(lock) {
            runCatching {
                dataDir.createDirectories()
                Files.writeString(
                    file(dataDir),
                    "",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE,
                )
            }
        }
    }

    private fun append(level: String, message: String, dataDir: Path) {
        synchronized(lock) {
            runCatching {
                dataDir.createDirectories()
                val path = file(dataDir)
                if (path.exists() && path.fileSize() > MAX_BYTES) {
                    val kept = Files.readString(path, StandardCharsets.UTF_8).takeLast(MAX_BYTES.toInt() / 2)
                    Files.writeString(path, "--- log rotated ---\n$kept\n", StandardCharsets.UTF_8)
                }
                val line = "[${LocalDateTime.now().format(stamp)}] [$level] $message\n"
                Files.writeString(
                    path,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.WRITE,
                )
            }
        }
    }

    private fun defaultDataDir(): Path =
        Path.of(System.getProperty("user.home"), ".starlitmoon-launcher")
}
