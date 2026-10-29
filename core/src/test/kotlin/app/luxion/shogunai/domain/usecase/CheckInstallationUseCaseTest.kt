package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.io.HostPath
import app.luxion.shogunai.domain.io.HostPathSource
import app.luxion.shogunai.domain.model.AgentToolId
import app.luxion.shogunai.domain.model.CheckState
import app.luxion.shogunai.domain.model.ClaudeComputerUse
import app.luxion.shogunai.domain.model.InstallationCheck
import app.luxion.shogunai.domain.model.InstallationFinding
import app.luxion.shogunai.domain.model.InstallationReport
import app.luxion.shogunai.domain.model.LocalFileOrigin
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.UnavailableReason
import app.luxion.shogunai.infrastructure.NioFileManager
import app.luxion.shogunai.infrastructure.ProcessBuilderShellCommandExecutor
import app.luxion.shogunai.infrastructure.RepositoryProvisioningFiles
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CheckInstallationUseCaseTest {

    private val settings = "/home/dev/.claude/settings.json"
    private val available = CommandLineToolStatus(CommandLineToolState.AVAILABLE, "/home/dev/.local/bin/shogun", "/home/dev/.local/bin")
    private val installedHooks = """
        {"hooks": {
          "WorktreeCreate": [{"hooks": [{"type": "command", "command": "shogun hook worktree-create"}]}],
          "WorktreeRemove": [{"hooks": [{"type": "command", "command": "shogun hook worktree-remove"}]}],
          "SessionStart": [{"hooks": [{"type": "command", "command": "shogun hook session-start"}]}],
          "UserPromptSubmit": [{"hooks": [{"type": "command", "command": "shogun hook agent-state"}]}],
          "Notification": [{"hooks": [{"type": "command", "command": "shogun hook agent-state"}]}],
          "Stop": [{"hooks": [{"type": "command", "command": "shogun hook agent-state"}]}],
          "SessionEnd": [{"hooks": [{"type": "command", "command": "shogun hook agent-state"}]}],
          "PreToolUse": [
            {"matcher": "Bash", "hooks": [{"type": "command", "command": "shogun hook desktop-turn", "if": "Bash(*computer *)"}]},
            {"matcher": "${ClaudeComputerUse.hookMatcher}", "hooks": [{"type": "command", "command": "shogun hook desktop-turn"}]}
          ]
        }}
    """.trimIndent()

    private fun check(
        gitOutput: String? = "git version 2.45.1\n",
        executables: Map<String, String> = mapOf("git" to "/usr/bin/git", "claude" to "/home/dev/.local/bin/claude"),
        source: HostPathSource = HostPathSource.LOGIN_SHELL,
        tool: suspend () -> CommandLineToolStatus = { available },
        settingsText: String? = installedHooks,
        herdr: () -> String? = { "/opt/homebrew/bin/herdr" },
        orca: () -> String? = { null },
        projects: List<Project> = emptyList(),
        fileManager: FakeFileManager = FakeFileManager(),
        executor: FakeShellCommandExecutor = FakeShellCommandExecutor { command ->
            if (gitOutput == null) FakeShellCommandExecutor.failure(command) else FakeShellCommandExecutor.success(command, gitOutput)
        },
    ): InstallationReport {
        settingsText?.let { fileManager.writtenTexts[settings] = it }
        return runBlocking {
            CheckInstallationUseCase(
                executor = executor,
                findExecutable = { executables[it] },
                path = { HostPath(listOf("/usr/bin"), source) },
                shell = "/bin/zsh",
                commandLineTool = tool,
                hooks = ClaudeCodeHooks(fileManager),
                hooksSettingsPath = settings,
                herdrLocation = herdr,
                orcaLocation = orca,
                projects = FakeProjectRepository(projects),
                fileManager = fileManager,
                provisioningSource = FakeRepositoryProvisioningSource(),
            )()
        }
    }

    private inline fun <reified T : InstallationFinding> InstallationReport.single(): InstallationCheck =
        checks.single { it.finding is T }

    @Test
    fun `every check runs in order`() {
        val report = check()

        assertEquals(listOf("git", "path", "shogun", "claude-hooks", "claude-code", "herdr", "orca"), report.checks.map { it.finding.id })
        assertFalse(report.failed)
    }

    @Test
    fun `a recent git passes with its version and location`() {
        val git = check().single<InstallationFinding.Git>()

        assertEquals(CheckState.PASSED, git.state)
        assertEquals(InstallationFinding.Git("/usr/bin/git", "2.45.1", "2.29"), git.finding)
    }

    @Test
    fun `git runs from where it was found`() {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, "git version 2.45.1") }

        check(executor = executor)

        assertEquals(listOf("/usr/bin/git", "--version"), executor.executedCommands.single())
    }

    @Test
    fun `an old git fails`() {
        val report = check(gitOutput = "git version 2.25.1\n")

        val git = report.single<InstallationFinding.Git>()
        assertEquals(CheckState.FAILED, git.state)
        assertEquals("2.25.1", (git.finding as InstallationFinding.Git).version)
        assertTrue(report.failed)
    }

    @Test
    fun `a missing git fails`() {
        val git = check(executables = emptyMap()).single<InstallationFinding.Git>()

        assertEquals(CheckState.FAILED, git.state)
        assertNull((git.finding as InstallationFinding.Git).location)
    }

    @Test
    fun `git whose version can't be read fails`() {
        val git = check(gitOutput = null).single<InstallationFinding.Git>()

        assertEquals(CheckState.FAILED, git.state)
        assertNull((git.finding as InstallationFinding.Git).version)
    }

    @Test
    fun `git that can't start fails with the reason`() {
        val executor = FakeShellCommandExecutor { throw CommandExecutionException(it, IOException("permission denied")) }

        val git = check(executor = executor).single<InstallationFinding.Git>()

        assertEquals(CheckState.FAILED, git.state)
        assertEquals("permission denied", (git.finding as InstallationFinding.Git).detail)
    }

    @Test
    fun `platform suffixes are ignored in git's version`() {
        assertEquals("2.39.5", CheckInstallationUseCase.gitVersion("git version 2.39.5 (Apple Git-154)"))
        assertEquals("2.45.1", CheckInstallationUseCase.gitVersion("git version 2.45.1.windows.1"))
        assertEquals("2.29", CheckInstallationUseCase.gitVersion("git version 2.29"))
        assertNull(CheckInstallationUseCase.gitVersion("not git"))
    }

    @Test
    fun `versions compare by number`() {
        assertTrue(CheckInstallationUseCase.isAtLeast("2.29", "2.29"))
        assertTrue(CheckInstallationUseCase.isAtLeast("2.29.0", "2.29"))
        assertTrue(CheckInstallationUseCase.isAtLeast("2.100.1", "2.29"))
        assertTrue(CheckInstallationUseCase.isAtLeast("3.0", "2.29"))
        assertFalse(CheckInstallationUseCase.isAtLeast("2.28.9", "2.29"))
        assertFalse(CheckInstallationUseCase.isAtLeast("1.99", "2.29"))
    }

    @Test
    fun `the PATH source passes unless it fell back`() {
        assertEquals(CheckState.PASSED, check(source = HostPathSource.LOGIN_SHELL).single<InstallationFinding.PathSource>().state)
        assertEquals(CheckState.PASSED, check(source = HostPathSource.PROCESS).single<InstallationFinding.PathSource>().state)
        val fallback = check(source = HostPathSource.FALLBACK).single<InstallationFinding.PathSource>()
        assertEquals(CheckState.WARNING, fallback.state)
        assertEquals(InstallationFinding.PathSource(HostPathSource.FALLBACK, "/bin/zsh"), fallback.finding)
    }

    @Test
    fun `the shogun command's state decides its check`() {
        fun stateFor(status: CommandLineToolStatus) = check(tool = { status }).single<InstallationFinding.ShogunCommand>().state

        assertEquals(CheckState.PASSED, stateFor(available))
        assertEquals(CheckState.WARNING, stateFor(CommandLineToolStatus(CommandLineToolState.INSTALLED_NOT_ON_PATH, "/home/dev/.local/bin/shogun", "/home/dev/.local/bin")))
        assertEquals(CheckState.FAILED, stateFor(CommandLineToolStatus(CommandLineToolState.BROKEN, "/home/dev/.local/bin/shogun", "/home/dev/.local/bin")))
        assertEquals(CheckState.FAILED, stateFor(CommandLineToolStatus(CommandLineToolState.MISSING)))
    }

    @Test
    fun `a shogun not on the PATH names its folder`() {
        val status = CommandLineToolStatus(CommandLineToolState.INSTALLED_NOT_ON_PATH, "/home/dev/.local/bin/shogun", "/home/dev/.local/bin")

        val finding = check(tool = { status }).single<InstallationFinding.ShogunCommand>().finding

        assertEquals(InstallationFinding.ShogunCommand(CommandLineToolState.INSTALLED_NOT_ON_PATH, "/home/dev/.local/bin/shogun", "/home/dev/.local/bin", false), finding)
    }

    @Test
    fun `installed hooks with a usable shogun pass`() {
        assertEquals(CheckState.PASSED, check().single<InstallationFinding.Hooks>().state)
    }

    @Test
    fun `hooks that call a missing shogun fail`() {
        val hooks = check(tool = { CommandLineToolStatus(CommandLineToolState.MISSING) }).single<InstallationFinding.Hooks>()

        assertEquals(CheckState.FAILED, hooks.state)
        assertEquals(CommandLineToolState.MISSING, (hooks.finding as InstallationFinding.Hooks).shogun)
    }

    @Test
    fun `hooks that call a broken launcher fail`() {
        val broken = CommandLineToolStatus(CommandLineToolState.BROKEN, "/home/dev/.local/bin/shogun", "/home/dev/.local/bin")

        assertEquals(CheckState.FAILED, check(tool = { broken }).single<InstallationFinding.Hooks>().state)
    }

    @Test
    fun `hooks with shogun off the PATH warn, unless a restart is all it needs`() {
        val offPath = CommandLineToolStatus(CommandLineToolState.INSTALLED_NOT_ON_PATH, "/home/dev/.local/bin/shogun", "/home/dev/.local/bin")

        assertEquals(CheckState.WARNING, check(tool = { offPath }).single<InstallationFinding.Hooks>().state)
        assertEquals(CheckState.PASSED, check(tool = { offPath.copy(restartNeeded = true) }).single<InstallationFinding.Hooks>().state)
    }

    @Test
    fun `missing hooks warn`() {
        val hooks = check(settingsText = null).single<InstallationFinding.Hooks>()

        assertEquals(CheckState.WARNING, hooks.state)
        assertEquals(HooksInstallState.MISSING, (hooks.finding as InstallationFinding.Hooks).state)
    }

    @Test
    fun `outdated hooks warn with their events`() {
        val outdated = installedHooks.replace("shogun hook session-start", "shogun hook session-begin")

        val hooks = check(settingsText = outdated).single<InstallationFinding.Hooks>()

        assertEquals(CheckState.WARNING, hooks.state)
        assertEquals(listOf("SessionStart"), (hooks.finding as InstallationFinding.Hooks).events)
    }

    @Test
    fun `invalid settings fail naming the file`() {
        val hooks = check(settingsText = "{ not json").single<InstallationFinding.Hooks>()

        assertEquals(CheckState.FAILED, hooks.state)
        val finding = hooks.finding as InstallationFinding.Hooks
        assertNull(finding.state)
        assertEquals(settings, finding.settingsPath)
        assertTrue(!finding.invalidDetail.isNullOrBlank())
    }

    @Test
    fun `agent tools are found or not found, never failed`() {
        val report = check(executables = mapOf("git" to "/usr/bin/git"))

        val tools = report.checks.filter { it.finding is InstallationFinding.AgentTool }
        assertEquals(
            listOf(
                InstallationCheck(CheckState.NOT_FOUND, InstallationFinding.AgentTool(AgentToolId.CLAUDE_CODE, null)),
                InstallationCheck(CheckState.PASSED, InstallationFinding.AgentTool(AgentToolId.HERDR, "/opt/homebrew/bin/herdr")),
                InstallationCheck(CheckState.NOT_FOUND, InstallationFinding.AgentTool(AgentToolId.ORCA, null)),
            ),
            tools,
        )
        assertFalse(report.failed)
    }

    @Test
    fun `a check that throws doesn't hide the others`() {
        val report = check(tool = { throw IllegalStateException("boom") }, herdr = { throw IllegalStateException("boom") })

        assertEquals(7, report.checks.size)
        assertEquals(CheckState.FAILED, report.single<InstallationFinding.ShogunCommand>().state)
        assertEquals(CheckState.NOT_FOUND, report.checks.single { it.finding.id == "herdr" }.state)
        assertEquals(CheckState.PASSED, report.single<InstallationFinding.Git>().state)
    }

    @Test
    fun `no projects means no project checks`() {
        assertTrue(check(projects = emptyList()).checks.none { it.finding is InstallationFinding.ProjectFiles })
    }

    @Test
    fun `a project whose base folder is gone fails`() {
        val project = Project("1", "main-repo", ProjectConfig("/home/dev/projects/main-repo", "/home/dev/worktrees", emptyList()))

        val result = check(projects = listOf(project)).single<InstallationFinding.ProjectFiles>()

        assertEquals(CheckState.FAILED, result.state)
        assertTrue((result.finding as InstallationFinding.ProjectFiles).baseMissing)
        assertEquals("project:main-repo", result.finding.id)
    }

    @Test
    fun `a project with every local file passes, counting them`() {
        val base = "/home/dev/projects/main-repo"
        val fileManager = FakeFileManager(existing = setOf("$base/local.properties", "$base/keys.properties"), directories = setOf(base))
        val project = Project("1", "main-repo", ProjectConfig(base, "/home/dev/worktrees", listOf("local.properties", "keys.properties")))

        val result = check(projects = listOf(project), fileManager = fileManager).single<InstallationFinding.ProjectFiles>()

        assertEquals(CheckState.PASSED, result.state)
        assertEquals(2, (result.finding as InstallationFinding.ProjectFiles).checked)
    }

    @Test
    fun `a project with a missing local file fails, naming it`() {
        val base = "/home/dev/projects/main-repo"
        val fileManager = FakeFileManager(directories = setOf(base))
        val project = Project("1", "main-repo", ProjectConfig(base, "/home/dev/worktrees", listOf("local.properties")))

        val result = check(projects = listOf(project), fileManager = fileManager).single<InstallationFinding.ProjectFiles>()

        assertEquals(CheckState.FAILED, result.state)
        val unavailable = (result.finding as InstallationFinding.ProjectFiles).unavailable.single()
        assertEquals("local.properties", unavailable.path)
        assertEquals(UnavailableReason.Missing, unavailable.reason)
    }

    @Test
    fun `a broken link matched by the repository's patterns fails the project, and nothing is written`() = runTest {
        val root = Files.createTempDirectory("doctor")
        try {
            val repository = root.resolve("main-repo")
            Files.createDirectories(repository)
            val git = ProcessBuilderShellCommandExecutor(path = null)
            git.execute(listOf("git", "init", "-q", "-b", "main"), repository.toString())
            repository.resolve(".gitignore").writeText("*.properties\n")
            repository.resolve(".shogunai.json").writeText("""{ "copy": ["*.properties"] }""")
            Files.createSymbolicLink(repository.resolve("keys.properties"), root.resolve("missing/keys.properties"))
            val before = Files.walk(root).use { paths -> paths.map { it.toString() }.toList().sorted() }
            val project = Project("1", "main-repo", ProjectConfig(repository.toString(), root.resolve("worktrees").toString(), emptyList()))

            val report = CheckInstallationUseCase(
                executor = git,
                findExecutable = { null },
                path = { HostPath(emptyList(), HostPathSource.PROCESS) },
                shell = null,
                commandLineTool = { CommandLineToolStatus(CommandLineToolState.MISSING) },
                hooks = ClaudeCodeHooks(NioFileManager()),
                hooksSettingsPath = root.resolve(".claude/settings.json").toString(),
                herdrLocation = { null },
                orcaLocation = { null },
                projects = FakeProjectRepository(listOf(project)),
                fileManager = NioFileManager(),
                provisioningSource = RepositoryProvisioningFiles(),
            )()

            val finding = assertIs<InstallationFinding.ProjectFiles>(report.checks.last().finding)
            assertEquals(CheckState.FAILED, report.checks.last().state)
            val unavailable = finding.unavailable.single()
            assertEquals("keys.properties", unavailable.path)
            assertEquals(LocalFileOrigin.REPOSITORY, unavailable.origin)
            assertEquals(UnavailableReason.BrokenLink(root.resolve("missing/keys.properties").toString()), unavailable.reason)
            assertEquals(before, Files.walk(root).use { paths -> paths.map { it.toString() }.toList().sorted() })
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a project whose files can't be resolved fails with the reason`() {
        val base = "/home/dev/projects/main-repo"
        val fileManager = FakeFileManager(directories = setOf(base))
        val project = Project("1", "main-repo", ProjectConfig(base, "/home/dev/worktrees", listOf("*.properties")))
        val executor = FakeShellCommandExecutor { command ->
            if (command.first() == "git" && command[1] == "ls-files") FakeShellCommandExecutor.failure(command, stderr = "not a git repository")
            else FakeShellCommandExecutor.success(command, "git version 2.45.1")
        }

        val result = check(projects = listOf(project), fileManager = fileManager, executor = executor).single<InstallationFinding.ProjectFiles>()

        assertEquals(CheckState.FAILED, result.state)
        assertTrue((result.finding as InstallationFinding.ProjectFiles).detail.orEmpty().contains("not a git repository"))
    }

    @Test
    fun `the check writes nothing`() {
        val base = "/home/dev/projects/main-repo"
        val fileManager = FakeFileManager(existing = setOf("$base/local.properties"), directories = setOf(base))
        val project = Project("1", "main-repo", ProjectConfig(base, "/home/dev/worktrees", listOf("local.properties", "keys.properties")))
        val outdated = installedHooks.replace("shogun hook session-start", "shogun hook session-begin")

        check(settingsText = outdated, projects = listOf(project), fileManager = fileManager)

        assertEquals(mapOf(settings to outdated), fileManager.writtenTexts)
        assertTrue(fileManager.copied.isEmpty())
        assertTrue(fileManager.deletedFiles.isEmpty())
    }
}
