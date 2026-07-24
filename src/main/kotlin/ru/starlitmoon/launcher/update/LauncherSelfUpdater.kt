package ru.starlitmoon.launcher.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.yield
import ru.starlitmoon.launcher.LauncherVersion
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.writeText

/**
 * Downloads an update package and applies it after the launcher process exits.
 *
 * Preferred path: app ZIP → extract → robocopy into install dir → restart.
 * Fallback: Inno Setup silent install (older releases).
 */
object LauncherSelfUpdater {
    private const val EXE_NAME = "StarlitMoonLauncher.exe"
    private const val UPDATE_FLAG = "pending.flag"
    private const val APPLY_PS1 = "apply-update.ps1"
    private const val APPLY_VBS = "apply-update.vbs"

    suspend fun downloadPackage(
        url: String,
        target: Path,
        onProgress: (fraction: Float, label: String) -> Unit,
    ) {
        val client = HttpClient(CIO) {
            expectSuccess = false
            followRedirects = true
            install(HttpTimeout) {
                requestTimeoutMillis = 15 * 60 * 1000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 15 * 60 * 1000
            }
        }
        try {
            client.prepareGet(url) {
                header("User-Agent", "StarlitMoon-Launcher/${LauncherVersion.CURRENT}")
                header("Accept", "application/octet-stream")
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    error("Не удалось скачать обновление (${response.status.value})")
                }
                val total = response.headers["Content-Length"]?.toLongOrNull()?.takeIf { it > 0 }
                target.parent?.createDirectories()
                val tmp = target.resolveSibling("${target.fileName}.part")
                tmp.deleteIfExists()
                var written = 0L
                var lastReport = 0L
                Files.newOutputStream(
                    tmp,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE,
                ).use { out ->
                    val channel: ByteReadChannel = response.bodyAsChannel()
                    val buffer = ByteArray(256 * 1024)
                    while (!channel.isClosedForRead) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read < 0) break
                        if (read == 0) {
                            yield()
                            continue
                        }
                        out.write(buffer, 0, read)
                        written += read
                        if (written - lastReport < 512 * 1024 && total != null && written < total) continue
                        lastReport = written
                        val frac = if (total != null) {
                            (written.toFloat() / total.toFloat()).coerceIn(0f, 0.99f)
                        } else {
                            0.15f
                        }
                        val mb = written / (1024.0 * 1024.0)
                        val label = if (total != null) {
                            val totalMb = total / (1024.0 * 1024.0)
                            "Загрузка %.0f / %.0f МБ".format(mb, totalMb)
                        } else {
                            "Загрузка %.0f МБ…".format(mb)
                        }
                        onProgress(frac, label)
                    }
                }
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
                onProgress(1f, "Загрузка завершена")
            }
        } finally {
            client.close()
        }
    }

    /** @deprecated use [downloadPackage] */
    suspend fun downloadInstaller(
        url: String,
        target: Path,
        onProgress: (fraction: Float, label: String) -> Unit,
    ) = downloadPackage(url, target, onProgress)

    /**
     * Extract ZIP into [stagingDir], then schedule file replace + restart after this process exits.
     */
    fun prepareZipUpdateAndRestart(
        zipPath: Path,
        stagingDir: Path,
        installDir: Path,
        relaunchExe: Path,
        launcherPid: Long,
        onProgress: (String) -> Unit = {},
    ) {
        require(zipPath.exists()) { "Архив обновления не найден" }
        onProgress("Распаковка обновления…")
        deleteRecursively(stagingDir)
        stagingDir.createDirectories()
        extractZipFlat(zipPath, stagingDir)
        val payload = resolveAppRoot(stagingDir)
        require(payload.resolve(EXE_NAME).exists() || payload.listDirectoryEntries().isNotEmpty()) {
            "В архиве обновления нет файлов лаунчера"
        }

        val updateDir = installDir.resolve("update").also { it.createDirectories() }
        val flag = updateDir.resolve(UPDATE_FLAG)
        val ps1 = updateDir.resolve(APPLY_PS1)
        val vbs = updateDir.resolve(APPLY_VBS)

        fun psLit(path: String): String = "'" + path.replace("'", "''") + "'"

        Files.writeString(flag, "1", StandardCharsets.UTF_8)

        val src = payload.toAbsolutePath().normalize().pathString
        val dir = installDir.toAbsolutePath().normalize().pathString
        val exe = relaunchExe.toAbsolutePath().normalize().pathString
        val altExe = installDir.resolve(EXE_NAME).toAbsolutePath().normalize().pathString
        val flagPath = flag.toAbsolutePath().normalize().pathString
        val stagingPath = stagingDir.toAbsolutePath().normalize().pathString
        val zipAbs = zipPath.toAbsolutePath().normalize().pathString

        val script = """
            ${'$'}ErrorActionPreference = 'SilentlyContinue'
            ${'$'}pidToWait = $launcherPid
            ${'$'}src = ${psLit(src)}
            ${'$'}dir = ${psLit(dir)}
            ${'$'}exe = ${psLit(exe)}
            ${'$'}altExe = ${psLit(altExe)}
            ${'$'}flag = ${psLit(flagPath)}
            ${'$'}staging = ${psLit(stagingPath)}
            ${'$'}zip = ${psLit(zipAbs)}
            ${'$'}deadline = (Get-Date).AddMinutes(5)
            while ((Get-Date) -lt ${'$'}deadline) {
              if (-not (Test-Path -LiteralPath ${'$'}flag)) { exit 0 }
              if (-not (Get-Process -Id ${'$'}pidToWait -ErrorAction SilentlyContinue)) { break }
              Start-Sleep -Milliseconds 200
            }
            if (-not (Test-Path -LiteralPath ${'$'}flag)) { exit 0 }
            Remove-Item -LiteralPath ${'$'}flag -Force -ErrorAction SilentlyContinue
            if (-not (Test-Path -LiteralPath ${'$'}src)) { exit 1 }
            Start-Sleep -Milliseconds 300
            # Fast in-place replace of launcher files (skip user data outside the app tree).
            & robocopy ${'$'}src ${'$'}dir /E /IS /IT /R:2 /W:1 /NFL /NDL /NJH /NJS /NP | Out-Null
            ${'$'}code = ${'$'}LASTEXITCODE
            if (${'$'}code -ge 8) { exit ${'$'}code }
            Start-Sleep -Milliseconds 200
            if (Test-Path -LiteralPath ${'$'}exe) {
              Start-Process -FilePath ${'$'}exe -WorkingDirectory ${'$'}dir
            } elseif (Test-Path -LiteralPath ${'$'}altExe) {
              Start-Process -FilePath ${'$'}altExe -WorkingDirectory ${'$'}dir
            }
            Remove-Item -LiteralPath ${'$'}zip -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath ${'$'}staging -Recurse -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath (Join-Path (Split-Path ${'$'}flag) '$APPLY_VBS') -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath (Join-Path (Split-Path ${'$'}flag) '$APPLY_PS1') -Force -ErrorAction SilentlyContinue
        """.trimIndent().replace("\n", "\r\n")
        ps1.writeText(script)

        writeHiddenLauncher(vbs, ps1)
        startHidden(vbs, installDir)
    }

    /** Legacy Setup.exe silent install path. */
    fun scheduleInstallAndRestart(
        installer: Path,
        installDir: Path,
        relaunchExe: Path,
        launcherPid: Long,
    ) {
        require(installer.exists()) { "Установщик не найден" }
        val updateDir = installDir.resolve("update").also { it.createDirectories() }
        val ps1 = updateDir.resolve(APPLY_PS1)
        val vbs = updateDir.resolve(APPLY_VBS)
        val flag = updateDir.resolve(UPDATE_FLAG)

        fun psLit(path: String): String = "'" + path.replace("'", "''") + "'"

        Files.writeString(flag, "1", StandardCharsets.UTF_8)

        val setup = installer.toAbsolutePath().normalize().pathString
        val dir = installDir.toAbsolutePath().normalize().pathString
        val exe = relaunchExe.toAbsolutePath().normalize().pathString
        val altExe = installDir.resolve(EXE_NAME).toAbsolutePath().normalize().pathString
        val flagPath = flag.toAbsolutePath().normalize().pathString

        val script = """
            ${'$'}ErrorActionPreference = 'SilentlyContinue'
            ${'$'}pidToWait = $launcherPid
            ${'$'}setup = ${psLit(setup)}
            ${'$'}dir = ${psLit(dir)}
            ${'$'}exe = ${psLit(exe)}
            ${'$'}altExe = ${psLit(altExe)}
            ${'$'}flag = ${psLit(flagPath)}
            ${'$'}deadline = (Get-Date).AddMinutes(10)
            while ((Get-Date) -lt ${'$'}deadline) {
              if (-not (Test-Path -LiteralPath ${'$'}flag)) { exit 0 }
              if (-not (Get-Process -Id ${'$'}pidToWait -ErrorAction SilentlyContinue)) { break }
              Start-Sleep -Milliseconds 250
            }
            if (-not (Test-Path -LiteralPath ${'$'}flag)) { exit 0 }
            Remove-Item -LiteralPath ${'$'}flag -Force -ErrorAction SilentlyContinue
            if (-not (Test-Path -LiteralPath ${'$'}setup)) { exit 0 }
            Start-Sleep -Milliseconds 400
            ${'$'}args = @('/VERYSILENT','/SUPPRESSMSGBOXES','/NORESTART','/CLOSEAPPLICATIONS','/FORCECLOSEAPPLICATIONS',('/DIR=' + ${'$'}dir))
            Start-Process -FilePath ${'$'}setup -ArgumentList ${'$'}args -Wait -WindowStyle Hidden | Out-Null
            Start-Sleep -Milliseconds 400
            if (Test-Path -LiteralPath ${'$'}exe) { Start-Process -FilePath ${'$'}exe }
            elseif (Test-Path -LiteralPath ${'$'}altExe) { Start-Process -FilePath ${'$'}altExe }
            Remove-Item -LiteralPath ${'$'}setup -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath (Join-Path (Split-Path ${'$'}flag) '$APPLY_VBS') -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath (Join-Path (Split-Path ${'$'}flag) '$APPLY_PS1') -Force -ErrorAction SilentlyContinue
        """.trimIndent().replace("\n", "\r\n")
        ps1.writeText(script)
        writeHiddenLauncher(vbs, ps1)
        startHidden(vbs, installDir)
    }

    fun cancelPendingRestart() {
        runCatching {
            val paths = resolveInstallPaths()
            val updateDir = paths.installDir.resolve("update")
            updateDir.resolve(UPDATE_FLAG).deleteIfExists()
            updateDir.resolve(APPLY_PS1).deleteIfExists()
            updateDir.resolve(APPLY_VBS).deleteIfExists()
            // Legacy names from older builds
            updateDir.resolve("run-update.ps1").deleteIfExists()
            updateDir.resolve("run-update.vbs").deleteIfExists()
            ProcessHandle.allProcesses().forEach { ph ->
                val cmd = ph.info().commandLine().orElse("")
                if (cmd.contains(APPLY_PS1, ignoreCase = true) ||
                    cmd.contains("run-update.ps1", ignoreCase = true)
                ) {
                    ph.destroy()
                }
            }
        }
    }

    fun resolveInstallPaths(): InstallPaths {
        val command = ProcessHandle.current().info().command().orElse(null)
            ?.takeIf { it.isNotBlank() }
            ?.let { Path.of(it).toAbsolutePath().normalize() }

        val exe = when {
            command != null && command.fileName.toString().endsWith(".exe", ignoreCase = true) -> command
            else -> {
                val javaHome = Path.of(System.getProperty("java.home")).toAbsolutePath().normalize()
                val candidates = listOf(
                    javaHome.parent?.resolve(EXE_NAME),
                    javaHome.parent?.parent?.resolve(EXE_NAME),
                )
                candidates.firstOrNull { it != null && it.exists() }
                    ?: error("Не удалось определить путь к лаунчеру. Скачайте установщик с GitHub.")
            }
        }
        val installDir = exe.parent ?: error("Нет папки установки")
        return InstallPaths(installDir = installDir, relaunchExe = exe)
    }

    data class InstallPaths(
        val installDir: Path,
        val relaunchExe: Path,
    )

    private fun writeHiddenLauncher(vbs: Path, ps1: Path) {
        val psPath = ps1.toAbsolutePath().normalize().pathString
        val body = buildString {
            appendLine("Set sh = CreateObject(\"WScript.Shell\")")
            append("sh.Run \"powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File \"\"")
            append(psPath.replace("\"", "\"\""))
            append("\"\"\", 0, False")
            append("\r\n")
        }
        Files.writeString(vbs, body, StandardCharsets.UTF_8)
    }

    private fun startHidden(vbs: Path, cwd: Path) {
        ProcessBuilder(
            "wscript.exe",
            "//B",
            "//Nologo",
            vbs.toAbsolutePath().normalize().pathString,
        ).apply {
            directory(cwd.toFile())
            redirectOutput(ProcessBuilder.Redirect.DISCARD)
            redirectError(ProcessBuilder.Redirect.DISCARD)
        }.start()
    }

    /** ZIP may contain files at root or a single top-level folder. */
    private fun resolveAppRoot(staging: Path): Path {
        val direct = staging.resolve(EXE_NAME)
        if (direct.exists()) return staging
        val kids = staging.listDirectoryEntries()
        if (kids.size == 1 && kids[0].isDirectory()) {
            val nested = kids[0]
            if (nested.resolve(EXE_NAME).exists()) return nested
        }
        return staging
    }

    private fun extractZipFlat(zipPath: Path, dest: Path) {
        dest.createDirectories()
        ZipInputStream(Files.newInputStream(zipPath), StandardCharsets.ISO_8859_1).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                var name = entry.name.replace('\\', '/').trimStart('/')
                if (name.isBlank() || name.contains("..")) {
                    zis.closeEntry()
                    continue
                }
                val out = dest.resolve(name)
                if (entry.isDirectory) {
                    out.createDirectories()
                } else {
                    out.parent?.createDirectories()
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING)
                }
                zis.closeEntry()
            }
        }
    }

    private fun deleteRecursively(path: Path) {
        if (!path.exists()) return
        runCatching {
            Files.walk(path).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }
}
