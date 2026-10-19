package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.InstallChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DetectInstallChannelUseCaseTest {

    private fun detect(
        os: HostOs,
        appLocation: String = "/somewhere",
        existing: Set<String> = emptySet(),
        executor: FakeShellCommandExecutor = FakeShellCommandExecutor(),
    ) = DetectInstallChannelUseCase(os, appLocation, { it in existing }, executor)

    @Test
    fun `Homebrew on Apple Silicon and on Intel`() = runTest {
        assertEquals(InstallChannel.HOMEBREW, detect(HostOs.MACOS, existing = setOf("/opt/homebrew/Caskroom/shogunai"))())
        assertEquals(InstallChannel.HOMEBREW, detect(HostOs.MACOS, existing = setOf("/usr/local/Caskroom/shogunai"))())
    }

    @Test
    fun `a dmg install when there is no cask`() = runTest {
        assertEquals(InstallChannel.DMG, detect(HostOs.MACOS)())
    }

    @Test
    fun `Scoop by the app location`() = runTest {
        val executor = FakeShellCommandExecutor()

        val channel = detect(HostOs.WINDOWS, "C:\\Users\\someone\\scoop\\apps\\shogunai\\current\\runtime", executor = executor)()

        assertEquals(InstallChannel.SCOOP, channel)
        assertEquals(emptyList(), executor.executedCommands)
    }

    @Test
    fun `winget when it lists the app`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, "ShogunAi LuxionServer.ShogunAi 1.2.0") }

        assertEquals(InstallChannel.WINGET, detect(HostOs.WINDOWS, executor = executor)())
        assertEquals(listOf("winget", "list", "--id", "LuxionServer.ShogunAi", "--exact"), executor.executedCommands.single())
    }

    @Test
    fun `an msi install when winget doesn't list the app`() = runTest {
        val notListed = FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it) }
        val noMatch = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, "No installed package found") }

        assertEquals(InstallChannel.MSI, detect(HostOs.WINDOWS, executor = notListed)())
        assertEquals(InstallChannel.MSI, detect(HostOs.WINDOWS, executor = noMatch)())
    }

    @Test
    fun `an msi install when winget isn't available`() = runTest {
        val executor = FakeShellCommandExecutor { throw CommandExecutionException(it, RuntimeException("not found")) }

        assertEquals(InstallChannel.MSI, detect(HostOs.WINDOWS, executor = executor)())
    }

    @Test
    fun `a deb install under opt and anything else on Linux`() = runTest {
        assertEquals(InstallChannel.DEB, detect(HostOs.LINUX, "/opt/shogunai/lib/runtime")())
        assertEquals(InstallChannel.UNKNOWN, detect(HostOs.LINUX, "/home/someone/shogunai")())
    }
}
