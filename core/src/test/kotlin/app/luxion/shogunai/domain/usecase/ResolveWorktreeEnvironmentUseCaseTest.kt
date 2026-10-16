package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.WorktreeEnvironment
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.parseEnvironmentVariable
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ResolveWorktreeEnvironmentUseCaseTest {

    private val repository = "/home/dev/projects/main-repo"
    private val common = "$repository/.git"
    private val task = "/home/dev/projects/worktrees/TASK-123"
    private val other = "/home/dev/projects/worktrees/TASK-456"
    private val adminDirectories = mapOf(
        repository to common,
        task to "$common/worktrees/TASK-123",
        other to "$common/worktrees/TASK-456",
    )

    private val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it) }

    private fun declaring(vararg variables: Pair<String, String>, envFile: String? = null) =
        FakeRepositoryProvisioningSource(
            byRepository = mapOf(
                repository to RepositoryProvisioning(
                    environment = EnvironmentDeclaration(variables.map { (name, value) -> parseEnvironmentVariable(name, value) }, envFile),
                ),
            ),
        )

    private fun gitExecutor() = object : app.luxion.shogunai.domain.executor.ShellCommandExecutor {
        override suspend fun execute(command: List<String>, workingDirectory: String?) =
            FakeShellCommandExecutor.success(command, "${adminDirectories.getValue(workingDirectory!!)}\n$common\n")
    }

    private fun useCase(
        source: FakeRepositoryProvisioningSource,
        store: FakeEnvironmentStateStore = FakeEnvironmentStateStore(),
        probe: FakePortProbe = FakePortProbe(),
    ) = ResolveWorktreeEnvironmentUseCase(gitExecutor(), source, store, probe)

    @Test
    fun `reserves the lowest free port and renders the templates`() = runTest {
        val store = FakeEnvironmentStateStore()
        val source = declaring("PORT" to "\${port}", "APP_SUFFIX" to "-\${worktree}")

        val environment = useCase(source, store)(repository, task).getOrThrow()

        assertEquals(listOf("PORT" to "20000", "APP_SUFFIX" to "-TASK-123"), environment.variables)
        assertEquals(mapOf("PORT" to 20000), store.readPorts("$common/worktrees/TASK-123"))
        assertEquals(1, store.locks)
    }

    @Test
    fun `shared keys use one port and bare placeholders one each`() = runTest {
        val source = declaring(
            "PORT" to "\${port}",
            "DEBUG_PORT" to "\${port}",
            "API_PORT" to "\${port:api}",
            "API_URL" to "http://localhost:\${port:api}",
        )

        val values = useCase(source)(repository, task).getOrThrow().variables.toMap()

        assertEquals(setOf("20000", "20001", "20002"), setOf(values["PORT"], values["DEBUG_PORT"], values["API_PORT"]))
        assertEquals("http://localhost:${values["API_PORT"]}", values["API_URL"])
    }

    @Test
    fun `ports are unique among the repository's worktrees and skip busy ones`() = runTest {
        val store = FakeEnvironmentStateStore(mapOf("$common/worktrees/TASK-456" to mapOf("PORT" to 20000)))
        val source = declaring("PORT" to "\${port}")

        val environment = useCase(source, store, FakePortProbe(busy = setOf(20001)))(repository, task).getOrThrow()

        assertEquals(mapOf("PORT" to 20002), environment.ports)
    }

    @Test
    fun `a reservation is stable even when its port is busy`() = runTest {
        val store = FakeEnvironmentStateStore(mapOf("$common/worktrees/TASK-123" to mapOf("PORT" to 20007)))
        val source = declaring("PORT" to "\${port}")

        val environment = useCase(source, store, FakePortProbe(busy = setOf(20007)))(repository, task).getOrThrow()

        assertEquals(mapOf("PORT" to 20007), environment.ports)
        assertEquals(0, store.writes)
    }

    @Test
    fun `keys no longer declared are released`() = runTest {
        val store = FakeEnvironmentStateStore(mapOf("$common/worktrees/TASK-123" to mapOf("PORT" to 20000, "DEBUG_PORT" to 20001)))

        useCase(declaring("PORT" to "\${port}"), store)(repository, task).getOrThrow()

        assertEquals(mapOf("PORT" to 20000), store.readPorts("$common/worktrees/TASK-123"))
    }

    @Test
    fun `the main worktree keeps its reservations in the common directory`() = runTest {
        val store = FakeEnvironmentStateStore()

        useCase(declaring("PORT" to "\${port}"), store)(repository, repository).getOrThrow()

        assertEquals(mapOf("PORT" to 20000), store.readPorts(common))
    }

    @Test
    fun `an empty declaration reserves and writes nothing`() = runTest {
        val store = FakeEnvironmentStateStore()

        val environment = ResolveWorktreeEnvironmentUseCase(executor, declaring(), store, FakePortProbe())(repository, task).getOrThrow()

        assertEquals(WorktreeEnvironment(), environment)
        assertEquals(0, store.locks)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `reading without reserving never writes`() = runTest {
        val store = FakeEnvironmentStateStore(mapOf("$common/worktrees/TASK-456" to mapOf("PORT" to 20003)))
        val source = declaring("PORT" to "\${port}")

        val unreserved = useCase(source, store)(repository, task, reserve = false).getOrThrow()
        val reserved = useCase(source, store)(repository, other, reserve = false).getOrThrow()

        assertEquals(WorktreeEnvironment(), unreserved)
        assertEquals(mapOf("PORT" to 20003), reserved.ports)
        assertEquals(0, store.writes)
        assertEquals(0, store.locks)
    }

    @Test
    fun `fails when the range is exhausted`() = runTest {
        val probe = FakePortProbe(busy = (ResolveWorktreeEnvironmentUseCase.FIRST_PORT..ResolveWorktreeEnvironmentUseCase.LAST_PORT).toSet())

        val error = useCase(declaring("PORT" to "\${port}"), probe = probe)(repository, task).exceptionOrNull()

        assertIs<WorktreeError.NoFreePort>(error)
    }

    @Test
    fun `two worktrees get different ports`() = runTest {
        val store = FakeEnvironmentStateStore()
        val source = declaring("PORT" to "\${port}")

        val first = useCase(source, store)(repository, task).getOrThrow().ports.getValue("PORT")
        val second = useCase(source, store)(repository, other).getOrThrow().ports.getValue("PORT")

        assertNotEquals(first, second)
    }

    private fun withEmulator(vararg variables: Pair<String, String>) = FakeRepositoryProvisioningSource(
        byRepository = mapOf(
            repository to RepositoryProvisioning(
                providers = RepositoryProviders(emulator = EmulatorProvider()),
                environment = EnvironmentDeclaration(variables.map { (name, value) -> parseEnvironmentVariable(name, value) }),
            ),
        ),
    )

    @Test
    fun `ports are unique across repositories on the machine`() = runTest {
        val store = FakeEnvironmentStateStore(mapOf("/home/dev/other-repo/.git/worktrees/TASK-9" to mapOf("PORT" to 20000)))

        val environment = useCase(declaring("PORT" to "\${port}"), store)(repository, task).getOrThrow()

        assertEquals(mapOf("PORT" to 20001), environment.ports)
    }

    @Test
    fun `the emulator provider adds the worktree's serial`() = runTest {
        val environment = useCase(withEmulator())(repository, task).getOrThrow()

        assertEquals(listOf("ANDROID_SERIAL" to "emulator-5554"), environment.variables)
        assertEquals(mapOf("emulator" to 5554), environment.ports)
    }

    @Test
    fun `emulator ports are even, skip a busy adb port and differ between worktrees`() = runTest {
        val store = FakeEnvironmentStateStore()
        val probe = FakePortProbe(busy = setOf(5555))

        val first = useCase(withEmulator(), store, probe)(repository, task).getOrThrow()
        val second = useCase(withEmulator(), store, probe)(repository, other).getOrThrow()

        assertEquals("emulator-5556", first.variables.toMap()["ANDROID_SERIAL"])
        assertEquals("emulator-5558", second.variables.toMap()["ANDROID_SERIAL"])
    }

    @Test
    fun `fails when every emulator port is taken`() = runTest {
        val probe = FakePortProbe(busy = (5554..5585).toSet())

        assertIs<WorktreeError.NoEmulatorPort>(useCase(withEmulator(), probe = probe)(repository, task).exceptionOrNull())
    }

    @Test
    fun `a declared ANDROID_SERIAL wins over the provider`() = runTest {
        val environment = useCase(withEmulator("ANDROID_SERIAL" to "emulator-5600"))(repository, task).getOrThrow()

        assertEquals(listOf("ANDROID_SERIAL" to "emulator-5600"), environment.variables)
    }

    @Test
    fun `formats for sh, dotenv and json with escaping`() {
        val environment = WorktreeEnvironment(variables = listOf("PORT" to "20000", "GREETING" to "it's \"\$HOME\" now"))

        assertEquals(
            "export PORT='20000'\nexport GREETING='it'\\''s \"\$HOME\" now'\n",
            formatEnvironment(environment, EnvironmentFormat.SH),
        )
        assertEquals(
            "PORT=20000\nGREETING=\"it's \\\"\\\$HOME\\\" now\"\n",
            formatEnvironment(environment, EnvironmentFormat.DOTENV),
        )
        assertEquals(
            "{\"PORT\":\"20000\",\"GREETING\":\"it's \\\"\$HOME\\\" now\"}\n",
            formatEnvironment(environment, EnvironmentFormat.JSON),
        )
    }
}
