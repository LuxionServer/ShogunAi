package app.luxion.shogunai.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.domain.io.HostPathSource
import app.luxion.shogunai.domain.model.AgentToolId
import app.luxion.shogunai.domain.model.CheckState
import app.luxion.shogunai.domain.model.InstallationCheck
import app.luxion.shogunai.domain.model.InstallationFinding
import app.luxion.shogunai.domain.model.InstallationReport
import app.luxion.shogunai.domain.model.LocalFileOrigin
import app.luxion.shogunai.domain.model.ThemeMode
import app.luxion.shogunai.domain.model.UnavailableLocalFile
import app.luxion.shogunai.domain.model.UnavailableReason
import app.luxion.shogunai.domain.usecase.CommandLineToolState
import app.luxion.shogunai.domain.usecase.HooksInstallState
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.SpanishStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.theme.ShogunAiTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class InstallationCheckUiTest {

    private val settings = "/home/dev/.claude/settings.json"
    private val base = "/home/dev/projects/main-repo"
    private val brokenLink = UnavailableLocalFile("keys.properties", "$base/keys.properties", LocalFileOrigin.REPOSITORY, UnavailableReason.BrokenLink("/missing/keys.properties"))

    private val everyFinding = listOf(
        InstallationCheck(CheckState.PASSED, InstallationFinding.Git("/usr/bin/git", "2.45.1", "2.29")),
        InstallationCheck(CheckState.FAILED, InstallationFinding.Git("/usr/bin/git", "2.25.1", "2.29")),
        InstallationCheck(CheckState.FAILED, InstallationFinding.Git(null, null, "2.29")),
        InstallationCheck(CheckState.FAILED, InstallationFinding.Git("/usr/bin/git", null, "2.29", "permission denied")),
        InstallationCheck(CheckState.FAILED, InstallationFinding.Git(null, null, "2.29", "boom")),
        InstallationCheck(CheckState.PASSED, InstallationFinding.PathSource(HostPathSource.LOGIN_SHELL, "/bin/zsh")),
        InstallationCheck(CheckState.PASSED, InstallationFinding.PathSource(HostPathSource.PROCESS, "/bin/zsh")),
        InstallationCheck(CheckState.WARNING, InstallationFinding.PathSource(HostPathSource.FALLBACK, null)),
        InstallationCheck(CheckState.PASSED, InstallationFinding.ShogunCommand(CommandLineToolState.AVAILABLE, "/home/dev/.local/bin/shogun", "/home/dev/.local/bin", false)),
        InstallationCheck(CheckState.WARNING, InstallationFinding.ShogunCommand(CommandLineToolState.INSTALLED_NOT_ON_PATH, "/home/dev/.local/bin/shogun", "/home/dev/.local/bin", false)),
        InstallationCheck(CheckState.WARNING, InstallationFinding.ShogunCommand(CommandLineToolState.INSTALLED_NOT_ON_PATH, "C:\\shogun.cmd", "C:\\bin", true)),
        InstallationCheck(CheckState.FAILED, InstallationFinding.ShogunCommand(CommandLineToolState.BROKEN, "/home/dev/.local/bin/shogun", "/home/dev/.local/bin", false)),
        InstallationCheck(CheckState.FAILED, InstallationFinding.ShogunCommand(CommandLineToolState.MISSING, null, null, false)),
        InstallationCheck(CheckState.PASSED, InstallationFinding.Hooks(settings, HooksInstallState.INSTALLED)),
        InstallationCheck(CheckState.WARNING, InstallationFinding.Hooks(settings, HooksInstallState.MISSING)),
        InstallationCheck(CheckState.WARNING, InstallationFinding.Hooks(settings, HooksInstallState.OUTDATED, listOf("SessionStart"))),
        InstallationCheck(CheckState.FAILED, InstallationFinding.Hooks(settings, HooksInstallState.INSTALLED, shogun = CommandLineToolState.MISSING)),
        InstallationCheck(CheckState.FAILED, InstallationFinding.Hooks(settings, HooksInstallState.INSTALLED, shogun = CommandLineToolState.BROKEN)),
        InstallationCheck(CheckState.WARNING, InstallationFinding.Hooks(settings, HooksInstallState.INSTALLED, shogun = CommandLineToolState.INSTALLED_NOT_ON_PATH)),
        InstallationCheck(CheckState.FAILED, InstallationFinding.Hooks(settings, null, invalidDetail = "Unexpected JSON token")),
        InstallationCheck(CheckState.PASSED, InstallationFinding.AgentTool(AgentToolId.HERDR, "/opt/homebrew/bin/herdr")),
        InstallationCheck(CheckState.NOT_FOUND, InstallationFinding.AgentTool(AgentToolId.ORCA, null)),
        InstallationCheck(CheckState.PASSED, InstallationFinding.ProjectFiles("main-repo", base, false, 0, emptyList())),
        InstallationCheck(CheckState.PASSED, InstallationFinding.ProjectFiles("main-repo", base, false, 1, emptyList())),
        InstallationCheck(CheckState.PASSED, InstallationFinding.ProjectFiles("main-repo", base, false, 3, emptyList())),
        InstallationCheck(CheckState.FAILED, InstallationFinding.ProjectFiles("main-repo", base, true, 0, emptyList())),
        InstallationCheck(CheckState.FAILED, InstallationFinding.ProjectFiles("main-repo", base, false, 0, emptyList(), "not a git repository")),
        InstallationCheck(
            CheckState.FAILED,
            InstallationFinding.ProjectFiles(
                "main-repo", base, false, 3,
                listOf(
                    brokenLink,
                    UnavailableLocalFile("local.properties", "$base/local.properties", LocalFileOrigin.PROJECT, UnavailableReason.Missing),
                    UnavailableLocalFile("keys.p12", "$base/keys.p12", LocalFileOrigin.PROJECT, UnavailableReason.Unreadable("denied")),
                ),
            ),
        ),
    )

    private fun ComposeUiTest.show(strings: Strings = EnglishStrings, content: @Composable () -> Unit) = setContent {
        CompositionLocalProvider(LocalStrings provides strings) {
            ShogunAiTheme(ThemeMode.LIGHT, content)
        }
    }

    @Test
    fun `every finding has a summary in both languages, and a fix unless it passed`() {
        listOf(EnglishStrings, SpanishStrings).forEach { strings ->
            everyFinding.forEach { check ->
                assertTrue(strings.installationSummary(check).isNotBlank(), "$check")
                val fix = strings.installationFix(check)
                if (check.state == CheckState.PASSED) assertNull(fix, "$check") else assertTrue(!fix.isNullOrBlank(), "$check")
            }
            CheckState.entries.forEach { assertTrue(strings.installationState(it).isNotBlank()) }
        }
    }

    @Test
    fun `Spanish texts leave no English behind`() {
        val english = listOf(" isn't ", " is at ", " the PATH", "Install ", "Couldn't", " doesn't ", "local file", " can't ")
        everyFinding.forEach { check ->
            val text = SpanishStrings.installationSummary(check) + "\n" + SpanishStrings.installationFix(check).orEmpty()
            english.forEach { word -> assertTrue(word !in text, "\"$word\" in \"$text\"") }
        }
    }

    @Test
    fun `the overview counts failures and warnings`() {
        fun report(vararg states: CheckState) =
            InstallationReport(states.map { InstallationCheck(it, InstallationFinding.AgentTool(AgentToolId.ORCA, null)) })

        assertEquals("Everything ShogunAi needs is in place.", EnglishStrings.installationOverview(report(CheckState.PASSED, CheckState.NOT_FOUND)))
        assertEquals("1 warning; ShogunAi works, but see below.", EnglishStrings.installationOverview(report(CheckState.WARNING)))
        assertEquals("2 warnings; ShogunAi works, but see below.", EnglishStrings.installationOverview(report(CheckState.WARNING, CheckState.WARNING)))
        assertEquals("1 problem stops ShogunAi from working.", EnglishStrings.installationOverview(report(CheckState.FAILED)))
        assertEquals("2 problems stop ShogunAi from working and 1 warning.", EnglishStrings.installationOverview(report(CheckState.FAILED, CheckState.FAILED, CheckState.WARNING)))
        assertEquals("Todo lo que ShogunAi necesita está en su sitio.", SpanishStrings.installationOverview(report(CheckState.PASSED)))
        assertEquals("1 aviso; ShogunAi funciona, pero revisa lo de abajo.", SpanishStrings.installationOverview(report(CheckState.WARNING)))
        assertEquals("3 avisos; ShogunAi funciona, pero revisa lo de abajo.", SpanishStrings.installationOverview(report(CheckState.WARNING, CheckState.WARNING, CheckState.WARNING)))
        assertEquals("1 problema impide que ShogunAi funcione.", SpanishStrings.installationOverview(report(CheckState.FAILED)))
        assertEquals("2 problemas impiden que ShogunAi funcione y hay 2 avisos.", SpanishStrings.installationOverview(report(CheckState.FAILED, CheckState.FAILED, CheckState.WARNING, CheckState.WARNING)))
    }

    @Test
    fun `a project's broken link names the file, its target and the fix`() {
        val check = InstallationCheck(CheckState.FAILED, InstallationFinding.ProjectFiles("main-repo", base, false, 1, listOf(brokenLink)))

        assertEquals("main-repo: 1 local file can't be copied or linked from $base", EnglishStrings.installationSummary(check))
        assertEquals(
            "keys.properties: a link to /missing/keys.properties, which doesn't exist\n" +
                "Restore the targets of the broken links, or remove them from .shogunai.json or .worktreeinclude.",
            EnglishStrings.installationFix(check),
        )
        assertEquals(
            "keys.properties: es un enlace a /missing/keys.properties, que no existe\n" +
                "Restaura los destinos de los enlaces rotos, o quítalos de .shogunai.json o .worktreeinclude.",
            SpanishStrings.installationFix(check),
        )
    }

    @Test
    fun `the state runs the check, and runs it again`() = runBlocking {
        var runs = 0
        val state = InstallationCheckState {
            runs++
            InstallationReport(listOf(InstallationCheck(if (runs == 1) CheckState.WARNING else CheckState.PASSED, InstallationFinding.Hooks(settings, HooksInstallState.MISSING))))
        }
        assertEquals(InstallationCheckResult.Running, state.result)

        state.run()
        assertEquals(CheckState.WARNING, assertIs<InstallationCheckResult.Done>(state.result).report.checks.single().state)
        state.run()
        assertEquals(CheckState.PASSED, assertIs<InstallationCheckResult.Done>(state.result).report.checks.single().state)
    }

    @Test
    fun `the state is running while the check runs and keeps a failure`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val state = InstallationCheckState {
            gate.await()
            throw IllegalStateException("projects.json is unreadable")
        }

        val running = async { state.run() }
        yield()
        assertEquals(InstallationCheckResult.Running, state.result)
        gate.complete(Unit)
        running.await()

        assertEquals("projects.json is unreadable", assertIs<InstallationCheckResult.Failed>(state.result).error.message)
    }

    @Test
    fun `the window lists each check with its state, summary and fix`() = runComposeUiTest {
        val report = InstallationReport(
            listOf(
                InstallationCheck(CheckState.PASSED, InstallationFinding.Git("/usr/bin/git", "2.45.1", "2.29")),
                InstallationCheck(CheckState.WARNING, InstallationFinding.Hooks(settings, HooksInstallState.MISSING)),
            ),
        )
        var again = 0
        var copies = 0
        show { InstallationCheckContent(InstallationCheckResult.Done(report), copied = false, onCheckAgain = { again++ }, onCopyReport = { copies++ }) }

        onNodeWithText("1 warning; ShogunAi works, but see below.").assertIsDisplayed()
        onNodeWithText("OK").assertIsDisplayed()
        onNodeWithText("Git 2.45.1 at /usr/bin/git").assertIsDisplayed()
        onNodeWithText("Warning").assertIsDisplayed()
        onNodeWithText("ShogunAi's Claude Code hooks aren't installed").assertIsDisplayed()
        onNodeWithText("Install them from Preferences › Claude Code, or run shogun hooks install.").assertIsDisplayed()
        onNodeWithText("Check Again").performClick()
        onNodeWithText("Copy Diagnostic Report").performClick()

        assertEquals(1, again)
        assertEquals(1, copies)
    }

    @Test
    fun `the window shows the check in Spanish`() = runComposeUiTest {
        val report = InstallationReport(listOf(InstallationCheck(CheckState.FAILED, InstallationFinding.ShogunCommand(CommandLineToolState.MISSING, null, null, false))))
        show(SpanishStrings) { InstallationCheckContent(InstallationCheckResult.Done(report), copied = true, onCheckAgain = {}, onCopyReport = {}) }

        onNodeWithText("1 problema impide que ShogunAi funcione.").assertIsDisplayed()
        onNodeWithText("Fallo").assertIsDisplayed()
        onNodeWithText("El comando shogun no está instalado").assertIsDisplayed()
        onNodeWithText("Instala el comando desde Preferencias › Claude Code.").assertIsDisplayed()
        onNodeWithText("Volver a comprobar").assertIsDisplayed()
        onNodeWithText(SpanishStrings.copied).assertIsDisplayed()
    }

    @Test
    fun `while running the window says so and can't check again`() = runComposeUiTest {
        show { InstallationCheckContent(InstallationCheckResult.Running, copied = false, onCheckAgain = {}, onCopyReport = {}) }

        onNodeWithText("Checking the installation…").assertIsDisplayed()
        onNodeWithText("Check Again").assertIsNotEnabled()
    }

    @Test
    fun `a check that couldn't run says why`() = runComposeUiTest {
        show { InstallationCheckContent(InstallationCheckResult.Failed(IllegalStateException("projects.json is unreadable")), copied = false, onCheckAgain = {}, onCopyReport = {}) }

        onNodeWithText("Couldn't run the check: projects.json is unreadable").assertIsDisplayed()
    }
}
