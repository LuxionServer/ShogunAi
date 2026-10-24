package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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

    @Test
    fun `rejects names that change other tools in any case`() {
        listOf(
            "PATH", "BASH_ENV", "NODE_OPTIONS", "LD_PRELOAD", "DYLD_INSERT_LIBRARIES", "GIT_SSH_COMMAND",
            "XDG_CONFIG_HOME", "PAGER", "EDITOR", "LESSOPEN", "SSH_ASKPASS", "HTTPS_PROXY",
            "NODE_TLS_REJECT_UNAUTHORIZED", "npm_config_registry", "PIP_INDEX_URL", "GOFLAGS", "RUSTC_WRAPPER",
            "JAVA_OPTS", "CC", "DOCKER_HOST", "KUBECONFIG", "ANTHROPIC_BASE_URL", "CLAUDE_CONFIG_DIR",
            "https_proxy", "Path",
        ).forEach { name ->
            assertTrue(isReservedVariableName(name), name)
            val error = assertFailsWith<InvalidEnvironmentTemplate>(name) { parseEnvironmentVariable(name, "x") }
            assertEquals(RepositoryConfigIssue.ReservedVariableName, error.issue, name)
            assertTrue(error.message.orEmpty().contains(name), name)
        }
    }

    @Test
    fun `accepts ordinary application names`() {
        listOf("PORT", "DATABASE_URL", "NODE_ENV", "VITE_API_URL", "ANDROID_SERIAL", "API_URL", "NEXT_PUBLIC_URL").forEach { name ->
            assertFalse(isReservedVariableName(name), name)
            parseEnvironmentVariable(name, "x")
        }
    }

    @Test
    fun `rejects templates with line breaks, control or invisible format characters`() {
        listOf("demo\nexport OTHER=1", "a\u001b[31m", "a\rb", "a\u202Eb", "a\u200Bb", "a\u2028b", "a\tb").forEach { value ->
            val error = assertFailsWith<InvalidEnvironmentTemplate>(value) { parseEnvironmentVariable("APP_NAME", value) }
            assertEquals(RepositoryConfigIssue.InvalidVariableValue, error.issue)
            assertTrue(error.message.orEmpty().contains("APP_NAME"))
        }
    }

    @Test
    fun `keeps the declared template text`() {
        assertEquals("http://\${worktree}:$$\${port}", parseEnvironmentVariable("URL", "http://\${worktree}:$$\${port}").source)
    }
}
