package app.luxion.shogunai.ui.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.domain.FakeClipboardWriter
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeHerdrClient
import app.luxion.shogunai.domain.FakeMachineCapacity
import app.luxion.shogunai.domain.FakeResourceLimitsRepository
import app.luxion.shogunai.domain.FakeShellPreferenceRepository
import app.luxion.shogunai.domain.FakeToolDetector
import app.luxion.shogunai.domain.FakeToolPreferenceRepository
import app.luxion.shogunai.domain.FakeUpdatePreferenceRepository
import app.luxion.shogunai.domain.io.LanguagePreferenceRepository
import app.luxion.shogunai.domain.io.ThemePreferenceRepository
import app.luxion.shogunai.domain.io.UserPathEntries
import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.domain.model.AppVersion
import app.luxion.shogunai.domain.model.CheckState
import app.luxion.shogunai.domain.model.CommandLineToolState
import app.luxion.shogunai.domain.model.DetectedTools
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.InstallationCheck
import app.luxion.shogunai.domain.model.InstallationFinding
import app.luxion.shogunai.domain.model.InstallationReport
import app.luxion.shogunai.domain.model.ThemeMode
import app.luxion.shogunai.domain.model.UpdateCheck
import app.luxion.shogunai.domain.usecase.ClaudeCodeHooks
import app.luxion.shogunai.domain.usecase.CommandLineTool
import app.luxion.shogunai.domain.usecase.HerdrPluginHealth
import app.luxion.shogunai.domain.usecase.ResolveResourceLimitsUseCase
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.tray.TrayPreferences
import app.luxion.shogunai.ui.worktree.InactivityPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class PreferencesContentUiTest {

    private val shellPreferences = FakeShellPreferenceRepository()

    private val appearance = Appearance(
        object : ThemePreferenceRepository {
            override fun load() = ThemeMode.SYSTEM
            override fun save(mode: ThemeMode) = Unit
        },
        object : LanguagePreferenceRepository {
            override fun load() = AppLanguage.ENGLISH
            override fun save(language: AppLanguage) = Unit
        },
    )

    private fun hooks() = ClaudeHooksState(
        hooks = ClaudeCodeHooks(FakeFileManager()),
        settingsPath = { "/home/dev/.claude/settings.json" },
        tool = CommandLineTool(
            os = HostOs.MACOS,
            appLauncher = null,
            home = "/home/dev",
            pathFolders = { listOf("/usr/local/bin") },
            userBinFolders = listOf("/usr/local/bin"),
            findShogun = { "/usr/local/bin/shogun" },
            files = FakeLauncherFiles(),
            userPath = object : UserPathEntries {
                override suspend fun contains(folder: String) = false
                override suspend fun add(folder: String) = Unit
                override suspend fun remove(folder: String) = Unit
            },
        ),
        noticeDismissedStore = { true },
        rememberNoticeDismissed = {},
    )

    @Test
    fun `opening the preferences reads the tools, the hooks and the Herdr plugin`() = runComposeUiTest {
        val tools = ToolsPreferences(FakeToolPreferenceRepository(), FakeToolDetector(DetectedTools()))
        val resourceLimits = FakeResourceLimitsRepository()
        val claudeHooks = hooks()
        val herdrPlugin = HerdrPluginSettings(HerdrPluginHealth(FakeHerdrClient()))
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                MaterialTheme {
                    PreferencesContent(
                        appearance = appearance,
                        tools = tools,
                        resources = ResourcePreferences(resourceLimits, ResolveResourceLimitsUseCase(resourceLimits, FakeMachineCapacity()), showsSimulators = true),
                        claudeHooks = claudeHooks,
                        herdrPlugin = herdrPlugin,
                        updates = UpdateState({ Result.success(UpdateCheck.UpToDate(AppVersion(1, 0, 0))) }, FakeUpdatePreferenceRepository(), FakeClipboardWriter(), {}),
                        tray = TrayPreferences(shellPreferences, isSupported = true),
                        inactivity = InactivityPreferences(shellPreferences),
                    )
                }
            }
        }

        waitUntil { tools.detected != null && claudeHooks.toolStatus != null && herdrPlugin.status != null }

        onNodeWithText(EnglishStrings.appearanceLabel).assertIsDisplayed()
        assertEquals(CommandLineToolState.AVAILABLE, claudeHooks.toolStatus?.state)
    }

    @Test
    fun `each theme has its label`() {
        assertEquals(
            listOf(EnglishStrings.themeLight, EnglishStrings.themeDark, EnglishStrings.themeSystem),
            listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM).map { it.label(EnglishStrings) },
        )
    }

    @Test
    fun `clearing the inactivity field keeps the saved days`() = runComposeUiTest {
        val inactivity = InactivityPreferences(shellPreferences)
        setContent { MaterialTheme { InactivitySection(inactivity) } }

        onNodeWithTag(InactivityFieldTag).performTextClearance()

        assertEquals(shellPreferences.loadInactiveAfterDays(), inactivity.days)
    }

    @Test
    fun `the installation panel runs the check on opening, again on request, and copies the report`() = runComposeUiTest {
        var runs = 0
        val report = InstallationReport(
            listOf(InstallationCheck(CheckState.NOT_FOUND, InstallationFinding.ShogunCommand(CommandLineToolState.MISSING, null, null, false))),
        )
        val state = InstallationCheckState {
            runs++
            report
        }
        val clipboard = RecordingClipboard()
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings, LocalClipboard provides clipboard) {
                MaterialTheme { InstallationCheckPanel(state) { "diagnostic report" } }
            }
        }

        waitUntil { runs == 1 }
        onNodeWithText(EnglishStrings.installationState(CheckState.NOT_FOUND)).assertIsDisplayed()
        onNodeWithText(EnglishStrings.installationCheckAgain).performClick()
        waitUntil { runs == 2 }
        onNodeWithText(EnglishStrings.menuCopyDiagnosticReport).performClick()
        waitUntil { clipboard.entries.isNotEmpty() }

        onNodeWithText(EnglishStrings.copied).assertIsDisplayed()
        assertNotNull(clipboard.entries.single())
    }
}
