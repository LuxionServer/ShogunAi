package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.InvalidEnvironmentTemplate
import app.luxion.shogunai.domain.model.IosSimulatorProvider
import app.luxion.shogunai.domain.model.parseEnvironmentVariable
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.readText

class RepositoryProvisioningFiles : RepositoryProvisioningSource {

    private val json = Json { prettyPrint = true }

    override fun read(repositoryPath: String): RepositoryProvisioning {
        val root = Path.of(repositoryPath)
        val configPath = root.resolve(RepositoryProvisioning.CONFIG_FILE_NAME)
        val config = readConfig(configPath)
        return RepositoryProvisioning(
            copyPatterns = config?.let { copyPatternsOf(it, configPath) }.orEmpty(),
            worktreeIncludePatterns = readWorktreeInclude(root.resolve(RepositoryProvisioning.WORKTREE_INCLUDE_FILE_NAME)),
            providers = config?.let { providersOf(it, configPath) } ?: RepositoryProviders(),
            environment = config?.let { environmentOf(it, configPath) } ?: EnvironmentDeclaration(),
        )
    }

    private fun environmentOf(config: JsonObject, path: Path): EnvironmentDeclaration {
        val invalid = { detail: String -> WorktreeError.InvalidRepositoryConfig(path.toString(), detail) }
        val variables = when (val field = config[ENV_FIELD]) {
            null, is JsonNull -> emptyList()
            is JsonObject -> field.map { (name, value) ->
                val primitive = value as? JsonPrimitive
                if (primitive == null || !primitive.isString) throw invalid("\"$ENV_FIELD.$name\" must be a string")
                try {
                    parseEnvironmentVariable(name, primitive.content)
                } catch (error: InvalidEnvironmentTemplate) {
                    throw invalid(error.message.orEmpty())
                }
            }
            else -> throw invalid("\"$ENV_FIELD\" must be an object")
        }
        val envFile = when (val field = config[ENV_FILE_FIELD]) {
            null, is JsonNull -> null
            is JsonPrimitive -> field.takeIf { it.isString }?.content?.takeIf(::isInsideWorktree)
                ?: throw invalid("\"$ENV_FILE_FIELD\" must be a relative path inside the worktree")
            else -> throw invalid("\"$ENV_FILE_FIELD\" must be a relative path inside the worktree")
        }
        return EnvironmentDeclaration(variables = variables, envFile = envFile)
    }

    private fun isInsideWorktree(relative: String): Boolean {
        if (relative.isBlank()) return false
        val path = runCatching { Path.of(relative) }.getOrNull() ?: return false
        if (path.isAbsolute || relative.startsWith("/") || relative.startsWith("\\")) return false
        val normalized = path.normalize()
        return normalized.toString().isNotEmpty() && !normalized.startsWith("..")
    }

    override fun addCopyPatterns(repositoryPath: String, patterns: List<String>) {
        val configPath = Path.of(repositoryPath).resolve(RepositoryProvisioning.CONFIG_FILE_NAME)
        val config = readConfig(configPath) ?: JsonObject(emptyMap())
        val existing = copyPatternsOf(config, configPath)
        val merged = (existing + patterns).distinct()
        val updated = JsonObject(config + (COPY_FIELD to JsonArray(merged.map(::JsonPrimitive))))
        writeAtomically(configPath, json.encodeToString(JsonElement.serializer(), updated) + "\n")
    }

    private fun readConfig(path: Path): JsonObject? {
        if (!path.exists()) return null
        val text = path.readText()
        if (text.isBlank()) return JsonObject(emptyMap())
        val element = try {
            Json.parseToJsonElement(text)
        } catch (error: SerializationException) {
            throw WorktreeError.InvalidRepositoryConfig(path.toString(), error.message?.lineSequence()?.firstOrNull())
        }
        return element as? JsonObject
            ?: throw WorktreeError.InvalidRepositoryConfig(path.toString(), "expected a JSON object")
    }

    private fun copyPatternsOf(config: JsonObject, path: Path): List<String> {
        val field = config[COPY_FIELD] ?: return emptyList()
        if (field is JsonNull) return emptyList()
        val invalid = { WorktreeError.InvalidRepositoryConfig(path.toString(), "\"$COPY_FIELD\" must be a list of strings") }
        val array = field as? JsonArray ?: throw invalid()
        return array.map { element ->
            val primitive = element as? JsonPrimitive
            if (primitive == null || !primitive.isString) throw invalid()
            primitive.content
        }.filter { it.isNotBlank() }
    }

    private fun providersOf(config: JsonObject, path: Path): RepositoryProviders {
        val field = config[PROVIDERS_FIELD] ?: return RepositoryProviders()
        if (field is JsonNull) return RepositoryProviders()
        val providers = field as? JsonObject
            ?: throw WorktreeError.InvalidRepositoryConfig(path.toString(), "\"$PROVIDERS_FIELD\" must be an object")
        return RepositoryProviders(
            gradle = providers[GRADLE_PROVIDER]?.let { value ->
                if (value !is JsonObject) {
                    throw WorktreeError.InvalidRepositoryConfig(
                        path.toString(),
                        "\"$PROVIDERS_FIELD.$GRADLE_PROVIDER\" must be an object",
                    )
                }
                GradleProvider
            },
            emulator = providers[EMULATOR_PROVIDER]?.let { value ->
                val options = providerOptions(value, EMULATOR_PROVIDER, path)
                EmulatorProvider(preferredAvd = optionalName(options, EMULATOR_PROVIDER, AVD_FIELD, path))
            },
            iosSimulator = providers[IOS_SIMULATOR_PROVIDER]?.let { value ->
                val options = providerOptions(value, IOS_SIMULATOR_PROVIDER, path)
                IosSimulatorProvider(
                    device = optionalName(options, IOS_SIMULATOR_PROVIDER, DEVICE_FIELD, path),
                    runtime = optionalName(options, IOS_SIMULATOR_PROVIDER, RUNTIME_FIELD, path),
                )
            },
        )
    }

    private fun providerOptions(value: JsonElement, provider: String, path: Path): JsonObject =
        value as? JsonObject
            ?: throw WorktreeError.InvalidRepositoryConfig(path.toString(), "\"$PROVIDERS_FIELD.$provider\" must be an object")

    private fun optionalName(options: JsonObject, provider: String, field: String, path: Path): String? {
        val invalid = WorktreeError.InvalidRepositoryConfig(
            path.toString(),
            "\"$PROVIDERS_FIELD.$provider.$field\" must be a non-empty string",
        )
        return when (val value = options[field]) {
            null, is JsonNull -> null
            is JsonPrimitive -> value.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() } ?: throw invalid
            else -> throw invalid
        }
    }

    private fun readWorktreeInclude(path: Path): List<String> {
        if (!path.exists()) return emptyList()
        return path.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
    }

    private fun writeAtomically(path: Path, content: String) {
        val tempFile = Files.createTempFile(path.parent, ".shogunai", ".json.tmp")
        Files.writeString(tempFile, content)
        Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private companion object {
        const val COPY_FIELD = "copy"
        const val PROVIDERS_FIELD = "providers"
        const val GRADLE_PROVIDER = "gradle"
        const val EMULATOR_PROVIDER = "emulator"
        const val AVD_FIELD = "avd"
        const val IOS_SIMULATOR_PROVIDER = "ios-simulator"
        const val DEVICE_FIELD = "device"
        const val RUNTIME_FIELD = "runtime"
        const val ENV_FIELD = "env"
        const val ENV_FILE_FIELD = "envFile"
    }
}
