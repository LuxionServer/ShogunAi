package app.luxion.shogunai.ui

import java.io.File
import javax.swing.JFileChooser

/** Wraps [JFileChooser] to pick folders or files from the system. */
object FilePicker {
    fun pickDirectory(initialPath: String): String? {
        val chooser = JFileChooser(currentDirectoryOrNull(initialPath)).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Select a folder"
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.absolutePath
        } else {
            null
        }
    }

    fun pickFiles(initialPath: String): List<String> {
        val chooser = JFileChooser(currentDirectoryOrNull(initialPath)).apply {
            fileSelectionMode = JFileChooser.FILES_ONLY
            isMultiSelectionEnabled = true
            dialogTitle = "Select secret files"
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

/**
 * Converts an absolute path to a path relative to [baseRepositoryPath], which is how
 * `ProjectConfig` expects `secretFiles`. If the chosen file is outside the base
 * repository, only its name is used.
 */
fun relativeToBase(baseRepositoryPath: String, absolutePath: String): String {
    if (baseRepositoryPath.isBlank()) return File(absolutePath).name
    return try {
        val relative = File(baseRepositoryPath).toPath().relativize(File(absolutePath).toPath()).toString()
        if (relative.startsWith("..")) File(absolutePath).name else relative
    } catch (e: IllegalArgumentException) {
        File(absolutePath).name
    }
}
