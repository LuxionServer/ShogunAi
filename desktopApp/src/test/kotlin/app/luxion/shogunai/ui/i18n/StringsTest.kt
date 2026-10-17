package app.luxion.shogunai.ui.i18n

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.domain.model.WorktreeError
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StringsTest {

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

    @Test
    fun `domain errors are translated and keep their data`() {
        val errors = listOf(
            WorktreeError.BaseRepositoryNotFound("~/projects/main-repo") to "~/projects/main-repo",
            WorktreeError.WorktreeAlreadyExists("~/projects/TASK-123") to "~/projects/TASK-123",
            WorktreeError.InvalidTaskId("bad..id") to "bad..id",
            WorktreeError.SecretFileNotFound(listOf("local.properties"), "~/projects/main-repo") to "local.properties",
            WorktreeError.InvalidRepositoryConfig("~/projects/main-repo/.shogunai.json", "unexpected token") to "~/projects/main-repo/.shogunai.json",
            WorktreeError.BranchNotFound("feature/TASK-123") to "feature/TASK-123",
            WorktreeError.BranchAlreadyCheckedOut("feature/TASK-123", "~/projects/TASK-123") to "~/projects/TASK-123",
            WorktreeError.ProcessListingFailed(listOf("lsof", "-d", "cwd"), 1) to "lsof -d cwd",
            WorktreeError.NoEmulatorPort(5554, 5584) to "5584",
            WorktreeError.AndroidSdkNotFound(listOf("ANDROID_HOME (/opt/sdk)")) to "ANDROID_HOME (/opt/sdk)",
            WorktreeError.EmulatorBootTimeout("emulator-5556", 180) to "emulator-5556",
            WorktreeError.AndroidToolFailed(listOf("adb", "devices"), 1, "") to "adb devices",
            WorktreeError.EmulatorProviderNotDeclared to "\"providers\": { \"emulator\": {} }",
            WorktreeError.NoAvdAvailable to "Device Manager",
            WorktreeError.NoFreePort(20000, 29999) to "29999",
            WorktreeError.NoIosRuntime to "Xcode",
            WorktreeError.SimulatorBootTimeout("ShogunAi · main-repo · TASK-123", 120) to "ShogunAi · main-repo · TASK-123",
            WorktreeError.IosSimulatorProviderNotDeclared to "\"providers\": { \"ios-simulator\": {} }",
            WorktreeError.IosSimulatorsNotSupported to "macOS",
            WorktreeError.AppleToolFailed(listOf("xcrun", "simctl", "boot"), 1, "") to "xcrun simctl boot",
            WorktreeError.EnvironmentFileTracked(".env") to ".env",
            WorktreeError.HerdrNotInstalled to "https://herdr.dev",
            WorktreeError.HerdrCommandFailed(listOf("herdr", "pane", "list"), "no herdr server is running") to "no herdr server is running",
        )
        errors.forEach { (error, expectedData) ->
            val english = EnglishStrings.worktreeError(error)
            val spanish = SpanishStrings.worktreeError(error)
            assertTrue(english.contains(expectedData), english)
            assertTrue(spanish.contains(expectedData), spanish)
            assertNotEquals(english, spanish)
        }
    }

    @Test
    fun `git failures include Git's error output verbatim`() {
        val output = "fatal: 'TASK-123' is not a working tree"
        val error = WorktreeError.GitCommandFailed(listOf("git", "worktree", "remove", "TASK-123"), 128, output)

        allStrings.forEach { strings ->
            val message = strings.worktreeError(error)
            assertTrue(message.endsWith("\n$output"), message)
            assertTrue(message.contains("128"), message)
        }
    }

    @Test
    fun `errors wrapping a cause include the cause's message`() {
        val error = WorktreeError.SecretCopyFailed(IOException("disk full"))

        allStrings.forEach { strings ->
            assertTrue(strings.worktreeError(error).contains("disk full"))
        }
    }

    @Test
    fun `commands that can't start are reported in the selected language`() {
        val error = CommandExecutionException(listOf("git", "status"), IOException("No such file or directory"))

        assertEquals("Could not start the command: git status", EnglishStrings.worktreeError(error))
        assertEquals("No se pudo iniciar el comando: git status", SpanishStrings.worktreeError(error))
    }

    @Test
    fun `unexpected throwables use the generic message`() {
        val error = IllegalStateException("boom")

        assertEquals("Unexpected error: boom", EnglishStrings.worktreeError(error))
        assertEquals("Error inesperado: boom", SpanishStrings.worktreeError(error))
    }

    @Test
    fun `each language names itself in its own language`() {
        assertEquals("English", EnglishStrings.languageName)
        assertEquals("Español", SpanishStrings.languageName)
    }
}
