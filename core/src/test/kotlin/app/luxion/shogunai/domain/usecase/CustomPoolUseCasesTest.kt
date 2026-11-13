package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeCustomPoolLeaseStore
import app.luxion.shogunai.domain.FakeDeviceWaitQueue
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeProcessLiveness
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeRepositoryTrust
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.CustomPool
import app.luxion.shogunai.domain.model.CustomPoolCommandKind
import app.luxion.shogunai.domain.model.CustomPoolFailure
import app.luxion.shogunai.domain.model.CustomPoolHolderNote
import app.luxion.shogunai.domain.model.CustomPoolLease
import app.luxion.shogunai.domain.model.CustomPoolLeases
import app.luxion.shogunai.domain.model.CustomPoolLoss
import app.luxion.shogunai.domain.model.CustomPoolProblem
import app.luxion.shogunai.domain.model.CustomPoolVariable
import app.luxion.shogunai.domain.model.CustomPoolWait
import app.luxion.shogunai.domain.model.CustomPoolWaitProgress
import app.luxion.shogunai.domain.model.ProcessIdentity
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.WaitQueueName
import app.luxion.shogunai.domain.model.WaitTicket
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.approvalSubject
import app.luxion.shogunai.domain.model.customPoolTicketRequest
import app.luxion.shogunai.domain.model.parsePoolTemplate
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomPoolUseCasesTest {

    private val repo = "/projects/main-repo"
    private val api = "/projects/api"
    private fun common(repository: String) = "$repository/.git"
    private fun worktree(id: String, repository: String = repo) = "/worktrees/${repository.substringAfterLast('/')}/$id"
    private fun admin(id: String, repository: String = repo) = "${common(repository)}/worktrees/$id"

    private fun template(vararg parts: String) = parts.map(::parsePoolTemplate)

    private val db = CustomPool(
        "db",
        1,
        template("./scripts/db-up.sh", "\${slot}"),
        template("./scripts/db-down.sh", "\${slot}"),
        listOf(CustomPoolVariable("DATABASE_URL", parsePoolTemplate("postgres://localhost/app_\${slot}"))),
    )

    private var now: Instant = Instant.parse("2026-01-05T14:00:00Z")
    private val pauses = mutableListOf<Duration>()
    private val onSleep = mutableListOf<() -> Unit>()
    private val provisioning = FakeRepositoryProvisioningSource(
        mapOf(repo to RepositoryProvisioning(pools = listOf(db)), api to RepositoryProvisioning(pools = listOf(db))),
        unreadable = setOf("/projects/broken"),
    )
    private val trust = FakeRepositoryTrust(approvesEverything = true)
    private val leases = FakeCustomPoolLeaseStore()
    private val queue = FakeDeviceWaitQueue()
    private val liveness = FakeProcessLiveness()
    private val agents = mutableMapOf<String, AgentState>()
    private val missingFolders = mutableSetOf<String>()
    private val responses = mutableMapOf<String, CommandResult>()
    private var unstartable: String? = null
    private val executor = FakeShellCommandExecutor { command ->
        if (command.first() == unstartable) throw CommandExecutionException(command, IOException("no such file"))
        responses[command.first()] ?: FakeShellCommandExecutor.success(command)
    }

    private fun ledger(currentTrust: FakeRepositoryTrust = trust) = CustomPoolLedger(
        provisioningSource = provisioning,
        trust = currentTrust,
        leases = leases,
        stateStore = FakeEnvironmentStateStore(),
        waitQueue = queue,
        liveness = liveness,
        directoriesOf = { path ->
            if (path.endsWith("/outside")) error("not a repository")
            val repository = if (path.contains("/api/") || path == api) api else repo
            val id = path.substringAfterLast('/')
            WorktreeGitDirectories(if (path == repository) common(repository) else admin(id, repository), common(repository))
        },
        repositoryOf = { path -> if (path.contains("/api/")) api else repo },
        executor = executor,
        folderExists = { it !in missingFolders },
        leaseDuration = { Duration.ofMinutes(20) },
        agentActivity = { agents[it] },
        clock = { now },
        sleep = { pause ->
            pauses += pause
            now += pause
            onSleep.removeFirstOrNull()?.invoke()
        },
    )

    private val shared = ledger()
    private val acquire = AcquireCustomPoolUseCase(shared)
    private val release = ReleaseCustomPoolUseCase(shared)
    private val read = ReadCustomPoolsUseCase(shared)

    private fun lease(id: String, slot: Int = 1, renewedAgo: Duration = Duration.ofMinutes(25), repository: String = repo, pool: String = "db", runningBy: ProcessIdentity? = null) =
        CustomPoolLease(common(repository), repository, pool, slot, admin(id, repository), worktree(id, repository), (now - renewedAgo).toEpochMilli(), (now - renewedAgo).toEpochMilli(), runningBy)

    private fun holding(vararg held: CustomPoolLease) {
        leases.leases = leases.leases.copy(leases = leases.leases.leases + held)
    }

    private fun waiter(id: String, pool: String = "db", process: ProcessIdentity = ProcessIdentity(3000, 1)) =
        WaitTicket("ticket-$id", admin(id), worktree(id), process, now.toEpochMilli(), customPoolTicketRequest(common(repo), pool)).also {
            queue.tickets[WaitQueueName.CUSTOM_POOL] = queue.tickets[WaitQueueName.CUSTOM_POOL].orEmpty() + it
        }

    private fun withMax(max: Int) = replaceProvisioning(RepositoryProvisioning(pools = listOf(db.copy(max = max))))

    private fun replaceProvisioning(updated: RepositoryProvisioning) = provisioning.replace(repo, updated)

    private fun failureOf(result: Result<*>): CustomPoolProblem = assertIs<CustomPoolFailure>(result.exceptionOrNull()).problem

    @Test
    fun `a free slot is taken and the acquire command runs in the worktree with it`() = runTest {
        withMax(3)
        holding(lease("TASK-1", slot = 1, renewedAgo = Duration.ZERO))

        val acquired = acquire(worktree("TASK-2"), "db").getOrThrow()

        assertEquals(2, acquired.slot)
        assertFalse(acquired.renewed)
        assertNull(acquired.takenFrom)
        assertNull(acquired.waitedSeconds)
        assertEquals(now + Duration.ofMinutes(20), acquired.expiresAt)
        assertEquals(listOf("DATABASE_URL" to "postgres://localhost/app_2"), acquired.variables)
        assertEquals(listOf(listOf("./scripts/db-up.sh", "2")), executor.executedCommands)
        assertEquals<List<String?>>(listOf(worktree("TASK-2")), executor.workingDirectories)
        val recorded = leases.leases.leases.single { it.slot == 2 }
        assertEquals(admin("TASK-2"), recorded.adminDirectory)
        assertNull(recorded.runningBy)
    }

    @Test
    fun `a failing acquire command frees the slot and names the command`() = runTest {
        responses["./scripts/db-up.sh"] = FakeShellCommandExecutor.failure(listOf("./scripts/db-up.sh", "1"), 1, "no database")

        val problem = failureOf(acquire(worktree("TASK-1"), "db"))

        assertEquals(CustomPoolProblem.CommandFailed("db", CustomPoolCommandKind.ACQUIRE, worktree("TASK-1"), listOf("./scripts/db-up.sh", "1"), 1, "no database"), problem)
        assertEquals(CustomPoolLeases(), leases.leases)
    }

    @Test
    fun `an acquire command that can't start fails without an exit code`() = runTest {
        unstartable = "./scripts/db-up.sh"

        val problem = assertIs<CustomPoolProblem.CommandFailed>(failureOf(acquire(worktree("TASK-1"), "db")))

        assertNull(problem.exitCode)
        assertEquals("Could not start the command: ./scripts/db-up.sh 1", problem.errorOutput)
    }

    @Test
    fun `an unknown pool names the declared ones`() = runTest {
        assertEquals(CustomPoolProblem.UnknownPool("cache", listOf("db")), failureOf(acquire(worktree("TASK-1"), "cache")))
    }

    @Test
    fun `an unapproved repository runs nothing`() = runTest {
        val unapproved = FakeRepositoryTrust()

        val acquired = AcquireCustomPoolUseCase(ledger(unapproved))(worktree("TASK-1"), "db")
        val released = ReleaseCustomPoolUseCase(ledger(unapproved))(worktree("TASK-1"), "db")

        assertEquals(CustomPoolProblem.NotApproved("db", repo), failureOf(acquired))
        assertEquals(CustomPoolProblem.NotApproved("db", repo), failureOf(released))
        assertEquals(emptyList(), executor.executedCommands)
    }

    @Test
    fun `the approved form of the repository lets it acquire`() = runTest {
        val approved = FakeRepositoryTrust(mapOf(repo to RepositoryProvisioning(pools = listOf(db)).approvalSubject))

        assertEquals(1, AcquireCustomPoolUseCase(ledger(approved))(worktree("TASK-1"), "db").getOrThrow().slot)
    }

    @Test
    fun `acquiring again keeps the slot, renews the lease and runs acquire again, even with waiters`() = runTest {
        holding(lease("TASK-1", renewedAgo = Duration.ofMinutes(17)), lease("TASK-9", repository = api))
        waiter("TASK-2")

        val acquired = acquire(worktree("TASK-1"), "db").getOrThrow()

        assertTrue(acquired.renewed)
        assertEquals(1, acquired.slot)
        assertEquals(now.toEpochMilli(), leases.leases.leases.first().renewedAt)
        assertEquals(listOf(listOf("./scripts/db-up.sh", "1")), executor.executedCommands)
    }

    @Test
    fun `pools with the same name in two repositories don't share capacity`() = runTest {
        holding(lease("TASK-1", renewedAgo = Duration.ZERO))

        val acquired = acquire(worktree("TASK-9", api), "db").getOrThrow()

        assertEquals(1, acquired.slot)
        assertEquals(2, leases.leases.leases.size)
    }

    @Test
    fun `a full pool fails without changing anything and lists the holders`() = runTest {
        withMax(2)
        agents[worktree("TASK-2")] = AgentState.WORKING
        holding(lease("TASK-1", 1, Duration.ofMinutes(5)), lease("TASK-2", 2, Duration.ofMinutes(25)))
        waiter("TASK-4")
        val before = leases.leases

        val problem = failureOf(acquire(worktree("TASK-3"), "db"))

        val expiry = (now + Duration.ofMinutes(15)).toEpochMilli()
        val working = (now + Duration.ofMinutes(-5)).toEpochMilli()
        assertEquals(
            CustomPoolProblem.Full(
                "db",
                2,
                listOf(CustomPoolHolderNote("main-repo · TASK-1", 1, expiry, false), CustomPoolHolderNote("main-repo · TASK-2", 2, working, true)),
                1,
            ),
            problem,
        )
        assertEquals(before, leases.leases)
        assertEquals(emptyList(), executor.executedCommands)
    }

    @Test
    fun `an expired slot is taken over after running release for its holder`() = runTest {
        holding(lease("TASK-1"))

        val acquired = acquire(worktree("TASK-2"), "db").getOrThrow()

        assertEquals("main-repo · TASK-1", acquired.takenFrom)
        assertEquals(listOf(listOf("./scripts/db-down.sh", "1"), listOf("./scripts/db-up.sh", "1")), executor.executedCommands)
        assertEquals<List<String?>>(listOf(worktree("TASK-1"), worktree("TASK-2")), executor.workingDirectories)
        assertEquals(admin("TASK-2"), leases.leases.leases.single().adminDirectory)
        assertEquals(listOf(CustomPoolLoss(common(repo), "db", 1, admin("TASK-1"), worktree("TASK-1"), now.toEpochMilli(), "main-repo · TASK-2")), leases.leases.losses)
    }

    @Test
    fun `acquiring again clears the worktree's loss`() = runTest {
        val loss = CustomPoolLoss(common(repo), "db", 1, admin("TASK-1"), worktree("TASK-1"), 0L, "main-repo · TASK-2")
        val others = listOf(loss.copy(repository = common(api)), loss.copy(pool = "gpu"), loss.copy(adminDirectory = admin("TASK-3")))
        leases.leases = CustomPoolLeases(losses = listOf(loss) + others)

        acquire(worktree("TASK-1"), "db").getOrThrow()

        assertEquals(others, leases.leases.losses)
    }

    @Test
    fun `release runs in the repository when the holder's folder is gone, and not at all without a release command`() = runTest {
        missingFolders += worktree("TASK-1")
        holding(lease("TASK-1"))

        acquire(worktree("TASK-2"), "db").getOrThrow()
        replaceProvisioning(RepositoryProvisioning(pools = listOf(db.copy(release = null))))
        now += Duration.ofMinutes(30)
        acquire(worktree("TASK-3"), "db").getOrThrow()

        assertEquals<List<String?>>(listOf(repo, worktree("TASK-2"), worktree("TASK-3")), executor.workingDirectories)
        assertEquals("main-repo · TASK-3", leases.leases.leases.single().displayName)
    }

    @Test
    fun `a failing release during a take over keeps the old holder`() = runTest {
        holding(lease("TASK-1"))
        responses["./scripts/db-down.sh"] = FakeShellCommandExecutor.failure(listOf("./scripts/db-down.sh", "1"))
        val before = leases.leases

        val problem = assertIs<CustomPoolProblem.CommandFailed>(failureOf(acquire(worktree("TASK-2"), "db")))

        assertEquals(CustomPoolCommandKind.RELEASE, problem.kind)
        assertEquals(worktree("TASK-1"), problem.worktreePath)
        assertEquals(before, leases.leases)
    }

    @Test
    fun `the take over follows the release order and skips protected slots`() = runTest {
        withMax(4)
        agents[worktree("TASK-1")] = AgentState.WORKING
        agents[worktree("TASK-2")] = AgentState.DONE
        agents[worktree("TASK-4")] = AgentState.BLOCKED
        holding(
            lease("TASK-1", 1),
            lease("TASK-2", 2, Duration.ofMinutes(40)),
            lease("TASK-3", 3, Duration.ofMinutes(25), runningBy = ProcessIdentity(77, 1)),
            lease("TASK-4", 4, Duration.ofMinutes(50)),
        )

        assertEquals("main-repo · TASK-2", acquire(worktree("TASK-5"), "db").getOrThrow().takenFrom)
        liveness.dead += ProcessIdentity(77, 1)
        assertEquals("main-repo · TASK-3", acquire(worktree("TASK-6"), "db").getOrThrow().takenFrom)
        assertEquals("main-repo · TASK-4", acquire(worktree("TASK-7"), "db").getOrThrow().takenFrom)
        assertIs<CustomPoolProblem.Full>(failureOf(acquire(worktree("TASK-8"), "db")))
    }

    @Test
    fun `holders above a lowered max count as in use and are never handed over`() = runTest {
        withMax(2)
        holding(lease("TASK-1", 2, Duration.ZERO), lease("TASK-2", 3))

        assertIs<CustomPoolProblem.Full>(failureOf(acquire(worktree("TASK-3"), "db")))
    }

    @Test
    fun `a waiter takes the slot once the lease expires`() = runTest {
        holding(lease("TASK-1", renewedAgo = Duration.ofMinutes(17)))
        val progress = mutableListOf<CustomPoolWaitProgress>()

        val acquired = acquire(worktree("TASK-2"), "db", CustomPoolWait { progress += it }).getOrThrow()

        assertEquals(180, acquired.waitedSeconds)
        assertEquals("main-repo · TASK-1", acquired.takenFrom)
        assertEquals(Duration.ofSeconds(10), pauses.first())
        val first = progress.first()
        assertEquals(listOf("db", 1, 1, 1), listOf(first.pool, first.position, first.waiting, first.max))
        assertEquals(Instant.parse("2026-01-05T14:03:00Z"), first.nextLeaseExpiry)
        assertEquals(emptyList(), queue.tickets[WaitQueueName.CUSTOM_POOL].orEmpty())
    }

    @Test
    fun `progress names the next lease to expire among holders not working`() = runTest {
        withMax(4)
        agents[worktree("TASK-3")] = AgentState.WORKING
        holding(
            lease("TASK-1", 1, Duration.ofMinutes(10)),
            lease("TASK-2", 2, Duration.ofMinutes(15)),
            lease("TASK-3", 3, Duration.ofMinutes(25)),
            lease("TASK-5", 4, Duration.ofMinutes(5)),
        )
        waiter("TASK-8", pool = "gpu")
        liveness.dead += ProcessIdentity(4000, 1)
        waiter("TASK-9", process = ProcessIdentity(4000, 1))
        val progress = mutableListOf<CustomPoolWaitProgress>()

        acquire(worktree("TASK-4"), "db", CustomPoolWait(Duration.ofMinutes(1)) { progress += it })

        assertEquals(Instant.parse("2026-01-05T14:05:00Z"), progress.first().nextLeaseExpiry)
        assertEquals(1, progress.first().waiting)
    }

    @Test
    fun `a failure other than a full pool ends the wait at once`() = runTest {
        holding(lease("TASK-1", renewedAgo = Duration.ofMinutes(17)))
        responses["./scripts/db-down.sh"] = FakeShellCommandExecutor.failure(listOf("./scripts/db-down.sh", "1"))
        now += Duration.ofMinutes(5)

        assertIs<CustomPoolProblem.CommandFailed>(failureOf(acquire(worktree("TASK-2"), "db", CustomPoolWait())))
        unstartable = null
        responses.clear()
        val broken = AcquireCustomPoolUseCase(
            CustomPoolLedger(
                provisioning, trust, object : app.luxion.shogunai.domain.io.CustomPoolLeaseStore {
                    override fun read() = CustomPoolLeases()
                    override fun write(leases: CustomPoolLeases) = throw IllegalStateException("disk full")
                },
                FakeEnvironmentStateStore(), queue, liveness,
                { WorktreeGitDirectories(admin("TASK-2"), common(repo)) }, { repo }, executor, { true }, { Duration.ofMinutes(20) }, sleep = {},
            ),
        )
        assertIs<IllegalStateException>(broken(worktree("TASK-2"), "db", CustomPoolWait()).exceptionOrNull())
    }

    @Test
    fun `a failed acquire leaves alone a slot that changed hands meanwhile`() = runTest {
        responses["./scripts/db-up.sh"] = FakeShellCommandExecutor.failure(listOf("./scripts/db-up.sh", "1"))
        val other = lease("TASK-7", renewedAgo = Duration.ZERO)
        val stealing = AcquireCustomPoolUseCase(
            CustomPoolLedger(
                provisioning, trust, leases, FakeEnvironmentStateStore(), queue, liveness,
                { path -> WorktreeGitDirectories(admin(path.substringAfterLast('/')), common(repo)) }, { repo },
                FakeShellCommandExecutor { command ->
                    leases.leases = CustomPoolLeases(listOf(other))
                    FakeShellCommandExecutor.failure(command)
                },
                { true }, { Duration.ofMinutes(20) }, sleep = {},
            ),
        )

        assertIs<CustomPoolProblem.CommandFailed>(failureOf(stealing(worktree("TASK-1"), "db")))
        assertEquals(listOf(other), leases.leases.leases)
    }

    @Test
    fun `a free slot needs no wait`() = runTest {
        assertEquals(0L, acquire(worktree("TASK-1"), "db", CustomPoolWait()).getOrThrow().waitedSeconds)
    }

    @Test
    fun `a wait gives up after its bound and holds nothing`() = runTest {
        agents[worktree("TASK-1")] = AgentState.WORKING
        holding(lease("TASK-1"))
        val progress = mutableListOf<CustomPoolWaitProgress>()

        val problem = assertIs<CustomPoolProblem.WaitTimedOut>(failureOf(acquire(worktree("TASK-2"), "db", CustomPoolWait(Duration.ofMinutes(1)) { progress += it })))

        assertEquals(Duration.ofMinutes(1), problem.waited)
        assertEquals("main-repo · TASK-1", problem.blocked.holders.single().holder)
        assertNull(progress.first().nextLeaseExpiry)
        assertEquals(admin("TASK-1"), leases.leases.leases.single().adminDirectory)
    }

    @Test
    fun `an acquisition doesn't jump the queue of its own pool`() = runTest {
        waiter("TASK-2")
        waiter("TASK-3", pool = "gpu")

        val problem = assertIs<CustomPoolProblem.Full>(failureOf(acquire(worktree("TASK-4"), "db")))

        assertEquals(1, problem.waiting)
        assertEquals(emptyList(), leases.leases.leases)
    }

    @Test
    fun `releasing runs release and frees the slot`() = runTest {
        holding(lease("TASK-1", renewedAgo = Duration.ZERO), lease("TASK-2", 2), lease("TASK-1", pool = "gpu"), lease("TASK-1", repository = api))

        val released = release(worktree("TASK-1"), "db").getOrThrow()

        assertEquals(1, released.slot)
        assertEquals(listOf(listOf("./scripts/db-down.sh", "1")), executor.executedCommands)
        assertEquals(3, leases.leases.leases.size)
    }

    @Test
    fun `a failing release keeps the slot`() = runTest {
        holding(lease("TASK-1", renewedAgo = Duration.ZERO))
        responses["./scripts/db-down.sh"] = FakeShellCommandExecutor.failure(listOf("./scripts/db-down.sh", "1"))

        assertIs<CustomPoolProblem.CommandFailed>(failureOf(release(worktree("TASK-1"), "db")))
        assertEquals(1, leases.leases.leases.size)
    }

    @Test
    fun `releasing a pool the worktree doesn't hold says so`() = runTest {
        holding(lease("TASK-2"), lease("TASK-1", pool = "gpu"), lease("TASK-1", repository = api))

        assertNull(release(worktree("TASK-1"), "db").getOrThrow().slot)
        assertEquals(emptyList(), executor.executedCommands)
    }

    @Test
    fun `removing a worktree releases its slots, losses and waits`() = runTest {
        holding(lease("TASK-1", renewedAgo = Duration.ZERO), lease("TASK-2"))
        leases.leases = leases.leases.copy(losses = listOf(CustomPoolLoss(common(repo), "db", 1, admin("TASK-1"), worktree("TASK-1"), 0L, "x")))
        waiter("TASK-1")

        val failures = release.onWorktreeRemoved(worktree("TASK-1"))

        assertEquals(emptyList(), failures)
        assertEquals(listOf(listOf("./scripts/db-down.sh", "1")), executor.executedCommands)
        assertEquals(listOf(admin("TASK-2")), leases.leases.leases.map { it.adminDirectory })
        assertEquals(emptyList(), leases.leases.losses)
        assertEquals(emptyList(), queue.tickets[WaitQueueName.CUSTOM_POOL].orEmpty())
    }

    @Test
    fun `a failing release on removal is reported and the slot freed anyway`() = runTest {
        holding(lease("TASK-1"))
        responses["./scripts/db-down.sh"] = FakeShellCommandExecutor.failure(listOf("./scripts/db-down.sh", "1"))

        val failure = assertIs<CustomPoolFailure>(release.onWorktreeRemoved(worktree("TASK-1")).single())

        assertIs<CustomPoolProblem.CommandFailed>(failure.problem)
        assertEquals(CustomPoolLeases(), leases.leases)
    }

    @Test
    fun `removal frees an unapproved repository's slots without running release`() = runTest {
        holding(lease("TASK-1"))

        val failures = ReleaseCustomPoolUseCase(ledger(FakeRepositoryTrust())).onWorktreeRemoved(worktree("TASK-1"))

        assertEquals(CustomPoolProblem.NotApproved("db", repo), assertIs<CustomPoolFailure>(failures.single()).problem)
        assertEquals(emptyList(), executor.executedCommands)
        assertEquals(CustomPoolLeases(), leases.leases)
    }

    @Test
    fun `removal reports an unreadable configuration and skips pools no longer declared`() = runTest {
        holding(lease("TASK-1").copy(repositoryPath = "/projects/broken"), lease("TASK-1", pool = "gone"))

        val failures = release.onWorktreeRemoved("/worktrees/main-repo/TASK-1")

        assertIs<WorktreeError.InvalidRepositoryConfig>(failures.single())
        assertEquals(emptyList(), executor.executedCommands)
        assertEquals(CustomPoolLeases(), leases.leases)
    }

    @Test
    fun `removal does nothing without leases, losses or waiters, or outside a repository`() = runTest {
        assertEquals(emptyList(), release.onWorktreeRemoved("/somewhere/outside"))
        holding(lease("TASK-1"))
        assertEquals(emptyList(), release.onWorktreeRemoved("/somewhere/outside"))
        leases.leases = CustomPoolLeases()
        waiter("TASK-2")
        assertEquals(emptyList(), release.onWorktreeRemoved(worktree("TASK-2")))
        assertEquals(emptyList(), queue.tickets[WaitQueueName.CUSTOM_POOL].orEmpty())
    }

    @Test
    fun `a holder's variables are rendered with its slot`() = runTest {
        holding(lease("TASK-1", slot = 1), lease("TASK-1", pool = "gone"))

        assertEquals(listOf("DATABASE_URL" to "postgres://localhost/app_1"), shared.variablesFor(worktree("TASK-1")))
        assertEquals(emptyList(), shared.variablesFor(worktree("TASK-2")))
        assertEquals(emptyList(), shared.variablesFor("/somewhere/outside"))
        assertEquals(emptyList(), ledger(FakeRepositoryTrust()).variablesFor(worktree("TASK-1")))
        leases.leases = CustomPoolLeases()
        assertEquals(emptyList(), shared.variablesFor(worktree("TASK-1")))
    }

    @Test
    fun `the pools of a repository list holders, waiters and losses`() = runTest {
        withMax(2)
        agents[worktree("TASK-2")] = AgentState.WORKING
        holding(lease("TASK-2", 2, Duration.ZERO), lease("TASK-1", 1, Duration.ZERO, runningBy = ProcessIdentity(5, 5)), lease("TASK-9", repository = api))
        val loss = CustomPoolLoss(common(repo), "db", 1, admin("TASK-3"), worktree("TASK-3"), 0L, "main-repo · TASK-1")
        leases.leases = leases.leases.copy(losses = listOf(loss, loss.copy(pool = "gone"), loss.copy(repository = common(api))))
        val ticket = waiter("TASK-4")

        val state = read(repo).getOrThrow().single()

        assertEquals(listOf(1, 2), state.holders.map { it.lease.slot })
        assertTrue(state.holders.first().running)
        assertEquals(AgentState.WORKING, state.holders.last().agentState)
        assertEquals(listOf(ticket), state.waiting)
        assertEquals(listOf(loss), state.losses)
        assertEquals(emptyList(), ReadCustomPoolsUseCase(shared)("/projects/none").getOrThrow())
    }
}
