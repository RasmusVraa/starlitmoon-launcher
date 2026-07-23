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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.pathString

/**
 * Downloads the Setup EXE and relaunches after a silent Inno install.
 */
object LauncherSelfUpdater {
    private const val EXE_NAME = "StarlitMoonLauncher.exe"

    suspend fun downloadInstaller(
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
                    val buffer = ByteArray(64 * 1024)
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

    /**
     * Spawns a detached hidden PowerShell helper that waits for [launcherPid] to exit,
     * runs silent Setup, then starts the launcher again.
     *
     * Avoids `cmd start "title"` quirks that open a visible console and mis-parse `/min`.
     */
    fun scheduleInstallAndRestart(
        installer: Path,
        installDir: Path,
        relaunchExe: Path,
        launcherPid: Long,
    ) {
        require(installer.exists()) { "Установщик не найден" }
        val helper = Files.createTempFile("starlit-update-", ".ps1")
        val setup = installer.toAbsolutePath().normalize().pathString
        val dir = installDir.toAbsolutePath().normalize().pathString
        val exe = relaunchExe.toAbsolutePath().normalize().pathString
        val altExe = installDir.resolve(EXE_NAME).toAbsolutePath().normalize().pathString

        fun psLiteral(path: String): String = "'" + path.replace("'", "''") + "'"

        val script = """
            ${'$'}ErrorActionPreference = 'SilentlyContinue'
            ${'$'}pidToWait = $launcherPid
            ${'$'}setup = ${psLiteral(setup)}
            ${'$'}dir = ${psLiteral(dir)}
            ${'$'}exe = ${psLiteral(exe)}
            ${'$'}altExe = ${psLiteral(altExe)}
            while (Get-Process -Id ${'$'}pidToWait -ErrorAction SilentlyContinue) {
              Start-Sleep -Seconds 1
            }
            Start-Sleep -Seconds 1
            ${'$'}args = @(
              '/VERYSILENT',
              '/SUPPRESSMSGBOXES',
              '/NORESTART',
              '/CLOSEAPPLICATIONS',
              '/FORCECLOSEAPPLICATIONS',
              ('/DIR=' + ${'$'}dir)
            )
            Start-Process -FilePath ${'$'}setup -ArgumentList ${'$'}args -Wait -WindowStyle Hidden
            Start-Sleep -Seconds 1
            if (Test-Path -LiteralPath ${'$'}exe) {
              Start-Process -FilePath ${'$'}exe
            } elseif (Test-Path -LiteralPath ${'$'}altExe) {
              Start-Process -FilePath ${'$'}altExe
            }
            Remove-Item -LiteralPath ${'$'}setup -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath ${'$'}MyInvocation.MyCommand.Path -Force -ErrorAction SilentlyContinue
        """.trimIndent().replace("\n", "\r\n")
        Files.writeString(helper, script)

        ProcessBuilder(
            "powershell.exe",
            "-NoProfile",
            "-ExecutionPolicy", "Bypass",
            "-WindowStyle", "Hidden",
            "-File", helper.toAbsolutePath().normalize().pathString,
        ).apply {
            redirectOutput(ProcessBuilder.Redirect.DISCARD)
            redirectError(ProcessBuilder.Redirect.DISCARD)
        }.start()
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
}
