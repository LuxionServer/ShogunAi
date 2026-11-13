package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeHerdrConfigSource
import app.luxion.shogunai.domain.FakeKmpTestWiring
import app.luxion.shogunai.domain.FakeMachineCapacity
import app.luxion.shogunai.domain.FakeOrcaClient
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeResourceLimitsRepository
import app.luxion.shogunai.domain.ScriptedShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.engine.ShogunEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverlapCommandTest {

    private val base = "/home/dev/projects/main-repo"
    private val common = "$base/.git"
    private val task1 = "/home/dev/projects/worktrees/TASK-1"
    private val task2 = "/home/dev/projects/worktrees/TASK-2"
    private val task3 = "/home/dev/projects/worktrees/TASK-3"
    private val review = "/home/dev/projects/worktrees/review"
    private val gone = "/home/dev/projects/worktrees/gone"

    private val porcelain = """
        worktree $base
        HEAD 1111111111111111111111111111111111111111
        branch refs/heads/develop

        worktree $task1
        HEAD 2222222222222222222222222222222222222222
        branch refs/heads/feature/TASK-1

        worktree $task2
        HEAD 3333333333333333333333333333333333333333
        branch refs/heads/feature/TASK-2

        worktree $task3
        HEAD 4444444444444444444444444444444444444444
        branch refs/heads/feature/TASK-3

        worktree $review
        HEAD 5555555555555555555555555555555555555555
        detached

        worktree $gone
        HEAD 6666666666666666666666666666666666666666
        branch refs/heads/feature/gone
        prunable gitdir file points to non-existent location
    """.trimIndent()

    private val out = StringBuilder()
    private val err = StringBuilder()

    private class RepositoryGit(
        private val base: String,
        private val worktrees: List<String>,
        private val porcelain: String,
        private val changes: Map<String, List<String>>,
        private val failingDiff: Set<String> = emptySet(),
        private val withoutBranches: Boolean = false,
    ) : ScriptedShellCommandExecutor() {
        val calls = mutableListOf<Pair<List<String>, String?>>()

        override suspend fun respond(command: List<String>, workingDirectory: String?): CommandResult {
            calls += command to workingDirectory
            val worktree = worktrees.firstOrNull { workingDirectory?.startsWith(it) == true } ?: base
            if (withoutBranches && "--verify" in command) return CommandResult(command, 1, "", "")
            val stdout = when {
                command.take(3) == listOf("git", "worktree", "list") -> porcelain
                "--show-toplevel" in command -> "$worktree\n"
                "--git-dir" in command -> "$base/.git\n$base/.git\n"
                "--git-common-dir" in command -> "$base/.git\n"
                command.take(2) == listOf("git", "symbolic-ref") -> "origin/develop\n"
                command.take(2) == listOf("git", "config") -> "branch.feature/TASK-3.gh-merge-base epic/redesign\n"
                command.take(2) == listOf("git", "merge-base") -> "mergebase\n"
                command.take(2) == listOf("git", "diff") && worktree in failingDiff ->
                    return CommandResult(command, 128, "", "fatal: bad object mergebase")
                command.take(2) == listOf("git", "diff") -> changes[worktree].orEmpty().joinToString("") { "$it\u0000" }
                else -> ""
            }
            return CommandResult(command, 0, stdout, "")
        }
    }

    private val sharedChanges = mapOf(
        task1 to listOf("core/Engine.kt", "README.md"),
        task2 to listOf("core/Engine.kt", "docs/guide.md"),
        task3 to listOf("docs/guide.md"),
    )

    private fun git(changes: Map<String, List<String>> = sharedChanges, failingDiff: Set<String> = emptySet(), withoutBranches: Boolean = false) =
        RepositoryGit(base, listOf(task1, task2, task3, review, gone), porcelain, changes, failingDiff, withoutBranches)

    private fun cli(
        workingDirectory: String = base,
        executor: RepositoryGit = git(),
        provisioning: FakeRepositoryProvisioningSource = FakeRepositoryProvisioningSource(),
    ) = ShogunCli(
        engine = ShogunEngine(
            cliEnginePorts(
                executor = executor,
                fileManager = FakeFileManager(existing = listOf(base, task1, task2, task3, review)),
                projectRepository = FakeProjectRepository(listOf(Project("p", "Main repo", ProjectConfig(base, "/home/dev/projects/worktrees", emptyList())))),
                provisioningSource = provisioning,
                gradleDaemons = FakeGradleDaemonSource(),
                gradleUserHome = "/home/dev/.gradle",
                environmentStateStore = FakeEnvironmentStateStore(),
                portProbe = FakePortProbe(),
                androidSdk = FakeAndroidSdk(),
                appleSimulators = FakeAppleSimulators(),
                kmpTestWiring = FakeKmpTestWiring(),
                resourceLimits = FakeResourceLimitsRepository(ResourceSettings()),
                machineCapacity = FakeMachineCapacity(),
                derivedData = FakeDerivedDataSource(),
                herdrConfig = FakeHerdrConfigSource(),
                orca = FakeOrcaClient(installed = false),
                workingDirectory = workingDirectory,
                environment = hostEnvironment(),
            ),
        ),
        workingDirectory = workingDirectory,
        out = out,
        err = err,
        canonicalPath = { it.trimEnd('/') },
        environment = hostEnvironment(),
    )

    private fun json(): JsonObject = Json.parseToJsonElement(out.toString()).jsonObject

    @Test
    fun `the main checkout lists every file changed by more than one worktree with their bases`() {
        assertEquals(0, cli().run(listOf("overlap")), err.toString())

        assertEquals(
            """
            core/Engine.kt
              feature/TASK-1 (base develop) $task1
              feature/TASK-2 (base develop) $task2
            docs/guide.md
              feature/TASK-2 (base develop) $task2
              feature/TASK-3 (base epic/redesign) $task3

            """.trimIndent(),
            out.toString(),
        )
    }

    @Test
    fun `inside a worktree only the files it shares are listed`() {
        assertEquals(0, cli(workingDirectory = "$task1/core").run(listOf("overlap")), err.toString())

        assertEquals("core/Engine.kt\n  feature/TASK-1 (base develop) $task1\n  feature/TASK-2 (base develop) $task2\n", out.toString())
    }

    @Test
    fun `a target selects the worktree whose files are listed`() {
        assertEquals(0, cli().run(listOf("overlap", "TASK-3")), err.toString())

        assertEquals("docs/guide.md\n  feature/TASK-2 (base develop) $task2\n  feature/TASK-3 (base epic/redesign) $task3\n", out.toString())
    }

    @Test
    fun `says when nothing is shared`() {
        val separate = git(mapOf(task1 to listOf("a.kt"), task2 to listOf("b.kt")))

        assertEquals(0, cli(executor = separate).run(listOf("overlap")))
        assertEquals("No file is changed by more than one worktree.\n", out.toString())

        out.clear()
        assertEquals(0, cli(executor = separate).run(listOf("overlap", "TASK-1")))
        assertEquals("No file of feature/TASK-1 is changed by another worktree.\n", out.toString())

        out.clear()
        assertEquals(0, cli(executor = separate, workingDirectory = review).run(listOf("overlap")))
        assertEquals("No file of (detached) is changed by another worktree.\n", out.toString())
    }

    @Test
    fun `a detached worktree is shown compared with the main branch`() {
        assertEquals(0, cli(executor = git(mapOf(task1 to listOf("a.kt"), review to listOf("a.kt")))).run(listOf("overlap")))

        assertEquals("a.kt\n  feature/TASK-1 (base develop) $task1\n  (detached) (base develop) $review\n", out.toString())
    }

    @Test
    fun `an unknown target is an error`() {
        assertEquals(1, cli().run(listOf("overlap", "TASK-999")))

        assertTrue(err.contains("TASK-999"), err.toString())
        assertEquals("", out.toString())
    }

    @Test
    fun `skipped worktrees are reported on standard error and the others are still compared`() {
        assertEquals(0, cli(executor = git(failingDiff = setOf(task3))).run(listOf("overlap")))

        assertEquals(
            "shogun: skipped $task3: couldn't read its changes: fatal: bad object mergebase\n" +
                "shogun: skipped $gone: its folder is missing (prunable)\n",
            err.toString(),
        )
        assertEquals("core/Engine.kt\n  feature/TASK-1 (base develop) $task1\n  feature/TASK-2 (base develop) $task2\n", out.toString())
    }

    @Test
    fun `worktrees without a base to compare with are reported as skipped`() {
        assertEquals(0, cli(executor = git(withoutBranches = true)).run(listOf("overlap")))

        assertTrue(err.contains("shogun: skipped $task1: no base to compare with (no recorded base and no main branch)"), err.toString())
        assertEquals("No file is changed by more than one worktree.\n", out.toString())
    }

    @Test
    fun `json lists the files, their worktrees and the skipped ones`() {
        assertEquals(0, cli(workingDirectory = task1).run(listOf("overlap", "--json")), err.toString())

        val json = json()
        assertEquals(base, json.getValue("repository").jsonPrimitive.content)
        assertEquals(task1, json.getValue("worktree").jsonPrimitive.content)
        val file = json.getValue("files").jsonArray.single().jsonObject
        assertEquals("core/Engine.kt", file.getValue("path").jsonPrimitive.content)
        val worktree = file.getValue("worktrees").jsonArray.first().jsonObject
        assertEquals(mapOf("path" to task1, "branch" to "feature/TASK-1", "base" to "develop"), worktree.mapValues { it.value.jsonPrimitive.content })
        val skipped = json.getValue("skipped").jsonArray.single().jsonObject
        assertEquals(gone, skipped.getValue("path").jsonPrimitive.content)
        assertEquals("feature/gone", skipped.getValue("branch").jsonPrimitive.content)
        assertEquals("its folder is missing (prunable)", skipped.getValue("reason").jsonPrimitive.content)
    }

    @Test
    fun `json from the main checkout has no worktree and empty lists when nothing is shared`() {
        val separate = git(mapOf(review to listOf("a.kt"), task1 to listOf("a.kt")))

        assertEquals(0, cli(executor = separate).run(listOf("overlap", "--json")))
        assertEquals(JsonNull, json()["worktree"])
        assertEquals(JsonNull, json().getValue("files").jsonArray.single().jsonObject.getValue("worktrees").jsonArray.last().jsonObject["branch"])

        out.clear()
        assertEquals(0, cli(executor = git(emptyMap())).run(listOf("overlap", "--json")))
        assertEquals(0, json().getValue("files").jsonArray.size)
    }

    @Test
    fun `the files the repository declares are left out unless all is given`() {
        val provisioning = FakeRepositoryProvisioningSource(mapOf(base to RepositoryProvisioning(overlapIgnore = listOf("docs/roadmap*.md"))))
        val executor = git()

        assertEquals(0, cli(executor = executor, provisioning = provisioning).run(listOf("overlap")))
        val excluded = executor.calls.filter { it.first.take(2) == listOf("git", "diff") }
        assertTrue(excluded.all { it.first.takeLast(3) == listOf("--", ".", ":(exclude,glob)docs/roadmap*.md") }, excluded.toString())

        executor.calls.clear()
        assertEquals(0, cli(executor = executor, provisioning = provisioning).run(listOf("overlap", "--all")))
        val all = executor.calls.filter { it.first.take(2) == listOf("git", "diff") }
        assertTrue(all.isNotEmpty() && all.all { it.first.takeLast(2) == listOf("--", ".") }, all.toString())
    }

    @Test
    fun `an invalid repository file fails naming it`() {
        val provisioning = FakeRepositoryProvisioningSource(unreadable = setOf(base))

        assertEquals(1, cli(provisioning = provisioning).run(listOf("overlap")))

        assertTrue(err.contains(".shogunai.json"), err.toString())
    }

    @Test
    fun `parses a target, all and json and rejects anything else`() {
        assertEquals(ParsedArguments.Command(OverlapCommand()), parseArguments(listOf("overlap")))
        assertEquals(
            ParsedArguments.Command(OverlapCommand("TASK-1", all = true, json = true)),
            parseArguments(listOf("overlap", "--json", "TASK-1", "--all")),
        )
        assertEquals(ParsedArguments.UsageError("unknown option '--lines' for 'overlap'"), parseArguments(listOf("overlap", "--lines")))
        assertEquals(ParsedArguments.UsageError("'overlap' takes a single target, got 'a' and 'b'"), parseArguments(listOf("overlap", "a", "b")))
        assertEquals(2, cli().run(listOf("overlap", "--lines")))
        assertFalse(err.isEmpty())
    }

    @Test
    fun `help describes the command`() {
        assertEquals(0, cli().run(listOf("overlap", "--help")))

        assertTrue(out.startsWith("Usage: shogun overlap [options]"), out.toString())
        assertTrue(out.contains("overlap.ignore"), out.toString())
        assertTrue(CliHelp.USAGE.contains("overlap [<task id | path | branch>] [--all] [--json]"))
    }
}
