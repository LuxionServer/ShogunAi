package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ClaudePathsTest {

    @Test
    fun `the sessions directory follows the Claude config directory`() {
        assertEquals("/home/dev/.claude/sessions", claudeSessionsDirectory(null, "/home/dev"))
        assertEquals("/home/dev/.claude/sessions", claudeSessionsDirectory("", "/home/dev"))
        assertEquals("/custom/claude/sessions", claudeSessionsDirectory("/custom/claude/", "/home/dev"))
    }
}
