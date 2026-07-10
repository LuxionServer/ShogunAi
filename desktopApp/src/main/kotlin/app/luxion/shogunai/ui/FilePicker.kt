package app.luxion.shogunai.ui

import java.io.File
import javax.swing.JFileChooser

/** Envuelve [JFileChooser] para elegir carpetas o archivos desde el sistema. */
object FilePicker {
    fun pickDirectory(initialPath: String): String? {
        val chooser = JFileChooser(currentDirectoryOrNull(initialPath)).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Selecciona una carpeta"
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
            dialogTitle = "Selecciona archivos de secretos"
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
 * Convierte una ruta absoluta a una ruta relativa a [baseRepositoryPath], que es como
 * `ProjectConfig` espera los `secretFiles`. Si el archivo elegido está fuera del
 * repositorio base, se usa solo su nombre.
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
