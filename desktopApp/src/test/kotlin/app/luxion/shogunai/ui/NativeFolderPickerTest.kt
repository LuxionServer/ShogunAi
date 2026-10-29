package app.luxion.shogunai.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NativeFolderPickerTest {

    private class RecordingPicker(private val answer: String?) : PathPicker {
        val requests = mutableListOf<Pair<String, String>>()

        override suspend fun pickDirectory(initialPath: String, dialogTitle: String): String? {
            requests += initialPath to dialogTitle
            return answer
        }
    }

    @Test
    fun `returns what the native dialog chose, with its title`() = runTest {
        var title: String? = null
        val picker = NativeFolderPicker(nativeDialog = { _, dialogTitle, _ -> title = dialogTitle; "/home/dev/projects/api" })

        assertEquals("/home/dev/projects/api", picker.pickDirectory("", "Select a folder"))
        assertEquals("Select a folder", title)
    }

    @Test
    fun `starts at the current folder when it exists`() = runTest {
        val folder = Files.createTempDirectory("picker").toFile()
        var initial: File? = null
        val picker = NativeFolderPicker(nativeDialog = { start, _, _ -> initial = start; null })

        picker.pickDirectory(folder.absolutePath, "Select a folder")

        assertEquals(folder, initial)
        folder.delete()
    }

    @Test
    fun `starts at the platform's default when the folder is missing or empty`() = runTest {
        val starts = mutableListOf<File?>()
        val picker = NativeFolderPicker(nativeDialog = { start, _, _ -> starts += start; null })

        picker.pickDirectory("/home/dev/missing-folder", "Select a folder")
        picker.pickDirectory("", "Select a folder")

        assertEquals(listOf<File?>(null, null), starts)
    }

    @Test
    fun `cancelling returns nothing without falling back`() = runTest {
        val fallback = RecordingPicker("/home/dev/other")
        val picker = NativeFolderPicker(nativeDialog = { _, _, _ -> null }, fallback = fallback)

        assertNull(picker.pickDirectory("", "Select a folder"))
        assertEquals(emptyList(), fallback.requests)
    }

    @Test
    fun `falls back to the Java chooser at the same folder when the native dialog fails`() = runTest {
        val fallback = RecordingPicker("/home/dev/projects/api")
        val picker = NativeFolderPicker(nativeDialog = { _, _, _ -> error("portal unavailable") }, fallback = fallback)

        assertEquals("/home/dev/projects/api", picker.pickDirectory("/home/dev/projects", "Select a folder"))
        assertEquals(listOf("/home/dev/projects" to "Select a folder"), fallback.requests)
    }

    @Test
    fun `ignores a second request while a dialog is open`() = runTest {
        val release = CompletableDeferred<String?>()
        var opened = 0
        val picker = NativeFolderPicker(nativeDialog = { _, _, _ -> opened++; release.await() })

        val first = async { picker.pickDirectory("", "Select a folder") }
        testScheduler.runCurrent()
        val second = picker.pickDirectory("", "Select a folder")
        release.complete("/home/dev/projects/api")

        assertNull(second)
        assertEquals("/home/dev/projects/api", first.await())
        assertEquals(1, opened)
    }
}
