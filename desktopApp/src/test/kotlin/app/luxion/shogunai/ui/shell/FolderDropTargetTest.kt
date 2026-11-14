package app.luxion.shogunai.ui.shell

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class FolderDropTargetTest {

    private val directory = Files.createTempDirectory("folder-drop").toFile()
    private val state = FolderDropState()
    private val dropped = mutableListOf<String>()

    private class FileList(private val files: List<File>) : Transferable {
        override fun getTransferDataFlavors() = arrayOf(DataFlavor.javaFileListFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.javaFileListFlavor
        override fun getTransferData(flavor: DataFlavor): Any = files
    }

    private fun event(native: Any): DragAndDropEvent {
        val constructor = DragAndDropEvent::class.java.declaredConstructors.single { it.parameterCount == 4 }
        constructor.isAccessible = true
        return constructor.newInstance(null, native, 0L, null) as DragAndDropEvent
    }

    private fun target() = FolderDropTarget(state, { dropped += it }, transferableOf = { it.nativeEvent as Transferable })

    @Test
    fun `a dragged folder is accepted, highlights the window while over it and is dropped`() {
        val target = target()
        val folder = event(FileList(listOf(directory)))

        assertTrue(target.accepts(folder))
        target.onEntered(folder)
        assertTrue(state.hovering)
        assertTrue(target.onDrop(folder))

        assertFalse(state.hovering)
        assertEquals(listOf(directory.path), dropped)
    }

    @Test
    fun `leaving the window or ending the drag removes the highlight`() {
        val target = target()
        val folder = event(FileList(listOf(directory)))

        target.onEntered(folder)
        target.onExited(folder)
        assertFalse(state.hovering)
        target.onEntered(folder)
        target.onEnded(folder)
        assertFalse(state.hovering)
    }

    @Test
    fun `dragged text is neither accepted nor dropped`() {
        val target = target()
        val text = event(StringSelection("~/projects/main-repo"))

        assertFalse(target.accepts(text))
        assertFalse(target.onDrop(text))
        assertTrue(dropped.isEmpty())
    }

    @Test
    fun `an event that carries no drag data is ignored`() {
        val target = FolderDropTarget(state, { dropped += it })

        assertFalse(target.accepts(event("not a drag")))
    }
}
