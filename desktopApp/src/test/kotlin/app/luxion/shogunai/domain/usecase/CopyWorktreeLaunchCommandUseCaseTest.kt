package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeClipboardWriter
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CopyWorktreeLaunchCommandUseCaseTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "/home/dev/projects/main-repo",
        worktreesRoot = "/home/dev/projects",
        secretFiles = emptyList(),
    )
    private val worktreePath = "/home/dev/projects/TASK-123"

    @Test
    fun `copies the cd and default headroom-wrapped command to the clipboard`() = runTest {
        val clipboard = FakeClipboardWriter()
        val useCase = CopyWorktreeLaunchCommandUseCase(config, clipboard)

        val result = useCase(worktreePath)

        assertTrue(result.isSuccess)
        assertEquals(listOf("cd '$worktreePath' && headroom wrap claude"), clipboard.writtenText)
    }

    @Test
    fun `omits the headroom wrap when disabled`() = runTest {
        val configWithoutHeadroom = config.copy(agentLaunchConfig = AgentLaunchConfig(useHeadroom = false))
        val clipboard = FakeClipboardWriter()
        val useCase = CopyWorktreeLaunchCommandUseCase(configWithoutHeadroom, clipboard)

        useCase(worktreePath).getOrThrow()

        assertEquals(listOf("cd '$worktreePath' && claude"), clipboard.writtenText)
    }

    @Test
    fun `fails with ClipboardWriteFailed when the clipboard write fails`() = runTest {
        val cause = RuntimeException("no clipboard owner")
        val clipboard = FakeClipboardWriter { Result.failure(cause) }
        val useCase = CopyWorktreeLaunchCommandUseCase(config, clipboard)

        val error = useCase(worktreePath).exceptionOrNull()

        val clipboardError = assertIs<WorktreeError.ClipboardWriteFailed>(error)
        assertEquals(cause, clipboardError.cause)
    }
}
