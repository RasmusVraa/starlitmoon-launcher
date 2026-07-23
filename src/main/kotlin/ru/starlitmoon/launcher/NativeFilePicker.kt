package ru.starlitmoon.launcher

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter

/** Нативный выбор файла: Windows OpenFileDialog, иначе системный L&F. */
object NativeFilePicker {

    fun pickOpenFile(
        title: String,
        filterLabel: String,
        vararg extensions: String,
    ): File? {
        val exts = extensions.map { it.trim().lowercase().removePrefix(".") }.filter { it.isNotEmpty() }
        if (WindowsShell.isWindows()) {
            pickWindowsForms(title, filterLabel, exts)?.let { return it }
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
              [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
              [Console]::Write(${'$'}d.FileName)
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
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val out = proc.inputStream.readBytes().toString(StandardCharsets.UTF_8).trim()
            val code = proc.waitFor()
            if (code != 0 || out.isBlank()) null else File(out).takeIf { it.isFile }
        }.getOrNull()
    }

    private fun pickAwtOrSwing(
        title: String,
        filterLabel: String,
        extensions: List<String>,
    ): File? {
        val result = AtomicReference<File?>(null)
        val show = Runnable {
            // AWT FileDialog — нативный диалог ОС (на Windows не Swing-Metal).
            runCatching {
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
                } finally {
                    owner.dispose()
                }
            }.onFailure {
                runCatching {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
                }
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
