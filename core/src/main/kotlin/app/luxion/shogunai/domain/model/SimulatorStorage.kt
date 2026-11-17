package app.luxion.shogunai.domain.model

import java.time.Instant

data class SimulatorRuntimeImage(
    val identifier: String,
    val runtimeIdentifier: String,
    val version: String,
    val build: String,
    val sizeBytes: Long?,
    val lastUsedAt: Instant?,
    val deletable: Boolean,
) {
    val platform: String get() = runtimeIdentifier.substringAfterLast('.').substringBefore('-')
    val name: String get() = "$platform $version"
}

sealed interface RuntimeUser {
    val label: String

    data class Worktree(val simulatorName: String) : RuntimeUser {
        override val label: String get() = simulatorName.removePrefix(SIMULATOR_NAME_PREFIX)
    }

    data class Project(val name: String) : RuntimeUser {
        override val label: String get() = name
    }

    data object Default : RuntimeUser {
        override val label: String get() = "default runtime for new simulators"
    }
}

enum class RuntimeRefusal(val reason: String) {
    BUNDLED("it comes with Xcode"),
    BOOTED("a simulator on it is booted"),
    IN_USE("in use"),
}

data class StoredRuntime(
    val image: SimulatorRuntimeImage,
    val users: List<RuntimeUser> = emptyList(),
    val refusal: RuntimeRefusal? = null,
) {
    val canDelete: Boolean get() = refusal == null

    val refusalReason: String?
        get() = if (refusal == RuntimeRefusal.IN_USE) "used by ${users.joinToString { it.label }}" else refusal?.reason
}

data class DyldCacheFolder(val build: String, val path: String, val sizeBytes: Long?)

data class DyldCaches(val currentBuild: String? = null, val folders: List<DyldCacheFolder> = emptyList()) {
    val stale: List<DyldCacheFolder> get() = folders.filter { isStale(it) }
    val totalBytes: Long get() = folders.sumOf { it.sizeBytes ?: 0L }

    fun isStale(folder: DyldCacheFolder): Boolean = currentBuild != null && folder.build != currentBuild
}

fun dyldRemovalCommand(folder: DyldCacheFolder): String = "sudo rm -rf ${folder.path}"

data class SimulatorStorage(
    val supported: Boolean = true,
    val runtimes: List<StoredRuntime> = emptyList(),
    val unavailableDevices: List<SimulatorDevice> = emptyList(),
    val dyldCaches: DyldCaches = DyldCaches(),
) {
    val runtimeBytes: Long get() = runtimes.sumOf { it.image.sizeBytes ?: 0L }
    val unavailableBytes: Long get() = unavailableDevices.sumOf { it.dataBytes ?: 0L }
}

data class RuntimeDeletion(val runtime: StoredRuntime, val unavailableLeft: List<SimulatorDevice>) {
    val bytesFreed: Long get() = runtime.image.sizeBytes ?: 0L
    val unavailableBytes: Long get() = unavailableLeft.sumOf { it.dataBytes ?: 0L }
}

data class UnavailableDeletion(val devices: List<SimulatorDevice>) {
    val bytesFreed: Long get() = devices.sumOf { it.dataBytes ?: 0L }
}

class SimulatorRuntimeFailure(val problem: SimulatorRuntimeProblem) : WorktreeError(problem.message)

sealed interface SimulatorRuntimeProblem {
    val message: String

    data class NotFound(val query: String, val installed: List<SimulatorRuntimeImage>) : SimulatorRuntimeProblem {
        override val message: String
            get() = "No installed simulator runtime matches \"$query\"; ${installedText(installed)}"
    }

    data class Ambiguous(val query: String, val matches: List<SimulatorRuntimeImage>) : SimulatorRuntimeProblem {
        override val message: String
            get() = "\"$query\" matches several simulator runtimes; name one of: " +
                matches.joinToString { "${it.name} (${it.identifier})" }
    }

    data class Refused(val runtime: StoredRuntime) : SimulatorRuntimeProblem {
        override val message: String
            get() = "${runtime.image.name} can't be deleted: ${runtime.refusalReason}"
    }
}

private fun installedText(installed: List<SimulatorRuntimeImage>): String =
    if (installed.isEmpty()) "no runtime is installed" else "installed: " + installed.joinToString { it.name }
