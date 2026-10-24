package app.luxion.shogunai.infrastructure

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object LegacyAgentJson {

    private const val USE_HEADROOM = "useHeadroom"
    private val legacyDefaultAgent = JsonObject(mapOf("agentCommand" to JsonPrimitive("claude"), USE_HEADROOM to JsonPrimitive(true)))

    fun projects(element: JsonElement): JsonElement =
        if (element is JsonArray) JsonArray(element.map(::project)) else element

    fun legacyMultirepoDeclaration(element: JsonElement): JsonElement {
        if (element !is JsonObject) return element
        val agent = element["agent"]?.takeUnless { it is JsonNull }
        return JsonObject(element + ("agent" to (agent?.let(::withHeadroom) ?: legacyDefaultAgent)))
    }

    private fun project(element: JsonElement): JsonElement {
        val config = (element as? JsonObject)?.get("config") as? JsonObject ?: return element
        val defaultAgent = withHeadroom(config["agentLaunchConfig"] ?: JsonObject(emptyMap()))
        val additionalAgents = (config["additionalAgents"] as? JsonArray)?.let { agents -> JsonArray(agents.map(::withHeadroom)) }
        val patched = config + ("agentLaunchConfig" to defaultAgent) + listOfNotNull(additionalAgents?.let { "additionalAgents" to it })
        return JsonObject(element + ("config" to JsonObject(patched)))
    }

    private fun withHeadroom(agent: JsonElement): JsonElement =
        if (agent is JsonObject && USE_HEADROOM !in agent) JsonObject(agent + (USE_HEADROOM to JsonPrimitive(true))) else agent
}
