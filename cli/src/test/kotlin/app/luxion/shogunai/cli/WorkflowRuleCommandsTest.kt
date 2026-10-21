package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.WorkflowRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WorkflowRuleCommandsTest {

    private val placeholders = mapOf("<id>" to "TASK-123", "<ref>" to "epic/name", "<branch>" to "feature/TASK-123")

    private fun commandsIn(text: String): List<String> =
        Regex("`(shogun [^`]+)`").findAll(text).map { it.groupValues[1] }.toList()

    @Test
    fun `every command a rule names parses`() {
        val commands = WorkflowRules.all.flatMap { rule -> commandsIn(rule.text).map { rule.id to it } }

        assertTrue(commands.isNotEmpty())
        commands.forEach { (id, command) ->
            val resolved = placeholders.entries.fold(command) { text, (name, value) -> text.replace(name, value) }
            val parsed = parseArguments(resolved.split(" ").drop(1))
            assertIs<ParsedArguments.Command>(parsed, "$id: `$command` -> $parsed")
        }
    }

    @Test
    fun `the commands named in the rules are the documented ones`() {
        val named = WorkflowRules.all.flatMap { commandsIn(it.text) }.toSet()

        listOf("shogun new <id> --from <ref>", "shogun open <id> --herdr", "shogun clean --yes", "shogun gradle stop", "shogun xcode clean")
            .forEach { assertTrue(it in named, it) }
        assertEquals(named.size, named.toList().size)
    }
}
