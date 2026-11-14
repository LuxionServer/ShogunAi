package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.DesktopAppProvider
import app.luxion.shogunai.domain.model.DockerComposeProvider
import app.luxion.shogunai.domain.model.FlutterProvider
import app.luxion.shogunai.domain.model.NodeProvider
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.RepositoryConfigRules
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.IosSimulatorProvider
import app.luxion.shogunai.domain.model.MavenProvider
import app.luxion.shogunai.domain.model.isReservedVariableName
import app.luxion.shogunai.domain.model.isValidVariableName
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.XcodeProvider
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.nio.file.Path

internal fun providersOf(config: JsonObject, path: Path): RepositoryProviders {
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
            XcodeProvider(
                compilationCache = optionalBoolean(options, XCODE_PROVIDER, COMPILATION_CACHE_FIELD, path) ?: false,
                cocoaPods = optionalBoolean(options, XCODE_PROVIDER, COCOA_PODS_FIELD, path) ?: false,
            )
        },
        maven = providers[MAVEN_PROVIDER]?.let { value ->
            val options = providerOptions(value, MAVEN_PROVIDER, path)
            MavenProvider(mavenMaxHeap = optionalMemorySize(options, MAVEN_PROVIDER, MAVEN_MAX_HEAP_FIELD, path))
        },
        dockerCompose = providers[DOCKER_COMPOSE_PROVIDER]?.let { value ->
            val options = providerOptions(value, DOCKER_COMPOSE_PROVIDER, path)
            DockerComposeProvider(portVariables = portVariablesOf(options, path))
        },
        desktopApp = providers[DESKTOP_APP_PROVIDER]?.let { value ->
            val options = providerOptions(value, DESKTOP_APP_PROVIDER, path)
            DesktopAppProvider(
                module = gradleModuleOf(options, path),
                isolatedHome = optionalBoolean(options, DESKTOP_APP_PROVIDER, ISOLATED_HOME_FIELD, path) ?: true,
            )
        },
        flutter = providers[FLUTTER_PROVIDER]?.let { value ->
            providerOptions(value, FLUTTER_PROVIDER, path)
            FlutterProvider
        },
        node = providers[NODE_PROVIDER]?.let { value -> providerOptions(value, NODE_PROVIDER, path).let { NodeProvider } },
    )
}

private fun gradleModuleOf(options: JsonObject, path: Path): String {
    val invalid = WorktreeError.InvalidRepositoryConfig(
        path.toString(),
        "\"$PROVIDERS_FIELD.$DESKTOP_APP_PROVIDER.$MODULE_FIELD\" must be a Gradle project path such as \":desktopApp\"",
    )
    val value = options[MODULE_FIELD] as? JsonPrimitive ?: throw invalid
    return value.stringOrNull()?.takeIf(RepositoryConfigRules::isGradleProjectPath) ?: throw invalid
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
        is JsonPrimitive -> value.stringOrNull()?.takeIf { it.isNotBlank() } ?: throw invalid
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
        is JsonPrimitive -> value.stringOrNull()?.takeIf(RepositoryConfigRules::isMemorySize) ?: throw invalid
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
        if (isReservedVariableName(name)) throw invalid("\"$label\": \"$name\" is a reserved variable name because it changes how other tools behave")
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
        is JsonPrimitive -> value.takeIf { !it.isString }?.let { RepositoryConfigRules.positiveWholeNumber(it.content) } ?: throw invalid
        else -> throw invalid
    }
}
