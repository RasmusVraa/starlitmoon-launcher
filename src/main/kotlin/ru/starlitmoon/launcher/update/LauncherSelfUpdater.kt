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
import ru.starlitmoon.launcher.LauncherLog
import ru.starlitmoon.launcher.LauncherVersion
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.system.exitProcess

/**
 * Downloads an update package and applies it **after** the JVM exits.
 *
 * Never extracts ZIP inside the launcher process — that was crashing the UI at ~93%.
 * All apply work runs in a detached `.cmd` helper.
 */
object LauncherSelfUpdater {
    private const val EXE_NAME = "StarlitMoonLauncher.exe"
    private const val UPDATE_FLAG = "pending.flag"
    private const val APPLY_CMD = "apply-update.cmd"
    private const val APPLY_LOG = "apply-update.log"

    val pendingApply: AtomicBoolean = AtomicBoolean(false)

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
                if (written < 1_000_000L) {
                    error("Файл обновления слишком маленький ($written байт)")
                }
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
                onProgress(1f, "Загрузка завершена")
            }
        } finally {
            client.close()
        }
    }

    /**
     * Schedule ZIP apply entirely outside the JVM (extract + robocopy + relaunch).
     * Does **not** unpack in-process.
     */
    fun scheduleZipUpdateAndRestart(
        zipPath: Path,
        stagingDir: Path,
        installDir: Path,
        relaunchExe: Path,
        launcherPid: Long,
    ) {
        require(zipPath.exists()) { "Архив обновления не найден" }
        val updateDir = installDir.resolve("update").also { it.createDirectories() }
        val flag = updateDir.resolve(UPDATE_FLAG)
        val cmd = updateDir.resolve(APPLY_CMD)
        val log = updateDir.resolve(APPLY_LOG)

        Files.writeString(flag, "1", StandardCharsets.UTF_8)
        // Clean staging in the helper, not here (avoids locks / UI failures).
        stagingDir.parent?.createDirectories()

        val zip = zipPath.toAbsolutePath().normalize().pathString
        val staging = stagingDir.toAbsolutePath().normalize().pathString
        val dir = installDir.toAbsolutePath().normalize().pathString
        val exe = relaunchExe.toAbsolutePath().normalize().pathString
        val altExe = installDir.resolve(EXE_NAME).toAbsolutePath().normalize().pathString
        val flagPath = flag.toAbsolutePath().normalize().pathString
        val logPath = log.toAbsolutePath().normalize().pathString

        val batch = buildString {
            appendLine("@echo off")
            appendLine("setlocal EnableExtensions EnableDelayedExpansion")
            appendLine("set \"LOG=$logPath\"")
            appendLine("echo [%TIME%] zip-apply start pid=$launcherPid>>\"%LOG%\"")
            appendLine("set \"FLAG=$flagPath\"")
            appendLine("set \"ZIP=$zip\"")
            appendLine("set \"STAGING=$staging\"")
            appendLine("set \"DIR=$dir\"")
            appendLine("set \"EXE=$exe\"")
            appendLine("set \"ALTEXE=$altExe\"")
            appendLine("set /a TRIES=0")
            appendLine(":waitpid")
            appendLine("if not exist \"%FLAG%\" (echo [%TIME%] flag gone>>\"%LOG%\" & exit /b 0)")
            appendLine("tasklist /FI \"PID eq $launcherPid\" 2>NUL | findstr /I \"$launcherPid\" >NUL")
            appendLine("if not errorlevel 1 (")
            appendLine("  set /a TRIES+=1")
            appendLine("  if !TRIES! GEQ 240 (echo [%TIME%] wait timeout>>\"%LOG%\" & goto extract)")
            appendLine("  ping -n 2 127.0.0.1 >NUL")
            appendLine("  goto waitpid")
            appendLine(")")
            appendLine("echo [%TIME%] pid gone>>\"%LOG%\"")
            appendLine("ping -n 3 127.0.0.1 >NUL")
            appendLine(":extract")
            appendLine("if not exist \"%ZIP%\" (echo [%TIME%] zip missing>>\"%LOG%\" & exit /b 1)")
            appendLine("if exist \"%STAGING%\" rmdir /s /q \"%STAGING%\" >NUL 2>&1")
            appendLine("mkdir \"%STAGING%\" >NUL 2>&1")
            appendLine("echo [%TIME%] extracting>>\"%LOG%\"")
            // Prefer tar (Win10+), fallback to PowerShell Expand-Archive.
            appendLine("tar -xf \"%ZIP%\" -C \"%STAGING%\" 1>>\"%LOG%\" 2>&1")
            appendLine("if errorlevel 1 (")
            appendLine("  echo [%TIME%] tar failed, trying Expand-Archive>>\"%LOG%\"")
            appendLine("  powershell -NoProfile -ExecutionPolicy Bypass -Command \"Expand-Archive -LiteralPath '%ZIP%' -DestinationPath '%STAGING%' -Force\" 1>>\"%LOG%\" 2>&1")
            appendLine("  if errorlevel 1 (echo [%TIME%] extract failed>>\"%LOG%\" & exit /b 1)")
            appendLine(")")
            appendLine("set \"SRC=\"")
            appendLine("if exist \"%STAGING%\\$EXE_NAME\" set \"SRC=%STAGING%\"")
            appendLine("if not defined SRC for /d %%D in (\"%STAGING%\\*\") do (")
            appendLine("  if exist \"%%~fD\\$EXE_NAME\" set \"SRC=%%~fD\"")
            appendLine(")")
            appendLine("if not defined SRC (")
            appendLine("  echo [%TIME%] exe not found in staging>>\"%LOG%\"")
            appendLine("  dir /s /b \"%STAGING%\">>\"%LOG%\"")
            appendLine("  exit /b 1")
            appendLine(")")
            appendLine("echo [%TIME%] SRC=!SRC!>>\"%LOG%\"")
            appendLine("set /a COPYTRY=0")
            appendLine(":copyloop")
            appendLine("set /a COPYTRY+=1")
            appendLine("echo [%TIME%] robocopy !COPYTRY!>>\"%LOG%\"")
            appendLine("robocopy \"!SRC!\" \"%DIR%\" /E /IS /IT /R:3 /W:2 /NFL /NDL /NJH /NJS /NP >>\"%LOG%\" 2>&1")
            appendLine("set \"RC=!ERRORLEVEL!\"")
            appendLine("echo [%TIME%] robocopy rc=!RC!>>\"%LOG%\"")
            appendLine("if !RC! LSS 8 goto launch")
            appendLine("if !COPYTRY! LSS 15 (ping -n 2 127.0.0.1 >NUL & goto copyloop)")
            appendLine("echo [%TIME%] robocopy failed>>\"%LOG%\" & exit /b 8")
            appendLine(":launch")
            appendLine("if exist \"%EXE%\" (")
            appendLine("  echo [%TIME%] start EXE>>\"%LOG%\"")
            appendLine("  start \"\" /D \"%DIR%\" \"%EXE%\"")
            appendLine(") else if exist \"%ALTEXE%\" (")
            appendLine("  echo [%TIME%] start ALTEXE>>\"%LOG%\"")
            appendLine("  start \"\" /D \"%DIR%\" \"%ALTEXE%\"")
            appendLine(") else (")
            appendLine("  echo [%TIME%] no exe to launch>>\"%LOG%\" & exit /b 1")
            appendLine(")")
            appendLine("del /f /q \"%FLAG%\" >NUL 2>&1")
            appendLine("del /f /q \"%ZIP%\" >NUL 2>&1")
            appendLine("rmdir /s /q \"%STAGING%\" >NUL 2>&1")
            appendLine("echo [%TIME%] done>>\"%LOG%\"")
            appendLine("ping -n 2 127.0.0.1 >NUL")
            appendLine("del /f /q \"%~f0\" >NUL 2>&1")
        }.replace("\n", "\r\n")

        // Write as OEM-safe ASCII-ish; avoid UTF-8 BOM breaking @echo off
        Files.write(cmd, batch.toByteArray(StandardCharsets.UTF_8))
        startDetachedCmd(cmd, installDir, log)
        pendingApply.set(true)
        LauncherLog.info("Self-update: ZIP helper scheduled (no in-process extract)")
    }

    fun scheduleInstallAndRestart(
        installer: Path,
        installDir: Path,
        relaunchExe: Path,
        launcherPid: Long,
    ) {
        require(installer.exists()) { "Установщик не найден" }
        val updateDir = installDir.resolve("update").also { it.createDirectories() }
        val cmd = updateDir.resolve(APPLY_CMD)
        val flag = updateDir.resolve(UPDATE_FLAG)
        val log = updateDir.resolve(APPLY_LOG)
        Files.writeString(flag, "1", StandardCharsets.UTF_8)

        val setup = installer.toAbsolutePath().normalize().pathString
        val dir = installDir.toAbsolutePath().normalize().pathString
        val exe = relaunchExe.toAbsolutePath().normalize().pathString
        val altExe = installDir.resolve(EXE_NAME).toAbsolutePath().normalize().pathString
        val flagPath = flag.toAbsolutePath().normalize().pathString
        val logPath = log.toAbsolutePath().normalize().pathString

        val batch = buildString {
            appendLine("@echo off")
            appendLine("setlocal EnableExtensions EnableDelayedExpansion")
            appendLine("set \"LOG=$logPath\"")
            appendLine("echo [%TIME%] setup-apply start>>\"%LOG%\"")
            appendLine("set \"FLAG=$flagPath\"")
            appendLine("set \"SETUP=$setup\"")
            appendLine("set \"DIR=$dir\"")
            appendLine("set \"EXE=$exe\"")
            appendLine("set \"ALTEXE=$altExe\"")
            appendLine("set /a TRIES=0")
            appendLine(":waitpid")
            appendLine("if not exist \"%FLAG%\" exit /b 0")
            appendLine("tasklist /FI \"PID eq $launcherPid\" 2>NUL | findstr /I \"$launcherPid\" >NUL")
            appendLine("if not errorlevel 1 (")
            appendLine("  set /a TRIES+=1")
            appendLine("  if !TRIES! GEQ 300 goto runsetup")
            appendLine("  ping -n 2 127.0.0.1 >NUL")
            appendLine("  goto waitpid")
            appendLine(")")
            appendLine("ping -n 2 127.0.0.1 >NUL")
            appendLine(":runsetup")
            appendLine("if not exist \"%SETUP%\" (echo [%TIME%] setup missing>>\"%LOG%\" & exit /b 0)")
            appendLine("echo [%TIME%] running Inno>>\"%LOG%\"")
            appendLine("\"%SETUP%\" /VERYSILENT /SUPPRESSMSGBOXES /NORESTART /CLOSEAPPLICATIONS /FORCECLOSEAPPLICATIONS /DIR=\"%DIR%\"")
            appendLine("set \"RC=!ERRORLEVEL!\"")
            appendLine("echo [%TIME%] setup rc=!RC!>>\"%LOG%\"")
            appendLine("ping -n 2 127.0.0.1 >NUL")
            appendLine("if exist \"%EXE%\" (start \"\" /D \"%DIR%\" \"%EXE%\") else if exist \"%ALTEXE%\" (start \"\" /D \"%DIR%\" \"%ALTEXE%\")")
            appendLine("del /f /q \"%FLAG%\" >NUL 2>&1")
            appendLine("del /f /q \"%SETUP%\" >NUL 2>&1")
            appendLine("echo [%TIME%] done>>\"%LOG%\"")
            appendLine("ping -n 2 127.0.0.1 >NUL")
            appendLine("del /f /q \"%~f0\" >NUL 2>&1")
        }.replace("\n", "\r\n")
        Files.write(cmd, batch.toByteArray(StandardCharsets.UTF_8))
        startDetachedCmd(cmd, installDir, log)
        pendingApply.set(true)
        LauncherLog.info("Self-update: Setup helper scheduled")
    }

    fun cancelPendingRestart() {
        if (pendingApply.get()) {
            LauncherLog.info("Self-update: skip cancel — apply pending")
            return
        }
        runCatching {
            val paths = resolveInstallPaths()
            val updateDir = paths.installDir.resolve("update")
            val flag = updateDir.resolve(UPDATE_FLAG)
            if (flag.exists()) {
                val ageMs = runCatching {
                    System.currentTimeMillis() - Files.getLastModifiedTime(flag).toMillis()
                }.getOrDefault(Long.MAX_VALUE)
                val applyRunning = ProcessHandle.allProcesses().anyMatch { ph ->
                    ph.info().commandLine().orElse("").contains(APPLY_CMD, ignoreCase = true)
                }
                if (applyRunning || ageMs < 180_000L) {
                    LauncherLog.info("Self-update: leave in-flight apply alone")
                    return
                }
            }
            flag.deleteIfExists()
            updateDir.resolve(APPLY_CMD).deleteIfExists()
            updateDir.resolve("apply-update.ps1").deleteIfExists()
            updateDir.resolve("apply-update.vbs").deleteIfExists()
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
                command.parent?.resolve(EXE_NAME)?.takeIf { it.exists() } ?: command
            }
            else -> {
                val javaHome = Path.of(System.getProperty("java.home")).toAbsolutePath().normalize()
                val candidates = listOf(
                    javaHome.parent?.resolve(EXE_NAME),
                    javaHome.parent?.parent?.resolve(EXE_NAME),
                )
                candidates.firstOrNull { it != null && it.exists() }
                    ?: error("Не удалось определить путь к лаунчеру. Установите Setup с GitHub.")
            }
        }
        val installDir = exe.parent ?: error("Нет папки установки")
        return InstallPaths(installDir = installDir, relaunchExe = exe)
    }

    data class InstallPaths(val installDir: Path, val relaunchExe: Path)

    private fun startDetachedCmd(cmdFile: Path, cwd: Path, log: Path) {
        val cmdPath = cmdFile.toAbsolutePath().normalize().pathString
        runCatching {
            Files.writeString(
                log,
                "[${java.time.LocalTime.now()}] spawn $cmdPath\r\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
        }
        // Single /c string so paths with spaces (Program Files) stay quoted for `start`.
        val line = "start \"StarlitUpdate\" /MIN \"$cmdPath\""
        val pb = ProcessBuilder("cmd.exe", "/c", line)
        pb.directory(cwd.toFile())
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
        pb.redirectError(ProcessBuilder.Redirect.DISCARD)
        val proc = pb.start()
        proc.waitFor(8, TimeUnit.SECONDS)
        Thread.sleep(1000)
        LauncherLog.info("Self-update: detached helper launched")
    }

    /** Must be called OUTSIDE runCatching — exitProcess can throw into onFailure otherwise. */
    fun exitForApply(): Nothing {
        pendingApply.set(true)
        try {
            Thread.sleep(500)
        } catch (_: InterruptedException) {
        }
        exitProcess(0)
    }
}
