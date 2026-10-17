package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorktreeEnvironmentTest {

    @Test
    fun `parses placeholders, shared keys and literal dollars`() {
        val variable = parseEnvironmentVariable("API_URL", "http://localhost:\${port:api}/\${worktree}?p=$$5")

        assertEquals(
            listOf(
                TemplatePart.Literal("http://localhost:"),
                TemplatePart.Port("port:api"),
                TemplatePart.Literal("/"),
                TemplatePart.WorktreeName,
                TemplatePart.Literal("?p=$5"),
            ),
            variable.template,
        )
    }

    @Test
    fun `a bare port placeholder is keyed by the variable name`() {
        assertEquals(listOf(TemplatePart.Port("PORT")), parseEnvironmentVariable("PORT", "\${port}").template)
    }

    @Test
    fun `renders with the worktree name and reserved ports`() {
        val variable = parseEnvironmentVariable("URL", "http://\${worktree}:\${port}")

        assertEquals("http://TASK-123:20001", variable.render("TASK-123", mapOf("URL" to 20001)))
    }

    @Test
    fun `lists port keys once`() {
        val declaration = EnvironmentDeclaration(
            listOf(
                parseEnvironmentVariable("PORT", "\${port}"),
                parseEnvironmentVariable("API_PORT", "\${port:api}"),
                parseEnvironmentVariable("API_URL", "http://localhost:\${port:api}"),
            ),
        )

        assertEquals(listOf("PORT", "port:api"), declaration.portKeys)
        assertEquals("api", portKeyLabel("port:api"))
    }

    @Test
    fun `rejects unknown placeholders, unclosed ones and invalid names`() {
        val unknown = assertFailsWith<InvalidEnvironmentTemplate> { parseEnvironmentVariable("A", "\${branch}") }
        assertTrue(unknown.message.orEmpty().contains("\${branch}"))
        assertFailsWith<InvalidEnvironmentTemplate> { parseEnvironmentVariable("A", "\${port") }
        assertFailsWith<InvalidEnvironmentTemplate> { parseEnvironmentVariable("A", "\${port:}") }
        assertFailsWith<InvalidEnvironmentTemplate> { parseEnvironmentVariable("1PORT", "x") }
        assertFailsWith<InvalidEnvironmentTemplate> { parseEnvironmentVariable("MY-PORT", "x") }
    }
}
