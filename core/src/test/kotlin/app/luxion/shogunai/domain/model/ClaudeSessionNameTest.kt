package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClaudeSessionNameTest {

    @Test
    fun `group worktrees are named after their alias and the group's id`() {
        assertEquals("app-task-123", groupMemberSessionName("app", "TASK-123"))
        assertEquals("api-task-123", groupMemberSessionName("api", "TASK-123"))
    }

    @Test
    fun `a worktree of a single repository is named after the project and its folder`() {
        val worktree = Worktree(path = "/home/dev/projects/main-repo-worktrees/TASK-123/", branch = "feature/TASK-123")

        assertEquals("main-repo-task-123", worktreeSessionName("main-repo", worktree))
    }

    @Test
    fun `a main worktree is named after the project`() {
        val worktree = Worktree(path = "/home/dev/projects/main-repo", branch = "main", isMain = true)

        assertEquals("main-repo", worktreeSessionName("main-repo", worktree))
    }

    @Test
    fun `the multirepo agent is named after the multirepo`() {
        val multirepo = Multirepo(folder = "/home/dev/projects/Product", declaration = MultirepoDeclaration())

        assertEquals("product", multirepoSessionName(multirepo))
    }

    @Test
    fun `characters outside the set become single hyphens`() {
        assertEquals("app-web-task-123-v2", groupMemberSessionName("App_Web", "TASK-123.v2"))
        assertEquals("a-b", sessionName("--A  ", "", "__b__"))
    }

    @Test
    fun `long names are capped without a trailing hyphen`() {
        val name = sessionName("a".repeat(63), "b")

        assertEquals("a".repeat(63), name)
        assertEquals(SESSION_NAME_MAX_LENGTH, sessionName("x".repeat(100)).length)
    }

    @Test
    fun `plain claude commands get the name appended`() {
        assertEquals("claude -n app-task-123", namedClaudeCommand("claude", "app-task-123"))
        assertEquals("claude --model opus -n x", namedClaudeCommand("  claude --model opus ", "x"))
        assertEquals("/opt/tools/claude -n x", namedClaudeCommand("/opt/tools/claude", "x"))
        assertEquals("headroom wrap claude --model opus -n x", namedClaudeCommand("headroom wrap claude --model opus", "x"))
        assertEquals("MODE=fast claude -n x", namedClaudeCommand("MODE=fast claude", "x"))
        assertEquals("env MODE=fast claude -n x", namedClaudeCommand("env MODE=fast claude", "x"))
        assertEquals("MODE=fast headroom wrap claude -n x", namedClaudeCommand("MODE=fast headroom wrap claude", "x"))
    }

    @Test
    fun `other commands are left alone`() {
        assertNull(namedClaudeCommand("codex", "x"))
        assertNull(namedClaudeCommand("claude -n mine", "x"))
        assertNull(namedClaudeCommand("claude --name mine", "x"))
        assertNull(namedClaudeCommand("claude --name=mine", "x"))
        assertNull(namedClaudeCommand("source env.sh && claude", "x"))
        assertNull(namedClaudeCommand("claude; codex", "x"))
        assertNull(namedClaudeCommand("claude | tee log", "x"))
        assertNull(namedClaudeCommand("claude &", "x"))
        assertNull(namedClaudeCommand("claude-wrapper", "x"))
        assertNull(namedClaudeCommand("myclaude", "x"))
        assertNull(namedClaudeCommand("headroom wrap codex", "x"))
        assertNull(namedClaudeCommand("", "x"))
        assertNull(namedClaudeCommand("claude", ""))
    }

    @Test
    fun `the first free name is used`() {
        assertEquals("app-task-123", uniqueSessionName("app-task-123", setOf("api-task-123")))
        assertEquals("app-task-123-2", uniqueSessionName("app-task-123", setOf("app-task-123")))
        assertEquals("app-task-123-3", uniqueSessionName("app-task-123", setOf("app-task-123", "app-task-123-2")))
    }
}
