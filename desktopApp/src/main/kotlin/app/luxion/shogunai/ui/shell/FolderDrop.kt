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
private fun Transferable.droppedDirectory(): String? =
    runCatching {
        if (!isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return null
        singleDirectory(getTransferData(DataFlavor.javaFileListFlavor) as List<File>)
    }.getOrNull()

class FolderDropState {
    var hovering by mutableStateOf(false)
        internal set
}

@Composable
fun rememberFolderDropState(): FolderDropState = remember { FolderDropState() }

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
fun Modifier.folderDropTarget(state: FolderDropState, onDrop: (String) -> Unit): Modifier =
    dragAndDropTarget(
        shouldStartDragAndDrop = { event: DragAndDropEvent -> event.awtTransferable.droppedDirectory() != null },
        target = object : DragAndDropTarget {
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
                val folder = event.awtTransferable.droppedDirectory() ?: return false
                onDrop(folder)
                return true
            }
        },
    )
