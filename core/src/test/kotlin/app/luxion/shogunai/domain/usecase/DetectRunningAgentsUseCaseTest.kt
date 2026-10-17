package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DetectRunningAgentsUseCaseTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "/home/dev/projects/main-repo",
        worktreesRoot = "/home/dev/projects",
        secretFiles = emptyList(),
    )

    private val main = Worktree(path = "/home/dev/projects/main-repo", branch = "develop", isMain = true)
    private val nested = Worktree(path = "/home/dev/projects/main-repo/.claude/worktrees/task", branch = "worktree-task")
    private val sibling = Worktree(path = "/home/dev/projects/TASK-1", branch = "feature/TASK-1")
    private val worktrees = listOf(main, nested, sibling)

    private val lsofOutput = """
        p100
        c2.1.301
        fcwd
        n/home/dev/projects/main-repo/.claude/worktrees/task
        p101
        czsh
        fcwd
        n/home/dev/projects/main-repo
        p102
        cnode
        fcwd
        n/home/dev/projects/TASK-1/app
        p103
        ccodex
        fcwd
        n/home/dev/projects/main-repo/src
        p104
        cclaude
        fcwd
        n/home/dev/projects/main-repo/.claude/worktrees/task
        p200
        cclaude
        fcwd
        n/home/dev/elsewhere
    """.trimIndent()

    private val psOutput = """
          100 claude
          101 -zsh
          102 /usr/local/bin/node
          103 /opt/homebrew/bin/codex
          104 /Users/dev/.local/bin/claude
    """.trimIndent()

    private fun executor(lsof: String = lsofOutput, ps: String = psOutput, failLsof: Boolean = false) =
        FakeShellCommandExecutor { command ->
            when (command.first()) {
                "lsof" -> if (failLsof) failure(command, exitCode = 1) else success(command, stdout = lsof)
                "ps" -> success(command, stdout = ps)
                else -> success(command)
            }
        }

    @Test
    fun `agents are attributed to the deepest worktree containing their working directory`() = runTest {
        val agents = DetectRunningAgentsUseCase(executor())(config, worktrees).getOrThrow()

        assertEquals(listOf(RunningAgent("claude")), agents[nested.path])
        assertEquals(listOf(RunningAgent("codex")), agents[main.path])
        assertEquals(null, agents[sibling.path])
    }

    @Test
    fun `only processes inside some worktree are inspected with ps`() = runTest {
        val executor = executor()

        DetectRunningAgentsUseCase(executor)(config, worktrees).getOrThrow()

        val ps = executor.executedCommands.single { it.first() == "ps" }
        assertEquals(setOf("100", "101", "102", "103", "104"), ps.last().split(",").toSet())
    }

    @Test
    fun `the project's own agent command is recognized`() = runTest {
        val custom = config.copy(agentLaunchConfig = AgentLaunchConfig(agentCommand = "/opt/tools/my-agent --fast"))
        val executor = executor(
            lsof = "p300\ncmy-agent\nfcwd\nn${sibling.path}",
            ps = "300 /opt/tools/my-agent",
        )

        val agents = DetectRunningAgentsUseCase(executor)(custom, worktrees).getOrThrow()

        assertEquals(listOf(RunningAgent("my-agent")), agents[sibling.path])
    }

    @Test
    fun `no process inside a worktree skips ps`() = runTest {
        val executor = executor(lsof = "p1\nczsh\nfcwd\nn/somewhere/else")

        val agents = DetectRunningAgentsUseCase(executor)(config, worktrees).getOrThrow()

        assertTrue(agents.isEmpty())
        assertTrue(executor.executedCommands.none { it.first() == "ps" })
    }

    @Test
    fun `a failing lsof fails the detection`() = runTest {
        val error = DetectRunningAgentsUseCase(executor(failLsof = true))(config, worktrees).exceptionOrNull()

        assertIs<WorktreeError.ProcessListingFailed>(error)
    }

    @Test
    fun `one detection attributes agents across projects with each project's own agent`() = runTest {
        val api = Worktree(path = "/home/dev/projects/api", branch = "main", isMain = true)
        val executor = executor(
            lsof = "p300\ncmy-agent\nfcwd\nn${sibling.path}\np301\ncmy-agent\nfcwd\nn${api.path}\np302\ncclaude\nfcwd\nn${api.path}",
            ps = "300 /opt/tools/my-agent\n301 /opt/tools/my-agent\n302 claude",
        )

        val agents = DetectRunningAgentsUseCase(executor)(
            listOf(
                AgentSearch("/opt/tools/my-agent", worktrees),
                AgentSearch("claude", listOf(api)),
            ),
        ).getOrThrow()

        assertEquals(listOf(RunningAgent("my-agent")), agents[sibling.path])
        assertEquals(listOf(RunningAgent("claude")), agents[api.path])
        assertEquals(1, executor.executedCommands.count { it.first() == "lsof" })
    }

    @Test
    fun `nothing to search runs no command`() = runTest {
        val executor = executor()

        val agents = DetectRunningAgentsUseCase(executor)(emptyList()).getOrThrow()

        assertTrue(agents.isEmpty())
        assertTrue(executor.executedCommands.isEmpty())
    }
}
