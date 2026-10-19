package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AgentLaunchConfigTest {

    @Test
    fun `default config wraps claude with headroom`() {
        assertEquals("headroom wrap claude", AgentLaunchConfig().resolvedCommand())
    }

    @Test
    fun `disabling headroom returns the plain agent command`() {
        val config = AgentLaunchConfig(agentCommand = "claude", useHeadroom = false)

        assertEquals("claude", config.resolvedCommand())
    }

    @Test
    fun `custom agent command is wrapped with headroom by default`() {
        val config = AgentLaunchConfig(agentCommand = "my-agent-cli")

        assertEquals("headroom wrap my-agent-cli", config.resolvedCommand())
    }

    @Test
    fun `display name is the given name or the capitalized executable`() {
        assertEquals("Claude", AgentLaunchConfig().displayName)
        assertEquals("Codex", AgentLaunchConfig(agentCommand = "/opt/bin/codex --full-auto").displayName)
        assertEquals("Work agent", AgentLaunchConfig(name = "Work agent", agentCommand = "codex").displayName)
    }

    @Test
    fun `a blank name falls back to the executable`() {
        assertEquals("Codex", AgentLaunchConfig(name = " ", agentCommand = "codex").displayName)
    }
}
