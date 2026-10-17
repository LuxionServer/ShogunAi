package app.luxion.shogunai.infrastructure

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HerdrConfigFileTest {

    private val home = Files.createTempDirectory("herdr-home").toFile()

    @AfterTest
    fun cleanUp() {
        home.deleteRecursively()
    }

    private fun configFile(
        environment: Map<String, String> = emptyMap(),
        osName: String = "Mac OS X",
    ) = HerdrConfigFile(environmentVariable = { environment[it] }, userHome = home.path, osName = osName)

    private fun writeConfig(content: String, file: File = File(home, ".config/herdr/config.toml")) {
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    @Test
    fun `without a config file the default folder is used`() {
        assertEquals(File(home, ".herdr/worktrees").path, configFile().worktreesRoot())
    }

    @Test
    fun `the worktrees directory of the config is used and expanded`() {
        writeConfig("[ui]\ndirectory = \"/nope\"\n\n[worktrees]\n# where checkouts go\ndirectory = \"~/code/herdr-worktrees\" # custom\n")

        assertEquals(File(home, "code/herdr-worktrees").path, configFile().worktreesRoot())
    }

    @Test
    fun `a directory in another table is ignored`() {
        writeConfig("[ui]\ndirectory = \"/elsewhere\"\n")

        assertEquals(File(home, ".herdr/worktrees").path, configFile().worktreesRoot())
    }

    @Test
    fun `an absolute literal string is used as is`() {
        writeConfig("[worktrees]\ndirectory = '/srv/worktrees'\n")

        assertEquals("/srv/worktrees", configFile().worktreesRoot())
    }

    @Test
    fun `an unreadable config falls back to the default`() {
        File(home, ".config/herdr/config.toml").mkdirs()

        assertEquals(File(home, ".herdr/worktrees").path, configFile().worktreesRoot())
    }

    @Test
    fun `invalid content falls back to the default`() {
        writeConfig("[worktrees\ndirectory = \n")

        assertEquals(File(home, ".herdr/worktrees").path, configFile().worktreesRoot())
    }

    @Test
    fun `HERDR_CONFIG_PATH wins over the default location`() {
        val custom = File(home, "custom.toml")
        writeConfig("[worktrees]\ndirectory = \"/from/env\"\n", custom)

        assertEquals("/from/env", configFile(mapOf("HERDR_CONFIG_PATH" to custom.path)).worktreesRoot())
    }

    @Test
    fun `XDG_CONFIG_HOME is honoured`() {
        val xdg = File(home, "xdg")
        writeConfig("[worktrees]\ndirectory = \"/from/xdg\"\n", File(xdg, "herdr/config.toml"))

        assertEquals("/from/xdg", configFile(mapOf("XDG_CONFIG_HOME" to xdg.path)).worktreesRoot())
    }

    @Test
    fun `APPDATA is used on Windows`() {
        val appData = File(home, "AppData/Roaming")

        assertEquals(
            File(appData, "herdr/config.toml").path,
            configFile(mapOf("APPDATA" to appData.path), osName = "Windows 11").configPath(),
        )
    }

    @Test
    fun `escaped quotes in a basic string are unescaped`() {
        assertEquals("/a \"b\"", HerdrConfigFile.worktreesDirectory(listOf("[worktrees]", "directory = \"/a \\\"b\\\"\"")))
        assertNull(HerdrConfigFile.worktreesDirectory(listOf("directory = \"/top-level\"")))
    }
}
