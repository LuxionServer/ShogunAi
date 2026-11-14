package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.CustomPoolLeaseStore
import app.luxion.shogunai.domain.model.CustomPool
import app.luxion.shogunai.domain.model.CustomPoolLease
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.parsePoolTemplate
import app.luxion.shogunai.domain.usecase.CustomPoolLedger
import app.luxion.shogunai.domain.usecase.ReleaseCustomPoolUseCase
import app.luxion.shogunai.domain.usecase.WorktreeGitDirectories
import java.time.Duration

val dbPool = CustomPool(
    "db",
    1,
    listOf(parsePoolTemplate("./scripts/db-up.sh"), parsePoolTemplate("\${slot}")),
    listOf(parsePoolTemplate("./scripts/db-down.sh"), parsePoolTemplate("\${slot}")),
)

fun customPoolRelease(
    repositoryPath: String,
    executor: ShellCommandExecutor,
    leases: CustomPoolLeaseStore,
    queue: FakeDeviceWaitQueue = FakeDeviceWaitQueue(),
) = ReleaseCustomPoolUseCase(
    CustomPoolLedger(
        FakeRepositoryProvisioningSource(mapOf(repositoryPath to RepositoryProvisioning(pools = listOf(dbPool)))),
        FakeRepositoryTrust(approvesEverything = true),
        leases,
        FakeEnvironmentStateStore(),
        queue,
        FakeProcessLiveness(),
        { path -> WorktreeGitDirectories("$repositoryPath/.git/worktrees/${path.substringAfterLast('/')}", "$repositoryPath/.git") },
        { repositoryPath },
        executor,
        { true },
        { Duration.ofMinutes(20) },
        sleep = {},
    ),
)

fun dbLease(repositoryPath: String, worktreePath: String, slot: Int = 1) = CustomPoolLease(
    "$repositoryPath/.git",
    repositoryPath,
    "db",
    slot,
    "$repositoryPath/.git/worktrees/${worktreePath.substringAfterLast('/')}",
    worktreePath,
    0L,
    0L,
)
