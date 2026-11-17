package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.DyldCaches
import app.luxion.shogunai.domain.model.SimulatorDevice
import app.luxion.shogunai.domain.model.SimulatorStorage
import app.luxion.shogunai.domain.model.StoredRuntime
import app.luxion.shogunai.domain.model.dyldRemovalCommand
import app.luxion.shogunai.cli.ShogunCli.Companion.EXIT_OK
import app.luxion.shogunai.engine.ShogunEngine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

enum class SimulatorStorageAction { STORAGE, RUNTIME_DELETE, CLEAN_UNAVAILABLE }

data class SimulatorStorageCommand(
    val action: SimulatorStorageAction,
    val runtime: String = "",
    val confirmed: Boolean = false,
    val json: Boolean = false,
) : CliCommand, JsonOutput {
    override fun withJson(): CliCommand = copy(json = true)
}

internal val SIMULATOR_STORAGE_ACTIONS = setOf("storage", "runtime", "clean-unavailable")

internal fun parseSimulatorStorage(rest: List<String>): SimulatorStorageCommand {
    val action = when (rest.first()) {
        "storage" -> SimulatorStorageAction.STORAGE
        "clean-unavailable" -> SimulatorStorageAction.CLEAN_UNAVAILABLE
        else -> SimulatorStorageAction.RUNTIME_DELETE
    }
    val arguments = if (action == SimulatorStorageAction.RUNTIME_DELETE) runtimeDeleteArguments(rest.drop(1)) else rest.drop(1)
    val name = if (action == SimulatorStorageAction.RUNTIME_DELETE) "simulator runtime delete" else "simulator ${rest.first()}"
    var command = SimulatorStorageCommand(action)
    arguments.forEach { argument ->
        command = when {
            argument in setOf("--yes", "-y") && action != SimulatorStorageAction.STORAGE -> command.copy(confirmed = true)
            argument.startsWith("-") -> throw UsageException("unknown option '$argument' for '$name'")
            action != SimulatorStorageAction.RUNTIME_DELETE -> throw UsageException("'$name' takes no arguments, got '$argument'")
            command.runtime.isNotEmpty() -> throw UsageException("'$name' takes a single runtime, got '${command.runtime}' and '$argument'")
            else -> command.copy(runtime = argument)
        }
    }
    if (action == SimulatorStorageAction.RUNTIME_DELETE && command.runtime.isEmpty()) {
        throw UsageException("'$name' needs the runtime to delete, as shown by 'shogun simulator storage'")
    }
    return command
}

private fun runtimeDeleteArguments(rest: List<String>): List<String> {
    if (rest.firstOrNull() != "delete") throw UsageException("'simulator runtime' needs the action delete")
    return rest.drop(1)
}

internal class SimulatorStorageCommands(
    private val engine: ShogunEngine,
    private val out: (String) -> Unit,
) {

    suspend fun run(command: SimulatorStorageCommand): Int = when (command.action) {
        SimulatorStorageAction.STORAGE -> storage(command)
        SimulatorStorageAction.RUNTIME_DELETE -> deleteRuntime(command, command.runtime)
        SimulatorStorageAction.CLEAN_UNAVAILABLE -> cleanUnavailable(command)
    }

    private suspend fun storage(command: SimulatorStorageCommand): Int {
        val storage = engine.listSimulatorStorage().getOrThrow()
        if (command.json) out(storageJson(storage).compact()) else storageLines(storage).forEach(out)
        return EXIT_OK
    }

    private suspend fun deleteRuntime(command: SimulatorStorageCommand, query: String): Int {
        if (!command.confirmed) {
            val runtime = engine.deleteSimulatorRuntime.find(query).getOrThrow()
            if (command.json) {
                out(buildJsonObject { put("deleted", false); put("runtime", runtimeJson(runtime)) }.compact())
            } else {
                out("Would delete ${runtimeTitle(runtime)}, ${sizeText(runtime.image.sizeBytes)}.")
                runtime.refusalReason?.let { out("It can't be deleted now: $it.") }
                out("Run again with --yes to delete it.")
            }
            return EXIT_OK
        }
        val deletion = engine.deleteSimulatorRuntime(query).getOrThrow()
        if (command.json) {
            out(
                buildJsonObject {
                    put("deleted", true)
                    put("runtime", runtimeJson(deletion.runtime))
                    put("bytesFreed", deletion.bytesFreed)
                    putJsonArray("unavailableDevices") { deletion.unavailableLeft.forEach { add(deviceJson(it)) } }
                }.compact(),
            )
            return EXIT_OK
        }
        out("Deleted ${runtimeTitle(deletion.runtime)}, freed ${ShogunCli.formatMemory(deletion.bytesFreed)}.")
        if (deletion.unavailableLeft.isNotEmpty()) {
            out(
                "${deviceCount(deletion.unavailableLeft.size)} now unavailable (${ShogunCli.formatMemory(deletion.unavailableBytes)}); " +
                    "delete them with 'shogun simulator clean-unavailable --yes'.",
            )
        }
        return EXIT_OK
    }

    private suspend fun cleanUnavailable(command: SimulatorStorageCommand): Int {
        if (!command.confirmed) {
            val storage = engine.listSimulatorStorage().getOrThrow()
            if (!storage.supported) return notSupported(command)
            val devices = storage.unavailableDevices
            if (command.json) {
                out(unavailableJson(deleted = false, devices, storage.unavailableBytes).compact())
            } else if (devices.isEmpty()) {
                out(NO_UNAVAILABLE)
            } else {
                out("${deviceCount(devices.size)} unavailable, ${ShogunCli.formatMemory(storage.unavailableBytes)}:")
                devices.forEach { out("  ${deviceLine(it)}") }
                out("Run again with --yes to delete them.")
            }
            return EXIT_OK
        }
        val deletion = engine.deleteUnavailableSimulators().getOrThrow()
        when {
            command.json -> out(unavailableJson(deleted = true, deletion.devices, deletion.bytesFreed).compact())
            deletion.devices.isEmpty() -> out(NO_UNAVAILABLE)
            else -> out("Deleted ${unavailableCount(deletion.devices.size)}, freed ${ShogunCli.formatMemory(deletion.bytesFreed)}.")
        }
        return EXIT_OK
    }

    private fun notSupported(command: SimulatorStorageCommand): Int {
        if (command.json) out(buildJsonObject { put("supported", false) }.compact()) else out(NOT_SUPPORTED)
        return EXIT_OK
    }

    private companion object {
        const val NO_UNAVAILABLE = "No simulator device is unavailable."
    }
}

internal const val NOT_SUPPORTED = "Simulator storage only applies on macOS."

internal fun storageLines(storage: SimulatorStorage): List<String> {
    if (!storage.supported) return listOf(NOT_SUPPORTED)
    val runtimes = if (storage.runtimes.isEmpty()) {
        listOf("No simulator runtime is installed.")
    } else {
        listOf("Simulator runtimes, ${ShogunCli.formatMemory(storage.runtimeBytes)}:") + storage.runtimes.map { "  ${runtimeLine(it)}" }
    }
    val unavailable = if (storage.unavailableDevices.isEmpty()) {
        "No simulator device is unavailable."
    } else {
        "${deviceCount(storage.unavailableDevices.size)} unavailable, ${ShogunCli.formatMemory(storage.unavailableBytes)}: " +
            "delete them with 'shogun simulator clean-unavailable --yes'"
    }
    return runtimes + unavailable + dyldLines(storage.dyldCaches)
}

private fun runtimeLine(runtime: StoredRuntime): String {
    val lastUsedAt = runtime.image.lastUsedAt
    val lastUsed = if (lastUsedAt == null) "never" else lastUsedAt.toString().take(DATE_LENGTH)
    val reason = runtime.refusalReason
    val state = if (reason != null) "kept: $reason" else "unused: shogun simulator runtime delete ${runtime.image.identifier}"
    return "${runtimeTitle(runtime)}  ${sizeText(runtime.image.sizeBytes)}  last used $lastUsed  $state"
}

private fun dyldLines(caches: DyldCaches): List<String> {
    if (caches.folders.isEmpty()) return emptyList()
    return listOf("Simulator dyld caches, ${ShogunCli.formatMemory(caches.totalBytes)}:") + caches.folders.map { folder ->
        val stale = if (caches.isStale(folder)) "  from another macOS build, remove it yourself: ${dyldRemovalCommand(folder)}" else ""
        "  ${folder.build}  ${sizeText(folder.sizeBytes)}$stale"
    }
}

private fun runtimeTitle(runtime: StoredRuntime): String = "${runtime.image.name} (${runtime.image.build.ifEmpty { "unknown build" }})"

private fun deviceLine(device: SimulatorDevice): String = "${device.name} ${device.udid}  ${sizeText(device.dataBytes)}"

private fun deviceCount(count: Int): String = if (count == 1) "1 simulator device is" else "$count simulator devices are"

private fun unavailableCount(count: Int): String = if (count == 1) "1 unavailable simulator device" else "$count unavailable simulator devices"

private fun sizeText(bytes: Long?): String = if (bytes == null) "size unknown" else ShogunCli.formatMemory(bytes)

private const val DATE_LENGTH = 10

internal fun storageJson(storage: SimulatorStorage): JsonObject = buildJsonObject {
    put("supported", storage.supported)
    putJsonArray("runtimes") { storage.runtimes.forEach { add(runtimeJson(it)) } }
    put("runtimeBytes", storage.runtimeBytes)
    putJsonArray("unavailableDevices") { storage.unavailableDevices.forEach { add(deviceJson(it)) } }
    put("unavailableBytes", storage.unavailableBytes)
    putJsonObject("dyldCaches") {
        put("currentBuild", storage.dyldCaches.currentBuild)
        putJsonArray("folders") {
            storage.dyldCaches.folders.forEach { folder ->
                addJsonObject {
                    put("build", folder.build)
                    put("path", folder.path)
                    put("sizeBytes", folder.sizeBytes)
                    put("stale", storage.dyldCaches.isStale(folder))
                    put("removalCommand", dyldRemovalCommand(folder))
                }
            }
        }
    }
}

private fun runtimeJson(runtime: StoredRuntime): JsonObject = buildJsonObject {
    put("identifier", runtime.image.identifier)
    put("runtimeIdentifier", runtime.image.runtimeIdentifier)
    put("name", runtime.image.name)
    put("version", runtime.image.version)
    put("build", runtime.image.build)
    put("sizeBytes", runtime.image.sizeBytes)
    put("lastUsedAt", runtime.image.lastUsedAt?.toString())
    put("canDelete", runtime.canDelete)
    put("reason", runtime.refusalReason)
    putJsonArray("users") { runtime.users.forEach { add(JsonPrimitive(it.label)) } }
}

private fun deviceJson(device: SimulatorDevice): JsonObject = buildJsonObject {
    put("udid", device.udid)
    put("name", device.name)
    put("runtimeIdentifier", device.runtimeIdentifier)
    put("dataBytes", device.dataBytes)
}

private fun unavailableJson(deleted: Boolean, devices: List<SimulatorDevice>, bytes: Long): JsonObject = buildJsonObject {
    put("deleted", deleted)
    putJsonArray("devices") { devices.forEach { add(deviceJson(it)) } }
    put("bytes", bytes)
}
