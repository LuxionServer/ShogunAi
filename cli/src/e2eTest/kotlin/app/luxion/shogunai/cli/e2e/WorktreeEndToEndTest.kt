package app.luxion.shogunai.cli.e2e

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorktreeEndToEndTest {

    private val sandbox = Sandbox()
    private val repository = sandbox.repository("main-repo")
    private val worktreesRoot = sandbox.root.resolve("main-repo-worktrees")

    @AfterTest
    fun cleanUp() = sandbox.close()

    @Test
    fun `project add writes the catalog under the isolated home`() {
        sandbox.shogunSucceeds(repository, "project", "add")

        val catalog = sandbox.home.resolve(".shogunai/projects.json")
        assertTrue(catalog.readText().contains(repository.toString().jsonEscaped()), catalog.readText())
        val realCatalog = Path.of(System.getProperty("user.home"), ".shogunai", "projects.json")
        assertFalse(realCatalog.exists() && realCatalog.readText().contains(sandbox.root.toString().jsonEscaped()))
    }

    @Test
    fun `new creates a worktree on a feature branch`() {
        sandbox.shogunSucceeds(repository, "project", "add")

        val result = sandbox.shogunSucceeds(repository, "new", "TASK-1")

        val worktree = worktreesRoot.resolve("TASK-1")
        assertEquals(worktree, Path.of(result.output.lines().last()).real())
        assertEquals(mapOf(repository to "main", worktree to "feature/TASK-1"), worktreeBranches())
        assertEquals("main", sandbox.shogunSucceeds(worktree, "base").output)
    }

    @Test
    fun `new with --fix creates a fix branch and --json describes it`() {
        sandbox.shogunSucceeds(repository, "project", "add")

        val result = sandbox.shogunSucceeds(repository, "new", "TASK-3", "--fix", "--json")

        val json = Json.parseToJsonElement(result.stdout).jsonObject
        assertEquals("fix/TASK-3", json.getValue("branch").jsonPrimitive.content)
        val worktree = Path.of(json.getValue("path").jsonPrimitive.content).real()
        assertEquals(worktreesRoot.resolve("TASK-3"), worktree)
        assertEquals("fix/TASK-3", worktreeBranches()[worktree])
    }

    @Test
    fun `new --branch checks out a local branch and a branch only on origin`() {
        sandbox.shogunSucceeds(repository, "project", "add")
        sandbox.git(repository, "branch", "feature/local-work")
        val clone = sandbox.root.resolve("other-clone")
        sandbox.git(sandbox.root, "clone", "--quiet", sandbox.root.resolve("remotes/main-repo.git").toString(), clone.toString())
        sandbox.git(clone, "switch", "--quiet", "--create", "feature/remote-work")
        sandbox.commit(clone, "remote.txt", "remote\n", "Remote work")
        sandbox.git(clone, "push", "--quiet", "origin", "feature/remote-work")

        sandbox.shogunSucceeds(repository, "new", "--branch", "feature/local-work")
        sandbox.shogunSucceeds(repository, "new", "--branch", "feature/remote-work", "--fetch")

        val branches = worktreeBranches().values
        assertTrue("feature/local-work" in branches, branches.toString())
        assertTrue("feature/remote-work" in branches, branches.toString())
        val remoteWorktree = worktreeBranches().entries.single { it.value == "feature/remote-work" }.key
        assertEquals("remote\n", remoteWorktree.resolve("remote.txt").readText())
        assertEquals("origin/feature/remote-work", sandbox.git(remoteWorktree, "rev-parse", "--abbrev-ref", "@{upstream}"))
    }

    @Test
    fun `status lists the worktree with its base`() {
        sandbox.shogunSucceeds(repository, "project", "add")
        sandbox.shogunSucceeds(repository, "new", "TASK-1")

        val status = Json.parseToJsonElement(sandbox.shogunSucceeds(repository, "status", "--json").stdout).jsonObject

        val worktree = status.getValue("worktrees").jsonArray.map { it.jsonObject }
            .single { it.getValue("branch").jsonPrimitive.content == "feature/TASK-1" }
        assertEquals(worktreesRoot.resolve("TASK-1"), Path.of(worktree.getValue("path").jsonPrimitive.content).real())
        assertEquals("main", worktree.getValue("base").jsonPrimitive.content)
    }

    @Test
    fun `remove deletes a worktree and its merged branch`() {
        sandbox.shogunSucceeds(repository, "project", "add")
        sandbox.shogunSucceeds(repository, "new", "TASK-1")

        sandbox.shogunSucceeds(repository, "remove", "TASK-1", "--delete-branch")

        assertFalse(worktreesRoot.resolve("TASK-1").exists())
        assertEquals(mapOf(repository to "main"), worktreeBranches())
        assertEquals("", sandbox.git(repository, "branch", "--list", "feature/TASK-1"))
    }

    @Test
    fun `remove refuses a worktree with changes unless forced`() {
        sandbox.shogunSucceeds(repository, "project", "add")
        sandbox.shogunSucceeds(repository, "new", "TASK-1")
        val worktree = worktreesRoot.resolve("TASK-1")
        sandbox.write(worktree, "work.txt", "unsaved\n")

        val refused = sandbox.shogun(repository, "remove", "TASK-1")
        assertTrue(refused.exitCode != 0, refused.describe())
        assertTrue(worktree.exists())

        sandbox.shogunSucceeds(repository, "remove", "TASK-1", "--force")
        assertFalse(worktree.exists())
    }

    @Test
    fun `clean lists a merged worktree and removes it with its branch`() {
        sandbox.shogunSucceeds(repository, "project", "add")
        sandbox.shogunSucceeds(repository, "new", "TASK-1")
        sandbox.shogunSucceeds(repository, "new", "TASK-2")
        val merged = worktreesRoot.resolve("TASK-1")
        sandbox.commit(merged, "feature.txt", "done\n", "Finish TASK-1")
        sandbox.commit(worktreesRoot.resolve("TASK-2"), "draft.txt", "draft\n", "Start TASK-2")
        sandbox.merge(repository, "feature/TASK-1")

        val listing = sandbox.shogunSucceeds(repository, "clean")
        assertTrue(listing.stdout.contains(merged.fileName.toString()), listing.describe())
        assertFalse(listing.stdout.contains("TASK-2"), listing.describe())
        assertTrue(merged.exists())

        sandbox.shogunSucceeds(repository, "clean", "--yes", "--delete-branches")

        assertFalse(merged.exists())
        assertEquals(mapOf(repository to "main", worktreesRoot.resolve("TASK-2") to "feature/TASK-2"), worktreeBranches())
        assertEquals("", sandbox.git(repository, "branch", "--list", "feature/TASK-1"))
    }

    private fun worktreeBranches(): Map<Path, String> =
        sandbox.git(repository, "worktree", "list", "--porcelain").split(Regex("\\R\\R")).filter { it.isNotBlank() }.associate { entry ->
            val lines = entry.lines()
            val path = Path.of(lines.first { it.startsWith("worktree ") }.removePrefix("worktree ")).real()
            path to lines.first { it.startsWith("branch ") }.removePrefix("branch refs/heads/")
        }

    private fun String.jsonEscaped(): String = replace("\\", "\\\\")

    private fun Path.real(): Path = if (Files.exists(this)) toRealPath() else normalize()
}
