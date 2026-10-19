package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RepositoryConfigRulesTest {

    @Test
    fun `accepts memory sizes with a unit`() {
        listOf("2g", "1024m", "512K", "4G").forEach { assertNull(RepositoryConfigRules.memorySizeIssue(it), it) }
    }

    @Test
    fun `rejects memory sizes without the expected shape`() {
        listOf("2 GB", "2", "g", "-1g", "2gb", "").forEach {
            assertEquals(RepositoryConfigIssue.InvalidMemorySize, RepositoryConfigRules.memorySizeIssue(it), it)
        }
    }

    @Test
    fun `accepts positive whole minutes`() {
        assertNull(RepositoryConfigRules.minutesIssue("30"))
        assertEquals(30, RepositoryConfigRules.positiveWholeNumber("30"))
    }

    @Test
    fun `rejects zero, negative and non numeric minutes`() {
        listOf("0", "-5", "1.5", "abc", "").forEach {
            assertEquals(RepositoryConfigIssue.InvalidMinutes, RepositoryConfigRules.minutesIssue(it), it)
        }
    }

    @Test
    fun `accepts variable names with letters, digits and underscores`() {
        listOf("PORT", "_PRIVATE", "api_port2").forEach { assertNull(RepositoryConfigRules.variableNameIssue(it), it) }
    }

    @Test
    fun `rejects variable names starting with a digit or with other characters`() {
        listOf("1PORT", "MY-PORT", "", "A B").forEach {
            assertEquals(RepositoryConfigIssue.InvalidVariableName, RepositoryConfigRules.variableNameIssue(it), it)
        }
    }

    @Test
    fun `accepts the documented templates`() {
        listOf("\${port}", "\${port:api}", "\${worktree}", "http://localhost:\${port}/\${worktree}", "cost: $$5").forEach {
            assertNull(RepositoryConfigRules.templateIssue("VAR", it), it)
        }
    }

    @Test
    fun `reports unknown and unclosed placeholders`() {
        assertEquals(RepositoryConfigIssue.UnknownPlaceholder("branch"), RepositoryConfigRules.templateIssue("VAR", "\${branch}"))
        assertEquals(RepositoryConfigIssue.UnknownPlaceholder("port:"), RepositoryConfigRules.templateIssue("VAR", "\${port:}"))
        assertEquals(RepositoryConfigIssue.UnclosedPlaceholder, RepositoryConfigRules.templateIssue("VAR", "\${port"))
    }

    @Test
    fun `accepts a relative env file inside the worktree`() {
        listOf(".env.shogunai", "config/local.env", "./a.env").forEach { assertNull(RepositoryConfigRules.envFileIssue(it), it) }
    }

    @Test
    fun `rejects env files outside the worktree`() {
        listOf("../shared.env", "/etc/app.env", "", "  ", "a/../../b.env").forEach {
            assertEquals(RepositoryConfigIssue.EnvFileOutsideWorktree, RepositoryConfigRules.envFileIssue(it), it)
        }
    }

    @Test
    fun `rejects reserved variable names`() {
        listOf("PATH", "BASH_ENV", "NODE_OPTIONS", "LD_PRELOAD", "DYLD_INSERT_LIBRARIES", "GIT_SSH_COMMAND").forEach {
            assertEquals(RepositoryConfigIssue.ReservedVariableName, RepositoryConfigRules.variableNameIssue(it), it)
        }
    }
}
