package app.luxion.shogunai.infrastructure

import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NioLauncherFilesTest {

    private val root = createTempDirectory("nio-launcher-files-test")
    private val files = NioLauncherFiles()

    @AfterTest
    fun tearDown() {
        root.toFile().deleteRecursively()
    }

    @Test
    fun `writes, reads and deletes a script creating its folder`() {
        val script = root.resolve("bin/shogun.cmd").toString()

        files.writeText(script, "@echo off")

        assertTrue(files.exists(script))
        assertEquals("@echo off", files.readText(script))
        files.delete(script)
        assertFalse(files.exists(script))
        assertNull(files.readText(script))
    }

    @Test
    fun `only an existing folder is a writable folder`() {
        val folder = root.resolve("bin").createDirectories()

        assertTrue(files.isWritableFolder(folder.toString()))
        assertFalse(files.isWritableFolder(root.resolve("missing").toString()))
    }

    @Test
    fun `creates a link replacing what was there and reads its target`() {
        val target = root.resolve("ShogunAi").toString()
        val link = root.resolve("links/shogun").toString()
        files.writeText(link, "old")

        files.createLink(target, link)

        assertEquals(target, files.linkTarget(link))
        assertNull(files.linkTarget(target))
        assertFalse(root.resolve("ShogunAi").exists())
    }

    @Test
    fun `a path the system can't represent has no link target`() {
        assertNull(files.linkTarget("bad\u0000path"))
    }
}
