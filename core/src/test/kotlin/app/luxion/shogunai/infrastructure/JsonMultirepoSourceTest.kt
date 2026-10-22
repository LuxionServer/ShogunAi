package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.WorktreeError
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonMultirepoSourceTest {

    private val home = createTempDirectory("json-multirepo-source-test")
    private val folder = home.resolve("product").also { Files.createDirectories(it) }
    private val registry = home.resolve(".shogunai/multirepos.json")
    private val source = JsonMultirepoSource(registry)

    @AfterTest
    fun tearDown() {
        home.toFile().deleteRecursively()
    }

    @Test
    fun `writes and reads a declaration with relative and absolute paths and an agent`() {
        val declaration = MultirepoDeclaration(
            repos = mapOf("app" to "product-app", "backend" to "/src/backend"),
            worktreesRoot = "../product-worktrees",
            defaultRepos = listOf("app"),
            agent = AgentLaunchConfig(agentCommand = "claude"),
        )

        source.write(folder.toString(), declaration)

        assertEquals(declaration, source.read(folder.toString()))
        assertTrue(folder.resolve(".shogunai-multirepo.json").readText().contains("\"worktreesRoot\": \"../product-worktrees\""))
    }

    @Test
    fun `a folder without a declaration has none`() {
        assertNull(source.read(folder.toString()))
    }

    @Test
    fun `invalid JSON names the file`() {
        folder.resolve(".shogunai-multirepo.json").writeText("{ not json")

        val error = assertFailsWith<WorktreeError.InvalidMultirepo> { source.read(folder.toString()) }

        assertTrue(error.path.endsWith(".shogunai-multirepo.json"))
    }

    @Test
    fun `unknown keys are ignored`() {
        folder.resolve(".shogunai-multirepo.json").writeText("""{ "repos": { "app": "product-app" }, "worktreesRoot": "../w", "future": 1 }""")

        assertEquals(mapOf("app" to "product-app"), source.read(folder.toString())?.repos)
    }

    @Test
    fun `remembers and forgets folders in a file of its own`() {
        source.remember(folder.toString())
        source.remember(folder.toString())

        assertEquals(listOf(folder.toString()), source.knownFolders())
        assertTrue(registry.readText().contains("multirepos"))

        source.forget(folder.toString())

        assertEquals(emptyList(), source.knownFolders())
    }

    @Test
    fun `delete removes only the declaration`() {
        source.write(folder.toString(), MultirepoDeclaration(worktreesRoot = "../w"))

        source.delete(folder.toString())

        assertFalse(folder.resolve(".shogunai-multirepo.json").exists())
        assertTrue(folder.exists())
    }

    @Test
    fun `an unreadable registry reads as empty`() {
        Files.createDirectories(registry.parent)
        registry.writeText("garbage")

        assertEquals(emptyList(), source.knownFolders())
    }
}
