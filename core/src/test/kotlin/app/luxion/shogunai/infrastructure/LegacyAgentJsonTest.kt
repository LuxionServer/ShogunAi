package app.luxion.shogunai.infrastructure

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class LegacyAgentJsonTest {

    private fun projects(text: String) = LegacyAgentJson.projects(Json.parseToJsonElement(text))

    @Test
    fun `anything but a list of projects is left as it is`() {
        assertEquals(Json.parseToJsonElement("""{"a":1}"""), projects("""{"a":1}"""))
    }

    @Test
    fun `projects without a config object are left as they are`() {
        val text = """["text",{"id":"p"},{"id":"q","config":"x"}]"""

        assertEquals(Json.parseToJsonElement(text), projects(text))
    }

    @Test
    fun `agents written before headroom existed keep it on and agents that aren't objects are left alone`() {
        val text = """[{"config":{"agentLaunchConfig":{"useHeadroom":false},"additionalAgents":["x",{"name":"Other"}]}}]"""

        assertEquals(
            Json.parseToJsonElement("""[{"config":{"agentLaunchConfig":{"useHeadroom":false},"additionalAgents":["x",{"name":"Other","useHeadroom":true}]}}]"""),
            projects(text),
        )
    }
}
