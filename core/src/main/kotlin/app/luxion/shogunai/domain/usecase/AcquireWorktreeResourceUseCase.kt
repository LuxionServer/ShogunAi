package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.AppleSimulators
import app.luxion.shogunai.domain.io.EnvironmentStateStore
import app.luxion.shogunai.domain.io.PortProbe
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.EMULATOR_PORT_KEY
import app.luxion.shogunai.domain.model.FIRST_EMULATOR_PORT
import app.luxion.shogunai.domain.model.IOS_SIMULATOR_KEY
import app.luxion.shogunai.domain.model.LAST_EMULATOR_PORT
import app.luxion.shogunai.domain.model.PoolHolder
import app.luxion.shogunai.domain.model.PoolRelease
import app.luxion.shogunai.domain.model.PoolReleaseReason
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.SimulatorSettings
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.Instant

data class ResourceAcquisition(val acquiredNow: Boolean, val releases: List<PoolRelease>)

data class DeviceStart<T>(val device: T, val acquisition: ResourceAcquisition)

class AcquireWorktreeResourceUseCase(
    private val provisioningSource: RepositoryProvisioningSource,
    private val resolveEnvironment: ResolveWorktreeEnvironmentUseCase,
    private val writeEnvironmentFile: WriteEnvironmentFileUseCase,
    private val stateStore: EnvironmentStateStore,
    private val portProbe: PortProbe,
    private val simulators: AppleSimulators,
    private val readPool: ReadResourcePoolUseCase,
    private val contributions: (SimulatorSettings) -> ProviderContributions,
    private val clock: () -> Instant = Instant::now,
) {
    suspend fun emulator(repositoryPath: String, worktreePath: String): Result<ResourceAcquisition> =
        acquire(ResourceKind.EMULATOR, repositoryPath, worktreePath, SimulatorSettings())

    suspend fun simulator(
        repositoryPath: String,
        worktreePath: String,
        requested: SimulatorSettings = SimulatorSettings(),
    ): Result<ResourceAcquisition> = acquire(ResourceKind.IOS_SIMULATOR, repositoryPath, worktreePath, requested)

    private suspend fun acquire(
        kind: ResourceKind,
        repositoryPath: String,
        worktreePath: String,
        requested: SimulatorSettings,
    ): Result<ResourceAcquisition> = runCatching {
        val directories = resolveEnvironment.directoriesOf(worktreePath)
        val key = keyOf(kind)
        val outcome = withContext(Dispatchers.IO) {
            stateStore.withLock {
                runBlocking { acquireLocked(kind, key, repositoryPath, worktreePath, directories, requested) }
            }
        }
        val victims = outcome.released.mapNotNull { it.worktreePath }
        (listOf(worktreePath).filter { outcome.acquiredNow || outcome.released.isNotEmpty() } + victims).distinct()
            .forEach { refreshEnvironmentFile(repositoryPath, it) }
        ResourceAcquisition(outcome.acquiredNow, outcome.released)
    }

    private suspend fun acquireLocked(
        kind: ResourceKind,
        key: String,
        repositoryPath: String,
        worktreePath: String,
        directories: WorktreeGitDirectories,
        requested: SimulatorSettings,
    ): LockedOutcome {
        var usage = readPool.of(kind, repositoryPath)
        val own = usage.holders.firstOrNull { it.adminDirectory?.let(::normalizePath) == normalizePath(directories.admin) }
        if (own != null && !isStale(kind, own)) {
            touch(directories.admin, key)
            return LockedOutcome(acquiredNow = false, released = emptyList())
        }
        if (own != null) {
            stateStore.release(directories.admin, key)
            usage = usage.copy(holders = usage.holders - own)
        }
        val released = mutableListOf<PoolRelease>()
        val limit = usage.effectiveLimit
        var remaining = usage.holders
        while (limit != null && remaining.size >= limit) {
            val victim = leastRecentlyUsedIdle(remaining) ?: throw WorktreeError.ResourcePoolFull(kind, limit, remaining)
            release(victim, key)
            released += PoolRelease(kind, victim.worktreePath, PoolReleaseReason.MAKE_ROOM, victim.displayName)
            remaining = remaining - victim
        }
        while (true) {
            if (reserve(kind, key, repositoryPath, worktreePath, directories, requested)) break
            val victim = leastRecentlyUsedIdle(remaining) ?: throw WorktreeError.NoEmulatorPort(FIRST_EMULATOR_PORT, LAST_EMULATOR_PORT)
            release(victim, key)
            released += PoolRelease(kind, victim.worktreePath, PoolReleaseReason.MAKE_ROOM, victim.displayName)
            remaining = remaining - victim
        }
        touch(directories.admin, key)
        return LockedOutcome(acquiredNow = true, released = released)
    }

    private suspend fun reserve(
        kind: ResourceKind,
        key: String,
        repositoryPath: String,
        worktreePath: String,
        directories: WorktreeGitDirectories,
        requested: SimulatorSettings,
    ): Boolean = when (kind) {
        ResourceKind.EMULATOR -> {
            val taken = stateStore.reservedPortsOnMachine(directories.common)
            val port = (FIRST_EMULATOR_PORT..LAST_EMULATOR_PORT step 2).firstOrNull { candidate ->
                candidate !in taken && portProbe.isFree(candidate) && portProbe.isFree(candidate + 1)
            }
            if (port != null) {
                stateStore.writePorts(directories.admin, stateStore.readPorts(directories.admin) + (key to port))
            }
            port != null
        }
        ResourceKind.IOS_SIMULATOR -> {
            val providers = provisioningSource.read(repositoryPath).providers
            val request = contributions(requested).forRepository(repositoryPath, providers)
                .flatMap { it.resources }
                .first { it.key == key }
            val udid = request.create(ResourceContext(folderName(repositoryPath), folderName(worktreePath)))
            stateStore.writeResources(directories.admin, stateStore.readResources(directories.admin) + (key to udid))
            true
        }
        ResourceKind.GRADLE_DAEMON -> throw IllegalArgumentException("Gradle daemons aren't acquired")
    }

    private suspend fun release(holder: PoolHolder, key: String) {
        val admin = checkNotNull(holder.adminDirectory)
        if (holder.kind == ResourceKind.IOS_SIMULATOR) {
            val udid = holder.reference
            if (udid != null && simulators.devices().any { it.udid == udid }) simulators.delete(udid)
        }
        stateStore.release(admin, key)
    }

    private suspend fun isStale(kind: ResourceKind, holder: PoolHolder): Boolean =
        kind == ResourceKind.IOS_SIMULATOR && simulators.devices().none { it.udid == holder.reference }

    private fun leastRecentlyUsedIdle(holders: List<PoolHolder>): PoolHolder? =
        holders.filter { it.idle && it.adminDirectory != null }
            .minWithOrNull(compareBy<PoolHolder> { it.lastUsed != null }.thenBy { it.lastUsed })

    private fun touch(admin: String, key: String) {
        stateStore.writeLastUsed(admin, stateStore.readLastUsed(admin) + (key to clock().toEpochMilli()))
    }

    private suspend fun refreshEnvironmentFile(repositoryPath: String, worktreePath: String) {
        runCatching {
            val environment = resolveEnvironment(repositoryPath, worktreePath, reserve = false).getOrThrow()
            writeEnvironmentFile(repositoryPath, worktreePath, environment).getOrThrow()
        }
    }

    private fun keyOf(kind: ResourceKind): String = when (kind) {
        ResourceKind.EMULATOR -> EMULATOR_PORT_KEY
        ResourceKind.IOS_SIMULATOR -> IOS_SIMULATOR_KEY
        ResourceKind.GRADLE_DAEMON -> throw IllegalArgumentException("Gradle daemons aren't acquired")
    }

    private fun folderName(path: String): String = normalizePath(path).substringAfterLast('/')

    private class LockedOutcome(val acquiredNow: Boolean, val released: List<PoolRelease>)
}
