package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class WorktreeAgentsTest {

    private val claude = AgentLaunchConfig(name = "Claude", agentCommand = "claude")
    private val codex = AgentLaunchConfig(name = "Codex", agentCommand = "codex")
    private val config = ProjectConfig(
        baseRepositoryPath = "/home/dev/projects/main-repo",
        worktreesRoot = "/home/dev/projects/worktrees",
        secretFiles = emptyList(),
        agentLaunchConfig = claude,
        additionalAgents = listOf(codex),
    )
    private val task = "/home/dev/projects/worktrees/TASK-1"

    @Test
    fun `a worktree without a choice uses the default agent`() {
        assertEquals(claude, WorktreeAgents.agentFor(config, emptyMap(), task))
    }

    @Test
    fun `a chosen agent is used for the worktree`() {
        val choices = WorktreeAgents.choose(config, emptyMap(), task, codex)

        assertEquals(codex, WorktreeAgents.agentFor(config, choices, task))
    }

    @Test
    fun `choosing the default agent clears the choice`() {
        val choices = WorktreeAgents.choose(config, mapOf(task to "Codex"), task, claude)

        assertEquals(emptyMap(), choices)
    }

    @Test
    fun `an agent removed from the project falls back to the default`() {
        val withoutCodex = config.copy(additionalAgents = emptyList())

        assertEquals(claude, WorktreeAgents.agentFor(withoutCodex, mapOf(task to "Codex"), task))
    }

    @Test
    fun `prunes choices of missing worktrees and removed agents of the project only`() {
        val other = "/home/dev/other/TASK-9"
        val choices = mapOf(
            task to "Codex",
            "$task-gone" to "Codex",
            "/home/dev/projects/worktrees/TASK-2" to "Removed",
            other to "Codex",
        )

        val pruned = WorktreeAgents.prune(
            config,
            choices,
            setOf(task, "/home/dev/projects/worktrees/TASK-2"),
        )

        assertEquals(mapOf(task to "Codex", other to "Codex"), pruned)
    }
}
