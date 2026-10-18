package app.luxion.shogunai.ui.shell

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FolderDropTest {

    private val directory = Files.createTempDirectory("folder-drop").toFile()
    private val file = File(directory, "notes.txt").apply { writeText("x") }

    @Test
    fun `a single directory is accepted`() {
        assertEquals(directory.path, singleDirectory(listOf(directory)))
    }

    @Test
    fun `a file is ignored`() {
        assertNull(singleDirectory(listOf(file)))
    }

    @Test
    fun `several items are ignored`() {
        assertNull(singleDirectory(listOf(directory, directory)))
    }

    @Test
    fun `an empty drop is ignored`() {
        assertNull(singleDirectory(emptyList()))
    }
}
