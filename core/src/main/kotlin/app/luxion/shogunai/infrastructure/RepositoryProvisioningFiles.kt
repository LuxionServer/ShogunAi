package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.DockerComposeProvider
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.model.RepositoryConfigEdit
import app.luxion.shogunai.domain.model.RepositoryConfigRules
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.InvalidEnvironmentTemplate
import app.luxion.shogunai.domain.model.IosSimulatorProvider
import app.luxion.shogunai.domain.model.MavenProvider
import app.luxion.shogunai.domain.model.isReservedVariableName
import app.luxion.shogunai.domain.model.isValidVariableName
import app.luxion.shogunai.domain.model.parseEnvironmentVariable
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.XcodeProvider
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.security.MessageDigest
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
            linkPatterns = config?.let { linkPatternsOf(it, configPath) }.orEmpty(),
            postCreateCommands = config?.let { postCreateCommandsOf(it, configPath) }.orEmpty(),
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
            is JsonPrimitive -> field.takeIf { it.isString }?.content?.takeIf(RepositoryConfigRules::isInsideWorktree)
                ?: throw invalid("\"$ENV_FILE_FIELD\" must be a relative path inside the worktree")
            else -> throw invalid("\"$ENV_FILE_FIELD\" must be a relative path inside the worktree")
        }
        return EnvironmentDeclaration(variables = variables, envFile = envFile)
    }

    override fun fingerprint(repositoryPath: String): String {
        val path = configPathOf(repositoryPath)
        if (!path.exists()) return ABSENT_FINGERPRINT
        return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
    }

    override fun addPatterns(
        repositoryPath: String,
        copyPatterns: List<String>,
        linkPatterns: List<String>,
        expectedFingerprint: String?,
    ) {
        val configPath = configPathOf(repositoryPath)
        expectedFingerprint?.let { requireUnchanged(repositoryPath, configPath, it) }
        val config = readConfig(configPath) ?: JsonObject(emptyMap())
        val updated = LinkedHashMap(config)
        if (copyPatterns.isNotEmpty()) {
            updated[COPY_FIELD] = JsonArray((copyPatternsOf(config, configPath) + copyPatterns).distinct().map(::JsonPrimitive))
        }
        if (linkPatterns.isNotEmpty()) {
            updated[LINK_FIELD] = JsonArray((linkPatternsOf(config, configPath) + linkPatterns).distinct().map(::JsonPrimitive))
        }
        writeAtomically(configPath, render(JsonObject(updated)))
    }

    override fun preview(repositoryPath: String, edit: RepositoryConfigEdit): String {
        val configPath = configPathOf(repositoryPath)
        return render(applyEdit(readConfig(configPath) ?: JsonObject(emptyMap()), edit, configPath))
    }

    override fun write(repositoryPath: String, edit: RepositoryConfigEdit, expectedFingerprint: String) {
        val configPath = configPathOf(repositoryPath)
        requireUnchanged(repositoryPath, configPath, expectedFingerprint)
        val updated = applyEdit(readConfig(configPath) ?: JsonObject(emptyMap()), edit, configPath)
        if (!configPath.exists() && updated.isEmpty()) return
        writeAtomically(configPath, render(updated))
    }

    private fun configPathOf(repositoryPath: String): Path =
        Path.of(repositoryPath).resolve(RepositoryProvisioning.CONFIG_FILE_NAME)

    private fun requireUnchanged(repositoryPath: String, configPath: Path, expected: String) {
        if (fingerprint(repositoryPath) != expected) throw WorktreeError.RepositoryConfigChanged(configPath.toString())
    }

    private fun render(config: JsonObject): String = json.encodeToString(JsonElement.serializer(), config) + "\n"

    private fun applyEdit(config: JsonObject, edit: RepositoryConfigEdit, path: Path): JsonObject {
        val result = LinkedHashMap(config)
        if (edit.addCopyPatterns.isNotEmpty()) {
            val merged = (copyPatternsOf(config, path) + edit.addCopyPatterns).distinct()
            result[COPY_FIELD] = JsonArray(merged.map(::JsonPrimitive))
        }
        if (edit.addLinkPatterns.isNotEmpty()) {
            val merged = (linkPatternsOf(config, path) + edit.addLinkPatterns).distinct()
            result[LINK_FIELD] = JsonArray(merged.map(::JsonPrimitive))
        }
        val providers = providersObjectOf(config, path)
        val updatedProviders = LinkedHashMap(providers)
        updatedProviders.applyProvider(GRADLE_PROVIDER, edit.providers.gradle?.let { gradleOptions(it) })
        updatedProviders.applyProvider(EMULATOR_PROVIDER, edit.providers.emulator?.let { emulatorOptions(it) })
        updatedProviders.applyProvider(IOS_SIMULATOR_PROVIDER, edit.providers.iosSimulator?.let { simulatorOptions(it) })
        updatedProviders.applyProvider(XCODE_PROVIDER, edit.providers.xcode?.let { xcodeOptions(it) })
        updatedProviders.applyProvider(MAVEN_PROVIDER, edit.providers.maven?.let { mavenOptions(it) })
        updatedProviders.applyProvider(DOCKER_COMPOSE_PROVIDER, edit.providers.dockerCompose?.let { composeOptions(it) })
        result.putOrRemove(PROVIDERS_FIELD, updatedProviders.takeIf { it.isNotEmpty() }?.let(::JsonObject))
        result.putOrRemove(
            ENV_FIELD,
            edit.environment.takeIf { it.isNotEmpty() }
                ?.let { entries -> JsonObject(entries.associate { it.name to JsonPrimitive(it.template) }) },
        )
        result.putOrRemove(ENV_FILE_FIELD, edit.envFile?.let(::JsonPrimitive))
        return JsonObject(result)
    }

    private fun providersObjectOf(config: JsonObject, path: Path): JsonObject =
        when (val field = config[PROVIDERS_FIELD]) {
            null, is JsonNull -> JsonObject(emptyMap())
            is JsonObject -> field
            else -> throw WorktreeError.InvalidRepositoryConfig(path.toString(), "\"$PROVIDERS_FIELD\" must be an object")
        }

    private fun MutableMap<String, JsonElement>.applyProvider(id: String, options: Map<String, JsonElement>?) {
        if (options == null) {
            remove(id)
            return
        }
        val existing = (this[id] as? JsonObject).orEmpty()
        val merged = LinkedHashMap(existing)
        val owned = OPTION_FIELDS.getValue(id)
        owned.forEach { field -> if (field !in options) merged.remove(field) }
        merged.putAll(options)
        this[id] = JsonObject(merged)
    }

    private fun MutableMap<String, JsonElement>.putOrRemove(key: String, value: JsonElement?) {
        if (value == null) remove(key) else this[key] = value
    }

    private fun gradleOptions(gradle: GradleProvider): Map<String, JsonElement> = buildMap {
        gradle.daemonMaxHeap?.let { put(DAEMON_MAX_HEAP_FIELD, JsonPrimitive(it)) }
        gradle.idleTimeoutMinutes?.let { put(IDLE_TIMEOUT_MINUTES_FIELD, JsonPrimitive(it)) }
        gradle.kotlinDaemonMaxHeap?.let { put(KOTLIN_DAEMON_MAX_HEAP_FIELD, JsonPrimitive(it)) }
    }

    private fun emulatorOptions(emulator: EmulatorProvider): Map<String, JsonElement> = buildMap {
        emulator.preferredAvd?.let { put(AVD_FIELD, JsonPrimitive(it)) }
    }

    private fun simulatorOptions(simulator: IosSimulatorProvider): Map<String, JsonElement> = buildMap {
        simulator.device?.let { put(DEVICE_FIELD, JsonPrimitive(it)) }
        simulator.runtime?.let { put(RUNTIME_FIELD, JsonPrimitive(it)) }
    }

    private fun xcodeOptions(xcode: XcodeProvider): Map<String, JsonElement> = buildMap {
        if (xcode.compilationCache) put(COMPILATION_CACHE_FIELD, JsonPrimitive(true))
    }

    private fun mavenOptions(maven: MavenProvider): Map<String, JsonElement> = buildMap {
        maven.mavenMaxHeap?.let { put(MAVEN_MAX_HEAP_FIELD, JsonPrimitive(it)) }
    }

    private fun composeOptions(compose: DockerComposeProvider): Map<String, JsonElement> = buildMap {
        if (compose.portVariables.isNotEmpty()) put(PORT_VARIABLES_FIELD, JsonArray(compose.portVariables.map(::JsonPrimitive)))
    }

    private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())

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

    private fun copyPatternsOf(config: JsonObject, path: Path): List<String> = patternsOf(config, COPY_FIELD, path)

    private fun linkPatternsOf(config: JsonObject, path: Path): List<String> = patternsOf(config, LINK_FIELD, path)

    private fun postCreateCommandsOf(config: JsonObject, path: Path): List<List<String>> {
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

    private fun providersOf(config: JsonObject, path: Path): RepositoryProviders {
        val field = config[PROVIDERS_FIELD] ?: return RepositoryProviders()
        if (field is JsonNull) return RepositoryProviders()
        val providers = field as? JsonObject
            ?: throw WorktreeError.InvalidRepositoryConfig(path.toString(), "\"$PROVIDERS_FIELD\" must be an object")
        return RepositoryProviders(
            gradle = providers[GRADLE_PROVIDER]?.let { value ->
                val options = providerOptions(value, GRADLE_PROVIDER, path)
                GradleProvider(
                    daemonMaxHeap = optionalMemorySize(options, GRADLE_PROVIDER, DAEMON_MAX_HEAP_FIELD, path),
                    idleTimeoutMinutes = optionalPositiveWholeNumber(options, IDLE_TIMEOUT_MINUTES_FIELD, path),
                    kotlinDaemonMaxHeap = optionalMemorySize(options, GRADLE_PROVIDER, KOTLIN_DAEMON_MAX_HEAP_FIELD, path),
                )
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
            xcode = providers[XCODE_PROVIDER]?.let { value ->
                val options = providerOptions(value, XCODE_PROVIDER, path)
                XcodeProvider(compilationCache = optionalBoolean(options, XCODE_PROVIDER, COMPILATION_CACHE_FIELD, path) ?: false)
            },
            maven = providers[MAVEN_PROVIDER]?.let { value ->
                val options = providerOptions(value, MAVEN_PROVIDER, path)
                MavenProvider(mavenMaxHeap = optionalMemorySize(options, MAVEN_PROVIDER, MAVEN_MAX_HEAP_FIELD, path))
            },
            dockerCompose = providers[DOCKER_COMPOSE_PROVIDER]?.let { value ->
                val options = providerOptions(value, DOCKER_COMPOSE_PROVIDER, path)
                DockerComposeProvider(portVariables = portVariablesOf(options, path))
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

    private fun optionalBoolean(options: JsonObject, provider: String, field: String, path: Path): Boolean? {
        val invalid = WorktreeError.InvalidRepositoryConfig(path.toString(), "\"$PROVIDERS_FIELD.$provider.$field\" must be true or false")
        return when (val value = options[field]) {
            null, is JsonNull -> null
            is JsonPrimitive -> value.takeIf { !it.isString }?.booleanOrNull ?: throw invalid
            else -> throw invalid
        }
    }

    private fun optionalMemorySize(options: JsonObject, provider: String, field: String, path: Path): String? {
        val invalid = WorktreeError.InvalidRepositoryConfig(
            path.toString(),
            "\"$PROVIDERS_FIELD.$provider.$field\" must be a memory size such as \"2g\" or \"512m\"",
        )
        return when (val value = options[field]) {
            null, is JsonNull -> null
            is JsonPrimitive -> value.takeIf { it.isString }?.content?.takeIf(RepositoryConfigRules::isMemorySize) ?: throw invalid
            else -> throw invalid
        }
    }

    private fun portVariablesOf(options: JsonObject, path: Path): List<String> {
        val label = "$PROVIDERS_FIELD.$DOCKER_COMPOSE_PROVIDER.$PORT_VARIABLES_FIELD"
        val invalid = { detail: String -> WorktreeError.InvalidRepositoryConfig(path.toString(), detail) }
        val array = when (val value = options[PORT_VARIABLES_FIELD]) {
            null, is JsonNull -> return emptyList()
            is JsonArray -> value
            else -> throw invalid("\"$label\" must be a list of variable names")
        }
        val names = array.map { element ->
            val primitive = element as? JsonPrimitive
            if (primitive == null || !primitive.isString) throw invalid("\"$label\" must be a list of variable names")
            primitive.content
        }
        names.forEach { name ->
            if (!isValidVariableName(name)) throw invalid("\"$label\": \"$name\" isn't a valid variable name")
            if (isReservedVariableName(name)) throw invalid("\"$label\": \"$name\" is a reserved variable name")
        }
        names.groupBy { it }.entries.firstOrNull { it.value.size > 1 }?.let { throw invalid("\"$label\": \"${it.key}\" is listed more than once") }
        return names
    }

    private fun optionalPositiveWholeNumber(options: JsonObject, field: String, path: Path): Int? {
        val invalid = WorktreeError.InvalidRepositoryConfig(
            path.toString(),
            "\"$PROVIDERS_FIELD.$GRADLE_PROVIDER.$field\" must be a positive whole number",
        )
        return when (val value = options[field]) {
            null, is JsonNull -> null
            is JsonPrimitive -> value.takeIf { !it.isString }?.content?.let(RepositoryConfigRules::positiveWholeNumber) ?: throw invalid
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
        const val LINK_FIELD = "link"
        const val HOOKS_FIELD = "hooks"
        const val POST_CREATE_FIELD = "postCreate"
        const val PROVIDERS_FIELD = "providers"
        const val GRADLE_PROVIDER = "gradle"
        const val DAEMON_MAX_HEAP_FIELD = "daemonMaxHeap"
        const val IDLE_TIMEOUT_MINUTES_FIELD = "idleTimeoutMinutes"
        const val KOTLIN_DAEMON_MAX_HEAP_FIELD = "kotlinDaemonMaxHeap"
        const val EMULATOR_PROVIDER = "emulator"
        const val AVD_FIELD = "avd"
        const val IOS_SIMULATOR_PROVIDER = "ios-simulator"
        const val DEVICE_FIELD = "device"
        const val RUNTIME_FIELD = "runtime"
        const val XCODE_PROVIDER = "xcode"
        const val COMPILATION_CACHE_FIELD = "compilationCache"
        const val MAVEN_PROVIDER = "maven"
        const val MAVEN_MAX_HEAP_FIELD = "mavenMaxHeap"
        const val DOCKER_COMPOSE_PROVIDER = "docker-compose"
        const val PORT_VARIABLES_FIELD = "portVariables"
        const val ENV_FIELD = "env"
        const val ENV_FILE_FIELD = "envFile"
        const val ABSENT_FINGERPRINT = "absent"
        val OPTION_FIELDS = mapOf(
            GRADLE_PROVIDER to listOf(DAEMON_MAX_HEAP_FIELD, IDLE_TIMEOUT_MINUTES_FIELD, KOTLIN_DAEMON_MAX_HEAP_FIELD),
            EMULATOR_PROVIDER to listOf(AVD_FIELD),
            IOS_SIMULATOR_PROVIDER to listOf(DEVICE_FIELD, RUNTIME_FIELD),
            XCODE_PROVIDER to listOf(COMPILATION_CACHE_FIELD),
            MAVEN_PROVIDER to listOf(MAVEN_MAX_HEAP_FIELD),
            DOCKER_COMPOSE_PROVIDER to listOf(PORT_VARIABLES_FIELD),
        )
    }
}
