package app.luxion.shogunai.ui.shell

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File

internal fun singleDirectory(files: List<File>): String? =
    files.singleOrNull()?.takeIf { it.isDirectory }?.path

@Suppress("UNCHECKED_CAST")
internal fun droppedDirectory(transferable: Transferable): String? {
    if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return null
    return singleDirectory(transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>)
}

class FolderDropState {
    var hovering by mutableStateOf(false)
        internal set
}

@Composable
fun rememberFolderDropState(): FolderDropState = remember { FolderDropState() }

@OptIn(ExperimentalComposeUiApi::class)
internal class FolderDropTarget(
    private val state: FolderDropState,
    private val onDrop: (String) -> Unit,
    private val transferableOf: (DragAndDropEvent) -> Transferable = DragAndDropEvent::awtTransferable,
) : DragAndDropTarget {

    fun accepts(event: DragAndDropEvent): Boolean = directoryOf(event) != null

    override fun onEntered(event: DragAndDropEvent) {
        state.hovering = true
    }

    override fun onExited(event: DragAndDropEvent) {
        state.hovering = false
    }

    override fun onEnded(event: DragAndDropEvent) {
        state.hovering = false
    }

    override fun onDrop(event: DragAndDropEvent): Boolean {
        state.hovering = false
        val folder = directoryOf(event) ?: return false
        onDrop(folder)
        return true
    }

    private fun directoryOf(event: DragAndDropEvent): String? = runCatching { droppedDirectory(transferableOf(event)) }.getOrNull()
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.folderDropTarget(state: FolderDropState, onDrop: (String) -> Unit): Modifier {
    val target = FolderDropTarget(state, onDrop)
    return dragAndDropTarget(shouldStartDragAndDrop = target::accepts, target = target)
}
