package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.AgentActivity
import app.luxion.shogunai.domain.model.AgentTool
import app.luxion.shogunai.domain.model.ObservedPort
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.SpanishStrings
import kotlin.test.Test
import kotlin.test.assertEquals

class OrcaDetailsTest {

    @Test
    fun `an agent waiting on a tool shows the tool with its input, then its prompt`() {
        val activity = AgentActivity(AgentTool("Bash", "curl -s https://example.com -o page.html"), prompt = "Download the page")

        assertEquals(
            listOf("Waiting on Bash: curl -s https://example.com -o page.html", "Prompt: Download the page"),
            agentActivityLines(activity, EnglishStrings),
        )
        assertEquals(
            listOf("Esperando a Bash: curl -s https://example.com -o page.html", "Prompt: Download the page"),
            agentActivityLines(activity, SpanishStrings),
        )
    }

    @Test
    fun `a tool without input is named alone`() {
        assertEquals(listOf("Waiting on Bash"), agentActivityLines(AgentActivity(AgentTool("Bash")), EnglishStrings))
    }

    @Test
    fun `a ten-line last message shows its first three lines marked as shortened`() {
        val message = (1..10).joinToString("\n") { "line $it" }

        assertEquals(listOf("Last message: line 1\nline 2\nline 3…"), agentActivityLines(AgentActivity(lastMessage = message), EnglishStrings))
    }

    @Test
    fun `a short text isn't shortened`() {
        assertEquals("one\ntwo\nthree", shortened("one\ntwo\nthree"))
    }

    @Test
    fun `reserved ports Orca sees listening are marked`() {
        val label = portsLabel(mapOf("PORT" to 20001, "port:api" to 20002), setOf(20001, 8765), EnglishStrings::portListening)

        assertEquals("PORT 20001 (listening) · api 20002", label)
    }

    @Test
    fun `an observed port shows its process and address when there are`() {
        assertEquals("8765 · Python · http://127.0.0.1:8765", observedPortLabel(ObservedPort(8765, "Python", "http://127.0.0.1:8765")))
        assertEquals("20001 · java", observedPortLabel(ObservedPort(20001, "java")))
        assertEquals("5580", observedPortLabel(ObservedPort(5580)))
    }
}
