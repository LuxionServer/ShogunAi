package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.HostOs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OrcaNotificationSettingsTest {
    private val home = Files.createTempDirectory("orca-home").toFile()
    private val both = setOf(AgentState.BLOCKED, AgentState.DONE)

    @AfterTest
    fun cleanUp() {
        home.deleteRecursively()
    }

    private fun macData() = File(home, "Library/Application Support/orca")

    private fun database(dataDirectory: File = macData(), profile: String = "local-default"): File =
        File(dataDirectory, "profiles/$profile/profile-state.db").apply {
            parentFile.mkdirs()
            writeText("")
        }

    private fun replying(stdout: String) = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, stdout) }

    private fun settings(
        executor: FakeShellCommandExecutor,
        os: HostOs = HostOs.MACOS,
        environment: Map<String, String> = emptyMap(),
    ) = OrcaNotificationSettings(executor, { environment[it] }, home.path, os)

    private fun payload(notifications: String) = """{"theme":"dark","notifications":$notifications}"""

    @Test
    fun `without Orca's data folder nothing is notified by Orca`() = runTest {
        val executor = replying("")

        assertEquals(emptySet(), settings(executor).notifiedStates())
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `the settings are read read-only from the active profile's database`() = runTest {
        val db = database()
        val executor = replying(payload("""{"enabled":true,"agentTaskComplete":true,"agentNeedsInput":false}"""))

        assertEquals(setOf(AgentState.DONE), settings(executor).notifiedStates())
        val command = executor.executedCommands.single()
        assertEquals(listOf("sqlite3", "-readonly"), command.take(2))
        assertEquals("file:${db.absoluteFile.toURI().path.replace(" ", "%20")}?mode=ro&immutable=1", command[2])
        assertEquals("select payload from profile_state_documents where domain = 'settings'", command[3])
    }

    @Test
    fun `needing input follows task completion when Orca never stored it`() = runTest {
        database()

        assertEquals(both, settings(replying(payload("""{"enabled":true,"agentTaskComplete":true}"""))).notifiedStates())
        assertEquals(emptySet(), settings(replying(payload("""{"enabled":true,"agentTaskComplete":false}"""))).notifiedStates())
        assertEquals(setOf(AgentState.BLOCKED), settings(replying(payload("""{"agentTaskComplete":false,"agentNeedsInput":true}"""))).notifiedStates())
    }

    @Test
    fun `notifications turned off in Orca notify nothing`() = runTest {
        database()

        assertEquals(emptySet(), settings(replying(payload("""{"enabled":false,"agentTaskComplete":true}"""))).notifiedStates())
        assertEquals(both, settings(replying(payload("""{"enabled":"false"}"""))).notifiedStates())
    }

    @Test
    fun `anything unreadable falls back to Orca's defaults`() = runTest {
        assertEquals(both, settingsWithData(replying("")).notifiedStates())
        database()
        assertEquals(both, settings(replying("")).notifiedStates())
        assertEquals(both, settings(replying("not json")).notifiedStates())
        assertEquals(both, settings(replying("""{"theme":"dark"}""")).notifiedStates())
        assertEquals(both, settings(replying("""["settings"]""")).notifiedStates())
        assertEquals(both, settings(FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it) }).notifiedStates())
        assertEquals(both, settings(throwing(CommandExecutionException(listOf("sqlite3"), IOException("missing")))).notifiedStates())
    }

    @Test
    fun `a cancelled read is not swallowed`() = runTest {
        database()

        assertFailsWith<CancellationException> { settings(throwing(CancellationException("stop"))).notifiedStates() }
    }

    @Test
    fun `the active profile comes from Orca's profile index`() = runTest {
        database(profile = "work")
        File(macData(), "orca-profile-index.json").writeText("""{"activeProfileId":"work"}""")
        val executor = replying(payload("""{"enabled":true,"agentTaskComplete":false}"""))

        assertEquals(emptySet(), settings(executor).notifiedStates())
        assertTrue(executor.executedCommands.single()[2].contains("/profiles/work/"))
    }

    @Test
    fun `an unusable profile id falls back to the default profile`() = runTest {
        database()
        listOf("""{"activeProfileId":"../x"}""", """{"activeProfileId":"a\\b"}""", """{"activeProfileId":""}""", """{"activeProfileId":3}""", """{"activeProfileId":{}}""", """{"other":"work"}""", "[]", "nope").forEach { index ->
            File(macData(), "orca-profile-index.json").writeText(index)
            val executor = replying(payload("""{"enabled":false}"""))

            assertEquals(emptySet(), settings(executor).notifiedStates(), index)
            assertTrue(executor.executedCommands.single()[2].contains("/profiles/local-default/"), index)
        }
    }

    @Test
    fun `Orca's data folder follows each system's conventions`() = runTest {
        val cases = listOf(
            Triple(HostOs.LINUX, emptyMap(), File(home, ".config/orca")),
            Triple(HostOs.LINUX, mapOf("XDG_CONFIG_HOME" to File(home, "xdg").path), File(home, "xdg/orca")),
            Triple(HostOs.LINUX, mapOf("XDG_CONFIG_HOME" to ""), File(home, ".config/orca")),
            Triple(HostOs.WINDOWS, mapOf("APPDATA" to File(home, "roaming").path), File(home, "roaming/orca")),
            Triple(HostOs.WINDOWS, mapOf("APPDATA" to " "), File(home, "AppData/Roaming/orca")),
        )
        cases.forEach { (os, environment, folder) ->
            database(folder)
            val executor = replying(payload("""{"enabled":false}"""))

            assertEquals(emptySet(), settings(executor, os, environment).notifiedStates(), "$os $environment")
            folder.deleteRecursively()
        }
    }

    private fun settingsWithData(executor: FakeShellCommandExecutor): OrcaNotificationSettings {
        macData().mkdirs()
        return settings(executor)
    }

    private fun throwing(error: Throwable) = FakeShellCommandExecutor { _: List<String> -> throw error }
}
