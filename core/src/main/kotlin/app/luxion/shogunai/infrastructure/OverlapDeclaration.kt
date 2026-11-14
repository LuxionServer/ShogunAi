package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path

private const val OVERLAP_FIELD = "overlap"
private const val IGNORE_FIELD = "ignore"

internal fun overlapIgnoreOf(config: JsonObject, path: Path): List<String> {
    val invalid = { detail: String -> WorktreeError.InvalidRepositoryConfig(path.toString(), detail) }
    val overlap = when (val field = config[OVERLAP_FIELD]) {
        null, is JsonNull -> return emptyList()
        is JsonObject -> field
        else -> throw invalid("\"$OVERLAP_FIELD\" must be an object")
    }
    val ignore = when (val field = overlap[IGNORE_FIELD]) {
        null, is JsonNull -> return emptyList()
        is JsonArray -> field
        else -> throw invalid("\"$OVERLAP_FIELD.$IGNORE_FIELD\" must be a list of strings")
    }
    return ignore.map { element ->
        val primitive = element as? JsonPrimitive
        if (primitive == null || !primitive.isString) throw invalid("\"$OVERLAP_FIELD.$IGNORE_FIELD\" must be a list of strings")
        primitive.content
    }.filter { it.isNotBlank() }
}
