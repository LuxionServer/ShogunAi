package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path

internal fun copyPatternsOf(config: JsonObject, path: Path): List<String> = patternsOf(config, COPY_FIELD, path)

internal fun linkPatternsOf(config: JsonObject, path: Path): List<String> = patternsOf(config, LINK_FIELD, path)

internal fun postCreateCommandsOf(config: JsonObject, path: Path): List<List<String>> {
    val invalid = { detail: String -> WorktreeError.InvalidRepositoryConfig(path.toString(), detail) }
    val hooks = when (val field = config[HOOKS_FIELD]) {
        null, is JsonNull -> return emptyList()
        is JsonObject -> field
        else -> throw invalid("\"$HOOKS_FIELD\" must be an object")
    }
    val postCreate = when (val field = hooks[POST_CREATE_FIELD]) {
        null, is JsonNull -> return emptyList()
        is JsonArray -> field
        else -> throw invalid("\"$HOOKS_FIELD.$POST_CREATE_FIELD\" must be a list of commands")
    }
    val commandsInvalid = {
        invalid("\"$HOOKS_FIELD.$POST_CREATE_FIELD\" must be a list of commands, each a non-empty list of strings")
    }
    val strings = { array: JsonArray ->
        array.map { element ->
            val primitive = element as? JsonPrimitive
            if (primitive == null || !primitive.isString) throw commandsInvalid()
            primitive.content
        }.also { if (it.isEmpty()) throw commandsInvalid() }
    }
    if (postCreate.isEmpty()) return emptyList()
    if (postCreate.first() is JsonPrimitive) return listOf(strings(postCreate))
    return postCreate.map { element -> strings(element as? JsonArray ?: throw commandsInvalid()) }
}

private fun patternsOf(config: JsonObject, name: String, path: Path): List<String> {
    val field = config[name] ?: return emptyList()
    if (field is JsonNull) return emptyList()
    val invalid = { WorktreeError.InvalidRepositoryConfig(path.toString(), "\"$name\" must be a list of strings") }
    val array = field as? JsonArray ?: throw invalid()
    return array.map { element ->
        val primitive = element as? JsonPrimitive
        if (primitive == null || !primitive.isString) throw invalid()
        primitive.content
    }.filter { it.isNotBlank() }
}
