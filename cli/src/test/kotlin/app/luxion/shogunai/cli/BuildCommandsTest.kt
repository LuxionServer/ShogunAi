package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeBuildTurnStore
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeDeviceWaitQueue
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeForegroundCommandRunner
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeHerdrConfigSource
import app.luxion.shogunai.domain.FakeKmpTestWiring
import app.luxion.shogunai.domain.FakeMachineCapacity
import app.luxion.shogunai.domain.FakeOrcaClient
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeProcessLiveness
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeResourceLimitsRepository
import app.luxion.shogunai.domain.ScriptedShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.io.BuildTurnStore
import app.luxion.shogunai.domain.model.BuildSlot
import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.ProcessIdentity
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.model.WaitQueueName
import app.luxion.shogunai.domain.model.WaitTicket
import app.luxion.shogunai.domain.model.toLocalClockTime
import app.luxion.shogunai.engine.EngineOptions
import app.luxion.shogunai.engine.ShogunEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildCommandsTest {

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

    private val out = StringBuilder()
    private val err = StringBuilder()
    private var now: Instant = Instant.parse("2026-01-05T14:00:00Z")
    private val onPause = mutableListOf<() -> Unit>()
    private val slots = FakeBuildTurnStore()
    private val queue = FakeDeviceWaitQueue()
    private var runner = FakeForegroundCommandRunner()
    private var settings = ResourceSettings(profile = CapacityProfile.LIGHT)

    private val executor = object : ScriptedShellCommandExecutor() {
        override suspend fun respond(command: List<String>, workingDirectory: String?): CommandResult {
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

    private fun cli(workingDirectory: String = task1, input: String = "", store: BuildTurnStore = slots) = ShogunCli(
        engine = ShogunEngine(
            cliEnginePorts(
                executor = executor,
                fileManager = FakeFileManager(existing = listOf(base, task1, task2)),
                projectRepository = FakeProjectRepository(emptyList()),
                provisioningSource = FakeRepositoryProvisioningSource(),
                gradleDaemons = FakeGradleDaemonSource(),
                gradleUserHome = "/home/dev/.gradle",
                environmentStateStore = FakeEnvironmentStateStore(),
                portProbe = FakePortProbe(),
                androidSdk = FakeAndroidSdk(),
                appleSimulators = FakeAppleSimulators(),
                kmpTestWiring = FakeKmpTestWiring(),
                resourceLimits = FakeResourceLimitsRepository(settings),
                machineCapacity = FakeMachineCapacity(),
                derivedData = FakeDerivedDataSource(),
                herdrConfig = FakeHerdrConfigSource(),
                orca = FakeOrcaClient(installed = false),
                workingDirectory = workingDirectory,
                environment = hostEnvironment(),
                deviceWaitQueue = queue,
                processLiveness = FakeProcessLiveness(),
                buildTurns = store,
                foregroundCommands = runner,
            ),
            EngineOptions(
                clock = { now },
                pauseWhileWaiting = { pause ->
                    now += pause
                    onPause.removeFirstOrNull()?.invoke()
                },
            ),
        ),
        workingDirectory = workingDirectory,
        out = out,
        err = err,
        canonicalPath = { it.trimEnd('/') },
        environment = hostEnvironment(),
        readInput = { input },
    )

    private fun runningIn(admin: String, worktree: String, command: String = "./gradlew build") =
        BuildSlot("slot-${worktree.substringAfterLast('/')}", admin, worktree, command, now.toEpochMilli(), ProcessIdentity(2000, 1))
            .also { slots.slots = slots.slots + it }

    private fun waiting(admin: String, worktree: String) = WaitTicket("t-$worktree", admin, worktree, ProcessIdentity(5, 1), now.toEpochMilli())

    private fun json(): JsonObject = Json.parseToJsonElement(out.toString()).jsonObject

    private fun hookInput(command: String, cwd: String = task1, tool: String = "Bash"): String = buildJsonObject {
        put("tool_name", tool)
        put("cwd", cwd)
        put("tool_input", buildJsonObject { put("command", command) })
    }.toString()

    @Test
    fun `run executes the command here in a free slot and exits with its code`() {
        runner = FakeForegroundCommandRunner(exitCode = 3, whileRunning = { assertEquals(admin1, slots.slots.single().adminDirectory) })

        assertEquals(3, cli(workingDirectory = "$task1/app").run(listOf("build", "run", "--", "./gradlew", "test", "--json")))

        assertEquals(listOf(listOf("./gradlew", "test", "--json") to "$task1/app"), runner.runs)
        assertEquals("", out.toString())
        assertEquals("", err.toString())
        assertTrue(slots.slots.isEmpty())
    }

    @Test
    fun `run waits for a slot showing its place only when it changes`() {
        runningIn(admin2, task2)
        onPause += {}
        onPause += { slots.slots = emptyList() }

        assertEquals(0, cli().run(listOf("build", "run", "--wait-timeout", "5", "--", "mvn", "verify")), err.toString())

        assertEquals("waiting for a build slot: 1st of 1 in line for 1 build slot, held by main-repo · TASK-2\n", err.toString())
        assertEquals(listOf("mvn", "verify"), runner.runs.single().first)
    }

    @Test
    fun `waiting behind an earlier waiter says the slot is free for it`() {
        settings = ResourceSettings(profile = CapacityProfile.BALANCED)
        runningIn(admin2, task2)
        queue.write(WaitQueueName.BUILD, listOf(waiting("$common/worktrees/TASK-3", "/home/dev/projects/worktrees/TASK-3")))
        onPause += { queue.write(WaitQueueName.BUILD, queue.read(WaitQueueName.BUILD).filterNot { it.id.startsWith("t-") }) }

        assertEquals(0, cli().run(listOf("build", "run", "--", "./gradlew", "test")), err.toString())

        assertEquals("waiting for a build slot: 2nd of 2 in line for 2 build slots, held by main-repo · TASK-2\n", err.toString())
    }

    @Test
    fun `waiting for a free slot behind an earlier waiter says it is free for it`() {
        queue.write(WaitQueueName.BUILD, listOf(waiting("$common/worktrees/TASK-3", "/home/dev/projects/worktrees/TASK-3")))
        onPause += { queue.write(WaitQueueName.BUILD, emptyList()) }

        assertEquals(0, cli().run(listOf("build", "run", "--", "./gradlew", "test")), err.toString())

        assertEquals("waiting for a build slot: 2nd of 2 in line for 1 build slot, free for earlier waiters\n", err.toString())
    }

    @Test
    fun `a wait that times out runs nothing and names the holder`() {
        runningIn(admin2, task2)

        assertEquals(1, cli().run(listOf("build", "run", "--wait-timeout", "1", "--", "./gradlew", "test")))

        assertTrue(runner.runs.isEmpty())
        assertTrue(err.lines().any { it == "shogun: Waited 1 min for a build slot; The only build slot is taken by main-repo · TASK-2" }, err.toString())
    }

    @Test
    fun `a command that can't start exits with 127`() {
        runner = FakeForegroundCommandRunner(exitCode = null)

        assertEquals(127, cli().run(listOf("build", "run", "--", "does-not-exist")))

        assertEquals("shogun: couldn't start does-not-exist\n", err.toString())
        assertTrue(slots.slots.isEmpty())
    }

    @Test
    fun `run outside a repository needs a worktree and runs nothing`() {
        assertEquals(1, cli(workingDirectory = "/tmp/elsewhere").run(listOf("build", "run", "--", "./gradlew", "test")))

        assertTrue(err.toString().startsWith("shogun: No Git repository found at /tmp/elsewhere"), err.toString())
        assertTrue(runner.runs.isEmpty())
    }

    @Test
    fun `invalid build invocations are usage errors`() {
        listOf(
            listOf("build"),
            listOf("build", "start"),
            listOf("build", "run", "./gradlew", "test"),
            listOf("build", "run", "--"),
            listOf("build", "run", "--wait-timeout", "0", "--", "./gradlew"),
            listOf("build", "run", "--wait", "--", "./gradlew"),
            listOf("build", "status", "--all"),
        ).forEach { arguments ->
            err.clear()
            assertEquals(2, cli().run(arguments), arguments.toString())
            assertTrue(err.startsWith("shogun: "), err.toString())
        }
        assertTrue(runner.runs.isEmpty())
    }

    @Test
    fun `status lists the slots, the running builds and the waiters`() {
        runningIn(admin2, task2, "./gradlew test")
        queue.write(WaitQueueName.BUILD, listOf(waiting(admin1, task1)))

        assertEquals(0, cli().run(listOf("build", "status")), err.toString())

        val since = now.toLocalClockTime()
        assertEquals(
            "build slots: 1 of 1 in use\n" +
                "  main-repo · TASK-2 running `./gradlew test` since $since\n" +
                "  1. main-repo · TASK-1 waiting since $since\n",
            out.toString(),
        )
    }

    @Test
    fun `status without a limit says so`() {
        settings = ResourceSettings(profile = CapacityProfile.PER_WORKTREE)

        assertEquals(0, cli().run(listOf("build", "status")), err.toString())

        assertEquals("build slots: no limit, 0 running\n", out.toString())
    }

    @Test
    fun `status json has the limit, the running builds and the waiters`() {
        runningIn(admin2, task2, "./gradlew test")
        queue.write(WaitQueueName.BUILD, listOf(waiting(admin1, task1)))

        assertEquals(0, cli().run(listOf("build", "status", "--json")), err.toString())

        val status = json()
        assertEquals(1, status["limit"]?.jsonPrimitive?.int)
        val running = status["running"]!!.jsonArray.single().jsonObject
        assertEquals(base, running["repository"]?.jsonPrimitive?.content)
        assertEquals(task2, running["worktree"]?.jsonPrimitive?.content)
        assertEquals("./gradlew test", running["command"]?.jsonPrimitive?.content)
        assertEquals(now.toString(), running["since"]?.jsonPrimitive?.content)
        val waiter = status["waiting"]!!.jsonArray.single().jsonObject
        assertEquals(listOf(base, task1, now.toString()), listOf("repository", "worktree", "since").map { waiter[it]?.jsonPrimitive?.content })
    }

    @Test
    fun `status json of an idle machine without a limit`() {
        settings = ResourceSettings(profile = CapacityProfile.PER_WORKTREE)

        assertEquals(0, cli().run(listOf("build", "status", "--json")), err.toString())

        assertEquals("""{"limit":null,"running":[],"waiting":[]}""", out.toString().trim())
    }

    @Test
    fun `worktree status shows running and waiting builds`() {
        settings = ResourceSettings(profile = CapacityProfile.BALANCED)
        runningIn(admin2, task2)
        runningIn(admin2, task2)
        queue.write(WaitQueueName.BUILD, listOf(waiting(admin1, task1)))

        assertEquals(0, cli(workingDirectory = base).run(listOf("status")), err.toString())

        val lines = out.lines()
        assertTrue(lines.single { it.startsWith(task2) }.contains("build(running 2)"), out.toString())
        assertTrue(lines.single { it.startsWith(task1) }.contains("build(waiting 1/1)"), out.toString())
        assertTrue(lines.single { it.startsWith("$base ") }.contains("build(").not(), out.toString())
    }

    @Test
    fun `worktree status json adds the build field`() {
        runningIn(admin2, task2)
        queue.write(WaitQueueName.BUILD, listOf(waiting("$common/worktrees/TASK-3", "/home/dev/projects/worktrees/TASK-3"), waiting(admin1, task1)))

        assertEquals(0, cli(workingDirectory = base).run(listOf("status", "--json")), err.toString())

        val builds = json()["worktrees"]!!.jsonArray.map { it.jsonObject }.associate { it["path"]!!.jsonPrimitive.content to it["build"] }
        assertEquals(JsonNull, builds[base])
        assertEquals("""{"running":1,"waiting":null}""", builds[task2].toString())
        assertEquals("""{"running":0,"waiting":2}""", builds[task1].toString())
    }

    @Test
    fun `worktree status survives an unreadable build turn`() {
        val broken = object : BuildTurnStore {
            override fun read(): List<BuildSlot> = throw IllegalStateException("unreadable")
            override fun write(slots: List<BuildSlot>) = Unit
        }

        assertEquals(0, cli(workingDirectory = base, store = broken).run(listOf("status", "--json")), err.toString())

        assertTrue(json()["worktrees"]!!.jsonArray.all { it.jsonObject["build"] == JsonNull })
    }

    @Test
    fun `the hook denies an unwrapped heavy build with the command to run`() {
        assertEquals(0, cli(input = hookInput("cd app && ./gradlew test koverVerify")).run(listOf("hook", "build-turn")), err.toString())

        val output = json()["hookSpecificOutput"]!!.jsonObject
        assertEquals("PreToolUse", output["hookEventName"]?.jsonPrimitive?.content)
        assertEquals("deny", output["permissionDecision"]?.jsonPrimitive?.content)
        val reason = output["permissionDecisionReason"]!!.jsonPrimitive.content
        assertTrue("`shogun build run -- ./gradlew test koverVerify`" in reason, reason)
        assertTrue("in the background" in reason, reason)
    }

    @Test
    fun `the hook lets everything else run silently`() {
        settings = ResourceSettings(profile = CapacityProfile.LIGHT)
        listOf(
            hookInput("shogun build run -- ./gradlew test"),
            hookInput("./gradlew --stop"),
            hookInput("grep -r gradle docs"),
            hookInput("./gradlew test", cwd = "/tmp/elsewhere"),
            hookInput("./gradlew test", tool = "Read"),
            """{"tool_name":"Bash","tool_input":{}}""",
            """{"tool_name":3,"tool_input":{"command":"./gradlew test"}}""",
            """{"tool_name":" ","tool_input":{"command":"./gradlew test"}}""",
            """{"tool_name":"Bash"}""",
            """[]""",
            "not json",
        ).forEach { input ->
            out.clear()
            assertEquals(0, cli(input = input).run(listOf("hook", "build-turn")), input)
            assertEquals("", out.toString(), input)
            assertEquals("", err.toString(), input)
        }
    }

    @Test
    fun `the hook lets builds run when there is no limit`() {
        settings = ResourceSettings(profile = CapacityProfile.PER_WORKTREE)

        assertEquals(0, cli(input = hookInput("./gradlew test")).run(listOf("hook", "build-turn")))

        assertEquals("", out.toString())
    }

    @Test
    fun `the hook uses the current directory when the input has none`() {
        val input = """{"tool_name":"Bash","tool_input":{"command":"xcodebuild test"}}"""

        assertEquals(0, cli(input = input).run(listOf("hook", "build-turn")))
        assertTrue(out.contains("shogun build run -- xcodebuild test"), out.toString())
        out.clear()
        assertEquals(0, cli(workingDirectory = "/tmp/elsewhere", input = input).run(listOf("hook", "build-turn")))
        assertEquals("", out.toString())
    }

    @Test
    fun `help describes the build command and its hook`() {
        assertEquals(0, cli().run(listOf("build", "--help")))
        assertTrue("build run [--wait-timeout <minutes>] -- <command>" in out, out.toString())
        out.clear()
        assertEquals(0, cli().run(listOf("hook", "--help")))
        assertTrue("hook build-turn" in out, out.toString())
    }

    @Test
    fun `a slot left by a dead build doesn't block the next one`() {
        val dead = ProcessIdentity(9, 9)
        slots.slots = listOf(BuildSlot("old", admin2, task2, "./gradlew test", now.toEpochMilli() - Duration.ofHours(1).toMillis(), dead))
        val liveness = FakeProcessLiveness().apply { this.dead += dead }
        val engine = ShogunEngine(
            cliEnginePorts(
                executor = executor,
                fileManager = FakeFileManager(existing = listOf(base, task1, task2)),
                projectRepository = FakeProjectRepository(emptyList()),
                provisioningSource = FakeRepositoryProvisioningSource(),
                gradleDaemons = FakeGradleDaemonSource(),
                gradleUserHome = "/home/dev/.gradle",
                environmentStateStore = FakeEnvironmentStateStore(),
                portProbe = FakePortProbe(),
                androidSdk = FakeAndroidSdk(),
                appleSimulators = FakeAppleSimulators(),
                kmpTestWiring = FakeKmpTestWiring(),
                resourceLimits = FakeResourceLimitsRepository(settings),
                machineCapacity = FakeMachineCapacity(),
                derivedData = FakeDerivedDataSource(),
                herdrConfig = FakeHerdrConfigSource(),
                orca = FakeOrcaClient(installed = false),
                workingDirectory = task1,
                environment = hostEnvironment(),
                deviceWaitQueue = queue,
                processLiveness = liveness,
                buildTurns = slots,
                foregroundCommands = runner,
            ),
        )
        val cli = ShogunCli(engine, task1, out, err, canonicalPath = { it.trimEnd('/') }, environment = hostEnvironment())

        assertEquals(0, cli.run(listOf("build", "run", "--", "./gradlew", "test")), err.toString())

        assertEquals(1, runner.runs.size)
        assertTrue(slots.slots.isEmpty())
    }
}
