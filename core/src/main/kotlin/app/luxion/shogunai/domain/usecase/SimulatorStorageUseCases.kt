package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.AppleSimulators
import app.luxion.shogunai.domain.io.DyldCacheSource
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.DyldCaches
import app.luxion.shogunai.domain.model.IOS_SIMULATOR_KEY
import app.luxion.shogunai.domain.model.IosSimulatorProvider
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.RuntimeDeletion
import app.luxion.shogunai.domain.model.RuntimeRefusal
import app.luxion.shogunai.domain.model.RuntimeUser
import app.luxion.shogunai.domain.model.SimulatorDevice
import app.luxion.shogunai.domain.model.SimulatorRuntimeFailure
import app.luxion.shogunai.domain.model.SimulatorRuntimeImage
import app.luxion.shogunai.domain.model.SimulatorRuntimeProblem
import app.luxion.shogunai.domain.model.SimulatorSettings
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.SimulatorStorage
import app.luxion.shogunai.domain.model.StoredRuntime
import app.luxion.shogunai.domain.model.UnavailableDeletion
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.chooseSimulator
import app.luxion.shogunai.domain.model.runSuspendCatching
import app.luxion.shogunai.domain.model.versionComparator
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class ListSimulatorStorageUseCase(
    private val simulators: AppleSimulators,
    private val dyldCaches: DyldCacheSource,
    private val projects: ProjectRepository,
    private val provisioningSource: RepositoryProvisioningSource,
    private val recordedSimulators: suspend (String, String) -> Result<Set<String>>,
) {
    suspend operator fun invoke(): Result<SimulatorStorage> = runSuspendCatching {
        if (!simulators.isSupported()) return@runSuspendCatching SimulatorStorage(supported = false)
        coroutineScope {
            val caches = async { DyldCaches(dyldCaches.currentBuild(), dyldCaches.folders()) }
            val devices = simulators.devices()
            SimulatorStorage(
                runtimes = runtimesOf(devices),
                unavailableDevices = devices.filterNot { it.isAvailable },
                dyldCaches = caches.await(),
            )
        }
    }

    private suspend fun runtimesOf(devices: List<SimulatorDevice>): List<StoredRuntime> {
        val images = simulators.runtimeImages()
        val defaultIdentifier = chooseSimulator(SimulatorSettings(), SimulatorSettings(), null, simulators.runtimes())?.let { it.runtime.identifier }
        val defaultImage = images.filter { it.runtimeIdentifier == defaultIdentifier }.maxWithOrNull(compareBy(versionComparator) { it.version })
        val worktreeUsers = worktreeUsersOf(devices)
        val projectUsers = projectUsers()
        return images
            .sortedWith(compareBy<SimulatorRuntimeImage> { it.platform }.thenByDescending(versionComparator) { it.version })
            .map { image ->
                val users = worktreeUsers.filter { it.first == image.runtimeIdentifier }.map { it.second } +
                    projectUsers.filter { (names, _) -> names.any { it.equals(image.name, ignoreCase = true) } }.map { it.second }.distinct() +
                    listOfNotNull(RuntimeUser.Default.takeIf { image == defaultImage })
                StoredRuntime(image, users, refusalOf(image, users, devices))
            }
    }

    private suspend fun worktreeUsersOf(devices: List<SimulatorDevice>): List<Pair<String?, RuntimeUser>> {
        val recorded = declaringProjects().flatMap { (project, _) ->
            recordedSimulators(project.config.baseRepositoryPath, IOS_SIMULATOR_KEY).getOrDefault(emptySet())
        }.toSet()
        return devices.filter { it.udid in recorded }.map { it.runtimeIdentifier to RuntimeUser.Worktree(it.name) }
    }

    private fun projectUsers(): List<Pair<List<String>, RuntimeUser>> =
        declaringProjects().map { (project, provider) ->
            listOfNotNull(project.config.simulator.runtime, provider.runtime) to RuntimeUser.Project(project.name)
        }

    private fun declaringProjects(): List<Pair<Project, IosSimulatorProvider>> =
        projects.list().mapNotNull { project ->
            runSuspendCatching { provisioningSource.read(project.config.baseRepositoryPath).providers.iosSimulator }.getOrNull()
                ?.let { project to it }
        }

    private fun refusalOf(image: SimulatorRuntimeImage, users: List<RuntimeUser>, devices: List<SimulatorDevice>): RuntimeRefusal? = when {
        !image.deletable -> RuntimeRefusal.BUNDLED
        devices.any { it.runtimeIdentifier == image.runtimeIdentifier && it.state != SimulatorState.SHUTDOWN } -> RuntimeRefusal.BOOTED
        users.isNotEmpty() -> RuntimeRefusal.IN_USE
        else -> null
    }
}

class DeleteSimulatorRuntimeUseCase(
    private val list: ListSimulatorStorageUseCase,
    private val simulators: AppleSimulators,
) {
    suspend fun find(query: String): Result<StoredRuntime> = runSuspendCatching { match(storage().runtimes, query) }

    suspend operator fun invoke(query: String): Result<RuntimeDeletion> = runSuspendCatching {
        val runtime = match(storage().runtimes, query)
        if (!runtime.canDelete) throw SimulatorRuntimeFailure(SimulatorRuntimeProblem.Refused(runtime))
        simulators.deleteRuntime(runtime.image.identifier)
        RuntimeDeletion(runtime, simulators.devices().filterNot { it.isAvailable })
    }

    private suspend fun storage(): SimulatorStorage {
        val storage = list().getOrThrow()
        if (!storage.supported) throw WorktreeError.IosSimulatorsNotSupported
        return storage
    }

    private fun match(runtimes: List<StoredRuntime>, query: String): StoredRuntime {
        val wanted = query.trim()
        val tiers = listOf<(SimulatorRuntimeImage) -> Boolean>(
            { it.identifier == wanted },
            { it.runtimeIdentifier == wanted },
            { it.name.equals(wanted, ignoreCase = true) },
            { it.version == wanted },
        )
        val matches = tiers.map { tier -> runtimes.filter { tier(it.image) } }.firstOrNull { it.isNotEmpty() }
            ?: throw SimulatorRuntimeFailure(SimulatorRuntimeProblem.NotFound(wanted, runtimes.map { it.image }))
        return matches.singleOrNull()
            ?: throw SimulatorRuntimeFailure(SimulatorRuntimeProblem.Ambiguous(wanted, matches.map { it.image }))
    }
}

class DeleteUnavailableDevicesUseCase(
    private val simulators: AppleSimulators,
) {
    suspend operator fun invoke(): Result<UnavailableDeletion> = runSuspendCatching {
        if (!simulators.isSupported()) throw WorktreeError.IosSimulatorsNotSupported
        val unavailable = simulators.devices().filterNot { it.isAvailable }
        if (unavailable.isNotEmpty()) simulators.deleteUnavailable()
        UnavailableDeletion(unavailable)
    }
}
