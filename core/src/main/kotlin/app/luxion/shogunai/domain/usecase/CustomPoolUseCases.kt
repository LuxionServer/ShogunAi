package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.CustomPoolLeaseStore
import app.luxion.shogunai.domain.io.DeviceWaitQueue
import app.luxion.shogunai.domain.io.EnvironmentStateStore
import app.luxion.shogunai.domain.io.ProcessLiveness
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.io.RepositoryTrust
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.CustomPool
import app.luxion.shogunai.domain.model.CustomPoolCommandKind
import app.luxion.shogunai.domain.model.CustomPoolFailure
import app.luxion.shogunai.domain.model.CustomPoolHolder
import app.luxion.shogunai.domain.model.CustomPoolLease
import app.luxion.shogunai.domain.model.CustomPoolLeases
import app.luxion.shogunai.domain.model.CustomPoolProblem
import app.luxion.shogunai.domain.model.CustomPoolState
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.WaitQueueName
import app.luxion.shogunai.domain.model.WaitTicket
import app.luxion.shogunai.domain.model.approvalSubject
import app.luxion.shogunai.domain.model.customPoolTicketRequest
import app.luxion.shogunai.domain.model.normalizePath
import app.luxion.shogunai.domain.model.releaseRank
import app.luxion.shogunai.domain.model.runSuspendCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

internal data class PoolTarget(
    val repository: String,
    val repositoryPath: String,
    val admin: String,
    val worktree: String,
    val pool: CustomPool,
) {
    val request: String get() = customPoolTicketRequest(repository, pool.name)
}

class CustomPoolLedger(
    private val provisioningSource: RepositoryProvisioningSource,
    private val trust: RepositoryTrust,
    private val leases: CustomPoolLeaseStore,
    private val stateStore: EnvironmentStateStore,
    private val waitQueue: DeviceWaitQueue,
    private val liveness: ProcessLiveness,
    private val directoriesOf: suspend (worktreePath: String) -> WorktreeGitDirectories,
    private val repositoryOf: suspend (worktreePath: String) -> String,
    private val executor: ShellCommandExecutor,
    private val folderExists: (String) -> Boolean,
    private val leaseDuration: () -> Duration,
    private val agentActivity: (worktreePath: String) -> AgentState? = { null },
    private val clock: () -> Instant = Instant::now,
    sleep: suspend (Duration) -> Unit,
) {
    internal val inLine = WaitInLine(stateStore, waitQueue, liveness, clock, sleep)

    internal fun now(): Instant = clock()

    internal fun me() = liveness.current()

    internal fun readLeases(): CustomPoolLeases = leases.read()

    internal fun writeLeases(updated: CustomPoolLeases) = leases.write(updated)

    internal suspend fun <T> locked(block: () -> T): T = withContext(Dispatchers.IO) { stateStore.withLock(block) }

    internal suspend fun adminOf(worktreePath: String): String = normalizePath(directoriesOf(worktreePath).admin)

    internal suspend fun commonOf(worktreePath: String): String = directoriesOf(worktreePath).common

    internal fun isRunning(lease: CustomPoolLease): Boolean = lease.runningBy?.let(liveness::isAlive) == true

    internal fun holder(lease: CustomPoolLease) =
        CustomPoolHolder(lease, lease.expiresAt(leaseDuration()), agentActivity(lease.worktreePath), isRunning(lease))

    internal fun rankOf(lease: CustomPoolLease, now: Instant): Int? =
        releaseRank(stopped = true, busy = isRunning(lease), leaseExpiresAt = lease.expiresAt(leaseDuration()), agent = agentActivity(lease.worktreePath), now = now)

    internal fun queueOf(request: String): List<WaitTicket> =
        inLine.liveQueue(WaitQueueName.CUSTOM_POOL).filter { it.request == request }

    internal fun anyWaiting(): Boolean = waitQueue.read(WaitQueueName.CUSTOM_POOL).isNotEmpty()

    internal fun waitersOf(request: String): List<WaitTicket> =
        waitQueue.read(WaitQueueName.CUSTOM_POOL).filter { it.request == request && liveness.isAlive(it.process) }

    internal fun isApproved(repositoryPath: String, provisioning: RepositoryProvisioning): Boolean =
        trust.isApproved(repositoryPath, provisioning.approvalSubject)

    internal fun provisioningOf(repositoryPath: String): RepositoryProvisioning = provisioningSource.read(repositoryPath)

    internal suspend fun target(worktreePath: String, poolName: String): PoolTarget {
        val directories = directoriesOf(worktreePath)
        val repositoryPath = repositoryOf(worktreePath)
        val provisioning = provisioningSource.read(repositoryPath)
        val pool = provisioning.pools.firstOrNull { it.name == poolName }
            ?: throw CustomPoolFailure(CustomPoolProblem.UnknownPool(poolName, provisioning.pools.map { it.name }))
        if (!isApproved(repositoryPath, provisioning)) throw CustomPoolFailure(CustomPoolProblem.NotApproved(poolName, repositoryPath))
        return PoolTarget(normalizePath(directories.common), repositoryPath, normalizePath(directories.admin), normalizePath(worktreePath), pool)
    }

    internal suspend fun run(pool: CustomPool, kind: CustomPoolCommandKind, lease: CustomPoolLease): CustomPoolProblem.CommandFailed? {
        val worktreeName = poolFolderName(lease.worktreePath)
        val command = when (kind) {
            CustomPoolCommandKind.ACQUIRE -> pool.acquireCommand(lease.slot, worktreeName)
            CustomPoolCommandKind.RELEASE -> pool.releaseCommand(lease.slot, worktreeName) ?: return null
        }
        val directory = if (folderExists(lease.worktreePath)) lease.worktreePath else lease.repositoryPath
        val result = try {
            executor.executeWithin(command, CommandTimeouts.INSTALL, directory)
        } catch (error: CommandExecutionException) {
            return CustomPoolProblem.CommandFailed(pool.name, kind, lease.worktreePath, command, null, error.message)
        }
        return if (result.isSuccess) null else CustomPoolProblem.CommandFailed(pool.name, kind, lease.worktreePath, command, result.exitCode, result.stderr)
    }

    suspend fun variablesFor(worktreePath: String): List<Pair<String, String>> {
        if (leases.read().leases.isEmpty()) return emptyList()
        return runSuspendCatching {
            val admin = adminOf(worktreePath)
            val mine = leases.read().leases.filter { it.heldBy(admin) }
            mine.groupBy { it.repositoryPath }.flatMap { (repositoryPath, held) ->
                val provisioning = provisioningSource.read(repositoryPath)
                if (!isApproved(repositoryPath, provisioning)) return@flatMap emptyList()
                held.sortedBy { it.pool }.flatMap { lease ->
                    provisioning.pools.firstOrNull { it.name == lease.pool }?.variablesFor(lease.slot, poolFolderName(lease.worktreePath)).orEmpty()
                }
            }
        }.getOrDefault(emptyList())
    }
}

internal fun poolFolderName(path: String): String = normalizePath(path).substringAfterLast('/')

class ReadCustomPoolsUseCase(private val ledger: CustomPoolLedger) {

    suspend operator fun invoke(repositoryPath: String): Result<List<CustomPoolState>> = runSuspendCatching {
        val pools = ledger.provisioningOf(repositoryPath).pools
        if (pools.isEmpty()) return@runSuspendCatching emptyList()
        val repository = normalizePath(ledger.commonOf(repositoryPath))
        ledger.locked {
            val current = ledger.readLeases()
            pools.map { pool ->
                CustomPoolState(
                    pool,
                    current.leases.filter { it.belongsTo(repository, pool.name) }.sortedBy { it.slot }.map(ledger::holder),
                    ledger.queueOf(customPoolTicketRequest(repository, pool.name)),
                    current.losses.filter { it.repository == repository && it.pool == pool.name },
                )
            }
        }
    }
}
