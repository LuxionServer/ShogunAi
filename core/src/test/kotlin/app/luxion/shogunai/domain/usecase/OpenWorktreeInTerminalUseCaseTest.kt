package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeTerminalLauncher
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.TerminalId
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.parseEnvironmentVariable
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OpenWorktreeInTerminalUseCaseTest {

    private val repository = "/home/dev/projects/main-repo"
    private val common = "$repository/.git"
    private val config = ProjectConfig(
        baseRepositoryPath = repository,
        worktreesRoot = "/home/dev/projects/worktrees",
        secretFiles = emptyList(),
        agentLaunchConfig = AgentLaunchConfig(agentCommand = "claude", useHeadroom = false),
    )

    private val gitExecutor = object : ShellCommandExecutor {
        override suspend fun execute(command: List<String>, workingDirectory: String?) =
            FakeShellCommandExecutor.success(command, "$common/worktrees/TASK-8\n$common\n")
    }

    private val posixTerminal = FakeTerminalLauncher(TerminalId.TERMINAL)
    private val windowsTerminal = FakeTerminalLauncher(TerminalId.WINDOWS_TERMINAL)

    private fun useCase(vararg variables: Pair<String, String>) = OpenWorktreeInTerminalUseCase(
        config,
        listOf(posixTerminal, windowsTerminal),
        ResolveWorktreeEnvironmentUseCase(
            gitExecutor,
            FakeRepositoryProvisioningSource(
                byRepository = mapOf(
                    repository to RepositoryProvisioning(
                        environment = EnvironmentDeclaration(variables.map { (name, value) -> parseEnvironmentVariable(name, value) }),
                    ),
                ),
            ),
            FakeEnvironmentStateStore(),
            FakePortProbe(),
        ),
    )

    @Test
    fun `builds a POSIX line with the folder, the environment and the agent`() = runTest {
        useCase("PORT" to "\${port}", "SUFFIX" to "-\${worktree}")("/home/dev/projects/worktrees/TASK-8", TerminalId.TERMINAL).getOrThrow()

        val launch = posixTerminal.launches.single()
        assertEquals(
            "cd '/home/dev/projects/worktrees/TASK-8' && export PORT='20000' && export SUFFIX='-TASK-8' && claude",
            launch.line,
        )
        assertEquals(listOf("PORT" to "20000", "SUFFIX" to "-TASK-8"), launch.variables)
        assertEquals("claude", launch.agentCommand)
    }

    @Test
    fun `a worktree without environment just changes folder and runs the agent`() = runTest {
        useCase()("/home/dev/projects/worktrees/TASK-8", TerminalId.TERMINAL).getOrThrow()

        assertEquals("cd '/home/dev/projects/worktrees/TASK-8' && claude", posixTerminal.launches.single().line)
    }

    @Test
    fun `quotes paths and values holding shell syntax`() = runTest {
        useCase("MESSAGE" to "it's \$(id) `id` a b")("/home/dev/projects/x\$(id) y", TerminalId.TERMINAL).getOrThrow()

        assertEquals(
            "cd '/home/dev/projects/x\$(id) y' && export MESSAGE='it'\\''s \$(id) `id` a b' && claude",
            posixTerminal.launches.single().line,
        )
    }

    @Test
    fun `builds a PowerShell line for Windows Terminal`() = runTest {
        useCase("MESSAGE" to "it's \$(id) a b")("C:\\work\\x\$(id) y", TerminalId.WINDOWS_TERMINAL).getOrThrow()

        assertEquals(
            "Set-Location -LiteralPath 'C:\\work\\x\$(id) y'; \$env:MESSAGE = 'it''s \$(id) a b'; claude",
            windowsTerminal.launches.single().line,
        )
    }

    @Test
    fun `reports a terminal without a launcher`() = runTest {
        val error = useCase()("/home/dev/projects/worktrees/TASK-8", TerminalId.ZELLIJ).exceptionOrNull()

        assertIs<WorktreeError.TerminalUnsupported>(error)
        assertTrue(posixTerminal.launches.isEmpty())
    }

    @Test
    fun `passes the launcher's failure through`() = runTest {
        val failure = WorktreeError.TerminalLaunchFailed(TerminalId.TERMINAL, "boom")
        posixTerminal.failure = failure

        val error = useCase()("/home/dev/projects/worktrees/TASK-8", TerminalId.TERMINAL).exceptionOrNull()

        assertEquals(failure, error)
    }
}
