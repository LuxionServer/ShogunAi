package app.luxion.shogunai.ui.i18n

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.InstallChannel
import app.luxion.shogunai.domain.model.PoolHolder
import app.luxion.shogunai.domain.model.PoolHolderState
import app.luxion.shogunai.domain.model.RepositoryConfigIssue
import app.luxion.shogunai.domain.model.RepositoryConfigRules
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.ui.worktree.StateGroupKind
import app.luxion.shogunai.ui.worktree.criterion
import app.luxion.shogunai.ui.worktree.hint
import java.io.IOException
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StringsTest {

    private val poolHolders = listOf(
        PoolHolder(
            ResourceKind.EMULATOR,
            "/home/dev/worktrees/TASK-1",
            "/home/dev/projects/api/.git/worktrees/TASK-1",
            PoolHolderState.RUNNING,
            false,
            null,
        ),
        PoolHolder(
            ResourceKind.EMULATOR,
            "/home/dev/worktrees/TASK-2",
            "/home/dev/projects/main-repo/.git/worktrees/TASK-2",
            PoolHolderState.BOOTING,
            false,
            null,
        ),
    )

    @Test
    fun `a full pool names the limit, each holder as project and worktree and the way out`() {
        val error = WorktreeError.ResourcePoolFull(ResourceKind.EMULATOR, 2, poolHolders)

        val english = EnglishStrings.describe(error)
        assertTrue(english.summary.contains("limit of 2 emulators"))
        assertTrue(english.summary.contains("api · TASK-1 (running)"))
        assertTrue(english.summary.contains("main-repo · TASK-2 (booting)"))
        assertTrue(english.nextStep.contains("Preferences"))
        assertEquals(ErrorFix.OpenPreferences, english.fix)

        val spanish = SpanishStrings.describe(error)
        assertTrue(spanish.summary.contains("api · TASK-1 (en ejecución)"))
        assertTrue(spanish.nextStep.contains("Preferencias"))
    }

    @Test
    fun `a full pool says how many worktrees wait and a timed-out wait names the holders`() {
        val full = WorktreeError.ResourcePoolFull(ResourceKind.EMULATOR, 2, poolHolders, waiting = 2)

        assertTrue(EnglishStrings.describe(full).summary.contains("2 worktrees are waiting for one"))
        assertTrue(SpanishStrings.describe(full).summary.contains("2 worktrees esperan uno"))

        val timedOut = WorktreeError.DeviceWaitTimedOut(ResourceKind.EMULATOR, Duration.ofMinutes(30), 2, poolHolders)

        assertTrue(EnglishStrings.describe(timedOut).summary.startsWith("No emulator was freed within 30 min: api · TASK-1"))
        assertTrue(SpanishStrings.describe(timedOut).summary.startsWith("No se liberó ningún emulador en 30 min"))
    }

    private val allStrings = AppLanguage.entries.map { it.strings() }

    @Test
    fun `each language maps to its own string table`() {
        assertEquals(EnglishStrings, AppLanguage.ENGLISH.strings())
        assertEquals(SpanishStrings, AppLanguage.SPANISH.strings())
    }

    @Test
    fun `the worktree list strings are translated and differ between languages`() {
        val english = EnglishStrings
        val spanish = SpanishStrings

        assertEquals("Worktree", english.menuWorktree)
        assertEquals("Worktree", spanish.menuWorktree)
        assertNotEquals(english.mainWorktree, spanish.mainWorktree)
        assertEquals("main", english.mainWorktree)
        assertEquals("Copy path", english.copyPath)
        assertNotEquals(english.copyPath, spanish.copyPath)
        assertEquals("Status", english.statusTitle)
        assertNotEquals(english.statusTitle, spanish.statusTitle)
        assertEquals("Agents", english.agentsTitle)
        assertNotEquals(english.agentsTitle, spanish.agentsTitle)
        assertEquals("Environment", english.environmentTitle)
        assertNotEquals(english.environmentTitle, spanish.environmentTitle)
        assertEquals("Devices", english.devicesTitle)
        assertNotEquals(english.devicesTitle, spanish.devicesTitle)
        assertEquals("Disk", english.diskTitle)
        assertNotEquals(english.diskTitle, spanish.diskTitle)
        assertEquals("Delete worktree", english.deleteWorktree)
        assertNotEquals(english.deleteWorktree, spanish.deleteWorktree)
        assertEquals("Clear selection", english.clearSelection)
        assertNotEquals(english.clearSelection, spanish.clearSelection)
    }

    @Test
    fun `the project form validation strings are translated and differ between languages`() {
        val english = EnglishStrings
        val spanish = SpanishStrings

        assertNotEquals(english.baseRepositoryNotTopLevelError, spanish.baseRepositoryNotTopLevelError)
        assertNotEquals(english.baseRepositoryAlreadyAdded("api"), spanish.baseRepositoryAlreadyAdded("api"))
        assertTrue(english.baseRepositoryAlreadyAdded("api").contains("api"))
        assertNotEquals(english.worktreesRootEmptyError, spanish.worktreesRootEmptyError)
        assertNotEquals(english.agentCommandEmptyError, spanish.agentCommandEmptyError)
        assertNotEquals(english.discardRepositoryEditsTitle, spanish.discardRepositoryEditsTitle)
        assertTrue(spanish.discardRepositoryEditsMessage("/p").contains("/p"))
    }

    @Test
    fun `the local files strings mention folders in both languages`() {
        val english = EnglishStrings
        val spanish = SpanishStrings

        assertTrue(english.removeLocalFile.contains("folder"))
        assertTrue(spanish.removeLocalFile.contains("carpeta"))
        assertTrue(english.selectLocalFilesDialogTitle.contains("folders"))
        assertTrue(spanish.selectLocalFilesDialogTitle.contains("carpetas"))
        assertTrue(english.localFilesDescription.contains("folder"))
        assertTrue(spanish.localFilesDescription.contains("carpeta"))
        assertNotEquals(english.newLocalFileLabel, spanish.newLocalFileLabel)
    }

    @Test
    fun `the worktree view strings are translated and differ between languages`() {
        val english = EnglishStrings
        val spanish = SpanishStrings
        assertEquals("All worktrees", english.allWorktrees)
        assertEquals("Todos los worktrees", spanish.allWorktrees)
        assertNotEquals(english.viewsTitle, spanish.viewsTitle)
        assertNotEquals(english.allProjects, spanish.allProjects)
        assertNotEquals(english.projectLabel, spanish.projectLabel)
        assertNotEquals(english.filterWorktreesPlaceholder, spanish.filterWorktreesPlaceholder)
        assertNotEquals(english.noWorktreeMatches, spanish.noWorktreeMatches)
        assertNotEquals(english.clearFilter, spanish.clearFilter)
        assertNotEquals(english.menuFind, spanish.menuFind)
        assertNotEquals(english.loadingWorktrees, spanish.loadingWorktrees)
        assertEquals("1 worktree", english.worktreeCount(1))
        assertEquals("3 worktrees", spanish.worktreeCount(3))
    }

    private val gitOutput = "fatal: 'TASK-123' is not a working tree"

    private val everyError: List<Throwable> = listOf(
        WorktreeError.BaseRepositoryNotFound("~/projects/main-repo"),
        WorktreeError.WorktreeAlreadyExists("~/projects/TASK-123"),
        WorktreeError.InvalidTaskId("bad..id"),
        WorktreeError.SecretFileNotFound(listOf("local.properties"), "~/projects/main-repo"),
        WorktreeError.InvalidRepositoryConfig("~/projects/main-repo/.shogunai.json", "unexpected token"),
        WorktreeError.RepositoryNotFound("~/projects/main-repo"),
        WorktreeError.NotAWorktree("~/projects/TASK-123"),
        WorktreeError.LocalFileUnreadable("~/.shogunai/projects.json", "unexpected token"),
        WorktreeError.LocalFileUnreadable("~/.shogunai/settings.json", null),
        WorktreeError.BareRepositoryNotSupported("~/projects/main-repo.git"),
        WorktreeError.RepositoryHasNoCommits,
        WorktreeError.RepositoryConfigChanged("~/projects/main-repo/.shogunai.json"),
        WorktreeError.SecretCopyFailed(IOException("disk full")),
        WorktreeError.PostCreateCommandFailed("~/projects/TASK-123", listOf("npm", "install"), 1, "npm ERR! missing script"),
        WorktreeError.ClipboardWriteFailed(IOException("busy")),
        WorktreeError.GitLfsNotFound,
        WorktreeError.BranchNotFound("feature/TASK-123"),
        WorktreeError.StartPointNotFound("epic/redesign"),
        WorktreeError.NotABranch("v1.2.0"),
        WorktreeError.BranchAlreadyCheckedOut("feature/TASK-123", "~/projects/TASK-123"),
        WorktreeError.ProcessListingFailed(listOf("lsof", "-d", "cwd"), 1),
        WorktreeError.NoFreePort(20000, 29999),
        WorktreeError.NoEmulatorPort(5554, 5584),
        WorktreeError.ResourcePoolFull(ResourceKind.EMULATOR, 2, poolHolders),
        WorktreeError.DeviceWaitTimedOut(ResourceKind.EMULATOR, Duration.ofMinutes(30), 2, poolHolders),
        WorktreeError.AndroidSdkNotFound(listOf("ANDROID_HOME (/opt/sdk)")),
        WorktreeError.NoAvdAvailable,
        WorktreeError.EmulatorBootTimeout("emulator-5556", 180),
        WorktreeError.EmulatorExitedEarly("emulator-5556", "PANIC: Broken AVD system path"),
        WorktreeError.AndroidToolFailed(listOf("adb", "devices"), 1, "adb: device offline"),
        WorktreeError.NoIosRuntime,
        WorktreeError.SimulatorBootTimeout("ShogunAi · main-repo · TASK-123", 120),
        WorktreeError.IosSimulatorProviderNotDeclared,
        WorktreeError.IosSimulatorsNotSupported,
        WorktreeError.AppleToolFailed(listOf("xcrun", "simctl", "boot"), 1, "Unable to boot device"),
        WorktreeError.EmulatorProviderNotDeclared,
        WorktreeError.EnvironmentFileTracked(".env"),
        WorktreeError.EnvironmentFileWriteFailed(".env", IOException("read-only")),
        WorktreeError.WorktreeFolderInUse("~/projects/TASK-123", true, listOf("git", "worktree", "remove", "~/projects/TASK-123"), 255, "error: failed to delete: Permission denied"),
        WorktreeError.HerdrNotInstalled,
        WorktreeError.HerdrCommandFailed(listOf("herdr", "pane", "list"), "no herdr server is running"),
        WorktreeError.HerdrAgentNotStarted("w1:p2", "headroom wrap claude"),
        WorktreeError.OrcaNotInstalled,
        WorktreeError.OrcaNotStarted,
        WorktreeError.OrcaCommandFailed(listOf("orca", "repo", "list", "--json"), "runtime_unavailable"),
        WorktreeError.OrcaAgentNotStarted("term_1", "env 'PORT=4100' claude -n main-repo-task-1"),
        WorktreeError.GitCommandFailed(listOf("git", "worktree", "remove", "TASK-123"), 128, gitOutput),
        CommandExecutionException(listOf("git", "status"), IOException("No such file or directory")),
        IllegalStateException("boom"),
    )

    private val everyGitFailure: List<WorktreeError.GitCommandFailed> = listOf(
        "fatal: Unable to create '~/projects/main-repo/.git/index.lock': File exists.",
        "fatal: could not create leading directories of '~/projects/task-1': Permission denied",
        "fatal: not a git repository (or any of the parent directories): .git",
        "fatal: a branch named 'TASK-123' already exists",
        "fatal: '~/projects/task-1' contains modified or untracked files, use --force to delete it",
        "fatal: something nobody expected",
    ).map { WorktreeError.GitCommandFailed(listOf("git", "worktree", "add"), 128, it) }

    @Test
    fun `every state group has its own hint and criterion in both languages`() {
        assertEquals("agent waiting, uncommitted or unpushed", StateGroupKind.NEEDS_YOU.hint(EnglishStrings))
        assertEquals("agente esperando, sin commit o sin subir", StateGroupKind.NEEDS_YOU.hint(SpanishStrings))
        allStrings.forEach { strings ->
            val hints = StateGroupKind.entries.map { it.hint(strings) }
            val criteria = StateGroupKind.entries.map { it.criterion(strings) }
            assertTrue((hints + criteria).all { it.isNotBlank() })
            assertEquals(hints.size, hints.toSet().size)
            assertEquals(criteria.size, criteria.toSet().size)
        }
        StateGroupKind.entries.forEach { kind ->
            assertNotEquals(kind.hint(EnglishStrings), kind.hint(SpanishStrings))
            assertNotEquals(kind.criterion(EnglishStrings), kind.criterion(SpanishStrings))
        }
    }

    @Test
    fun `every error has a summary and a next step in both languages`() {
        (everyError + everyGitFailure).forEach { error ->
            val english = EnglishStrings.describe(error)
            val spanish = SpanishStrings.describe(error)
            listOf(english, spanish).forEach {
                assertTrue(it.summary.isNotBlank(), error.toString())
                assertTrue(it.nextStep.isNotBlank(), error.toString())
            }
            assertNotEquals(english.summary, spanish.summary, error.toString())
            assertNotEquals(english.nextStep, spanish.nextStep, error.toString())
        }
    }

    @Test
    fun `summaries never contain raw command lines, exit codes or tool output`() {
        val rawFragments = listOf(
            gitOutput, "128", "git worktree remove", "adb devices", "adb: device offline",
            "Unable to boot device", "xcrun simctl boot", "lsof -d cwd", "no herdr server is running",
            "disk full", "unexpected token", "ANDROID_HOME (/opt/sdk)", "boom", "exit code", "runtime_unavailable",
        )
        (everyError + everyGitFailure).forEach { error ->
            allStrings.forEach { strings ->
                val description = strings.describe(error)
                rawFragments.forEach { fragment ->
                    assertTrue(!description.summary.contains(fragment), "${description.summary} contains $fragment")
                    assertTrue(!description.nextStep.contains(fragment), "${description.nextStep} contains $fragment")
                }
            }
        }
    }

    @Test
    fun `domain errors name the data they are about`() {
        val named = listOf(
            WorktreeError.BaseRepositoryNotFound("~/projects/main-repo") to "~/projects/main-repo",
            WorktreeError.WorktreeAlreadyExists("~/projects/TASK-123") to "~/projects/TASK-123",
            WorktreeError.InvalidTaskId("bad..id") to "bad..id",
            WorktreeError.SecretFileNotFound(listOf("local.properties"), "~/projects/main-repo") to "local.properties",
            WorktreeError.InvalidRepositoryConfig("~/projects/main-repo/.shogunai.json", "x") to "~/projects/main-repo/.shogunai.json",
            WorktreeError.RepositoryConfigChanged("~/projects/main-repo/.shogunai.json") to "~/projects/main-repo/.shogunai.json",
            WorktreeError.BranchNotFound("feature/TASK-123") to "feature/TASK-123",
            WorktreeError.StartPointNotFound("epic/redesign") to "epic/redesign",
            WorktreeError.NotABranch("v1.2.0") to "v1.2.0",
            WorktreeError.BranchAlreadyCheckedOut("feature/TASK-123", "~/projects/TASK-123") to "~/projects/TASK-123",
            WorktreeError.NoEmulatorPort(5554, 5584) to "5584",
            WorktreeError.EmulatorBootTimeout("emulator-5556", 180) to "emulator-5556",
            WorktreeError.EnvironmentFileTracked(".env") to ".env",
        )
        named.forEach { (error, expected) ->
            allStrings.forEach { strings ->
                val description = strings.describe(error)
                assertTrue((description.summary + description.nextStep).contains(expected), description.summary)
            }
        }
    }

    @Test
    fun `git failures keep Git's output verbatim in the technical details`() {
        val error = WorktreeError.GitCommandFailed(listOf("git", "worktree", "remove", "TASK-123"), 128, gitOutput)

        allStrings.forEach { strings ->
            val details = strings.describe(error).details
            assertEquals(gitOutput, details?.output)
            assertEquals(128, details?.exitCode)
            assertEquals(listOf("git", "worktree", "remove", "TASK-123"), details?.command)
            assertEquals("git worktree remove TASK-123\nexit code 128\n$gitOutput", details?.asPlainText())
        }
    }

    @Test
    fun `recognized git causes get a specific explanation`() {
        val lock = WorktreeError.GitCommandFailed(
            listOf("git", "worktree", "add"),
            128,
            "fatal: Unable to create '~/projects/main-repo/.git/index.lock': File exists.",
        )
        val unknown = WorktreeError.GitCommandFailed(listOf("git", "worktree", "add"), 128, "fatal: something nobody expected")

        val lockDescription = EnglishStrings.describe(lock)
        assertTrue(lockDescription.summary.contains("Another Git process"))
        assertTrue(lockDescription.nextStep.contains("~/projects/main-repo/.git/index.lock"))
        val unknownDescription = EnglishStrings.describe(unknown)
        assertEquals("The Git operation failed.", unknownDescription.summary)
        assertEquals("Check the technical details.", unknownDescription.nextStep)
    }

    @Test
    fun `errors wrapping a cause keep the cause's message in the technical details`() {
        val error = WorktreeError.SecretCopyFailed(IOException("disk full"))

        allStrings.forEach { assertEquals("disk full", it.describe(error).details?.output) }
    }

    @Test
    fun `an agent that doesn't start in Herdr keeps its pane and command in the details`() {
        val error = WorktreeError.HerdrAgentNotStarted("w1:p2", "headroom wrap claude")

        allStrings.forEach { strings ->
            val output = strings.describe(error).details?.output.orEmpty()
            assertTrue(output.contains("w1:p2") && output.contains("headroom wrap claude"), output)
        }
    }

    @Test
    fun `an agent that doesn't start in Orca keeps its terminal and command in the details`() {
        val error = WorktreeError.OrcaAgentNotStarted("term_1", "env 'PORT=4100' claude -n main-repo-task-1")

        allStrings.forEach { strings ->
            val output = strings.describe(error).details?.output.orEmpty()
            assertTrue(output.contains("term_1") && output.contains("claude -n main-repo-task-1"), output)
        }
    }

    @Test
    fun `Orca's error message is kept in the details and Orca's site is named when it is missing`() {
        val failed = WorktreeError.OrcaCommandFailed(listOf("orca", "repo", "list", "--json"), "runtime_unavailable")

        allStrings.forEach { strings ->
            assertEquals("runtime_unavailable", strings.describe(failed).details?.output)
            assertTrue(strings.describe(WorktreeError.OrcaNotInstalled).nextStep.contains("https://onorca.dev"))
        }
    }

    @Test
    fun `the Orca outcomes, origin and notices are translated and keep their data`() {
        allStrings.forEach { strings ->
            assertNotEquals(strings.agentStartedInOrca("TASK-123"), strings.focusedInOrca("TASK-123"))
            assertTrue(strings.agentStartedInOrca("TASK-123").contains("TASK-123"))
            val failed = strings.orcaCloseFailed("~/projects/TASK-123", "runtime_unavailable")
            assertTrue(failed.contains("~/projects/TASK-123") && failed.contains("runtime_unavailable"), failed)
        }
        assertNotEquals(EnglishStrings.agentStartedInOrca("TASK-123"), SpanishStrings.agentStartedInOrca("TASK-123"))
        assertNotEquals(EnglishStrings.focusedInOrca("TASK-123"), SpanishStrings.focusedInOrca("TASK-123"))
        assertNotEquals(EnglishStrings.openInOrca, SpanishStrings.openInOrca)
        assertNotEquals(EnglishStrings.orcaTerminalsWillClose, SpanishStrings.orcaTerminalsWillClose)
        assertNotEquals(EnglishStrings.orcaCleanupNotice, SpanishStrings.orcaCleanupNotice)
        assertNotEquals(EnglishStrings.openInOrcaMark, SpanishStrings.openInOrcaMark)
        assertEquals("Orca", EnglishStrings.originOrca)
        assertEquals("Orca", SpanishStrings.originOrca)
    }

    @Test
    fun `the three Herdr outcomes read differently in both languages`() {
        allStrings.forEach { strings ->
            val worktree = listOf(strings.openedInHerdr("TASK-123"), strings.agentStartedInHerdr("TASK-123"), strings.focusedInHerdr("TASK-123"))
            val multirepo = listOf(strings.multirepoAgentOpened, strings.multirepoAgentStarted, strings.multirepoAgentFocused)
            assertEquals(3, worktree.toSet().size)
            assertEquals(3, multirepo.toSet().size)
        }
        assertNotEquals(EnglishStrings.agentStartedInHerdr("TASK-123"), SpanishStrings.agentStartedInHerdr("TASK-123"))
        assertNotEquals(EnglishStrings.multirepoAgentStarted, SpanishStrings.multirepoAgentStarted)
    }

    @Test
    fun `commands that can't start name the tool and keep the command in the details`() {
        val error = CommandExecutionException(listOf("git", "status"), IOException("No such file or directory"))

        assertTrue(EnglishStrings.describe(error).summary.contains("git"))
        assertTrue(SpanishStrings.describe(error).summary.contains("git"))
        assertEquals(listOf("git", "status"), EnglishStrings.describe(error).details?.command)
    }

    @Test
    fun `unexpected throwables use the generic message and keep theirs in the details`() {
        val description = EnglishStrings.describe(IllegalStateException("boom"))

        assertEquals("Something unexpected went wrong.", description.summary)
        assertEquals("boom", description.details?.output)
    }

    @Test
    fun `errors about a project's repository offer to open its settings`() {
        listOf(
            WorktreeError.BaseRepositoryNotFound("~/projects/main-repo"),
            WorktreeError.RepositoryNotFound("~/projects/main-repo"),
            WorktreeError.EmulatorProviderNotDeclared,
        ).forEach { assertEquals(ErrorFix.OpenProjectSettings, EnglishStrings.describe(it).fix) }
        assertNull(EnglishStrings.describe(WorktreeError.NoAvdAvailable).fix)
    }

    @Test
    fun `each language names itself in its own language`() {
        assertEquals("English", EnglishStrings.languageName)
        assertEquals("Español", SpanishStrings.languageName)
    }

    @Test
    fun `agent labels name the agent and translate each Herdr state`() {
        AgentState.entries.forEach { state ->
            val english = EnglishStrings.agentLabel("claude", state)
            val spanish = SpanishStrings.agentLabel("claude", state)

            assertTrue(english.startsWith("claude") && spanish.startsWith("claude"))
            assertNotEquals(english, spanish)
        }
        assertEquals("claude", EnglishStrings.agentLabel("claude", null))
        assertEquals("claude", SpanishStrings.agentLabel("claude", null))
    }

    @Test
    fun `the Herdr close warning and notices are translated and keep their data`() {
        val english = EnglishStrings.herdrCloseFailed("~/projects/TASK-123", "workspace w2 not found")
        val spanish = SpanishStrings.herdrCloseFailed("~/projects/TASK-123", "workspace w2 not found")

        listOf(english, spanish).forEach {
            assertTrue(it.contains("~/projects/TASK-123") && it.contains("workspace w2 not found"), it)
        }
        assertNotEquals(english, spanish)
        assertNotEquals(EnglishStrings.herdrWorkspaceWillClose, SpanishStrings.herdrWorkspaceWillClose)
        assertNotEquals(EnglishStrings.herdrCleanupNotice, SpanishStrings.herdrCleanupNotice)
        assertNotEquals(EnglishStrings.openInHerdrMark, SpanishStrings.openInHerdrMark)
    }

    @Test
    fun `every documented template resolves`() {
        allStrings.forEach { strings ->
            assertTrue(strings.templateHelp.isNotEmpty())
            strings.templateHelp.forEach { help ->
                assertNull(RepositoryConfigRules.templateIssue("VAR", help.template), help.template)
                assertTrue(help.description.isNotBlank())
            }
        }
        assertEquals(EnglishStrings.templateHelp.map { it.template }, SpanishStrings.templateHelp.map { it.template })
    }

    @Test
    fun `every repository configuration issue has a translated message`() {
        val issues = listOf(
            RepositoryConfigIssue.InvalidMemorySize,
            RepositoryConfigIssue.InvalidMinutes,
            RepositoryConfigIssue.InvalidVariableName,
            RepositoryConfigIssue.ReservedVariableName,
            RepositoryConfigIssue.DuplicateVariableName,
            RepositoryConfigIssue.UnclosedPlaceholder,
            RepositoryConfigIssue.UnknownPlaceholder("branch"),
            RepositoryConfigIssue.EnvFileOutsideWorktree,
        )
        issues.forEach { issue ->
            val english = EnglishStrings.repositoryConfigIssue(issue)
            val spanish = SpanishStrings.repositoryConfigIssue(issue)
            assertTrue(english.isNotBlank() && spanish.isNotBlank())
            assertNotEquals(english, spanish)
        }
        assertTrue(EnglishStrings.repositoryConfigIssue(RepositoryConfigIssue.UnknownPlaceholder("branch")).contains("branch"))
    }

    @Test
    fun `the command palette strings are translated`() {
        assertEquals("Command palette", EnglishStrings.menuCommandPalette)
        assertNotEquals(EnglishStrings.menuCommandPalette, SpanishStrings.menuCommandPalette)
        assertNotEquals(EnglishStrings.paletteNoResults, SpanishStrings.paletteNoResults)
        assertNotEquals(EnglishStrings.paletteKindAction, SpanishStrings.paletteKindAction)
        assertNotEquals(EnglishStrings.paletteNewWorktreeIn("api"), SpanishStrings.paletteNewWorktreeIn("api"))
        assertNotEquals(EnglishStrings.paletteSettingsOf("api"), SpanishStrings.paletteSettingsOf("api"))
        allStrings.forEach { assertTrue(it.paletteNewWorktreeIn("api").contains("api")) }
    }

    @Test
    fun `the post-creation approval strings are translated`() {
        listOf<(app.luxion.shogunai.ui.i18n.Strings) -> String>(
            { it.postCreateApprovalTitle },
            { it.postCreateApprovalExplanation },
            { it.runAndRemember },
            { it.skipCommands },
            { it.postCreateCommandsTitle },
            { it.postCreateApproved },
            { it.postCreateNotApproved },
            { it.approveCommands },
            { it.revokeApproval },
            { it.worktreeCreatedCommandsSkipped("TASK-1") },
        ).forEach { text ->
            assertTrue(text(EnglishStrings).isNotBlank() && text(SpanishStrings).isNotBlank())
            assertNotEquals(text(EnglishStrings), text(SpanishStrings))
        }
    }

    @Test
    fun `the update strings are translated and name both versions`() {
        assertEquals("Check for Updates…", EnglishStrings.menuCheckForUpdates)
        assertNotEquals(EnglishStrings.menuCheckForUpdates, SpanishStrings.menuCheckForUpdates)
        assertNotEquals(EnglishStrings.updateSkipVersion, SpanishStrings.updateSkipVersion)
        assertNotEquals(EnglishStrings.updateCheckAutomaticallyLabel, SpanishStrings.updateCheckAutomaticallyLabel)
        allStrings.forEach { strings ->
            val message = strings.updateAvailableMessage("1.3.0", "1.2.0")
            assertTrue("1.3.0" in message && "1.2.0" in message)
            assertTrue("brew upgrade --cask shogunai" in strings.updateCommandReminder(InstallChannel.HOMEBREW, "brew upgrade --cask shogunai"))
        }
    }
}
