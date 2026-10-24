package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeRepositoryTrust
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeHerdrClient
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.ANDROID_SERIAL
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.model.HerdrOpening
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.parseEnvironmentVariable
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OpenWorktreeInHerdrUseCaseTest {

    private val repository = "/home/dev/projects/main-repo"
    private val common = "$repository/.git"
    private val task = Worktree(path = "/home/dev/projects/worktrees/TASK-8", branch = "feature/TASK-8")
    private val config = ProjectConfig(
        baseRepositoryPath = repository,
        worktreesRoot = "/home/dev/projects/worktrees",
        secretFiles = emptyList(),
        agentLaunchConfig = AgentLaunchConfig(agentCommand = "claude", useHeadroom = true),
    )

    private val gitExecutor = object : ShellCommandExecutor {
        override suspend fun execute(command: List<String>, workingDirectory: String?) =
            FakeShellCommandExecutor.success(command, "$common/worktrees/TASK-8\n$common\n")
    }

    private fun provisioning(vararg variables: Pair<String, String>) = FakeRepositoryProvisioningSource(
        byRepository = mapOf(
            repository to RepositoryProvisioning(
                environment = EnvironmentDeclaration(variables.map { (name, value) -> parseEnvironmentVariable(name, value) }),
            ),
        ),
    )

    private fun useCase(
        herdr: FakeHerdrClient,
        source: FakeRepositoryProvisioningSource = provisioning(),
        store: FakeEnvironmentStateStore = FakeEnvironmentStateStore(),
    ) = OpenWorktreeInHerdrUseCase(
        config,
        herdr,
        ResolveWorktreeEnvironmentUseCase(gitExecutor, source, store, FakePortProbe(), trust = FakeRepositoryTrust(approvesEverything = true)),
    )

    @Test
    fun `opens the worktree under its repository with its environment and runs the agent`() = runTest {
        val herdr = FakeHerdrClient()

        val opening = useCase(herdr, provisioning("PORT" to "\${port}", "APP_SUFFIX" to "-\${worktree}"))(task).getOrThrow()

        assertEquals(HerdrOpening.OPENED, opening)
        assertEquals(Triple(repository, task.path, "TASK-8"), herdr.opened.single())
        assertEquals(
            FakeHerdrClient.Split("w1:p1", task.path, listOf("PORT" to "20000", "APP_SUFFIX" to "-TASK-8")),
            herdr.splits.single(),
        )
        assertEquals(listOf("w1:p1"), herdr.closed)
        assertEquals(listOf("w1:p2" to "headroom wrap claude"), herdr.ran)
    }

    @Test
    fun `a provider's variables reach the pane`() = runTest {
        val herdr = FakeHerdrClient()
        val source = FakeRepositoryProvisioningSource(
            byRepository = mapOf(repository to RepositoryProvisioning(providers = RepositoryProviders(emulator = EmulatorProvider()))),
        )

        val acquired = FakeEnvironmentStateStore(mapOf("$common/worktrees/TASK-8" to mapOf("emulator" to 5554)))

        useCase(herdr, source, acquired)(task).getOrThrow()

        assertEquals(listOf(ANDROID_SERIAL to "emulator-5554"), herdr.splits.single().environment)
    }

    @Test
    fun `without an environment the agent runs in the root pane`() = runTest {
        val herdr = FakeHerdrClient()

        useCase(herdr)(task).getOrThrow()

        assertTrue(herdr.splits.isEmpty())
        assertTrue(herdr.closed.isEmpty())
        assertEquals(listOf("w1:p1" to "headroom wrap claude"), herdr.ran)
    }

    @Test
    fun `an already open worktree is only focused`() = runTest {
        val herdr = FakeHerdrClient(alreadyOpen = setOf(task.path))

        val opening = useCase(herdr, provisioning("PORT" to "\${port}"))(task).getOrThrow()

        assertEquals(HerdrOpening.FOCUSED, opening)
        assertEquals(1, herdr.opened.size)
        assertTrue(herdr.splits.isEmpty())
        assertTrue(herdr.closed.isEmpty())
        assertTrue(herdr.ran.isEmpty())
    }

    @Test
    fun `Herdr's errors are returned`() = runTest {
        val herdr = FakeHerdrClient(failure = WorktreeError.HerdrNotInstalled)

        assertIs<WorktreeError.HerdrNotInstalled>(useCase(herdr)(task).exceptionOrNull())
        assertTrue(herdr.ran.isEmpty())
    }

    @Test
    fun `a failing command is returned with Herdr's message`() = runTest {
        val herdr = FakeHerdrClient(runFailure = WorktreeError.HerdrCommandFailed(listOf("herdr", "pane", "run"), "pane w1:p1 not found"))

        val error = useCase(herdr)(task).exceptionOrNull()

        assertIs<WorktreeError.HerdrCommandFailed>(error)
        assertEquals("Herdr couldn't open the worktree: pane w1:p1 not found", error.message)
    }

    @Test
    fun `runs the given agent in the pane`() = runTest {
        val herdr = FakeHerdrClient()
        val codex = AgentLaunchConfig(agentCommand = "codex", useHeadroom = false)

        useCase(herdr)(task, codex).getOrThrow()

        assertEquals(listOf("w1:p1" to "codex"), herdr.ran)
    }
}
