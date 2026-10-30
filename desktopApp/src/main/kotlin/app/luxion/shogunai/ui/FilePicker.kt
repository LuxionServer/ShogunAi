package app.luxion.shogunai.ui

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogParent
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.awt.Window
import java.io.File
import javax.swing.JFileChooser

interface PathPicker {
    suspend fun pickDirectory(initialPath: String, dialogTitle: String): String?
}

val LocalPathPicker = staticCompositionLocalOf<PathPicker> { NativeFolderPicker() }

typealias NativeDirectoryDialog = suspend (initialFolder: File?, dialogTitle: String, owner: Window?) -> String?

class NativeFolderPicker(
    private val owner: () -> Window? = { null },
    private val nativeDialog: NativeDirectoryDialog = ::openFileKitDirectoryPicker,
    private val fallback: PathPicker = SwingFolderPicker,
) : PathPicker {
    private val open = Mutex()

    override suspend fun pickDirectory(initialPath: String, dialogTitle: String): String? {
        if (!open.tryLock()) return null
        try {
            return try {
                nativeDialog(existingFolderOrNull(initialPath), dialogTitle, owner())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fallback.pickDirectory(initialPath, dialogTitle)
            }
        } finally {
            open.unlock()
        }
    }
}

object SwingFolderPicker : PathPicker {
    override suspend fun pickDirectory(initialPath: String, dialogTitle: String): String? = withContext(Dispatchers.Swing) {
        val chooser = JFileChooser(existingFolderOrNull(initialPath)).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isFileHidingEnabled = false
            this.dialogTitle = dialogTitle
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else null
    }
}

private suspend fun openFileKitDirectoryPicker(initialFolder: File?, dialogTitle: String, owner: Window?): String? =
    FileKit.openDirectoryPicker(
        directory = initialFolder?.let(::PlatformFile),
        dialogSettings = FileKitDialogSettings(title = dialogTitle, parent = owner?.let(FileKitDialogParent::awt)),
    )?.file?.absolutePath

internal fun existingFolderOrNull(path: String): File? =
    path.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isDirectory }

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
