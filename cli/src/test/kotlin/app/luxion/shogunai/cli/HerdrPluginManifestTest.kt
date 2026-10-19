package app.luxion.shogunai.cli

import java.nio.file.Path
import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertEquals

class HerdrPluginManifestTest {
    private val manifest = Path.of("..", "integrations", "herdr", "herdr-plugin.toml")

    private fun eventTables(): List<Map<String, String>> {
        val tables = mutableListOf<MutableMap<String, String>>()
        for (line in manifest.readLines().map { it.trim() }) {
            if (line == "[[events]]") {
                tables += mutableMapOf()
            } else if (tables.isNotEmpty() && "=" in line) {
                tables.last()[line.substringBefore("=").trim()] = line.substringAfter("=").trim()
            }
        }
        return tables
    }

    @Test
    fun `the manifest subscribes to the events the CLI handles`() {
        val events = eventTables().map { it.getValue("on").trim('"') }

        assertEquals(listOf("worktree.created", "worktree.removed"), events)
    }

    @Test
    fun `every event runs the herdr hook`() {
        val commands = eventTables().map { it.getValue("command") }

        assertEquals(listOf("""["shogun", "hook", "herdr"]""", """["shogun", "hook", "herdr"]"""), commands)
    }
}
