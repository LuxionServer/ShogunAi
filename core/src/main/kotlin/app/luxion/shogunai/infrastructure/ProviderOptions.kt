package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.DockerComposeProvider
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.IosSimulatorProvider
import app.luxion.shogunai.domain.model.MavenProvider
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.XcodeProvider
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path

internal fun editedProviders(config: JsonObject, providers: RepositoryProviders, path: Path): JsonObject? {
    val updatedProviders = LinkedHashMap(providersObjectOf(config, path))
    updatedProviders.applyProvider(GRADLE_PROVIDER, providers.gradle?.let { gradleOptions(it) })
    updatedProviders.applyProvider(EMULATOR_PROVIDER, providers.emulator?.let { emulatorOptions(it) })
    updatedProviders.applyProvider(IOS_SIMULATOR_PROVIDER, providers.iosSimulator?.let { simulatorOptions(it) })
    updatedProviders.applyProvider(XCODE_PROVIDER, providers.xcode?.let { xcodeOptions(it) })
    updatedProviders.applyProvider(MAVEN_PROVIDER, providers.maven?.let { mavenOptions(it) })
    updatedProviders.applyProvider(DOCKER_COMPOSE_PROVIDER, providers.dockerCompose?.let { composeOptions(it) })
    updatedProviders.applyProvider(FLUTTER_PROVIDER, providers.flutter?.let { emptyMap() })
    updatedProviders.applyProvider(NODE_PROVIDER, providers.node?.let { emptyMap() })
    return updatedProviders.takeIf { it.isNotEmpty() }?.let(::JsonObject)
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
    val owned = PROVIDER_OPTION_FIELDS.getValue(id)
    owned.forEach { field -> if (field !in options) merged.remove(field) }
    merged.putAll(options)
    this[id] = JsonObject(merged)
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
    if (xcode.cocoaPods) put(COCOA_PODS_FIELD, JsonPrimitive(true))
}

private fun mavenOptions(maven: MavenProvider): Map<String, JsonElement> = buildMap {
    maven.mavenMaxHeap?.let { put(MAVEN_MAX_HEAP_FIELD, JsonPrimitive(it)) }
}

private fun composeOptions(compose: DockerComposeProvider): Map<String, JsonElement> = buildMap {
    if (compose.portVariables.isNotEmpty()) put(PORT_VARIABLES_FIELD, JsonArray(compose.portVariables.map(::JsonPrimitive)))
}

private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())
