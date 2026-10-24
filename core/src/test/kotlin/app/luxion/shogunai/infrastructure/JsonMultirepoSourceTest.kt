package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.WorkspaceSetting
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
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
    fun `the workspace field round trips as absent, false or an object and keeps the other fields`() {
        val base = MultirepoDeclaration(repos = mapOf("app" to "product-app"), worktreesRoot = "../product-worktrees")
        listOf(null, WorkspaceSetting(enabled = false), WorkspaceSetting(), WorkspaceSetting(alias = "docs-root", default = true)).forEach { setting ->
            val declaration = base.copy(workspace = setting)

            source.write(folder.toString(), declaration)

            assertEquals(declaration, source.read(folder.toString()))
        }
        source.write(folder.toString(), base.copy(workspace = WorkspaceSetting(enabled = false)))
        assertTrue(folder.resolve(".shogunai-multirepo.json").readText().contains("\"workspace\": false"))
        source.write(folder.toString(), base.copy(workspace = WorkspaceSetting(default = true)))
        assertTrue(folder.resolve(".shogunai-multirepo.json").readText().contains("\"default\": true"))
    }

    @Test
    fun `an invalid workspace field names the file`() {
        folder.resolve(".shogunai-multirepo.json").writeText("""{"worktreesRoot": "../w", "workspace": "yes"}""")

        assertFailsWith<WorktreeError.InvalidMultirepo> { source.read(folder.toString()) }
    }

    @Test
    fun `writes the format version and the agent's headroom option`() {
        source.write(folder.toString(), MultirepoDeclaration(worktreesRoot = "../w", agent = AgentLaunchConfig(agentCommand = "codex")))

        val written = Json.parseToJsonElement(declarationFile().readText()).jsonObject
        assertEquals(JsonPrimitive(2), written["version"])
        assertEquals(JsonPrimitive(false), written["agent"]?.jsonObject?.get("useHeadroom"))
    }

    @Test
    fun `a current declaration without an agent or the headroom option runs the agent plain`() {
        declarationFile().writeText("""{ "version": 2, "worktreesRoot": "../w" }""")
        assertNull(source.read(folder.toString())?.agent)

        declarationFile().writeText("""{ "version": 2, "worktreesRoot": "../w", "agent": { "agentCommand": "codex" } }""")
        assertEquals(AgentLaunchConfig(agentCommand = "codex", useHeadroom = false), source.read(folder.toString())?.agent)
    }

    @Test
    fun `a declaration without a version keeps the headroom its agent ran with`() {
        declarationFile().writeText("""{ "worktreesRoot": "../w", "agent": { "agentCommand": "codex" } }""")
        assertEquals(AgentLaunchConfig(agentCommand = "codex", useHeadroom = true), source.read(folder.toString())?.agent)

        declarationFile().writeText("""{ "worktreesRoot": "../w", "agent": { "agentCommand": "codex", "useHeadroom": false } }""")
        assertEquals(AgentLaunchConfig(agentCommand = "codex", useHeadroom = false), source.read(folder.toString())?.agent)
    }

    @Test
    fun `a declaration without a version or an agent runs claude with headroom, also after it is rewritten`() {
        declarationFile().writeText("""{ "worktreesRoot": "../w" }""")
        val legacy = source.read(folder.toString())!!
        assertEquals(AgentLaunchConfig(agentCommand = "claude", useHeadroom = true), legacy.agent)

        source.write(folder.toString(), legacy.copy(defaultRepos = listOf("app")))

        assertEquals(AgentLaunchConfig(agentCommand = "claude", useHeadroom = true), source.read(folder.toString())?.agent)
        assertTrue(declarationFile().readText().contains("\"useHeadroom\": true"))
    }

    private fun declarationFile() = folder.resolve(".shogunai-multirepo.json")

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
