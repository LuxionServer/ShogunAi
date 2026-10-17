package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.SimulatorSettings
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.WorktreeEnvironment
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.XcodeProvider
import app.luxion.shogunai.domain.model.parseEnvironmentVariable
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private fun withGradle(settings: GradleProvider, vararg variables: Pair<String, String>) = FakeRepositoryProvisioningSource(
        byRepository = mapOf(
            repository to RepositoryProvisioning(
                providers = RepositoryProviders(gradle = settings),
                environment = EnvironmentDeclaration(variables.map { (name, value) -> parseEnvironmentVariable(name, value) }),
            ),
        ),
    )

    private val gradleContributions = MachineProviderContributions(
        FakeAppleSimulators(),
        { SimulatorSettings() },
        gradleJvmArgsFor = { "-Xmx6g -Dfile.encoding=UTF-8" },
    )

    private fun withGradleContributions(source: FakeRepositoryProvisioningSource) =
        ResolveWorktreeEnvironmentUseCase(gitExecutor(), source, FakeEnvironmentStateStore(), FakePortProbe(), gradleContributions)

    @Test
    fun `the gradle provider adds the same GRADLE_OPTS to every worktree`() = runTest {
        val resolve = withGradleContributions(withGradle(GradleProvider(daemonMaxHeap = "2g", idleTimeoutMinutes = 30)))

        val expected = listOf(
            "GRADLE_OPTS" to "-Dorg.gradle.jvmargs=\"-Xmx2g -Dfile.encoding=UTF-8\" -Dorg.gradle.daemon.idletimeout=1800000",
        )
        assertEquals(expected, resolve(repository, repository).getOrThrow().variables)
        assertEquals(expected, resolve(repository, task).getOrThrow().variables)
        assertEquals(expected, resolve(repository, other).getOrThrow().variables)
    }

    @Test
    fun `the gradle provider without settings adds nothing`() = runTest {
        assertEquals(emptyList(), withGradleContributions(withGradle(GradleProvider()))(repository, task).getOrThrow().variables)
    }

    @Test
    fun `a declared GRADLE_OPTS wins over the gradle provider`() = runTest {
        val source = withGradle(GradleProvider(idleTimeoutMinutes = 30), "GRADLE_OPTS" to "-Dmine=1")

        assertEquals(listOf("GRADLE_OPTS" to "-Dmine=1"), withGradleContributions(source)(repository, task).getOrThrow().variables)
    }

    private fun withXcode(compilationCache: Boolean = true, vararg variables: Pair<String, String>) =
        FakeRepositoryProvisioningSource(
            byRepository = mapOf(
                repository to RepositoryProvisioning(
                    providers = RepositoryProviders(xcode = XcodeProvider(compilationCache = compilationCache)),
                    environment = EnvironmentDeclaration(variables.map { (name, value) -> parseEnvironmentVariable(name, value) }),
                ),
            ),
        )

    private fun xcodeContributions(
        fileManager: FileManager = FakeFileManager(),
        userHome: String = "/home/dev",
        isMac: Boolean = true,
    ) = MachineProviderContributions(
        FakeAppleSimulators(),
        { SimulatorSettings() },
        fileManager = fileManager,
        userHome = userHome,
        isMac = isMac,
    )

    private fun withXcodeContributions(
        source: FakeRepositoryProvisioningSource,
        store: FakeEnvironmentStateStore = FakeEnvironmentStateStore(),
        fileManager: FileManager = FakeFileManager(),
        userHome: String = "/home/dev",
        isMac: Boolean = true,
    ) = ResolveWorktreeEnvironmentUseCase(
        gitExecutor(),
        source,
        store,
        FakePortProbe(),
        xcodeContributions(fileManager, userHome, isMac),
    )

    @Test
    fun `the xcode provider with compilation cache sets the variable and writes the xcconfig file`() = runTest {
        val fileManager = FakeFileManager()
        val store = FakeEnvironmentStateStore()
        val resolve = withXcodeContributions(withXcode(compilationCache = true), store = store, fileManager = fileManager)

        val environment = resolve(repository, task).getOrThrow()

        val expectedPath = "/home/dev/.shogunai/xcode/compilation-cache.xcconfig"
        assertEquals(listOf("XCODE_XCCONFIG_FILE" to expectedPath), environment.variables)
        assertEquals(mapOf("xcode-compilation-cache" to expectedPath), environment.resources)
        assertEquals(XCODE_COMPILATION_CACHE_CONTENT, fileManager.writtenTexts[expectedPath])
        assertEquals(mapOf("xcode-compilation-cache" to expectedPath), store.readResources("$common/worktrees/TASK-123"))
    }

    @Test
    fun `the xcode provider without compilation cache contributes nothing`() = runTest {
        val fileManager = FakeFileManager()
        val resolve = withXcodeContributions(withXcode(compilationCache = false), fileManager = fileManager)

        val environment = resolve(repository, task).getOrThrow()

        assertEquals(emptyList(), environment.variables)
        assertTrue(fileManager.writtenTexts.isEmpty())
    }

    @Test
    fun `the xcode compilation cache contributes nothing when not on macOS`() = runTest {
        val fileManager = FakeFileManager()
        val resolve = withXcodeContributions(withXcode(compilationCache = true), fileManager = fileManager, isMac = false)

        val environment = resolve(repository, task).getOrThrow()

        assertEquals(emptyList(), environment.variables)
        assertTrue(fileManager.writtenTexts.isEmpty())
    }

    @Test
    fun `resolving xcode compilation cache without reserving never writes the file`() = runTest {
        val fileManager = FakeFileManager()
        val resolve = withXcodeContributions(withXcode(compilationCache = true), fileManager = fileManager)

        val environment = resolve(repository, task, reserve = false).getOrThrow()

        assertEquals(WorktreeEnvironment(), environment)
        assertTrue(fileManager.writtenTexts.isEmpty())
    }

    @Test
    fun `the xcconfig file is re-created when deleted`() = runTest {
        val fileManager = FakeFileManager()
        val store = FakeEnvironmentStateStore()
        val resolve = withXcodeContributions(withXcode(compilationCache = true), store = store, fileManager = fileManager)
        val expectedPath = "/home/dev/.shogunai/xcode/compilation-cache.xcconfig"

        resolve(repository, task).getOrThrow()
        assertEquals(XCODE_COMPILATION_CACHE_CONTENT, fileManager.writtenTexts[expectedPath])

        fileManager.delete(expectedPath)
        assertFalse(fileManager.exists(expectedPath))

        resolve(repository, task).getOrThrow()
        assertTrue(fileManager.exists(expectedPath))
        assertEquals(XCODE_COMPILATION_CACHE_CONTENT, fileManager.writtenTexts[expectedPath])
    }

    @Test
    fun `a declared XCODE_XCCONFIG_FILE wins over the xcode provider`() = runTest {
        val fileManager = FakeFileManager()
        val source = withXcode(compilationCache = true, "XCODE_XCCONFIG_FILE" to "/custom/path.xcconfig")
        val resolve = withXcodeContributions(source, fileManager = fileManager)

        val environment = resolve(repository, task).getOrThrow()

        assertEquals(listOf("XCODE_XCCONFIG_FILE" to "/custom/path.xcconfig"), environment.variables)
    }

    private class RecordingSimulators {
        val existing = mutableSetOf<String>()
        val created = mutableListOf<ResourceContext>()
        val discarded = mutableListOf<String>()
        var beforeCreate: () -> Unit = {}

        val contributions = ProviderContributions { _, _ ->
            listOf(
                EnvironmentContribution(
                    resources = listOf(
                        ResourceRequest(
                            key = "ios-simulator",
                            exists = { it in existing },
                            create = { context ->
                                beforeCreate()
                                created += context
                                "UDID-${created.size}".also { existing += it }
                            },
                            discard = { discarded += it; existing -= it },
                        ),
                    ),
                    variables = { listOf("IOS_SIMULATOR_UDID" to it.resources.getValue("ios-simulator")) },
                ),
            )
        }
    }

    private fun withSimulators(simulators: RecordingSimulators, store: FakeEnvironmentStateStore, source: FakeRepositoryProvisioningSource = declaring()) =
        ResolveWorktreeEnvironmentUseCase(gitExecutor(), source, store, FakePortProbe(), simulators.contributions)

    @Test
    fun `a provider resource is created once with the repository and worktree names`() = runTest {
        val simulators = RecordingSimulators()
        val store = FakeEnvironmentStateStore()

        val first = withSimulators(simulators, store)(repository, task).getOrThrow()
        val second = withSimulators(simulators, store)(repository, task).getOrThrow()

        assertEquals(listOf("IOS_SIMULATOR_UDID" to "UDID-1"), first.variables)
        assertEquals(first, second)
        assertEquals(listOf(ResourceContext("main-repo", "TASK-123")), simulators.created)
    }

    @Test
    fun `reading without reserving never creates a resource`() = runTest {
        val simulators = RecordingSimulators()

        val environment = withSimulators(simulators, FakeEnvironmentStateStore())(repository, task, reserve = false).getOrThrow()

        assertEquals(WorktreeEnvironment(), environment)
        assertTrue(simulators.created.isEmpty())
    }

    @Test
    fun `a resource that no longer exists is created again`() = runTest {
        val simulators = RecordingSimulators()
        val store = FakeEnvironmentStateStore()
        withSimulators(simulators, store)(repository, task).getOrThrow()
        simulators.existing.clear()

        val environment = withSimulators(simulators, store)(repository, task).getOrThrow()

        assertEquals(mapOf("ios-simulator" to "UDID-2"), environment.resources)
        assertEquals(mapOf("ios-simulator" to "UDID-2"), store.readResources("$common/worktrees/TASK-123"))
    }

    @Test
    fun `the loser of a concurrent creation discards its own resource`() = runTest {
        val simulators = RecordingSimulators()
        val store = FakeEnvironmentStateStore()
        simulators.beforeCreate = {
            store.writeResources("$common/worktrees/TASK-123", mapOf("ios-simulator" to "UDID-OTHER"))
            simulators.existing += "UDID-OTHER"
        }

        val environment = withSimulators(simulators, store)(repository, task).getOrThrow()

        assertEquals(mapOf("ios-simulator" to "UDID-OTHER"), environment.resources)
        assertEquals(listOf("UDID-1"), simulators.discarded)
    }

    @Test
    fun `a declared variable wins over a provider resource variable`() = runTest {
        val simulators = RecordingSimulators()

        val environment = withSimulators(simulators, FakeEnvironmentStateStore(), declaring("IOS_SIMULATOR_UDID" to "MINE"))(repository, task)
            .getOrThrow()

        assertEquals(listOf("IOS_SIMULATOR_UDID" to "MINE"), environment.variables)
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
