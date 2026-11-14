package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.model.RepositoryConfigRules
import app.luxion.shogunai.domain.model.InvalidEnvironmentTemplate
import app.luxion.shogunai.domain.model.hasHiddenCharacters
import app.luxion.shogunai.domain.model.parseEnvironmentVariable
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path

internal fun environmentOf(config: JsonObject, path: Path): EnvironmentDeclaration {
    val invalid = { detail: String -> WorktreeError.InvalidRepositoryConfig(path.toString(), detail) }
    val variables = when (val field = config[ENV_FIELD]) {
        null, is JsonNull -> emptyList()
        is JsonObject -> field.map { (name, value) ->
            val primitive = value as? JsonPrimitive
            if (primitive == null || !primitive.isString) throw invalid("\"$ENV_FIELD.$name\" must be a string")
            try {
                parseEnvironmentVariable(name, primitive.content)
            } catch (error: InvalidEnvironmentTemplate) {
                throw invalid(error.message)
            }
        }
        else -> throw invalid("\"$ENV_FIELD\" must be an object")
    }
    val envFile = when (val field = config[ENV_FILE_FIELD]) {
        null, is JsonNull -> null
        is JsonPrimitive -> {
            val value = field.stringOrNull()
                ?: throw invalid("\"$ENV_FILE_FIELD\" must be a relative path inside the worktree")
            if (hasHiddenCharacters(value)) {
                throw invalid("\"$ENV_FILE_FIELD\" can't contain line breaks, control characters or invisible format characters")
            }
            value.takeIf(RepositoryConfigRules::isInsideWorktree)
                ?: throw invalid("\"$ENV_FILE_FIELD\" must be a relative path inside the worktree")
        }
        else -> throw invalid("\"$ENV_FILE_FIELD\" must be a relative path inside the worktree")
    }
    return EnvironmentDeclaration(variables = variables, envFile = envFile)
}
