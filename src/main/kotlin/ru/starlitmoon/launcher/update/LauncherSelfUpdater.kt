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
    private const val APPLY_LOG = "apply-update.log"

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
        val log = updateDir.resolve(APPLY_LOG)

        fun psLit(path: String): String = "'" + path.replace("'", "''") + "'"

        Files.writeString(flag, "1", StandardCharsets.UTF_8)

        val src = payload.toAbsolutePath().normalize().pathString
        val dir = installDir.toAbsolutePath().normalize().pathString
        val exe = relaunchExe.toAbsolutePath().normalize().pathString
        val altExe = installDir.resolve(EXE_NAME).toAbsolutePath().normalize().pathString
        val flagPath = flag.toAbsolutePath().normalize().pathString
        val stagingPath = stagingDir.toAbsolutePath().normalize().pathString
        val zipAbs = zipPath.toAbsolutePath().normalize().pathString
        val logPath = log.toAbsolutePath().normalize().pathString

        val script = """
            ${'$'}ErrorActionPreference = 'Continue'
            ${'$'}log = ${psLit(logPath)}
            function Log(${'$'}msg) {
              ${'$'}line = ('[{0}] {1}' -f (Get-Date -Format 'HH:mm:ss'), ${'$'}msg)
              Add-Content -LiteralPath ${'$'}log -Value ${'$'}line -Encoding UTF8 -ErrorAction SilentlyContinue
            }
            Log 'apply-update started'
            ${'$'}pidToWait = $launcherPid
            ${'$'}src = ${psLit(src)}
            ${'$'}dir = ${psLit(dir)}
            ${'$'}exe = ${psLit(exe)}
            ${'$'}altExe = ${psLit(altExe)}
            ${'$'}flag = ${psLit(flagPath)}
            ${'$'}staging = ${psLit(stagingPath)}
            ${'$'}zip = ${psLit(zipAbs)}
            ${'$'}deadline = (Get-Date).AddMinutes(3)
            while ((Get-Date) -lt ${'$'}deadline) {
              if (-not (Test-Path -LiteralPath ${'$'}flag)) { Log 'flag removed — abort'; exit 0 }
              ${'$'}alive = ${'$'}false
              try {
                if (Get-Process -Id ${'$'}pidToWait -ErrorAction Stop) { ${'$'}alive = ${'$'}true }
              } catch { ${'$'}alive = ${'$'}false }
              if (-not ${'$'}alive) {
                # Also wait until install-dir launcher EXE releases file locks.
                ${'$'}lockers = @(Get-Process -Name 'StarlitMoonLauncher','java','javaw' -ErrorAction SilentlyContinue |
                  Where-Object {
                    ${'$'}p = ${'$'}_.Path
                    if ([string]::IsNullOrWhiteSpace(${'$'}p)) { ${'$'}false }
                    else { ${'$'}p.StartsWith(${'$'}dir, [System.StringComparison]::OrdinalIgnoreCase) }
                  })
                if (${'$'}lockers.Count -eq 0) { break }
                Log ("waiting for lockers: " + ((${'$'}lockers | ForEach-Object { ${'$'}_.Id }) -join ','))
              }
              Start-Sleep -Milliseconds 250
            }
            if (-not (Test-Path -LiteralPath ${'$'}flag)) { Log 'flag gone after wait'; exit 0 }
            if (-not (Test-Path -LiteralPath ${'$'}src)) { Log 'staging missing'; exit 1 }
            Log 'copying files via robocopy'
            Start-Sleep -Milliseconds 500
            ${'$'}copied = ${'$'}false
            for (${'$'}i = 1; ${'$'}i -le 8; ${'$'}i++) {
              & robocopy ${'$'}src ${'$'}dir /E /IS /IT /R:1 /W:1 /NFL /NDL /NJH /NJS /NP | Out-Null
              ${'$'}code = ${'$'}LASTEXITCODE
              Log ("robocopy attempt ${'$'}i exit=${'$'}code")
              if (${'$'}code -lt 8) { ${'$'}copied = ${'$'}true; break }
              Start-Sleep -Milliseconds 700
            }
            if (-not ${'$'}copied) { Log 'robocopy failed'; exit 8 }
            ${'$'}launch = ${'$'}null
            if (Test-Path -LiteralPath ${'$'}exe) { ${'$'}launch = ${'$'}exe }
            elseif (Test-Path -LiteralPath ${'$'}altExe) { ${'$'}launch = ${'$'}altExe }
            if (${'$'}null -eq ${'$'}launch) { Log 'exe missing after copy'; exit 1 }
            Log ("starting ${'$'}launch")
            Start-Sleep -Milliseconds 300
            try {
              Start-Process -FilePath ${'$'}launch -WorkingDirectory ${'$'}dir | Out-Null
              Log 'Start-Process ok'
            } catch {
              Log ("Start-Process failed: " + ${'$'}_.Exception.Message)
              Start-Process -FilePath 'cmd.exe' -ArgumentList @('/c','start','','/D',${'$'}dir,${'$'}launch) -WindowStyle Hidden | Out-Null
            }
            Remove-Item -LiteralPath ${'$'}flag -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath ${'$'}zip -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath ${'$'}staging -Recurse -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath (Join-Path (Split-Path ${'$'}flag) '$APPLY_PS1') -Force -ErrorAction SilentlyContinue
            Log 'done'
        """.trimIndent().replace("\n", "\r\n")
        ps1.writeText(script)
        startDetachedPowerShell(ps1, installDir, log)
        onProgress("Перезапуск…")
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
        val flag = updateDir.resolve(UPDATE_FLAG)
        val log = updateDir.resolve(APPLY_LOG)

        fun psLit(path: String): String = "'" + path.replace("'", "''") + "'"

        Files.writeString(flag, "1", StandardCharsets.UTF_8)

        val setup = installer.toAbsolutePath().normalize().pathString
        val dir = installDir.toAbsolutePath().normalize().pathString
        val exe = relaunchExe.toAbsolutePath().normalize().pathString
        val altExe = installDir.resolve(EXE_NAME).toAbsolutePath().normalize().pathString
        val flagPath = flag.toAbsolutePath().normalize().pathString
        val logPath = log.toAbsolutePath().normalize().pathString

        val script = """
            ${'$'}ErrorActionPreference = 'Continue'
            ${'$'}log = ${psLit(logPath)}
            function Log(${'$'}msg) {
              ${'$'}line = ('[{0}] {1}' -f (Get-Date -Format 'HH:mm:ss'), ${'$'}msg)
              Add-Content -LiteralPath ${'$'}log -Value ${'$'}line -Encoding UTF8 -ErrorAction SilentlyContinue
            }
            Log 'setup-update started'
            ${'$'}pidToWait = $launcherPid
            ${'$'}setup = ${psLit(setup)}
            ${'$'}dir = ${psLit(dir)}
            ${'$'}exe = ${psLit(exe)}
            ${'$'}altExe = ${psLit(altExe)}
            ${'$'}flag = ${psLit(flagPath)}
            ${'$'}deadline = (Get-Date).AddMinutes(10)
            while ((Get-Date) -lt ${'$'}deadline) {
              if (-not (Test-Path -LiteralPath ${'$'}flag)) { exit 0 }
              try { Get-Process -Id ${'$'}pidToWait -ErrorAction Stop | Out-Null } catch { break }
              Start-Sleep -Milliseconds 250
            }
            if (-not (Test-Path -LiteralPath ${'$'}flag)) { exit 0 }
            if (-not (Test-Path -LiteralPath ${'$'}setup)) { Log 'setup missing'; exit 0 }
            Log 'running Inno Setup'
            Start-Sleep -Milliseconds 400
            ${'$'}args = @('/VERYSILENT','/SUPPRESSMSGBOXES','/NORESTART','/CLOSEAPPLICATIONS','/FORCECLOSEAPPLICATIONS',('/DIR=' + ${'$'}dir))
            Start-Process -FilePath ${'$'}setup -ArgumentList ${'$'}args -Wait -WindowStyle Hidden | Out-Null
            Start-Sleep -Milliseconds 400
            if (Test-Path -LiteralPath ${'$'}exe) { Start-Process -FilePath ${'$'}exe -WorkingDirectory ${'$'}dir }
            elseif (Test-Path -LiteralPath ${'$'}altExe) { Start-Process -FilePath ${'$'}altExe -WorkingDirectory ${'$'}dir }
            Remove-Item -LiteralPath ${'$'}flag -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath ${'$'}setup -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath (Join-Path (Split-Path ${'$'}flag) '$APPLY_PS1') -Force -ErrorAction SilentlyContinue
            Log 'done'
        """.trimIndent().replace("\n", "\r\n")
        ps1.writeText(script)
        startDetachedPowerShell(ps1, installDir, log)
    }

    fun cancelPendingRestart() {
        runCatching {
            val paths = resolveInstallPaths()
            val updateDir = paths.installDir.resolve("update")
            updateDir.resolve(UPDATE_FLAG).deleteIfExists()
            updateDir.resolve(APPLY_PS1).deleteIfExists()
            // Legacy names from older builds
            updateDir.resolve("apply-update.vbs").deleteIfExists()
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
            command != null &&
                command.fileName.toString().equals(EXE_NAME, ignoreCase = true) -> command
            command != null && command.fileName.toString().endsWith(".exe", ignoreCase = true) &&
                !command.fileName.toString().equals("java.exe", ignoreCase = true) &&
                !command.fileName.toString().equals("javaw.exe", ignoreCase = true) -> {
                // Prefer sibling StarlitMoonLauncher.exe if we landed on a helper exe.
                command.parent?.resolve(EXE_NAME)?.takeIf { it.exists() } ?: command
            }
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

    /**
     * Launch PowerShell outside the launcher process tree so [exitProcess] / job-object
     * teardown cannot kill the updater mid-flight.
     */
    private fun startDetachedPowerShell(ps1: Path, cwd: Path, log: Path) {
        val psPath = ps1.toAbsolutePath().normalize().pathString
        val logPath = log.toAbsolutePath().normalize().pathString
        runCatching {
            Files.writeString(
                log,
                "[${java.time.LocalTime.now()}] spawning detached updater\r\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
        }
        // cmd `start` breaks away from the parent console/job so the updater survives exitProcess.
        val cmdLine = buildString {
            append("start \"StarlitMoonUpdate\" /MIN ")
            append("powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden ")
            append("-File \"")
            append(psPath.replace("\"", "\\\""))
            append("\"")
        }
        val started = runCatching {
            ProcessBuilder("cmd.exe", "/c", cmdLine)
                .directory(cwd.toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
                .waitFor()
            true
        }.getOrDefault(false)
        if (!started) {
            // Fallback: direct PowerShell (may die with parent on some setups).
            ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-WindowStyle",
                "Hidden",
                "-File",
                psPath,
            ).apply {
                directory(cwd.toFile())
                redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()))
                redirectError(ProcessBuilder.Redirect.appendTo(log.toFile()))
            }.start()
            Files.writeString(
                log,
                "fallback direct powershell used\r\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        } else {
            Files.writeString(
                log,
                "detached cmd start ok\r\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }
        // Give the shell a moment to spawn before the JVM tears down.
        Thread.sleep(400)
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
        // Compose ZIP sometimes nests under app/ without exe at that level — keep staging.
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
