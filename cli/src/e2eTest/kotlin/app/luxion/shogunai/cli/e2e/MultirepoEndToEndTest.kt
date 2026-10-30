package app.luxion.shogunai.cli.e2e

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultirepoEndToEndTest {

    private val sandbox = Sandbox()
    private val multirepo = sandbox.root.resolve("product").createDirectories()
    private val worktreesRoot = sandbox.root.resolve("product-worktrees")
    private val aliases = listOf("api", "app")

    init {
        aliases.forEach { alias ->
            val repository = sandbox.repository("product/$alias")
            sandbox.write(repository, ".gitignore", ".env\n")
            sandbox.git(repository, "add", ".gitignore")
            sandbox.git(repository, "commit", "--quiet", "-m", "Ignore the local environment")
            sandbox.git(repository, "push", "--quiet", "origin", "main")
            sandbox.write(repository, ".worktreeinclude", ".env\n")
            sandbox.write(repository, ".env", "SERVICE=$alias\n")
        }
        sandbox.shogunSucceeds(multirepo, "multirepo", "init", "--yes")
        aliases.forEach { alias -> sandbox.shogunSucceeds(multirepo, "multirepo", "add-repo", alias, alias, "--use-worktrees-root") }
        sandbox.shogunSucceeds(multirepo, "multirepo", "defaults", aliases.joinToString(","))
    }

    @AfterTest
    fun cleanUp() = sandbox.close()

    @Test
    fun `new creates one provisioned worktree per default repository on the same branch`() {
        sandbox.shogunSucceeds(multirepo, "new", "TASK-2")

        aliases.forEach { alias ->
            val worktree = worktreeOf(alias)
            assertEquals("feature/TASK-2", sandbox.git(worktree, "branch", "--show-current"))
            assertEquals("SERVICE=$alias\n", worktree.resolve(".env").toFile().readText())
        }
    }

    @Test
    fun `new with --repos covers only the given repository`() {
        sandbox.shogunSucceeds(multirepo, "new", "TASK-4", "--repos", "api")

        assertTrue(worktreesRoot.resolve("api/TASK-4").exists())
        assertFalse(worktreesRoot.resolve("app/TASK-4").exists())
    }

    @Test
    fun `status lists the group with every repository`() {
        sandbox.shogunSucceeds(multirepo, "new", "TASK-2")

        val status = Json.parseToJsonElement(sandbox.shogunSucceeds(multirepo, "status", "--json").stdout).jsonObject

        val group = status.getValue("groups").jsonArray.single().jsonObject
        assertEquals("feature/TASK-2", group.getValue("branch").jsonPrimitive.content)
        val repos = group.getValue("repos").jsonArray.map { it.jsonObject }
        assertEquals(aliases, repos.map { it.getValue("alias").jsonPrimitive.content })
        assertEquals(aliases.map(::worktreeOf), repos.map { Path.of(it.getValue("path").jsonPrimitive.content).toRealPath() })
    }

    @Test
    fun `open in Herdr opens every worktree of the group`() {
        FakeRunners.install(sandbox)
        sandbox.shogunSucceeds(multirepo, "new", "TASK-2")

        sandbox.shogunSucceeds(multirepo, "open", "TASK-2", "--herdr")

        val calls = FakeRunners.calls(sandbox, "herdr")
        val opened = calls.filter { it.take(2) == listOf("worktree", "open") }.map { Path.of(it[it.indexOf("--path") + 1]).toRealPath() }
        assertEquals(aliases.map(::worktreeOf).toSet(), opened.toSet())
        assertEquals(aliases.size, calls.count { it.take(2) == listOf("pane", "run") }, calls.toString())
    }

    @Test
    fun `clean removes a group once every branch is merged`() {
        sandbox.shogunSucceeds(multirepo, "new", "TASK-2")
        sandbox.commit(worktreeOf("api"), "endpoint.txt", "endpoint\n", "Add the endpoint")

        val pending = sandbox.shogunSucceeds(multirepo, "clean", "--yes")
        assertTrue(worktreeOf("api").exists(), pending.describe())

        sandbox.commit(worktreeOf("app"), "screen.txt", "screen\n", "Add the screen")
        aliases.forEach { alias -> sandbox.merge(multirepo.resolve(alias), "feature/TASK-2") }

        sandbox.shogunSucceeds(multirepo, "clean", "--yes", "--delete-branches")

        aliases.forEach { alias ->
            assertFalse(worktreesRoot.resolve("$alias/TASK-2").exists())
            assertEquals("", sandbox.git(multirepo.resolve(alias), "branch", "--list", "feature/TASK-2"))
        }
    }

    private fun worktreeOf(alias: String): Path = worktreesRoot.resolve("$alias/TASK-2").toRealPath()
}
