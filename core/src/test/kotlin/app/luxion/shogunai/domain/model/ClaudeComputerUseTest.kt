package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClaudeComputerUseTest {

    private val acting = listOf("left_click", "type", "key", "scroll", "open_application", "write_clipboard", "computer_batch")
    private val looking = listOf(
        "screenshot",
        "zoom",
        "cursor_position",
        "wait",
        "switch_display",
        "read_clipboard",
        "list_granted_applications",
        "request_access",
    )
    private val prefixes = listOf("mcp__computer-use__", "mcp__remote-devices__computer_")
    private val matcher = Regex(ClaudeComputerUse.hookMatcher)

    @Test
    fun `acting tools drive the desktop under both prefixes`() {
        prefixes.forEach { prefix ->
            acting.forEach { tool ->
                assertTrue(ClaudeComputerUse.drivesDesktop(prefix + tool), prefix + tool)
                assertTrue(matcher.containsMatchIn(prefix + tool), prefix + tool)
            }
        }
    }

    @Test
    fun `tools that only look don't drive the desktop under either prefix`() {
        prefixes.forEach { prefix ->
            looking.forEach { tool ->
                assertFalse(ClaudeComputerUse.drivesDesktop(prefix + tool), prefix + tool)
                assertFalse(matcher.containsMatchIn(prefix + tool), prefix + tool)
            }
        }
    }

    @Test
    fun `other tools don't drive the desktop`() {
        listOf("Bash", "left_click", "mcp__other__left_click", "mcp__computer-use__left_click_extra").forEach {
            assertFalse(ClaudeComputerUse.drivesDesktop(it), it)
            assertFalse(matcher.containsMatchIn(it), it)
        }
    }
}
