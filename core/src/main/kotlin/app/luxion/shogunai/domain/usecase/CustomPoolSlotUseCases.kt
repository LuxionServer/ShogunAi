package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.CustomPoolAcquisition
import app.luxion.shogunai.domain.model.CustomPoolCommandKind
import app.luxion.shogunai.domain.model.CustomPoolFailure
import app.luxion.shogunai.domain.model.CustomPoolLease
import app.luxion.shogunai.domain.model.CustomPoolLeases
import app.luxion.shogunai.domain.model.CustomPoolLoss
import app.luxion.shogunai.domain.model.CustomPoolProblem
import app.luxion.shogunai.domain.model.CustomPoolRelease
import app.luxion.shogunai.domain.model.CustomPoolWait
import app.luxion.shogunai.domain.model.CustomPoolWaitProgress
import app.luxion.shogunai.domain.model.WaitQueueName
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.normalizePath
import app.luxion.shogunai.domain.model.runSuspendCatching
import java.time.Instant

private data class SlotClaim(val lease: CustomPoolLease, val previous: CustomPoolLease?, val renewed: Boolean)

class AcquireCustomPoolUseCase(private val ledger: CustomPoolLedger) {

    suspend operator fun invoke(worktreePath: String, poolName: String, wait: CustomPoolWait? = null): Result<CustomPoolAcquisition> =
        runSuspendCatching {
            val target = ledger.target(worktreePath, poolName)
            if (wait == null) return@runSuspendCatching acquire(target, ticketId = null).getOrThrow()
            val waited = ledger.inLine.await(
                queue = WaitQueueName.CUSTOM_POOL,
                adminDirectory = target.admin,
                worktreePath = target.worktree,
                timeout = wait.timeout,
                attempt = { ticketId -> acquire(target, ticketId) },
                blockedBy = { (it as? CustomPoolFailure)?.problem as? CustomPoolProblem.Full },
                onWaiting = { line ->
                    val waiters = ledger.waitersOf(target.request)
                    val nextExpiry = line.blocked.holders.filterNot { it.agentWorking }.minOfOrNull { it.expiresAt }?.let(Instant::ofEpochMilli)
                    val position = waiters.indexOfFirst { normalizePath(it.adminDirectory) == target.admin } + 1
                    wait.onProgress(CustomPoolWaitProgress(target.pool.name, position, waiters.size, target.pool.max, line.blocked.holders, nextExpiry))
                    nextExpiry
                },
                timedOut = { waitedFor, full -> CustomPoolFailure(CustomPoolProblem.WaitTimedOut(waitedFor, full)) },
                request = target.request,
            )
            waited.value.copy(waitedSeconds = waited.waitedSeconds)
        }

    private suspend fun acquire(target: PoolTarget, ticketId: String?): Result<CustomPoolAcquisition> = runSuspendCatching {
        val claim = ledger.locked { claim(target, ticketId) }
        claim.previous?.let { previous ->
            ledger.run(target.pool, CustomPoolCommandKind.RELEASE, previous)?.let { failure ->
                ledger.locked { replace(claim.lease, previous) }
                throw CustomPoolFailure(failure)
            }
        }
        ledger.run(target.pool, CustomPoolCommandKind.ACQUIRE, claim.lease)?.let { failure ->
            ledger.locked { replace(claim.lease, null) }
            throw CustomPoolFailure(failure)
        }
        val finished = ledger.locked { finish(claim) }
        CustomPoolAcquisition(
            pool = target.pool.name,
            slot = finished.slot,
            expiresAt = ledger.holder(finished).expiresAt,
            renewed = claim.renewed,
            takenFrom = claim.previous?.displayName,
            waitedSeconds = null,
            variables = target.pool.variablesFor(finished.slot, poolFolderName(finished.worktreePath)),
        )
    }

    private fun claim(target: PoolTarget, ticketId: String?): SlotClaim {
        val now = ledger.now()
        val current = ledger.readLeases()
        val poolLeases = current.leases.filter { it.belongsTo(target.repository, target.pool.name) }
        val mine = poolLeases.firstOrNull { it.heldBy(target.admin) }
        if (mine != null) {
            val renewed = mine.copy(renewedAt = now.toEpochMilli(), runningBy = ledger.me())
            ledger.writeLeases(current.copy(leases = current.leases.map { if (it == mine) renewed else it }))
            return SlotClaim(renewed, null, renewed = true)
        }
        val queue = ledger.queueOf(target.request)
        val full = {
            CustomPoolFailure(
                CustomPoolProblem.Full(target.pool.name, target.pool.max, poolLeases.sortedBy { it.slot }.map { ledger.holder(it).note }, queue.othersThan(target.admin).size),
            )
        }
        if (queue.othersAheadOf(ticketId, target.admin)) throw full()
        val taken = poolLeases.map { it.slot }.toSet()
        val free = (1..target.pool.max).firstOrNull { it !in taken }.takeIf { poolLeases.size < target.pool.max }
        val previous = if (free == null) takeOver(poolLeases.filter { it.slot <= target.pool.max }, now) ?: throw full() else null
        val slot = free ?: previous!!.slot
        val lease = CustomPoolLease(
            target.repository,
            target.repositoryPath,
            target.pool.name,
            slot,
            target.admin,
            target.worktree,
            now.toEpochMilli(),
            now.toEpochMilli(),
            ledger.me(),
        )
        ledger.writeLeases(current.copy(leases = current.leases.filterNot { it == previous } + lease))
        return SlotClaim(lease, previous, renewed = false)
    }

    private fun takeOver(leases: List<CustomPoolLease>, now: Instant): CustomPoolLease? =
        leases.mapNotNull { lease -> ledger.rankOf(lease, now)?.let { lease to it } }
            .minWithOrNull(compareBy<Pair<CustomPoolLease, Int>> { it.second }.thenBy { it.first.renewedAt })
            ?.first

    private fun replace(claimed: CustomPoolLease, restored: CustomPoolLease?) {
        val current = ledger.readLeases()
        if (claimed !in current.leases) return
        ledger.writeLeases(current.copy(leases = current.leases.filterNot { it == claimed } + listOfNotNull(restored)))
    }

    private fun finish(claim: SlotClaim): CustomPoolLease {
        val now = ledger.now().toEpochMilli()
        val finished = claim.lease.copy(renewedAt = now, runningBy = null)
        val current = ledger.readLeases()
        val previous = claim.previous
        val kept = current.losses.filterNot { it.repository == finished.repository && it.pool == finished.pool && it.heldBy(finished.adminDirectory) }
        val loss = previous?.let {
            CustomPoolLoss(it.repository, it.pool, it.slot, it.adminDirectory, it.worktreePath, now, finished.displayName)
        }
        ledger.writeLeases(CustomPoolLeases(current.leases.map { if (it == claim.lease) finished else it }, kept + listOfNotNull(loss)))
        return finished
    }
}

private fun CustomPoolLoss.heldBy(admin: String): Boolean = normalizePath(adminDirectory) == normalizePath(admin)

class ReleaseCustomPoolUseCase(private val ledger: CustomPoolLedger) {

    suspend operator fun invoke(worktreePath: String, poolName: String): Result<CustomPoolRelease> = runSuspendCatching {
        val target = ledger.target(worktreePath, poolName)
        val mine = ledger.locked { ledger.readLeases().leases.firstOrNull { it.belongsTo(target.repository, poolName) && it.heldBy(target.admin) } }
            ?: return@runSuspendCatching CustomPoolRelease(poolName, null)
        ledger.run(target.pool, CustomPoolCommandKind.RELEASE, mine)?.let { throw CustomPoolFailure(it) }
        ledger.locked {
            val current = ledger.readLeases()
            ledger.writeLeases(current.copy(leases = current.leases.filterNot { it.belongsTo(target.repository, poolName) && it.heldBy(target.admin) }))
        }
        CustomPoolRelease(poolName, mine.slot)
    }

    suspend fun onWorktreeRemoved(worktreePath: String): List<WorktreeError> {
        if (ledger.readLeases() == CustomPoolLeases() && !ledger.anyWaiting()) return emptyList()
        val admin = runSuspendCatching { ledger.adminOf(worktreePath) }.getOrNull() ?: return emptyList()
        val mine = ledger.locked { ledger.readLeases().leases.filter { it.heldBy(admin) } }
        val failures = mine.groupBy { it.repositoryPath }.flatMap { (repositoryPath, held) -> release(repositoryPath, held) }
        ledger.locked {
            val current = ledger.readLeases()
            ledger.writeLeases(CustomPoolLeases(current.leases.filterNot { it.heldBy(admin) }, current.losses.filterNot { it.heldBy(admin) }))
        }
        ledger.inLine.drop(WaitQueueName.CUSTOM_POOL, admin)
        return failures
    }

    private suspend fun release(repositoryPath: String, held: List<CustomPoolLease>): List<WorktreeError> {
        val provisioning = try {
            ledger.provisioningOf(repositoryPath)
        } catch (error: WorktreeError) {
            return listOf(error)
        }
        if (!ledger.isApproved(repositoryPath, provisioning)) {
            return held.map { CustomPoolFailure(CustomPoolProblem.NotApproved(it.pool, repositoryPath)) }
        }
        return held.mapNotNull { lease ->
            provisioning.pools.firstOrNull { it.name == lease.pool }
                ?.let { ledger.run(it, CustomPoolCommandKind.RELEASE, lease) }
                ?.let(::CustomPoolFailure)
        }
    }
}
