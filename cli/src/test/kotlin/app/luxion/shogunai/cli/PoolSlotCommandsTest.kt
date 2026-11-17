package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeCustomPoolLeaseStore
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeDeviceWaitQueue
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeHerdrConfigSource
import app.luxion.shogunai.domain.FakeKmpTestWiring
import app.luxion.shogunai.domain.FakeMachineCapacity
import app.luxion.shogunai.domain.FakeOrcaClient
import app.luxion.shogunai.domain.FakePhysicalDeviceInventoryStore
import app.luxion.shogunai.domain.FakePhysicalDeviceLeaseStore
import app.luxion.shogunai.domain.FakePhysicalDevices
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeProcessLiveness
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeRepositoryTrust
import app.luxion.shogunai.domain.FakeResourceLimitsRepository
import app.luxion.shogunai.domain.ScriptedShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.model.CustomPool
import app.luxion.shogunai.domain.model.CustomPoolLease
import app.luxion.shogunai.domain.model.CustomPoolLeases
import app.luxion.shogunai.domain.model.CustomPoolLoss
import app.luxion.shogunai.domain.model.CustomPoolVariable
import app.luxion.shogunai.domain.model.ProcessIdentity
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.model.WaitQueueName
import app.luxion.shogunai.domain.model.WaitTicket
import app.luxion.shogunai.domain.model.customPoolTicketRequest
import app.luxion.shogunai.domain.model.parsePoolTemplate
import app.luxion.shogunai.domain.model.toLocalClockTime
import app.luxion.shogunai.engine.EngineOptions
import app.luxion.shogunai.engine.ShogunEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PoolSlotCommandsTest {

    private val base = "/home/dev/projects/main-repo"
    private val common = "$base/.git"
    private val task1 = "/home/dev/projects/worktrees/TASK-1"
    private val task2 = "/home/dev/projects/worktrees/TASK-2"
    private val admin1 = "$common/worktrees/TASK-1"
    private val admin2 = "$common/worktrees/TASK-2"

    private val porcelain = """
        worktree $base
        HEAD 1111111111111111111111111111111111111111
        branch refs/heads/main

        worktree $task1
        HEAD 2222222222222222222222222222222222222222
        branch refs/heads/feature/TASK-1

        worktree $task2
        HEAD 3333333333333333333333333333333333333333
        branch refs/heads/feature/TASK-2
    """.trimIndent()

    private val db = CustomPool(
        "db",
        1,
        listOf(parsePoolTemplate("./scripts/db-up.sh"), parsePoolTemplate("\${slot}")),
        listOf(parsePoolTemplate("./scripts/db-down.sh"), parsePoolTemplate("\${slot}")),
        listOf(CustomPoolVariable("DATABASE_URL", parsePoolTemplate("postgres://localhost/app_\${slot}"))),
    )

    private val out = StringBuilder()
    private val err = StringBuilder()
    private var now: Instant = Instant.parse("2026-01-05T14:00:00Z")
    private val queue = FakeDeviceWaitQueue()
    private val leases = FakeCustomPoolLeaseStore()
    private val ran = mutableListOf<Pair<List<String>, String?>>()
    private val failing = mutableSetOf<String>()
    private var trust = FakeRepositoryTrust(approvesEverything = true)
    private var provisioning = FakeRepositoryProvisioningSource(mapOf(base to RepositoryProvisioning(pools = listOf(db))))

    private val executor = object : ScriptedShellCommandExecutor() {
        override suspend fun respond(command: List<String>, workingDirectory: String?): CommandResult {
            if (command.first().startsWith("./scripts/")) {
                ran += command to workingDirectory
                return if (command.first() in failing) CommandResult(command, 3, "", "database is down") else CommandResult(command, 0, "", "")
            }
            if (workingDirectory?.startsWith("/home/dev/projects") != true) return CommandResult(command, 128, "", "not a git repository")
            val worktree = listOf(task1, task2).firstOrNull { workingDirectory.startsWith(it) } ?: base
            val admin = when (worktree) {
                task1 -> admin1
                task2 -> admin2
                else -> common
            }
            val stdout = when {
                command.take(3) == listOf("git", "worktree", "list") -> porcelain
                "--show-toplevel" in command -> "$worktree\n"
                "--git-dir" in command -> "$admin\n$common\n"
                "--git-common-dir" in command -> "$common\n"
                else -> ""
            }
            return CommandResult(command, 0, stdout, "")
        }
    }

    private fun cli(workingDirectory: String = task1) = ShogunCli(
        engine = ShogunEngine(
            cliEnginePorts(
                executor = executor,
                fileManager = FakeFileManager(existing = listOf(base, task1, task2), directories = setOf(base, task1, task2)),
                projectRepository = FakeProjectRepository(emptyList()),
                provisioningSource = provisioning,
                gradleDaemons = FakeGradleDaemonSource(),
                gradleUserHome = "/home/dev/.gradle",
                environmentStateStore = FakeEnvironmentStateStore(),
                portProbe = FakePortProbe(),
                androidSdk = FakeAndroidSdk(),
                appleSimulators = FakeAppleSimulators(),
                kmpTestWiring = FakeKmpTestWiring(),
                resourceLimits = FakeResourceLimitsRepository(ResourceSettings(leaseMinutes = 20)),
                machineCapacity = FakeMachineCapacity(),
                derivedData = FakeDerivedDataSource(),
                herdrConfig = FakeHerdrConfigSource(),
                orca = FakeOrcaClient(installed = false),
                workingDirectory = workingDirectory,
                environment = hostEnvironment(),
                trust = trust,
                deviceWaitQueue = queue,
                processLiveness = FakeProcessLiveness(),
                physicalDevices = listOf(FakePhysicalDevices()),
                deviceInventory = FakePhysicalDeviceInventoryStore(),
                deviceLeases = FakePhysicalDeviceLeaseStore(),
                poolLeases = leases,
            ),
            EngineOptions(clock = { now }, pauseWhileWaiting = { pause -> now += pause }),
        ),
        workingDirectory = workingDirectory,
        out = out,
        err = err,
        canonicalPath = { it.trimEnd('/') },
        environment = hostEnvironment(),
    )

    private fun run(vararg args: String, workingDirectory: String = task1): Int = cli(workingDirectory).run(args.toList())

    private fun json(): JsonObject = Json.parseToJsonElement(out.toString()).jsonObject

    private fun holding(admin: String, worktree: String, slot: Int = 1, renewedAgo: Duration = Duration.ofMinutes(1)) {
        val at = (now - renewedAgo).toEpochMilli()
        leases.leases = leases.leases.copy(leases = leases.leases.leases + CustomPoolLease(common, base, "db", slot, admin, worktree, at, at))
    }

    private fun clock(instant: Instant) = instant.toLocalClockTime()

    @Test
    fun `acquire runs the command in the worktree and prints the slot and the lines to export`() {
        assertEquals(0, run("pool", "acquire", "db"), err.toString())

        assertEquals<List<Pair<List<String>, String?>>>(listOf(listOf("./scripts/db-up.sh", "1") to task1), ran)
        assertEquals(
            "acquired slot 1 of pool db, lease until ${clock(now + Duration.ofMinutes(20))}\nexport DATABASE_URL='postgres://localhost/app_1'\n",
            out.toString(),
        )
        assertEquals("", err.toString())
    }

    @Test
    fun `acquiring again renews and the json reports the slot`() {
        holding(admin1, task1)

        assertEquals(0, run("pool", "acquire", "db", "--json"), err.toString())

        val result = json()
        assertEquals("db", result.getValue("pool").jsonPrimitive.content)
        assertEquals(1, result.getValue("slot").jsonPrimitive.int)
        assertEquals("true", result.getValue("renewed").jsonPrimitive.content)
        assertEquals(JsonNull, result["waited"])
        assertEquals(JsonNull, result["takenFrom"])
        assertEquals("postgres://localhost/app_1", result.getValue("variables").jsonObject.getValue("DATABASE_URL").jsonPrimitive.content)
        assertEquals((now + Duration.ofMinutes(20)).toString(), result.getValue("expiresAt").jsonPrimitive.content)
        out.clear()
        assertEquals(0, run("pool", "acquire", "db"))
        assertTrue(out.startsWith("renewed slot 1 of pool db"), out.toString())
    }

    @Test
    fun `waiting with a free slot says nothing about the wait`() {
        assertEquals(0, run("pool", "acquire", "db", "--wait"), err.toString())

        assertEquals("", err.toString())
        assertTrue(out.startsWith("acquired slot 1 of pool db"), out.toString())
    }

    @Test
    fun `status still lists the worktrees when the repository's configuration can't be read`() {
        provisioning = FakeRepositoryProvisioningSource(unreadable = setOf(base))

        assertEquals(0, run("status", "--json", workingDirectory = base), err.toString())

        assertEquals(3, json().getValue("worktrees").jsonArray.size)
    }

    @Test
    fun `a full pool fails naming the holder and pointing to wait`() {
        holding(admin2, task2)

        assertEquals(1, run("pool", "acquire", "db"))

        assertTrue("main-repo · TASK-2 holds slot 1, leased until ${clock(now + Duration.ofMinutes(19))}" in err && "--wait" in err, err.toString())
        assertEquals("", out.toString())
    }

    @Test
    fun `waiting prints progress on standard error and takes the slot over once its lease expires`() {
        holding(admin2, task2, renewedAgo = Duration.ofMinutes(19).plusSeconds(50))

        assertEquals(0, run("pool", "acquire", "db", "--wait", "--json"), err.toString())

        val progress = err.lines().filter { it.startsWith("waiting for pool") }
        assertEquals(1, progress.size, err.toString())
        assertTrue(progress.single().startsWith("waiting for pool db: 1st of 1 in line for 1 slot; main-repo · TASK-2 holds slot 1"), progress.single())
        assertTrue("waited 10 s" in err, err.toString())
        assertEquals(10, json().getValue("waited").jsonPrimitive.int)
        assertEquals("main-repo · TASK-2", json().getValue("takenFrom").jsonPrimitive.content)
        assertEquals(listOf("./scripts/db-down.sh", "./scripts/db-up.sh"), ran.map { it.first.first() })
    }

    @Test
    fun `a wait gives up after its bound`() {
        holding(admin2, task2)
        val waiting = WaitTicket("ticket", admin2, task2, ProcessIdentity(3000, 1), now.toEpochMilli(), customPoolTicketRequest(common, "db"))
        queue.write(WaitQueueName.CUSTOM_POOL, listOf(waiting))

        assertEquals(1, run("pool", "acquire", "db", "--wait-timeout", "1"))

        assertTrue(err.lines().any { it.startsWith("waiting for pool db: 2nd of 2 in line for 1 slot") }, err.toString())
        assertTrue("Waited 1 min for a slot of pool \"db\"" in err, err.toString())
        assertEquals("", out.toString())
    }

    @Test
    fun `a failing acquire command names the command and its output`() {
        failing += "./scripts/db-up.sh"

        assertEquals(1, run("pool", "acquire", "db"))

        assertTrue("The acquire command of pool \"db\" failed (exit code 3) for TASK-1: ./scripts/db-up.sh 1" in err, err.toString())
        assertTrue("database is down" in err)
        assertEquals(CustomPoolLeases(), leases.leases)
    }

    @Test
    fun `an unapproved repository names shogun trust`() {
        trust = FakeRepositoryTrust()

        assertEquals(1, run("pool", "acquire", "db"))

        assertTrue("`shogun trust`" in err, err.toString())
        assertTrue(ran.isEmpty())
    }

    @Test
    fun `usage errors exit with code 2`() {
        listOf(
            listOf("pool", "release", "db", "--wait"),
            listOf("pool", "acquire"),
            listOf("pool", "acquire", "db", "--wait-timeout", "0"),
            listOf("pool", "acquire", "db", "--wait-timeout"),
            listOf("pool", "acquire", "db", "--wait-timeout", "soon"),
            listOf("pool", "acquire", "db", "--fast"),
            listOf("pool", "acquire", "db", task1, "extra"),
            listOf("pool"),
            listOf("pool", "bogus"),
        ).forEach { args ->
            err.clear()
            assertEquals(2, cli().run(args), args.toString())
        }
    }

    @Test
    fun `release frees the slot, says when nothing was held and reports json`() {
        holding(admin1, task1, slot = 1)

        assertEquals(0, run("pool", "release", "db"), err.toString())
        assertEquals("released slot 1 of pool db\n", out.toString())
        out.clear()
        assertEquals(0, run("pool", "release", "db", task1))
        assertEquals("held no slot of pool db\n", out.toString())
        out.clear()
        holding(admin1, task1, slot = 1)
        assertEquals(0, run("pool", "release", "db", "--json"))
        assertEquals("""{"pool":"db","slot":1,"released":true}""", out.toString().trim())
    }

    @Test
    fun `pool status lists the repository's pools with holders and waiters`() {
        provisioning = FakeRepositoryProvisioningSource(mapOf(base to RepositoryProvisioning(pools = listOf(db.copy(max = 3)))))
        holding(admin1, task1, slot = 1)
        val running = CustomPoolLease(common, base, "db", 2, admin2, task2, now.toEpochMilli(), now.toEpochMilli(), ProcessIdentity(9, 9))
        leases.leases = leases.leases.copy(leases = leases.leases.leases + running + CustomPoolLease(common, base, "db", 3, "$common/worktrees/TASK-3", "/w/TASK-3", 0, 0))
        val ticket = WaitTicket("ticket", "$common/worktrees/TASK-4", "/w/TASK-4", ProcessIdentity(3000, 1), now.toEpochMilli(), customPoolTicketRequest(common, "db"))
        queue.write(WaitQueueName.CUSTOM_POOL, listOf(ticket))

        assertEquals(0, run("pool", "status"), err.toString())

        val lines = out.lines()
        val start = lines.indexOf("pool db 3/3")
        assertEquals(
            listOf(
                "  slot 1 main-repo · TASK-1 lease until ${clock(now + Duration.ofMinutes(19))}",
                "  slot 2 main-repo · TASK-2 running a command",
                "  slot 3 main-repo · TASK-3 lease until ${clock(Instant.EPOCH + Duration.ofMinutes(20))}",
                "  waiting: main-repo · TASK-4 since ${clock(now)}",
            ),
            lines.subList(start + 1, start + 5),
        )
        out.clear()
        assertEquals(0, run("pool", "status", "--json"))
        val pool = json().getValue("customPools").jsonArray.single().jsonObject
        assertEquals(3, pool.getValue("max").jsonPrimitive.int)
        assertEquals(listOf(1, 2, 3), pool.getValue("holders").jsonArray.map { it.jsonObject.getValue("slot").jsonPrimitive.int })
        assertEquals("true", pool.getValue("holders").jsonArray[1].jsonObject.getValue("running").jsonPrimitive.content)
        assertEquals("/w/TASK-4", pool.getValue("waiting").jsonArray.single().jsonObject.getValue("worktree").jsonPrimitive.content)
    }

    @Test
    fun `pool status json has no custom pools in a repository without them`() {
        provisioning = FakeRepositoryProvisioningSource()

        assertEquals(0, run("pool", "status", "--json"), err.toString())

        assertEquals(0, json().getValue("customPools").jsonArray.size)
    }

    @Test
    fun `status shows held, waited and lost slots`() {
        provisioning = FakeRepositoryProvisioningSource(mapOf(base to RepositoryProvisioning(pools = listOf(db, db.copy(name = "gpu")))))
        holding(admin1, task1)
        queue.write(WaitQueueName.CUSTOM_POOL, listOf(WaitTicket("ticket", admin2, task2, ProcessIdentity(3000, 1), now.toEpochMilli(), customPoolTicketRequest(common, "db"))))
        leases.leases = leases.leases.copy(losses = listOf(CustomPoolLoss(common, "gpu", 1, admin2, task2, now.toEpochMilli(), "main-repo · TASK-1")))

        assertEquals(0, run("status", workingDirectory = base), err.toString())

        val lines = out.lines()
        assertTrue(lines.single { it.startsWith(task1) }.contains("pool=db#1"), out.toString())
        val second = lines.single { it.startsWith(task2) }
        assertTrue("pool=db(waiting 1/1)" in second && "pool=gpu(taken for main-repo · TASK-1)" in second, second)
        out.clear()
        assertEquals(0, run("status", "--json", workingDirectory = base))
        val worktrees = json().getValue("worktrees").jsonArray.map { it.jsonObject }
        val held = worktrees.single { it.getValue("path").jsonPrimitive.content == task1 }.getValue("pools").jsonArray.single().jsonObject
        assertEquals(listOf("db", "holder", "1"), listOf(held.getValue("name"), held.getValue("role"), held.getValue("slot")).map { it.jsonPrimitive.content })
        val other = worktrees.single { it.getValue("path").jsonPrimitive.content == task2 }.getValue("pools").jsonArray.map { it.jsonObject }
        assertEquals(listOf("waiting", "lost"), other.map { it.getValue("role").jsonPrimitive.content })
        assertEquals(1, other.first().getValue("position").jsonPrimitive.int)
        assertEquals("main-repo · TASK-1", other.last().getValue("forWorktree").jsonPrimitive.content)
        assertEquals(0, worktrees.single { it.getValue("path").jsonPrimitive.content == base }.getValue("pools").jsonArray.size)
    }

    @Test
    fun `trust shows the pools before asking and lists them in json`() {
        trust = FakeRepositoryTrust()
        provisioning = FakeRepositoryProvisioningSource(mapOf(base to RepositoryProvisioning(pools = listOf(db, db.copy(name = "gpu", release = null, variables = emptyList())))))

        assertEquals(0, run("trust", "--yes", workingDirectory = base), err.toString())

        assertTrue(
            "Pools declared by $base:\n  db (at most 1 at once)\n    acquire: ./scripts/db-up.sh '\${slot}'\n    release: ./scripts/db-down.sh '\${slot}'\n" +
                "    DATABASE_URL = postgres://localhost/app_\${slot}\n  gpu (at most 1 at once)\n    acquire: ./scripts/db-up.sh '\${slot}'\n" in out,
            out.toString(),
        )
        assertTrue("approved" in out)
        out.clear()
        trust = FakeRepositoryTrust()
        assertEquals(0, run("trust", "--json", workingDirectory = base))
        val pools = json().getValue("pools").jsonArray.map { it.jsonObject }
        assertEquals(listOf("db", "gpu"), pools.map { it.getValue("name").jsonPrimitive.content })
        assertEquals(JsonNull, pools.last()["release"])
        assertEquals("DATABASE_URL", pools.first().getValue("env").jsonArray.single().jsonObject.getValue("name").jsonPrimitive.content)
    }

    @Test
    fun `removing a worktree reports a failing release and frees the slot`() {
        holding(admin2, task2)
        failing += "./scripts/db-down.sh"

        assertEquals(0, run("remove", task2, "--force", workingDirectory = base), err.toString())

        assertTrue("shogun: The release command of pool \"db\" failed (exit code 3) for TASK-2" in err, err.toString())
        assertEquals(CustomPoolLeases(), leases.leases)
    }

    @Test
    fun `the resources guide names the repository's pools`() {
        assertEquals(0, run("guide", "resources"), err.toString())

        assertTrue("`shogun pool acquire db --wait`" in out, out.toString())
    }
}
