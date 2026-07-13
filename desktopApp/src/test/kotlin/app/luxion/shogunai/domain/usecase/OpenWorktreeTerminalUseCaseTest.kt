package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeTerminalEmulatorDetector
import app.luxion.shogunai.domain.FakeTerminalLauncher
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.TerminalEmulator
import app.luxion.shogunai.domain.model.TerminalPreference
import app.luxion.shogunai.domain.model.TerminalSelectionMode
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OpenWorktreeTerminalUseCaseTest {

    private val worktreePath = "/home/dev/projects/TASK-123"

    private fun config(
        terminalPreference: TerminalPreference = TerminalPreference(),
        agentLaunchConfig: AgentLaunchConfig = AgentLaunchConfig(),
    ) = ProjectConfig(
        baseRepositoryPath = "/home/dev/projects/main-repo",
        worktreesRoot = "/home/dev/projects",
        secretFiles = emptyList(),
        terminalPreference = terminalPreference,
        agentLaunchConfig = agentLaunchConfig,
    )

    @Test
    fun `auto-detect launches the first available emulator`() = runTest {
        val launcher = FakeTerminalLauncher()
        val detector = FakeTerminalEmulatorDetector(listOf(TerminalEmulator.GNOME_TERMINAL))
        val useCase = OpenWorktreeTerminalUseCase(config(), launcher, detector)

        val result = useCase(worktreePath)

        assertTrue(result.isSuccess)
        assertEquals(1, launcher.launched.size)
        assertEquals(worktreePath, launcher.launched.single().second)
    }

    @Test
    fun `fixed emulator launches that exact emulator when available`() = runTest {
        val launcher = FakeTerminalLauncher()
        val detector = FakeTerminalEmulatorDetector(listOf(TerminalEmulator.KONSOLE))
        val preference = TerminalPreference(mode = TerminalSelectionMode.FIXED, emulator = TerminalEmulator.KONSOLE)
        val useCase = OpenWorktreeTerminalUseCase(config(preference), launcher, detector)

        val result = useCase(worktreePath)

        assertTrue(result.isSuccess)
        assertTrue(launcher.launched.single().first.first() == "konsole")
    }

    @Test
    fun `custom template substitutes path and command`() = runTest {
        val launcher = FakeTerminalLauncher()
        val detector = FakeTerminalEmulatorDetector()
        val preference = TerminalPreference(
            mode = TerminalSelectionMode.CUSTOM,
            customCommandTemplate = listOf("my-term", "--cwd", "{path}", "--run", "{command}"),
        )
        val useCase = OpenWorktreeTerminalUseCase(config(preference), launcher, detector)

        useCase(worktreePath).getOrThrow()

        assertEquals(
            listOf("my-term", "--cwd", worktreePath, "--run", "headroom wrap claude"),
            launcher.launched.single().first,
        )
    }

    @Test
    fun `fails with NoTerminalAvailable when auto-detect finds nothing`() = runTest {
        val launcher = FakeTerminalLauncher()
        val detector = FakeTerminalEmulatorDetector(emptyList())
        val useCase = OpenWorktreeTerminalUseCase(config(), launcher, detector)

        val error = useCase(worktreePath).exceptionOrNull()

        assertIs<WorktreeError.NoTerminalAvailable>(error)
        assertTrue(launcher.launched.isEmpty())
    }

    @Test
    fun `fails with TerminalLaunchFailed when the launcher reports failure`() = runTest {
        val cause = RuntimeException("no se pudo iniciar")
        val launcher = FakeTerminalLauncher { _, _ -> Result.failure(cause) }
        val detector = FakeTerminalEmulatorDetector(listOf(TerminalEmulator.XTERM))
        val useCase = OpenWorktreeTerminalUseCase(config(), launcher, detector)

        val error = useCase(worktreePath).exceptionOrNull()

        val launchError = assertIs<WorktreeError.TerminalLaunchFailed>(error)
        assertEquals(cause, launchError.cause)
    }

    @Test
    fun `runs the plain agent command when headroom wrapping is disabled`() = runTest {
        val launcher = FakeTerminalLauncher()
        val detector = FakeTerminalEmulatorDetector(listOf(TerminalEmulator.GNOME_TERMINAL))
        val agentLaunchConfig = AgentLaunchConfig(agentCommand = "claude", useHeadroom = false)
        val useCase = OpenWorktreeTerminalUseCase(config(agentLaunchConfig = agentLaunchConfig), launcher, detector)

        useCase(worktreePath).getOrThrow()

        val command = launcher.launched.single().first
        assertTrue(command.any { it.contains("claude") && !it.contains("headroom") })
    }
}
