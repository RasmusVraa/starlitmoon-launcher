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
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.system.exitProcess

/**
 * Downloads an update package and applies it after the launcher process exits.
 *
 * Preferred path: app ZIP → extract → robocopy into install dir → restart.
 * Fallback: Inno Setup silent install (older releases).
 */
object LauncherSelfUpdater {
    private const val EXE_NAME = "StarlitMoonLauncher.exe"
    private const val UPDATE_FLAG = "pending.flag"
    private const val APPLY_CMD = "apply-update.cmd"
    private const val APPLY_PS1 = "apply-update.ps1"
    private const val APPLY_LOG = "apply-update.log"

    /** Set when an apply helper was spawned — Main/boot must not cancel it. */
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
                    error("Файл обновления слишком маленький ($written байт) — возможно, ошибка GitHub")
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
     * Extract ZIP into [stagingDir], schedule file replace + restart, then hard-exit the JVM.
     */
    fun prepareZipUpdateAndRestart(
        zipPath: Path,
        stagingDir: Path,
        installDir: Path,
        relaunchExe: Path,
        launcherPid: Long,
        onProgress: (String, Float) -> Unit = { _, _ -> },
    ) {
        require(zipPath.exists()) { "Архив обновления не найден" }
        LauncherLog.info("Self-update: extract ${zipPath.fileName} → $stagingDir")
        onProgress("Распаковка обновления…", 0.93f)
        deleteRecursively(stagingDir)
        stagingDir.createDirectories()
        extractZipFlat(zipPath, stagingDir)
        onProgress("Проверка файлов…", 0.96f)
        val payload = resolveAppRoot(stagingDir)
        val stagedExe = payload.resolve(EXE_NAME)
        require(stagedExe.exists()) {
            "В архиве нет $EXE_NAME (корень=${payload.fileName}, файлов=${payload.listDirectoryEntries().size})"
        }

        val updateDir = installDir.resolve("update").also { it.createDirectories() }
        val flag = updateDir.resolve(UPDATE_FLAG)
        val cmd = updateDir.resolve(APPLY_CMD)
        val log = updateDir.resolve(APPLY_LOG)

        Files.writeString(flag, "1", StandardCharsets.UTF_8)

        val src = payload.toAbsolutePath().normalize().pathString
        val dir = installDir.toAbsolutePath().normalize().pathString
        val exe = relaunchExe.toAbsolutePath().normalize().pathString
        val altExe = installDir.resolve(EXE_NAME).toAbsolutePath().normalize().pathString
        val flagPath = flag.toAbsolutePath().normalize().pathString
        val stagingPath = stagingDir.toAbsolutePath().normalize().pathString
        val zipAbs = zipPath.toAbsolutePath().normalize().pathString
        val logPath = log.toAbsolutePath().normalize().pathString

        // Pure CMD — survives JVM exit better than PowerShell child trees on some setups.
        val batch = buildString {
            appendLine("@echo off")
            appendLine("setlocal EnableExtensions EnableDelayedExpansion")
            appendLine("set \"LOG=$logPath\"")
            appendLine("echo [%TIME%] apply-update started pid=$launcherPid>>\"%LOG%\"")
            appendLine("set \"FLAG=$flagPath\"")
            appendLine("set \"SRC=$src\"")
            appendLine("set \"DIR=$dir\"")
            appendLine("set \"EXE=$exe\"")
            appendLine("set \"ALTEXE=$altExe\"")
            appendLine("set \"STAGING=$stagingPath\"")
            appendLine("set \"ZIP=$zipAbs\"")
            appendLine("set /a TRIES=0")
            appendLine(":waitpid")
            appendLine("if not exist \"%FLAG%\" (")
            appendLine("  echo [%TIME%] flag gone — abort>>\"%LOG%\"")
            appendLine("  exit /b 0")
            appendLine(")")
            appendLine("tasklist /FI \"PID eq $launcherPid\" 2>NUL | findstr /I \"$launcherPid\" >NUL")
            appendLine("if not errorlevel 1 (")
            appendLine("  set /a TRIES+=1")
            appendLine("  if !TRIES! GEQ 180 (")
            appendLine("    echo [%TIME%] timeout waiting for pid>>\"%LOG%\"")
            appendLine("    goto docopy")
            appendLine("  )")
            appendLine("  ping -n 2 127.0.0.1 >NUL")
            appendLine("  goto waitpid")
            appendLine(")")
            appendLine("echo [%TIME%] launcher pid exited>>\"%LOG%\"")
            appendLine("ping -n 2 127.0.0.1 >NUL")
            appendLine(":docopy")
            appendLine("if not exist \"%SRC%\\$EXE_NAME\" (")
            appendLine("  echo [%TIME%] staging exe missing>>\"%LOG%\"")
            appendLine("  exit /b 1")
            appendLine(")")
            appendLine("set /a COPYTRY=0")
            appendLine(":copyloop")
            appendLine("set /a COPYTRY+=1")
            appendLine("echo [%TIME%] robocopy attempt !COPYTRY!>>\"%LOG%\"")
            appendLine("robocopy \"%SRC%\" \"%DIR%\" /E /IS /IT /R:2 /W:1 /NFL /NDL /NJH /NJS /NP >>\"%LOG%\" 2>&1")
            appendLine("set \"RC=!ERRORLEVEL!\"")
            appendLine("echo [%TIME%] robocopy exit=!RC!>>\"%LOG%\"")
            appendLine("if !RC! LSS 8 goto launch")
            appendLine("if !COPYTRY! LSS 12 (")
            appendLine("  ping -n 2 127.0.0.1 >NUL")
            appendLine("  goto copyloop")
            appendLine(")")
            appendLine("echo [%TIME%] robocopy failed>>\"%LOG%\"")
            appendLine("exit /b 8")
            appendLine(":launch")
            appendLine("if exist \"%EXE%\" (")
            appendLine("  echo [%TIME%] starting %EXE%>>\"%LOG%\"")
            appendLine("  start \"\" /D \"%DIR%\" \"%EXE%\"")
            appendLine(") else if exist \"%ALTEXE%\" (")
            appendLine("  echo [%TIME%] starting %ALTEXE%>>\"%LOG%\"")
            appendLine("  start \"\" /D \"%DIR%\" \"%ALTEXE%\"")
            appendLine(") else (")
            appendLine("  echo [%TIME%] exe missing after copy>>\"%LOG%\"")
            appendLine("  exit /b 1")
            appendLine(")")
            appendLine("del /f /q \"%FLAG%\" >NUL 2>&1")
            appendLine("del /f /q \"%ZIP%\" >NUL 2>&1")
            appendLine("rmdir /s /q \"%STAGING%\" >NUL 2>&1")
            appendLine("echo [%TIME%] done>>\"%LOG%\"")
            appendLine("ping -n 2 127.0.0.1 >NUL")
            appendLine("del /f /q \"%~f0\" >NUL 2>&1")
        }.replace("\n", "\r\n")
        cmd.writeText(batch, Charsets.UTF_8)

        onProgress("Запуск установщика обновления…", 0.98f)
        startDetachedCmd(cmd, installDir, log)
        pendingApply.set(true)
        LauncherLog.info("Self-update: apply helper started, exiting JVM")
        onProgress("Перезапуск…", 1f)
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
            appendLine("setlocal EnableExtensions")
            appendLine("set \"LOG=$logPath\"")
            appendLine("echo [%TIME%] setup-update started>>\"%LOG%\"")
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
            appendLine("  if %TRIES% GEQ 300 goto runsetup")
            appendLine("  ping -n 2 127.0.0.1 >NUL")
            appendLine("  goto waitpid")
            appendLine(")")
            appendLine(":runsetup")
            appendLine("if not exist \"%SETUP%\" exit /b 0")
            appendLine("\"%SETUP%\" /VERYSILENT /SUPPRESSMSGBOXES /NORESTART /CLOSEAPPLICATIONS /FORCECLOSEAPPLICATIONS /DIR=\"%DIR%\"")
            appendLine("ping -n 2 127.0.0.1 >NUL")
            appendLine("if exist \"%EXE%\" (start \"\" \"%EXE%\") else if exist \"%ALTEXE%\" (start \"\" \"%ALTEXE%\")")
            appendLine("del /f /q \"%FLAG%\" >NUL 2>&1")
            appendLine("del /f /q \"%SETUP%\" >NUL 2>&1")
            appendLine("del /f /q \"%~f0\" >NUL 2>&1")
        }.replace("\n", "\r\n")
        cmd.writeText(batch, Charsets.UTF_8)
        startDetachedCmd(cmd, installDir, log)
        pendingApply.set(true)
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
            // If a fresh apply is mid-flight, leave it alone.
            if (flag.exists()) {
                val ageMs = runCatching {
                    System.currentTimeMillis() - Files.getLastModifiedTime(flag).toMillis()
                }.getOrDefault(Long.MAX_VALUE)
                val applyRunning = ProcessHandle.allProcesses().anyMatch { ph ->
                    val c = ph.info().commandLine().orElse("")
                    c.contains(APPLY_CMD, ignoreCase = true) ||
                        c.contains(APPLY_PS1, ignoreCase = true)
                }
                if (applyRunning || ageMs < 120_000L) {
                    LauncherLog.info("Self-update: leave in-flight apply alone (age=${ageMs}ms)")
                    return
                }
            }
            flag.deleteIfExists()
            updateDir.resolve(APPLY_CMD).deleteIfExists()
            updateDir.resolve(APPLY_PS1).deleteIfExists()
            updateDir.resolve("apply-update.vbs").deleteIfExists()
            updateDir.resolve("run-update.ps1").deleteIfExists()
            updateDir.resolve("run-update.vbs").deleteIfExists()
            ProcessHandle.allProcesses().forEach { ph ->
                val cmdLine = ph.info().commandLine().orElse("")
                if (cmdLine.contains(APPLY_CMD, ignoreCase = true) ||
                    cmdLine.contains(APPLY_PS1, ignoreCase = true) ||
                    cmdLine.contains("run-update.ps1", ignoreCase = true)
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
     * Detach via `cmd /c start` so the helper survives [exitProcess] / job-object teardown.
     */
    private fun startDetachedCmd(cmdFile: Path, cwd: Path, log: Path) {
        val cmdPath = cmdFile.toAbsolutePath().normalize().pathString
        runCatching {
            Files.writeString(
                log,
                "[${java.time.LocalTime.now()}] spawning detached cmd\r\ncmd=$cmdPath\r\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
        }
        // start "" → empty title; /MIN minimized; path must be quoted separately.
        val pb = ProcessBuilder(
            "cmd.exe",
            "/c",
            "start",
            "",
            "/MIN",
            cmdPath,
        )
        pb.directory(cwd.toFile())
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
        pb.redirectError(ProcessBuilder.Redirect.DISCARD)
        val proc = pb.start()
        val finished = proc.waitFor(5, TimeUnit.SECONDS)
        if (!finished) {
            LauncherLog.warn("Self-update: start cmd still running (ok)")
        } else {
            LauncherLog.info("Self-update: start cmd exit=${proc.exitValue()}")
        }
        // Let Windows spawn the child before we tear down the JVM.
        Thread.sleep(800)
        Files.writeString(
            log,
            "detached start issued\r\n",
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
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
        // Sometimes jars live under app/ while exe is at staging root — already handled.
        kids.firstOrNull { it.isDirectory() && it.resolve(EXE_NAME).exists() }?.let { return it }
        return staging
    }

    private fun extractZipFlat(zipPath: Path, dest: Path) {
        dest.createDirectories()
        var files = 0
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
                    files++
                }
                zis.closeEntry()
            }
        }
        if (files == 0) error("Архив обновления пуст")
        LauncherLog.info("Self-update: extracted $files files")
    }

    private fun deleteRecursively(path: Path) {
        if (!path.exists()) return
        runCatching {
            Files.walk(path).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    /** Exit immediately after scheduling — do not rely on Compose requestExit. */
    fun exitForApply() {
        pendingApply.set(true)
        try {
            Thread.sleep(300)
        } catch (_: InterruptedException) {
        }
        exitProcess(0)
    }
}
