package app.luxion.shogunai.infrastructure

import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AtomicWriteTest {

    private val root = createTempDirectory("atomic-write-test")

    @AfterTest
    fun tearDown() {
        root.toFile().deleteRecursively()
    }

    @Test
    fun `replaces the file with the text ending in a newline, creating its folder`() {
        val target = root.resolve("nested").resolve("settings.json")

        writeAtomically(target, "{}")
        assertEquals("{}\n", target.readText())

        writeAtomically(target, "[]\n")
        assertEquals("[]\n", target.readText())
        assertEquals(listOf("settings.json"), target.parent.listDirectoryEntries().map { it.name })
    }

    @Test
    fun `a private file is readable and writable only by the user`() {
        val target = root.resolve("trust.json")

        writeAtomically(target, "{}", privateFile = true)

        val permissions = runCatching { Files.getPosixFilePermissions(target) }.getOrNull() ?: return
        assertEquals(PosixFilePermissions.fromString("rw-------"), permissions)
    }

    @Test
    fun `a failed move leaves no temporary file behind`() {
        val target = root.resolve("projects.json").also { it.createDirectories() }
        target.resolve("inside").writeText("blocks the replacement")

        assertFailsWith<IOException> { writeAtomically(target, "[]") }

        assertEquals(listOf("projects.json"), root.listDirectoryEntries().map { it.name })
    }
}
