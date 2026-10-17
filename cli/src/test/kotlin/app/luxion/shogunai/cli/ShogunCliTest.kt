package app.luxion.shogunai.cli

import app.luxion.shogunai.BuildInfo
import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeHerdrConfigSource
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeKmpTestWiring
import app.luxion.shogunai.domain.FakeGradleDaemonSource.Companion.gradleDaemon
import app.luxion.shogunai.domain.FakeGradleDaemonSource.Companion.kotlinDaemon
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.IosSimulatorProvider
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.XcodeProvider
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShogunCliTest {

    private val base = "/home/dev/projects/main-repo"
    private val worktreesRoot = "/home/dev/projects/worktrees"
    private val merged = "$worktreesRoot/TASK-1"
    private val dirty = "$base/.claude/worktrees/spike"
    private val project = Project(
        id = "p",
        name = "Main repo",
        config = ProjectConfig(baseRepositoryPath = base, worktreesRoot = worktreesRoot, secretFiles = listOf("local.properties")),
    )

    private val porcelain = """
        worktree $base
        HEAD 1111111111111111111111111111111111111111
        branch refs/heads/main

        worktree $merged
        HEAD 2222222222222222222222222222222222222222
        branch refs/heads/feature/TASK-1

        worktree $dirty
        HEAD 3333333333333333333333333333333333333333
        branch refs/heads/worktree-spike
        locked claude session
    """.trimIndent()

    private class Executor(private val respond: (List<String>, String?) -> CommandResult?) : ShellCommandExecutor {
        val calls = mutableListOf<Pair<List<String>, String?>>()

        override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
            calls += command to workingDirectory
            return respond(command, workingDirectory) ?: ok(command)
        }

        fun ran(vararg args: String) = calls.any { it.first == args.toList() }

        companion object {
            fun ok(command: List<String>, stdout: String = "") = CommandResult(command, 0, stdout, "")
            fun fail(command: List<String>, stderr: String = "boom", exitCode: Int = 1) = CommandResult(command, exitCode, "", stderr)
        }
    }

    private fun repositoryExecutor(
        extra: (List<String>, String?) -> CommandResult? = { _, _ -> null },
    ) = Executor { command, directory ->
        extra(command, directory) ?: when {
            command.containsAll(listOf("rev-parse", "--git-common-dir")) -> Executor.ok(command, "$base/.git\n")
            command.take(3) == listOf("git", "worktree", "list") -> Executor.ok(command, porcelain)
            command.take(2) == listOf("git", "symbolic-ref") -> Executor.fail(command)
            command.take(2) == listOf("git", "rev-parse") && command.last() == "refs/heads/main" -> Executor.ok(command)
            command.take(2) == listOf("git", "rev-parse") -> Executor.fail(command)
            "status" in command && directory == dirty -> Executor.ok(command, " M a.kt\n M b.kt\n")
            "rev-list" in command && command.last() == "@{upstream}...HEAD" -> Executor.fail(command)
            "rev-list" in command && directory == merged -> Executor.ok(command, "3\t0")
            "rev-list" in command -> Executor.ok(command, "0\t2")
            else -> null
        }
    }

    private val environmentStore = FakeEnvironmentStateStore()

    private val androidSdk = FakeAndroidSdk()
    private val appleSimulators = FakeAppleSimulators()
    private val kmpTestWiring = FakeKmpTestWiring()
    private val out = StringBuilder()
    private val err = StringBuilder()

    private fun cli(
        executor: ShellCommandExecutor,
        projects: List<Project> = listOf(project),
        fileManager: FakeFileManager = FakeFileManager(existing = listOf(base, "$base/local.properties")),
        provisioning: FakeRepositoryProvisioningSource = FakeRepositoryProvisioningSource(),
        workingDirectory: String = base,
        canonicalPath: (String) -> String = { it.trimEnd('/') },
        daemons: FakeGradleDaemonSource = FakeGradleDaemonSource(),
        derivedDataSource: FakeDerivedDataSource = FakeDerivedDataSource(),
        herdrWorktreesRoot: String = "/home/dev/.herdr/worktrees",
    ) = ShogunCli(
        executor = executor,
        fileManager = fileManager,
        projectRepository = FakeProjectRepository(projects),
        provisioningSource = provisioning,
        daemonSource = daemons,
        gradleUserHome = gradleUserHome,
        environmentStateStore = environmentStore,
        portProbe = FakePortProbe(),
        androidSdk = androidSdk,
        appleSimulators = appleSimulators,
        kmpTestWiring = kmpTestWiring,
        derivedDataSource = derivedDataSource,
        herdrConfig = FakeHerdrConfigSource(herdrWorktreesRoot),
        workingDirectory = workingDirectory,
        out = out,
        err = err,
        canonicalPath = canonicalPath,
        now = { now },
    )

    private val gradleUserHome = "/home/dev/.gradle"
    private val now = Instant.parse("2026-10-16T11:00:00Z")
    private val idleSinceTenMinutes = DaemonState.Idle(Instant.parse("2026-10-16T10:50:00Z"))

    @Test
    fun `gradle lists daemons with their state, worktree and total memory`() {
        val daemons = FakeGradleDaemonSource(
            listOf(
                gradleDaemon(10, idleSinceTenMinutes, lastBuildDirectory = merged, memoryBytes = 1_073_741_824),
                gradleDaemon(11, DaemonState.Busy, lastBuildDirectory = "/home/dev/projects/gone", memoryBytes = 1_073_741_824),
                kotlinDaemon(12, memoryBytes = 536_870_912),
            ),
        )
        val fileManager = FakeFileManager(existing = listOf(base, merged))

        val exitCode = cli(repositoryExecutor(), fileManager = fileManager, daemons = daemons).run(listOf("gradle"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(
            """
            10  gradle 9.6.1  memory=1.0 GB  state=idle  idle-for=10m  worktree=$merged
            11  gradle 9.6.1  memory=1.0 GB  state=busy  worktree=removed
            12  kotlin  memory=512.0 MB
            total  memory=2.5 GB  daemons=3

            """.trimIndent(),
            out.toString(),
        )
    }

    @Test
    fun `gradle without daemons says so`() {
        val exitCode = cli(repositoryExecutor()).run(listOf("gradle"))

        assertEquals(0, exitCode)
        assertEquals("No Gradle or Kotlin daemons running.\n", out.toString())
    }

    @Test
    fun `gradle works outside a repository without a worktree column`() {
        val executor = Executor { command, _ ->
            if (command.containsAll(listOf("rev-parse", "--git-common-dir"))) Executor.fail(command, "not a git repository") else null
        }
        val daemons = FakeGradleDaemonSource(listOf(gradleDaemon(10, DaemonState.Busy, lastBuildDirectory = merged)))

        val exitCode = cli(executor, daemons = daemons, workingDirectory = "/tmp").run(listOf("gradle"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.startsWith("10  gradle 9.6.1  memory=953.7 MB  state=busy\n"), out.toString())
    }

    @Test
    fun `gradle prints the build cache notice for a gradle repository without it`() {
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(gradle = GradleProvider()))),
        )

        cli(repositoryExecutor(), provisioning = provisioning).run(listOf("gradle"))

        assertTrue(out.endsWith(ShogunCli.BUILD_CACHE_NOTICE + "\n"), out.toString())
    }

    @Test
    fun `gradle omits the notice when the cache is on or the provider isn't declared`() {
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(gradle = GradleProvider()))),
        )
        val cached = FakeFileManager(
            existing = listOf(base),
            properties = mapOf("$base/gradle.properties" to mapOf("org.gradle.caching" to "true")),
        )

        cli(repositoryExecutor(), provisioning = provisioning, fileManager = cached).run(listOf("gradle"))
        cli(repositoryExecutor()).run(listOf("gradle"))

        assertFalse(out.contains(ShogunCli.BUILD_CACHE_NOTICE), out.toString())
    }

    @Test
    fun `gradle stop stops idle daemons and keeps busy ones`() {
        val daemons = FakeGradleDaemonSource(
            listOf(
                gradleDaemon(10, idleSinceTenMinutes),
                gradleDaemon(11, idleSinceTenMinutes),
                gradleDaemon(12, DaemonState.Busy),
            ),
        )
        val executor = repositoryExecutor()

        val exitCode = cli(executor, daemons = daemons).run(listOf("gradle", "stop"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("stopped 10  gradle 9.6.1\nstopped 11  gradle 9.6.1\nkept 12  gradle  (busy)\n", out.toString())
        assertTrue(executor.ran("kill", "10"))
        assertFalse(executor.ran("kill", "12"))
    }

    @Test
    fun `gradle stop of a busy daemon keeps it and exits with 0`() {
        val daemons = FakeGradleDaemonSource(listOf(gradleDaemon(4242, DaemonState.Busy)))

        val exitCode = cli(repositoryExecutor(), daemons = daemons).run(listOf("gradle", "stop", "4242"))

        assertEquals(0, exitCode)
        assertEquals("kept 4242  gradle  (busy)\n", out.toString())
    }

    @Test
    fun `gradle stop of a process that isn't a daemon exits with 1`() {
        val exitCode = cli(repositoryExecutor()).run(listOf("gradle", "stop", "99999"))

        assertEquals(1, exitCode)
        assertEquals("shogun: 99999 isn't a Gradle or Kotlin daemon\n", err.toString())
    }

    @Test
    fun `clean stops the idle daemons of removed worktrees`() {
        val daemons = FakeGradleDaemonSource(
            listOf(gradleDaemon(10, idleSinceTenMinutes, lastBuildDirectory = merged), gradleDaemon(11, idleSinceTenMinutes, lastBuildDirectory = base)),
        )
        val executor = repositoryExecutor()

        val exitCode = cli(executor, daemons = daemons).run(listOf("clean", "--yes", "--stop-gradle-daemons"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.contains("removed $merged\n"), out.toString())
        assertTrue(out.contains("stopped 10  gradle 9.6.1\n"), out.toString())
        assertTrue(executor.ran("kill", "10"))
        assertFalse(executor.ran("kill", "11"))
    }

    @Test
    fun `clean without the option stops no daemon`() {
        val daemons = FakeGradleDaemonSource(listOf(gradleDaemon(10, idleSinceTenMinutes, lastBuildDirectory = merged)))
        val executor = repositoryExecutor()

        cli(executor, daemons = daemons).run(listOf("clean", "--yes"))

        assertFalse(executor.calls.any { it.first.first() == "kill" })
        assertEquals(0, daemons.listings)
    }

    @Test
    fun `clean with the option but without yes lists the daemons it would stop`() {
        val daemons = FakeGradleDaemonSource(listOf(gradleDaemon(10, idleSinceTenMinutes, lastBuildDirectory = merged)))
        val executor = repositoryExecutor()

        val exitCode = cli(executor, daemons = daemons).run(listOf("clean", "--stop-gradle-daemons"))

        assertEquals(0, exitCode)
        assertEquals(
            "would remove $merged\nwould stop Gradle daemon 10\nRun 'shogun clean --yes' to remove it.\n",
            out.toString(),
        )
        assertFalse(executor.calls.any { it.first.first() == "kill" })
    }

    @Test
    fun `new prints only the worktree path and uses the project's folder`() {
        val executor = repositoryExecutor()

        val exitCode = cli(executor).run(listOf("new", "TASK-123", "--fix"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("$worktreesRoot/TASK-123\n", out.toString())
        assertTrue(executor.ran("git", "worktree", "add", "$worktreesRoot/TASK-123", "-b", "fix/TASK-123"))
    }

    @Test
    fun `new from an existing branch`() {
        val executor = repositoryExecutor { command, _ ->
            if (command.take(3) == listOf("git", "branch", "--list")) Executor.ok(command, "  hotfix/login\n") else null
        }

        val exitCode = cli(executor).run(listOf("new", "--branch", "hotfix/login"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("$worktreesRoot/hotfix-login\n", out.toString())
    }

    @Test
    fun `new without a project needs a worktrees root`() {
        val executor = repositoryExecutor()

        val exitCode = cli(executor, projects = emptyList()).run(listOf("new", "TASK-123"))

        assertEquals(2, exitCode)
        assertTrue(err.contains("--worktrees-root"))
        assertFalse(executor.calls.any { "add" in it.first })
    }

    @Test
    fun `a relative worktrees root is resolved against the current directory`() {
        val executor = repositoryExecutor()

        val exitCode = cli(executor, projects = emptyList()).run(listOf("new", "TASK-123", "--worktrees-root", "../wt"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("/home/dev/projects/wt/TASK-123\n", out.toString())
    }

    @Test
    fun `the project is found by canonical path`() {
        val aliased = project.copy(config = project.config.copy(baseRepositoryPath = "/link/main-repo"))
        val executor = repositoryExecutor()
        val canonical = { path: String -> if (path == "/link/main-repo") base else path }

        val fileManager = FakeFileManager(existing = listOf("/link/main-repo", "/link/main-repo/local.properties"))

        val exitCode = cli(executor, projects = listOf(aliased), fileManager = fileManager, canonicalPath = canonical)
            .run(listOf("new", "TASK-9"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("$worktreesRoot/TASK-9\n", out.toString())
    }

    @Test
    fun `a failed creation reports the error and exits with 1`() {
        val executor = repositoryExecutor()
        val fileManager = FakeFileManager(existing = listOf(base, "$base/local.properties", "$worktreesRoot/TASK-1"))

        val exitCode = cli(executor, fileManager = fileManager).run(listOf("new", "TASK-1"))

        assertEquals(1, exitCode)
        assertTrue(err.startsWith("shogun: Worktree directory already exists"), err.toString())
        assertEquals("", out.toString())
    }

    @Test
    fun `outside a repository every command fails with 1`() {
        val executor = Executor { command, _ -> Executor.fail(command, "fatal: not a git repository", 128) }

        val exitCode = cli(executor, workingDirectory = "/home/dev").run(listOf("status"))

        assertEquals(1, exitCode)
        assertEquals("shogun: No Git repository found at /home/dev\n", err.toString())
    }

    @Test
    fun `provision copies into the worktree containing the given path and lists the files`() {
        val executor = repositoryExecutor { command, _ ->
            if ("ls-files" in command) Executor.ok(command, ".env\u0000") else null
        }
        val provisioning = FakeRepositoryProvisioningSource(byRepository = mapOf(base to RepositoryProvisioning(copyPatterns = listOf(".env"))))

        val exitCode = cli(executor, provisioning = provisioning, workingDirectory = worktreesRoot).run(listOf("provision", "TASK-1"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("local.properties\n.env\n", out.toString())
    }

    @Test
    fun `provision refuses a directory that is not a linked worktree`() {
        val exitCode = cli(repositoryExecutor()).run(listOf("provision"))

        assertEquals(1, exitCode)
        assertTrue(err.contains("is not a linked worktree"), err.toString())
    }

    @Test
    fun `status prints one line per worktree`() {
        val exitCode = cli(repositoryExecutor()).run(listOf("status"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(
            listOf(
                "$base  main  main  changes=0",
                "$merged  feature/TASK-1  origin=shogunai  changes=0  merged",
                "$dirty  worktree-spike  origin=claude-code  locked  changes=2",
            ),
            out.lines().filter { it.isNotEmpty() },
        )
    }

    @Test
    fun `status shows worktrees inside Herdr's folder as created by Herdr`() {
        val exitCode = cli(repositoryExecutor(), herdrWorktreesRoot = worktreesRoot).run(listOf("status"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.lines().contains("$merged  feature/TASK-1  origin=herdr  changes=0  merged"), out.toString())
    }

    @Test
    fun `clean without yes only lists the candidates`() {
        val executor = repositoryExecutor()

        val exitCode = cli(executor).run(listOf("clean"))

        assertEquals(0, exitCode)
        assertEquals("would remove $merged\nRun 'shogun clean --yes' to remove it.\n", out.toString())
        assertFalse(executor.calls.any { it.first.take(3) == listOf("git", "worktree", "remove") })
    }

    @Test
    fun `clean stops the emulators of removed worktrees`() {
        val adminOfMerged = "$base/.git/worktrees/TASK-1"
        environmentStore.portsByAdminDirectory[adminOfMerged] = mapOf("emulator" to 5556)
        environmentStore.portsByAdminDirectory["$base/.git/worktrees/spike"] = mapOf("emulator" to 5558)
        androidSdk.states["emulator-5556"] = EmulatorState.RUNNING
        androidSdk.states["emulator-5558"] = EmulatorState.RUNNING
        val executor = repositoryExecutor { command, directory ->
            if ("--git-dir" in command) {
                val admin = when (directory) {
                    merged -> adminOfMerged
                    dirty -> "$base/.git/worktrees/spike"
                    else -> "$base/.git"
                }
                Executor.ok(command, "$admin\n$base/.git\n")
            } else {
                null
            }
        }
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(emulator = EmulatorProvider()))),
        )

        cli(executor, provisioning = provisioning).run(listOf("clean", "--stop-emulators"))
        assertTrue(out.contains("would stop emulator emulator-5556\n"), out.toString())
        assertTrue(androidSdk.stopped.isEmpty())
        out.clear()

        val exitCode = cli(executor, provisioning = provisioning).run(listOf("clean", "--yes", "--stop-emulators"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.contains("stopped emulator emulator-5556\n"), out.toString())
        assertEquals(listOf("emulator-5556"), androidSdk.stopped)
    }

    @Test
    fun `clean deletes the simulators of removed worktrees and orphans`() {
        environmentStore.resourcesByAdminDirectory["$base/.git/worktrees/TASK-1"] = mapOf("ios-simulator" to "SIM-MERGED")
        environmentStore.resourcesByAdminDirectory["$base/.git/worktrees/spike"] = mapOf("ios-simulator" to "SIM-SPIKE")
        appleSimulators.addExisting("SIM-MERGED", "ShogunAi · main-repo · TASK-1")
        appleSimulators.addExisting("SIM-SPIKE", "ShogunAi · main-repo · spike")
        appleSimulators.addExisting("SIM-ORPHAN", "ShogunAi · main-repo · TASK-0")
        appleSimulators.addExisting("SIM-USER", "iPhone 16")
        val executor = repositoryExecutor { command, directory ->
            if ("--git-dir" in command) {
                val admin = when (directory) {
                    merged -> "$base/.git/worktrees/TASK-1"
                    dirty -> "$base/.git/worktrees/spike"
                    else -> "$base/.git"
                }
                Executor.ok(command, "$admin\n$base/.git\n")
            } else {
                null
            }
        }
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(iosSimulator = IosSimulatorProvider()))),
        )

        cli(executor, provisioning = provisioning).run(listOf("clean", "--delete-simulators"))
        assertTrue(out.contains("would delete simulator ShogunAi · main-repo · TASK-1 (SIM-MERGED)"), out.toString())
        assertTrue(out.contains("would delete orphaned simulator ShogunAi · main-repo · TASK-0 (SIM-ORPHAN)"), out.toString())
        assertTrue(appleSimulators.deleted.isEmpty())
        out.clear()

        val exitCode = cli(executor, provisioning = provisioning).run(listOf("clean", "--yes", "--delete-simulators"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(listOf("SIM-MERGED", "SIM-ORPHAN"), appleSimulators.deleted)
        assertTrue(out.contains("deleted simulator SIM-ORPHAN"), out.toString())
    }

    @Test
    fun `clean with yes removes and deletes merged branches`() {
        val executor = repositoryExecutor()

        val exitCode = cli(executor).run(listOf("clean", "--yes", "--delete-branches"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(executor.ran("git", "worktree", "remove", merged))
        assertTrue(out.startsWith("removed $merged\n"), out.toString())
    }

    @Test
    fun `clean reports failures and exits with 1`() {
        val executor = repositoryExecutor { command, _ ->
            if (command.take(3) == listOf("git", "worktree", "remove")) Executor.fail(command, "is dirty") else null
        }

        val exitCode = cli(executor).run(listOf("clean", "--yes"))

        assertEquals(1, exitCode)
        assertTrue(err.contains("couldn't remove $merged"), err.toString())
    }

    @Test
    fun `clean with nothing to remove`() {
        val executor = repositoryExecutor { command, directory ->
            if ("rev-list" in command && directory == merged) Executor.ok(command, "0\t1") else null
        }

        val exitCode = cli(executor).run(listOf("clean"))

        assertEquals(0, exitCode)
        assertEquals("Nothing to clean up.\n", out.toString())
    }

    @Test
    fun `help and version`() {
        assertEquals(0, cli(repositoryExecutor()).run(listOf("help")))
        assertTrue(out.startsWith("Usage: shogun <command>"))
        out.clear()

        assertEquals(0, cli(repositoryExecutor()).run(listOf("--version")))
        assertEquals("shogun ${BuildInfo.VERSION}\n", out.toString())
    }

    @Test
    fun `usage errors exit with 2 and point to help`() {
        val exitCode = cli(repositoryExecutor()).run(listOf("frobnicate"))

        assertEquals(2, exitCode)
        assertEquals("shogun: unknown command 'frobnicate'\nRun 'shogun help' for usage.\n", err.toString())
    }

    @Test
    fun `xcode lists derived data for worktrees and orphans summary`() {
        val firstProject = "$base/iosApp/iosApp.xcodeproj"
        val secondProject = "$merged/iosApp/iosApp.xcodeproj"
        val orphanProject = "/home/dev/projects/gone/Gone.xcodeproj"
        val derivedData = FakeDerivedDataSource(
            existing = listOf(
                FakeDerivedDataSource.derivedData("Main-hash", firstProject, sizeBytes = 1_073_741_824L),
                FakeDerivedDataSource.derivedData("Merged-hash", secondProject, sizeBytes = 536_870_912L),
                FakeDerivedDataSource.derivedData("Gone-hash", orphanProject, sizeBytes = 2_147_483_648L),
            ),
            existingProjects = setOf(firstProject, secondProject),
        )
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(xcode = XcodeProvider()))),
        )
        val fileManager = FakeFileManager(existing = listOf(base, merged))

        val exitCode = cli(repositoryExecutor(), fileManager = fileManager, provisioning = provisioning, derivedDataSource = derivedData)
            .run(listOf("xcode"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(
            """
            $base  1.0 GB
            $merged  512.0 MB
            orphans: 1 (2.0 GB)
            """.trimIndent() + "\n",
            out.toString(),
        )
    }

    @Test
    fun `xcode reports provider is not active when not declared`() {
        val exitCode = cli(repositoryExecutor()).run(listOf("xcode"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("The Xcode provider isn't active.\n", out.toString())
    }

    @Test
    fun `xcode lists compilation cache size when declared`() {
        val derivedData = FakeDerivedDataSource(existing = emptyList(), cacheBytes = 1_073_741_824L)
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(xcode = XcodeProvider(compilationCache = true)))),
        )

        val exitCode = cli(repositoryExecutor(), provisioning = provisioning, derivedDataSource = derivedData)
            .run(listOf("xcode"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(
            """
            orphans: 0 (0 B)
            compilation cache: 1.0 GB
            """.trimIndent() + "\n",
            out.toString(),
        )
    }

    @Test
    fun `xcode lists folders on unavailable volumes`() {
        val folder = FakeDerivedDataSource.derivedData("Vol-hash", "/Volumes/Work/App.xcodeproj")
        val derivedData = FakeDerivedDataSource(
            existing = listOf(folder),
            unavailableVolumes = setOf("/Volumes/Work"),
        )
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(xcode = XcodeProvider()))),
        )

        val exitCode = cli(repositoryExecutor(), provisioning = provisioning, derivedDataSource = derivedData)
            .run(listOf("xcode"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(
            """
            orphans: 0 (0 B)
            folders on unavailable volumes: 1
            """.trimIndent() + "\n",
            out.toString(),
        )
    }

    @Test
    fun `xcode clean deletes orphaned derived data folders and reports space freed`() {
        val first = FakeDerivedDataSource.derivedData("Gone1-hash", "/home/dev/projects/gone1/App.xcodeproj", 1_073_741_824L)
        val second = FakeDerivedDataSource.derivedData("Gone2-hash", "/home/dev/projects/gone2/App.xcodeproj", 1_073_741_824L)
        val derivedData = FakeDerivedDataSource(existing = listOf(first, second))
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(xcode = XcodeProvider()))),
        )

        val exitCode = cli(repositoryExecutor(), provisioning = provisioning, derivedDataSource = derivedData)
            .run(listOf("xcode", "clean"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(listOf(first.path, second.path), derivedData.deleted)
        assertEquals(
            """
            deleted ${first.path}
            deleted ${second.path}
            freed 2.0 GB
            """.trimIndent() + "\n",
            out.toString(),
        )
    }

    @Test
    fun `xcode clean reports nothing to delete when no folder is orphaned`() {
        val derivedData = FakeDerivedDataSource(existing = emptyList())
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(xcode = XcodeProvider()))),
        )

        val exitCode = cli(repositoryExecutor(), provisioning = provisioning, derivedDataSource = derivedData)
            .run(listOf("xcode", "clean"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("Nothing to delete.\n", out.toString())
        assertTrue(derivedData.deleted.isEmpty())
    }

    @Test
    fun `xcode clean reports deletion failures on stderr and exits with 1`() {
        val orphan = FakeDerivedDataSource.derivedData("Stuck-hash", "/home/dev/projects/gone/App.xcodeproj")
        val derivedData = FakeDerivedDataSource(existing = listOf(orphan), failDeletionFor = setOf(orphan.path))
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(xcode = XcodeProvider()))),
        )

        val exitCode = cli(repositoryExecutor(), provisioning = provisioning, derivedDataSource = derivedData)
            .run(listOf("xcode", "clean"))

        assertEquals(1, exitCode)
        assertEquals("shogun: couldn't delete ${orphan.path}: can't delete ${orphan.path}\n", err.toString())
    }

    @Test
    fun `clean with delete derived data removes worktrees and deletes their derived data`() {
        val mergedProject = "$merged/iosApp/iosApp.xcodeproj"
        val folder = FakeDerivedDataSource.derivedData("Merged-hash", mergedProject)
        val derivedData = FakeDerivedDataSource(
            existing = listOf(folder),
            existingProjects = setOf(mergedProject),
        )
        val executor = repositoryExecutor { command, _ ->
            if (command.take(3) == listOf("git", "worktree", "remove")) {
                derivedData.existingProjects -= mergedProject
                Executor.ok(command)
            } else {
                null
            }
        }
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(xcode = XcodeProvider()))),
        )

        val exitCode = cli(executor, provisioning = provisioning, derivedDataSource = derivedData)
            .run(listOf("clean", "--yes", "--delete-derived-data"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.contains("removed $merged\n"), out.toString())
        assertTrue(out.contains("deleted DerivedData ${folder.path}\n"), out.toString())
        assertEquals(listOf(folder.path), derivedData.deleted)
    }

    @Test
    fun `clean without delete derived data touches no derived data`() {
        val mergedProject = "$merged/iosApp/iosApp.xcodeproj"
        val folder = FakeDerivedDataSource.derivedData("Merged-hash", mergedProject)
        val derivedData = FakeDerivedDataSource(existing = listOf(folder))
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(xcode = XcodeProvider()))),
        )

        cli(repositoryExecutor(), provisioning = provisioning, derivedDataSource = derivedData)
            .run(listOf("clean", "--yes"))

        assertTrue(derivedData.deleted.isEmpty())
    }

    @Test
    fun `clean with delete derived data without yes lists orphans that would be deleted`() {
        val orphan = FakeDerivedDataSource.derivedData("Gone-hash", "/home/dev/projects/gone/App.xcodeproj")
        val derivedData = FakeDerivedDataSource(existing = listOf(orphan))
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(xcode = XcodeProvider()))),
        )

        val exitCode = cli(repositoryExecutor(), provisioning = provisioning, derivedDataSource = derivedData)
            .run(listOf("clean", "--delete-derived-data"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.contains("would delete orphaned DerivedData ${orphan.path}\n"), out.toString())
        assertTrue(derivedData.deleted.isEmpty())
    }
}
