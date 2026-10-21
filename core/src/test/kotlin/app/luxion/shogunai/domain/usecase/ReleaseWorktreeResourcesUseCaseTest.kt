package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.Worktree
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseWorktreeResourcesUseCaseTest {

    private val repository = "/home/dev/projects/main-repo"
    private val common = "$repository/.git"
    private val task = "/home/dev/projects/worktrees/TASK-123"
    private val config = ProjectConfig(baseRepositoryPath = repository, worktreesRoot = "/home/dev/projects/worktrees", secretFiles = emptyList())
    private val worktree = Worktree(task, "feature/TASK-123")

    private val git = object : ShellCommandExecutor {
        override suspend fun execute(command: List<String>, workingDirectory: String?) = FakeShellCommandExecutor.success(
            command,
            if (workingDirectory == task) "$common/worktrees/TASK-123\n$common\n" else "$common\n$common\n",
        )
    }

    private fun releaseWith(emulator: EmulatorProvider?, sdk: FakeAndroidSdk): ReleaseWorktreeResourcesUseCase {
        val source = FakeRepositoryProvisioningSource(
            byRepository = mapOf(repository to RepositoryProvisioning(providers = RepositoryProviders(emulator = emulator))),
        )
        val acquired = FakeEnvironmentStateStore(mapOf("$common/worktrees/TASK-123" to mapOf("emulator" to 5554)))
        val resolve = ResolveWorktreeEnvironmentUseCase(git, source, acquired, FakePortProbe())
        val simulators = FakeAppleSimulators()
        val daemons = FakeGradleDaemonSource()
        return ReleaseWorktreeResourcesUseCase(
            provisioningSource = source,
            readEmulatorStates = ReadEmulatorStatesUseCase(source, resolve, sdk),
            stopEmulator = StopWorktreeEmulatorUseCase(source, resolve, sdk),
            readSimulatorStates = ReadSimulatorStatesUseCase(source, simulators, resolve),
            stopSimulator = StopWorktreeSimulatorUseCase(source, simulators, resolve),
            listDaemons = ListGradleDaemonsUseCase(daemons, FakeFileManager()),
            stopDaemons = StopGradleDaemonsUseCase(daemons, git),
        )
    }

    @Test
    fun `stops the running emulator of the worktree after planning`() = runTest {
        val sdk = FakeAndroidSdk(states = mutableMapOf("emulator-5554" to EmulatorState.RUNNING))
        val release = releaseWith(EmulatorProvider(), sdk)

        val plan = release.plan(config, worktree, listOf(worktree))
        val released = release.release(config, plan)

        assertEquals("emulator-5554", plan.emulatorSerial)
        assertEquals(listOf("emulator-5554"), released.stoppedEmulators)
        assertEquals(listOf("emulator-5554"), sdk.stopped)
        assertTrue(released.failures.isEmpty())
    }

    @Test
    fun `plans nothing for a stopped emulator or a repository without providers`() = runTest {
        val stopped = FakeAndroidSdk(states = mutableMapOf("emulator-5554" to EmulatorState.STOPPED))
        assertNull(releaseWith(EmulatorProvider(), stopped).plan(config, worktree, listOf(worktree)).emulatorSerial)

        val untouched = FakeAndroidSdk()
        val release = releaseWith(null, untouched)
        val released = release.release(config, release.plan(config, worktree, listOf(worktree)))

        assertEquals(ReleasedResources(), released)
        assertEquals(0, untouched.locates)
    }
}
