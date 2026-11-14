package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.CustomPool
import app.luxion.shogunai.domain.model.CustomPoolVariable
import app.luxion.shogunai.domain.model.InvalidPoolTemplate
import app.luxion.shogunai.domain.model.MAX_POOL_SLOTS
import app.luxion.shogunai.domain.model.PoolTemplate
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.hasHiddenCharacters
import app.luxion.shogunai.domain.model.isReservedVariableName
import app.luxion.shogunai.domain.model.isValidPoolName
import app.luxion.shogunai.domain.model.isValidVariableName
import app.luxion.shogunai.domain.model.parsePoolTemplate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.nio.file.Path

internal object CustomPoolDeclarations {

    private const val POOLS_FIELD = "pools"
    private const val MAX_FIELD = "max"
    private const val ACQUIRE_FIELD = "acquire"
    private const val RELEASE_FIELD = "release"
    private const val ENV_FIELD = "env"

    fun read(config: JsonObject?, path: Path, envNames: Set<String>): List<CustomPool> {
        val invalid = { detail: String -> WorktreeError.InvalidRepositoryConfig(path.toString(), detail) }
        val pools = when (val field = config?.get(POOLS_FIELD)) {
            null, is JsonNull -> return emptyList()
            is JsonObject -> field
            else -> throw invalid("\"$POOLS_FIELD\" must be an object of named pools")
        }
        val parsed = pools.map { (name, value) -> poolOf(name, value, invalid) }
        val taken = envNames.toMutableSet()
        parsed.forEach { pool ->
            pool.variables.forEach { variable ->
                if (!taken.add(variable.name)) {
                    throw invalid("\"$POOLS_FIELD.${pool.name}.$ENV_FIELD\": \"${variable.name}\" is already declared in \"env\" or another pool")
                }
            }
        }
        return parsed
    }

    private fun poolOf(name: String, value: JsonElement, invalid: (String) -> WorktreeError): CustomPool {
        if (!isValidPoolName(name)) {
            throw invalid("\"$POOLS_FIELD\": \"$name\" isn't a valid pool name; use up to 32 lowercase letters, digits and hyphens")
        }
        val label = "$POOLS_FIELD.$name"
        val options = value as? JsonObject ?: throw invalid("\"$label\" must be an object")
        return CustomPool(
            name = name,
            max = maxOf(options[MAX_FIELD], label, invalid),
            acquire = commandOf(options[ACQUIRE_FIELD], "$label.$ACQUIRE_FIELD", invalid)
                ?: throw invalid("\"$label.$ACQUIRE_FIELD\" is required"),
            release = commandOf(options[RELEASE_FIELD], "$label.$RELEASE_FIELD", invalid),
            variables = variablesOf(options[ENV_FIELD], "$label.$ENV_FIELD", invalid),
        )
    }

    private fun maxOf(value: JsonElement?, label: String, invalid: (String) -> WorktreeError): Int {
        val primitive = value as? JsonPrimitive
        val number = primitive?.takeIf { !it.isString }?.intOrNull
        return number?.takeIf { it in 1..MAX_POOL_SLOTS }
            ?: throw invalid("\"$label.$MAX_FIELD\" must be a whole number from 1 to $MAX_POOL_SLOTS")
    }

    private fun commandOf(value: JsonElement?, label: String, invalid: (String) -> WorktreeError): List<PoolTemplate>? {
        if (value == null || value is JsonNull) return null
        val notCommand = { invalid("\"$label\" must be one command as a non-empty list of strings") }
        val array = value as? JsonArray ?: throw notCommand()
        if (array.isEmpty()) throw notCommand()
        return array.map { element ->
            val primitive = element as? JsonPrimitive
            if (primitive == null || !primitive.isString) throw notCommand()
            templateOf(primitive.content, label, invalid)
        }
    }

    private fun variablesOf(value: JsonElement?, label: String, invalid: (String) -> WorktreeError): List<CustomPoolVariable> {
        val variables = when (value) {
            null, is JsonNull -> return emptyList()
            is JsonObject -> value
            else -> throw invalid("\"$label\" must be an object")
        }
        return variables.map { (name, template) ->
            if (!isValidVariableName(name)) throw invalid("\"$label\": \"$name\" isn't a valid variable name")
            if (isReservedVariableName(name)) throw invalid("\"$label\": \"$name\" is a reserved variable name because it changes how other tools behave")
            val primitive = template as? JsonPrimitive
            if (primitive == null || !primitive.isString) throw invalid("\"$label.$name\" must be a string")
            if (hasHiddenCharacters(primitive.content)) {
                throw invalid("\"$label.$name\" can't contain line breaks, control characters or invisible format characters")
            }
            CustomPoolVariable(name, templateOf(primitive.content, "$label.$name", invalid))
        }
    }

    private fun templateOf(value: String, label: String, invalid: (String) -> WorktreeError): PoolTemplate =
        try {
            parsePoolTemplate(value)
        } catch (error: InvalidPoolTemplate) {
            throw invalid("\"$label\": ${error.message}")
        }
}

internal fun poolsJsonOf(pools: List<CustomPool>): JsonArray? {
    if (pools.isEmpty()) return null
    return JsonArray(
        pools.sortedBy { it.name }.map { pool ->
            JsonObject(
                linkedMapOf(
                    "name" to JsonPrimitive(pool.name),
                    "max" to JsonPrimitive(pool.max),
                    "acquire" to JsonArray(pool.acquireSource.map(::JsonPrimitive)),
                    "release" to (pool.releaseSource?.let { release -> JsonArray(release.map(::JsonPrimitive)) } ?: JsonNull),
                    "env" to JsonArray(
                        pool.variables.sortedBy { it.name }.map { JsonArray(listOf(JsonPrimitive(it.name), JsonPrimitive(it.template.source))) },
                    ),
                ),
            )
        },
    )
}
