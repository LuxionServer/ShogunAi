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
    fun `copies the cd and the default plain agent command to the clipboard`() = runTest {
        val clipboard = FakeClipboardWriter()
        val useCase = CopyWorktreeLaunchCommandUseCase(config, clipboard, windows = false)

        val result = useCase(worktreePath)

        assertTrue(result.isSuccess)
        assertEquals(listOf("cd '$worktreePath' && claude"), clipboard.writtenText)
    }

    @Test
    fun `adds the headroom wrap when enabled`() = runTest {
        val configWithHeadroom = config.copy(agentLaunchConfig = AgentLaunchConfig(useHeadroom = true))
        val clipboard = FakeClipboardWriter()
        val useCase = CopyWorktreeLaunchCommandUseCase(configWithHeadroom, clipboard, windows = false)

        useCase(worktreePath).getOrThrow()

        assertEquals(listOf("cd '$worktreePath' && headroom wrap claude"), clipboard.writtenText)
    }

    @Test
    fun `fails with ClipboardWriteFailed when the clipboard write fails`() = runTest {
        val cause = RuntimeException("no clipboard owner")
        val clipboard = FakeClipboardWriter { Result.failure(cause) }
        val useCase = CopyWorktreeLaunchCommandUseCase(config, clipboard, windows = false)

        val error = useCase(worktreePath).exceptionOrNull()

        val clipboardError = assertIs<WorktreeError.ClipboardWriteFailed>(error)
        assertEquals(cause, clipboardError.cause)
    }

    @Test
    fun `quotes a path with shell syntax so it is taken literally`() = runTest {
        val clipboard = FakeClipboardWriter()

        CopyWorktreeLaunchCommandUseCase(config, clipboard, windows = false)("/home/dev/x\$(id)").getOrThrow()

        assertEquals(listOf("cd '/home/dev/x\$(id)' && claude"), clipboard.writtenText)
    }

    @Test
    fun `escapes a single quote in the path`() = runTest {
        val clipboard = FakeClipboardWriter()

        CopyWorktreeLaunchCommandUseCase(config, clipboard, windows = false)("/home/dev/it's").getOrThrow()

        assertEquals(listOf("cd '/home/dev/it'\\''s' && claude"), clipboard.writtenText)
    }

    @Test
    fun `uses double quotes on Windows`() = runTest {
        val clipboard = FakeClipboardWriter()

        CopyWorktreeLaunchCommandUseCase(config, clipboard, windows = true)("C:\\Users\\dev\\TASK-123").getOrThrow()

        assertEquals(listOf("cd \"C:\\Users\\dev\\TASK-123\" && claude"), clipboard.writtenText)
    }

    @Test
    fun `copies the command of the given agent`() = runTest {
        val clipboard = FakeClipboardWriter()
        val codex = AgentLaunchConfig(agentCommand = "codex --full-auto", useHeadroom = false)

        CopyWorktreeLaunchCommandUseCase(config, clipboard, windows = false)(worktreePath, codex).getOrThrow()

        assertEquals(listOf("cd '$worktreePath' && codex --full-auto"), clipboard.writtenText)
    }
}
