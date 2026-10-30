package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentInstructionsBlockTest {

    private val path = "/home/dev/.gemini/GEMINI.md"
    private val files = FakeFileManager()
    private val block = AgentInstructionsBlock(files)
    private val current = AgentInstructionsBlock.current()

    private fun text() = files.writtenTexts.getValue(path)

    @Test
    fun `installing creates the file with the block`() {
        assertEquals(GuideBlockState.MISSING, block.status(path).state)

        assertEquals(GuideBlockState.INSTALLED, block.install(path).state)

        assertEquals("$current\n", text())
    }

    @Test
    fun `installing keeps the text outside the block byte for byte`() {
        val own = "# My rules\n\n- be brief  \n\tindented\n"
        files.writeText(path, own)

        block.install(path)

        assertEquals("$own\n$current\n", text())
    }

    @Test
    fun `installing twice leaves one block`() {
        files.writeText(path, "own")

        block.install(path)
        val once = text()
        block.install(path)

        assertEquals(once, text())
        assertEquals(1, Regex("shogun:guide v").findAll(text()).count())
    }

    @Test
    fun `an older block is replaced and the rest is untouched`() {
        files.writeText(path, "before\n\n<!-- shogun:guide v0 -->\nold body\n<!-- /shogun:guide -->\nafter\n")
        assertEquals(GuideBlockState.OUTDATED, block.status(path).state)

        block.install(path)

        assertEquals("before\n\n$current\nafter\n", text())
        assertEquals(GuideBlockState.INSTALLED, block.status(path).state)
    }

    @Test
    fun `uninstalling restores what was there`() {
        val own = "# My rules\n"
        files.writeText(path, own)
        block.install(path)

        block.uninstall(path)

        assertEquals(own, text())
        assertEquals(GuideBlockState.MISSING, block.status(path).state)
    }

    @Test
    fun `uninstalling without a block writes nothing`() {
        files.writeText(path, "own\n")
        files.writtenTexts.clear()

        assertEquals(GuideBlockState.MISSING, block.uninstall(path).state)

        assertTrue(files.writtenTexts.isEmpty())
    }

    @Test
    fun `workflow text outside the block is reported but never changed`() {
        val own = "Create worktrees with shogun new.\n"
        files.writeText(path, own)

        assertTrue(block.install(path).duplicatesOutside)

        assertTrue(text().startsWith(own))
    }

    @Test
    fun `the block itself isn't a duplicate`() {
        block.install(path)

        assertFalse(block.status(path).duplicatesOutside)
    }

    @Test
    fun `a block is present unless it is missing`() {
        assertFalse(block.status(path).hasBlock)

        block.install(path)

        assertTrue(block.status(path).hasBlock)
    }
}
