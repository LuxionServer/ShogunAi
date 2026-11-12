package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.model.HostOs
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HostEnginePortsTest {

    private val home = createTempDirectory("host-engine-ports").toString()
    private val bin = createTempDirectory("host-engine-ports-bin").toString()
    private val environment = SystemHostEnvironment(
        HostOs.LINUX,
        mapOf("PATH" to bin, "GRADLE_USER_HOME" to "$home/gradle-home"),
        home,
        loginShell = null,
        userBinFolders = emptyList(),
    )
    private val files = ShogunFiles(home)

    private fun ports(shareDeviceStates: Boolean) =
        hostEnginePorts(environment, files, JsonSettingsFile(files.settings), { "$home/projects/main-repo" }, shareDeviceStates)

    @Test
    fun `the desktop app shares emulator state reads and the command line doesn't`() {
        assertIs<SharedDeviceStatesAndroidSdk>(ports(shareDeviceStates = true).androidSdk)
        assertIs<LocalAndroidSdk>(ports(shareDeviceStates = false).androidSdk)
    }

    @Test
    fun `the host's folders and Gradle home reach the ports`() {
        val ports = ports(shareDeviceStates = false)

        assertEquals(home, ports.userHome)
        assertEquals("$home/gradle-home", ports.gradleUserHome)
        assertEquals(listOf(AdbPhysicalDevices::class, DevicectlPhysicalDevices::class), ports.physicalDevices.map { it::class })
    }

    @Test
    fun `tools are looked for on the host's PATH`() {
        assertEquals(false, ports(shareDeviceStates = false).herdr.isInstalled())
    }

    @Test
    fun `commands run with the host's PATH`() = runTest {
        val java = ProcessHandle.current().info().command().get()

        val result = ports(shareDeviceStates = false).executor.executeWithin(listOf(java, "-version"), CommandTimeouts.LOCAL_TOOL)

        assertTrue(result.isSuccess, result.stderr)
    }

    @Test
    fun `devicectl is told the host's operating system`() {
        assertEquals("Mac OS X", devicectlOsName(HostOs.MACOS))
        assertEquals("LINUX", devicectlOsName(HostOs.LINUX))
    }
}
