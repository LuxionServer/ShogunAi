package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeDesktopTurnStore
import app.luxion.shogunai.domain.FakeDeviceWaitQueue
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeHerdrConfigSource
import app.luxion.shogunai.domain.FakeOrcaClient
import app.luxion.shogunai.domain.FakeKmpTestWiring
import app.luxion.shogunai.domain.FakeMachineCapacity
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeProcessLiveness
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeResourceLimitsRepository
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.DesktopTurnStore
import app.luxion.shogunai.domain.model.DesktopTurn
import app.luxion.shogunai.domain.model.ProcessIdentity
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.model.WaitQueueName
import app.luxion.shogunai.domain.model.WaitTicket
import app.luxion.shogunai.domain.model.toLocalClockTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopCommandsTest {

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
    private val turnStore = FakeDesktopTurnStore()
    private val queue = FakeDeviceWaitQueue()
    private val stateStore = FakeEnvironmentStateStore()

    private val executor = object : ShellCommandExecutor {
        override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
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

    private fun cli(workingDirectory: String = task1, input: String = "", store: DesktopTurnStore = turnStore) = ShogunCli(
        executor = executor,
        fileManager = FakeFileManager(existing = listOf(base, task1, task2)),
        projectRepository = FakeProjectRepository(emptyList()),
        provisioningSource = FakeRepositoryProvisioningSource(),
        daemonSource = FakeGradleDaemonSource(),
        gradleUserHome = "/home/dev/.gradle",
        environmentStateStore = stateStore,
        portProbe = FakePortProbe(),
        androidSdk = FakeAndroidSdk(),
        appleSimulators = FakeAppleSimulators(),
        kmpTestWiring = FakeKmpTestWiring(),
        resourceLimits = FakeResourceLimitsRepository(ResourceSettings(desktopTurnMinutes = 10)),
        machineCapacity = FakeMachineCapacity(),
        derivedDataSource = FakeDerivedDataSource(),
        herdrConfig = FakeHerdrConfigSource(),
        orca = FakeOrcaClient(installed = false),
        workingDirectory = workingDirectory,
        out = out,
        err = err,
        canonicalPath = { it.trimEnd('/') },
        environmentVariable = { null },
        userHome = "/home/dev",
        deviceWaitQueue = queue,
        processLiveness = FakeProcessLiveness(),
        deviceClock = { now },
        pauseWhileWaiting = { pause ->
            now += pause
            onPause.removeFirstOrNull()?.invoke()
        },
        desktopTurnStore = store,
        readInput = { input },
    )

    private fun clock(instant: Instant) = instant.toLocalClockTime()

    private fun heldByTask2(minutes: Long = 5): DesktopTurn =
        DesktopTurn(admin2, task2, (now - Duration.ofMinutes(1)).toEpochMilli(), (now + Duration.ofMinutes(minutes)).toEpochMilli())
            .also { turnStore.turn = it }

    private fun json(): JsonObject = Json.parseToJsonElement(out.toString()).jsonObject

    @Test
    fun `acquire takes a free desktop and prints until when`() {
        assertEquals(0, cli().run(listOf("desktop", "acquire")), err.toString())

        assertEquals("acquired the desktop until ${clock(now + Duration.ofMinutes(10))}\n", out.toString())
        assertEquals(admin1, turnStore.turn?.adminDirectory)
    }

    @Test
    fun `acquire again renews the turn`() {
        cli().run(listOf("desktop", "acquire"))
        out.clear()
        now += Duration.ofMinutes(5)

        assertEquals(0, cli().run(listOf("desktop", "acquire")), err.toString())

        assertEquals("renewed the desktop until ${clock(now + Duration.ofMinutes(10))}\n", out.toString())
    }

    @Test
    fun `acquire while another worktree holds it fails naming the holder`() {
        val turn = heldByTask2()
        queue.write(WaitQueueName.DESKTOP, listOf(WaitTicket("t", "$common/worktrees/TASK-3", "/home/dev/projects/worktrees/TASK-3", ProcessIdentity(5, 1), 0)))

        assertEquals(1, cli().run(listOf("desktop", "acquire")))

        assertEquals("", out.toString())
        assertTrue("main-repo · TASK-2" in err.toString(), err.toString())
        assertTrue(clock(Instant.ofEpochMilli(turn.expiresAt)) in err.toString(), err.toString())
        assertTrue("1 worktree is waiting" in err.toString(), err.toString())
        assertTrue("--wait" in err.toString(), err.toString())
        assertEquals(turn, turnStore.turn)
    }

    @Test
    fun `acquire with wait waits in line printing progress on standard error`() {
        heldByTask2()
        onPause += { turnStore.turn = null }

        assertEquals(0, cli().run(listOf("desktop", "acquire", "--wait", "--json")), err.toString())

        val result = json()
        assertEquals(task1, result["holder"]?.jsonObject?.get("worktree")?.jsonPrimitive?.content)
        assertEquals(false, result["renewed"]?.jsonPrimitive?.boolean)
        assertEquals(10, result["waited"]?.jsonPrimitive?.int)
        assertTrue("waiting for the desktop: 1st of 1 in line, held by main-repo · TASK-2" in err.toString(), err.toString())
        assertTrue("waited 10 s" in err.toString(), err.toString())
    }

    @Test
    fun `acquire with a timeout gives up and leaves the queue`() {
        heldByTask2(minutes = 60)

        assertEquals(1, cli().run(listOf("desktop", "acquire", "--wait-timeout", "1", "--json")))

        assertEquals("", out.toString())
        assertTrue("Waited 1 min for the desktop" in err.toString(), err.toString())
        assertTrue(queue.read(WaitQueueName.DESKTOP).isEmpty())
    }

    @Test
    fun `invalid wait options are usage errors`() {
        assertEquals(2, cli().run(listOf("desktop", "acquire", "--wait-timeout", "0")))
        assertEquals(2, cli().run(listOf("desktop", "release", "--wait")))
        assertEquals(2, cli().run(listOf("desktop", "take")))
    }

    @Test
    fun `acquire outside a repository says it needs a worktree`() {
        assertEquals(1, cli(workingDirectory = "/tmp/elsewhere").run(listOf("desktop", "acquire")))
    }

    @Test
    fun `json without waiting has a null waited and the waiters`() {
        assertEquals(0, cli().run(listOf("desktop", "acquire", "--json")), err.toString())

        val result = json()
        assertEquals(JsonNull, result["waited"])
        assertEquals(0, result["waiting"]?.jsonArray?.size)
        assertEquals(base, result["holder"]?.jsonObject?.get("repository")?.jsonPrimitive?.content)
        assertEquals((now + Duration.ofMinutes(10)).toString(), result["holder"]?.jsonObject?.get("expiresAt")?.jsonPrimitive?.content)
    }

    @Test
    fun `release by the holder frees the desktop`() {
        cli().run(listOf("desktop", "acquire"))
        out.clear()

        assertEquals(0, cli().run(listOf("desktop", "release")), err.toString())

        assertEquals("released the desktop\n", out.toString())
        assertNull(turnStore.turn)
    }

    @Test
    fun `release of a free desktop says nobody holds it`() {
        assertEquals(0, cli().run(listOf("desktop", "release", "--json")), err.toString())

        assertEquals(false, json()["released"]?.jsonPrimitive?.boolean)
        assertEquals(JsonNull, json()["holder"])
    }

    @Test
    fun `release of someone else's turn fails and keeps it`() {
        val turn = heldByTask2()

        assertEquals(1, cli().run(listOf("desktop", "release")))

        assertTrue("main-repo · TASK-2" in err.toString(), err.toString())
        assertEquals(turn, turnStore.turn)
    }

    @Test
    fun `status lists the holder and the waiters`() {
        val turn = heldByTask2()
        queue.write(WaitQueueName.DESKTOP, listOf(WaitTicket("t", admin1, task1, ProcessIdentity(5, 1), now.toEpochMilli())))

        assertEquals(0, cli().run(listOf("desktop", "status")), err.toString())

        assertEquals(
            "desktop held by main-repo · TASK-2 since ${clock(Instant.ofEpochMilli(turn.acquiredAt))} until ${clock(Instant.ofEpochMilli(turn.expiresAt))}\n" +
                "  1. main-repo · TASK-1 waiting since ${clock(now)}\n",
            out.toString(),
        )
    }

    @Test
    fun `status of a free desktop in json`() {
        assertEquals(0, cli().run(listOf("desktop", "status", "--json")), err.toString())

        assertEquals(JsonNull, json()["holder"])
        assertEquals(0, json()["waiting"]?.jsonArray?.size)
    }

    @Test
    fun `worktree status shows who holds and who waits for the desktop`() {
        val turn = heldByTask2()
        queue.write(WaitQueueName.DESKTOP, listOf(WaitTicket("t", admin1, task1, ProcessIdentity(5, 1), now.toEpochMilli())))

        assertEquals(0, cli(workingDirectory = base).run(listOf("status")), err.toString())

        val lines = out.lines()
        assertTrue(lines.single { it.startsWith(task2) }.contains("desktop(until ${clock(Instant.ofEpochMilli(turn.expiresAt))})"), out.toString())
        assertTrue(lines.single { it.startsWith(task1) }.contains("desktop(waiting 1/1)"), out.toString())
        assertTrue(lines.single { it.startsWith("$base ") }.contains("desktop").not(), out.toString())
    }

    @Test
    fun `worktree status json adds the desktop field to every worktree`() {
        val turn = heldByTask2()

        assertEquals(0, cli(workingDirectory = base).run(listOf("status", "--json")), err.toString())

        val worktrees = json()["worktrees"]!!.jsonArray.map { it.jsonObject }
        val holder = worktrees.single { it["path"]?.jsonPrimitive?.content == task2 }["desktop"]!!.jsonObject
        assertEquals("holder", holder["role"]?.jsonPrimitive?.content)
        assertEquals(Instant.ofEpochMilli(turn.expiresAt).toString(), holder["expiresAt"]?.jsonPrimitive?.content)
        assertEquals(listOf(JsonNull, JsonNull), worktrees.filter { it["path"]?.jsonPrimitive?.content != task2 }.map { it["desktop"] })
    }

    @Test
    fun `removing the holder's worktree releases the desktop`() {
        heldByTask2()

        assertEquals(0, cli(workingDirectory = base).run(listOf("remove", task2, "--force")), err.toString())

        assertNull(turnStore.turn)
    }

    private fun preToolUse(command: String, cwd: String = task1, tool: String = "Bash") =
        Json.encodeToString(
            JsonObject.serializer(),
            JsonObject(
                mapOf(
                    "session_id" to JsonPrimitive("963d9441"),
                    "cwd" to JsonPrimitive(cwd),
                    "hook_event_name" to JsonPrimitive("PreToolUse"),
                    "tool_name" to JsonPrimitive(tool),
                    "tool_input" to JsonObject(mapOf("command" to JsonPrimitive(command))),
                ),
            ),
        )

    private fun desktopTurnHook(command: String, cwd: String = task1, tool: String = "Bash", store: DesktopTurnStore = turnStore): Int =
        cli(input = preToolUse(command, cwd, tool), store = store).run(listOf("hook", "desktop-turn"))

    private fun denialReason(): String {
        val output = json().getValue("hookSpecificOutput").jsonObject
        assertEquals("PreToolUse", output["hookEventName"]?.jsonPrimitive?.content)
        assertEquals("deny", output["permissionDecision"]?.jsonPrimitive?.content)
        return output.getValue("permissionDecisionReason").jsonPrimitive.content
    }

    @Test
    fun `desktop-turn hook takes a free desktop before an acting orca computer command`() {
        assertEquals(0, desktopTurnHook("orca computer click --app com.example.app --element-index 4 --json"), err.toString())

        assertEquals("", out.toString())
        assertEquals(admin1, turnStore.turn?.adminDirectory)
        assertEquals((now + Duration.ofMinutes(10)).toEpochMilli(), turnStore.turn?.expiresAt)
    }

    @Test
    fun `desktop-turn hook renews the holder's turn`() {
        desktopTurnHook("orca computer click --app com.example.app --element-index 4")
        now += Duration.ofMinutes(5)

        assertEquals(0, desktopTurnHook("orca computer type-text --app com.example.app --text hello"), err.toString())

        assertEquals("", out.toString())
        assertEquals((now + Duration.ofMinutes(10)).toEpochMilli(), turnStore.turn?.expiresAt)
    }

    @Test
    fun `desktop-turn hook takes the turn for an absolute path in a compound command and for restoring a window`() {
        desktopTurnHook("cd /tmp && /opt/homebrew/bin/orca computer hotkey --app com.example.app --key CmdOrCtrl+A")
        assertEquals(admin1, turnStore.turn?.adminDirectory)

        turnStore.turn = null
        desktopTurnHook("orca computer get-app-state --app com.example.app --restore-window --json")
        assertEquals(admin1, turnStore.turn?.adminDirectory)
    }

    @Test
    fun `desktop-turn hook blocks the command while another worktree holds the turn`() {
        val turn = heldByTask2()

        assertEquals(0, desktopTurnHook("orca computer click --app com.example.app --element-index 4"), err.toString())

        val reason = denialReason()
        assertTrue("main-repo · TASK-2" in reason, reason)
        assertTrue(clock(Instant.ofEpochMilli(turn.expiresAt)) in reason, reason)
        assertTrue("shogun desktop acquire --wait" in reason && "background" in reason, reason)
        assertEquals(turn, turnStore.turn)
        assertTrue(queue.read(WaitQueueName.DESKTOP).isEmpty())
    }

    @Test
    fun `desktop-turn hook blocks the command while others wait and keeps them first`() {
        val waiter = WaitTicket("t", admin2, task2, ProcessIdentity(5, 1), 0)
        queue.write(WaitQueueName.DESKTOP, listOf(waiter))

        assertEquals(0, desktopTurnHook("orca computer press-key --app com.example.app --key Escape", cwd = base), err.toString())

        assertTrue("1 worktree is waiting" in denialReason())
        assertNull(turnStore.turn)
        assertEquals(listOf(waiter), queue.read(WaitQueueName.DESKTOP))
    }

    @Test
    fun `desktop-turn hook leaves read-only, unrelated and other tools' calls alone`() {
        val turn = heldByTask2()

        listOf(
            "orca computer get-app-state --app com.example.app --json" to "Bash",
            "echo computer science" to "Bash",
            "orca computer click --app com.example.app" to "Read",
        ).forEach { (command, tool) ->
            assertEquals(0, desktopTurnHook(command, tool = tool), err.toString())
            assertEquals("", out.toString(), command)
            assertEquals(turn, turnStore.turn)
        }
    }

    @Test
    fun `desktop-turn hook does nothing outside a repository`() {
        assertEquals(0, desktopTurnHook("orca computer click --app com.example.app", cwd = "/tmp/elsewhere"), err.toString())

        assertEquals("", out.toString())
        assertNull(turnStore.turn)
    }

    @Test
    fun `desktop-turn hook lets the command run when the turn can't be read`() {
        val unreadable = object : DesktopTurnStore {
            override fun read(): DesktopTurn? = error("unreadable")
            override fun write(turn: DesktopTurn?) = error("unwritable")
        }

        assertEquals(0, desktopTurnHook("orca computer click --app com.example.app", store = unreadable))

        assertEquals("", out.toString())
    }

    @Test
    fun `desktop-turn hook never allows a command explicitly`() {
        desktopTurnHook("orca computer click --app com.example.app")
        heldByTask2()
        desktopTurnHook("orca computer click --app com.example.app")

        assertTrue("\"allow\"" !in out.toString(), out.toString())
    }
}
