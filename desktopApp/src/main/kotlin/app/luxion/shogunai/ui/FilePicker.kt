package app.luxion.shogunai.ui

import java.io.File
import javax.swing.JFileChooser

object FilePicker {
    fun pickDirectory(initialPath: String, dialogTitle: String): String? {
        val chooser = JFileChooser(currentDirectoryOrNull(initialPath)).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isFileHidingEnabled = false
            this.dialogTitle = dialogTitle
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.absolutePath
        } else {
            null
        }
    }

    fun pickFiles(initialPath: String, dialogTitle: String): List<String> {
        val chooser = JFileChooser(currentDirectoryOrNull(initialPath)).apply {
            fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
            isFileHidingEnabled = false
            isMultiSelectionEnabled = true
            this.dialogTitle = dialogTitle
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFiles.map { it.absolutePath }
        } else {
            emptyList()
        }
    }

    private fun currentDirectoryOrNull(path: String): File? =
        path.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }
}

fun relativeToBase(baseRepositoryPath: String, absolutePath: String): String =
    relativeIfInsideBase(baseRepositoryPath, absolutePath) ?: File(absolutePath).name

internal fun isAbsolutePath(path: String): Boolean = path.startsWith('/') || File(path).isAbsolute

internal fun relativeIfInsideBase(baseRepositoryPath: String, absolutePath: String): String? {
    if (baseRepositoryPath.isBlank()) return null
    return try {
        val base = File(baseRepositoryPath).toPath().normalize()
        val relative = base.relativize(File(absolutePath).toPath().normalize()).toString()
        relative.replace('\\', '/').takeUnless { it.isEmpty() || it.startsWith("..") }
    } catch (e: IllegalArgumentException) {
        null
    }
}
