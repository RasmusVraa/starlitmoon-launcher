package ru.starlitmoon.launcher

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Native file open dialog.
 *
 * On Windows we use WinForms OpenFileDialog via a short PowerShell process so Cancel does not
 * open a second Swing dialog. The selected path is written to a UTF-8 temp file — never read from
 * PowerShell stdout — because `redirectErrorStream(true)` (and even clean stdout) can be polluted
 * by CLIXML progress records (`#< CLIXML...`), making `File(out).isFile` fail and the picker
 * silently return null (cape/skin never added).
 */
object NativeFilePicker {

    fun pickOpenFile(
        title: String,
        filterLabel: String,
        vararg extensions: String,
    ): File? {
        val exts = extensions.map { it.trim().lowercase().removePrefix(".") }.filter { it.isNotEmpty() }
        if (WindowsShell.isWindows()) {
            return pickWindowsForms(title, filterLabel, exts)
        }
        return pickAwtOrSwing(title, filterLabel, exts)
    }

    private fun pickWindowsForms(
        title: String,
        filterLabel: String,
        extensions: List<String>,
    ): File? {
        val patterns = if (extensions.isEmpty()) {
            "*.*"
        } else {
            extensions.joinToString(";") { "*.$it" }
        }
        val filter = if (extensions.isEmpty()) {
            "Все файлы (*.*)|*.*"
        } else {
            "$filterLabel ($patterns)|$patterns|Все файлы (*.*)|*.*"
        }
        val resultFile = Files.createTempFile("starlit-picker-", ".path").toFile().apply {
            deleteOnExit()
            writeText("", Charsets.UTF_8)
        }
        val resultPathLiteral = resultFile.absolutePath.replace("'", "''")
        val script = """
            Add-Type -AssemblyName System.Windows.Forms
            ${'$'}d = New-Object System.Windows.Forms.OpenFileDialog
            ${'$'}d.Title = @'
            $title
            '@.Trim()
            ${'$'}d.Filter = @'
            $filter
            '@.Trim()
            ${'$'}d.Multiselect = ${'$'}false
            ${'$'}d.CheckFileExists = ${'$'}true
            if (${'$'}d.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) {
              [IO.File]::WriteAllText(
                '$resultPathLiteral',
                ${'$'}d.FileName,
                (New-Object System.Text.UTF8Encoding ${'$'}false)
              )
            }
        """.trimIndent()

        return runCatching {
            val encoded = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
            val pb = ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-STA",
                "-ExecutionPolicy", "Bypass",
                "-EncodedCommand", encoded,
            )
            // Keep stderr separate — CLIXML progress must not touch the result channel.
            pb.redirectErrorStream(false)
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
            pb.redirectError(ProcessBuilder.Redirect.DISCARD)
            val proc = pb.start()
            proc.waitFor()
            val path = resultFile.readText(Charsets.UTF_8).trim()
                .trimStart('\uFEFF')
                .lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() }
                .orEmpty()
            resultFile.delete()
            if (path.isBlank()) null else File(path).takeIf { it.isFile }
        }.getOrElse {
            runCatching { resultFile.delete() }
            null
        }
    }

    private fun pickAwtOrSwing(
        title: String,
        filterLabel: String,
        extensions: List<String>,
    ): File? {
        val result = AtomicReference<File?>(null)
        val show = Runnable {
            runCatching {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
            }
            val owner = Frame()
            try {
                val dialog = FileDialog(owner, title, FileDialog.LOAD)
                if (extensions.isNotEmpty()) {
                    dialog.file = extensions.joinToString(";") { "*.$it" }
                    dialog.setFilenameFilter { _, name ->
                        extensions.any { name.endsWith(".$it", ignoreCase = true) }
                    }
                }
                dialog.isVisible = true
                val name = dialog.file
                val dir = dialog.directory
                if (!name.isNullOrBlank() && !dir.isNullOrBlank()) {
                    result.set(File(dir, name).takeIf { it.isFile })
                }
            } catch (_: Throwable) {
                val chooser = JFileChooser().apply {
                    fileSelectionMode = JFileChooser.FILES_ONLY
                    dialogTitle = title
                    if (extensions.isNotEmpty()) {
                        fileFilter = FileNameExtensionFilter(filterLabel, *extensions.toTypedArray())
                    }
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    result.set(chooser.selectedFile?.takeIf { it.isFile })
                }
            } finally {
                owner.dispose()
            }
        }
        if (SwingUtilities.isEventDispatchThread()) {
            show.run()
        } else {
            SwingUtilities.invokeAndWait(show)
        }
        return result.get()
    }
}
