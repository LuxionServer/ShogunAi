package app.luxion.shogunai.ui.shell

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.domain.FakeClipboardWriter
import app.luxion.shogunai.domain.FakeReleaseSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeUpdatePreferenceRepository
import app.luxion.shogunai.domain.model.HostArchitecture
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.usecase.CheckForUpdateUseCase
import app.luxion.shogunai.domain.usecase.DetectInstallChannelUseCase
import app.luxion.shogunai.domain.model.AppVersion
import app.luxion.shogunai.domain.model.InstallChannel
import app.luxion.shogunai.domain.model.PublishedRelease
import app.luxion.shogunai.domain.model.ReleaseAsset
import app.luxion.shogunai.domain.model.ReleaseNotes
import app.luxion.shogunai.domain.model.ThemeMode
import app.luxion.shogunai.domain.model.UpdateAction
import app.luxion.shogunai.domain.model.UpdateCheck
import app.luxion.shogunai.domain.model.UpdateCheckMode
import app.luxion.shogunai.domain.model.VersionNotes
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.SpanishStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.theme.ShogunAiTheme
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class UpdateViewsUiTest {

    private val running = AppVersion(1, 2, 0)
    private val dmg = ReleaseAsset("ShogunAi-1.3.0-arm64.dmg", "https://example.test/arm64.dmg")
    private val deb = ReleaseAsset("shogunai_1.3.0-1_amd64.deb", "https://example.test/app.deb")
    private val bilingual = ReleaseNotes(english = "## What's new\n\n- **Faster start-up.** Opens in a second.", spanish = "## Novedades\n\n- **Arranque más rápido.** Abre en un segundo.")
    private val release = PublishedRelease(AppVersion(1, 3, 0), "https://example.test/releases/1.3.0", listOf(dmg, deb), bilingual)

    private val preferences = FakeUpdatePreferenceRepository()
    private val clipboard = FakeClipboardWriter()
    private val opened = mutableListOf<String>()

    private fun available(
        channel: InstallChannel = InstallChannel.HOMEBREW,
        action: UpdateAction = UpdateAction.RunCommand("brew upgrade --cask shogunai"),
        notes: List<VersionNotes> = listOf(release.versionNotes()),
    ) = UpdateCheck.Available(running, release, channel, action, notes)

    private fun state(result: Result<UpdateCheck>, requests: MutableList<UpdateCheckMode> = mutableListOf()) = UpdateState(
        check = { mode ->
            requests += mode
            result
        },
        preferences = preferences,
        clipboard = clipboard,
        openUrl = { opened += it },
    )

    private fun androidx.compose.ui.test.ComposeUiTest.show(
        strings: () -> Strings = { EnglishStrings },
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) = setContent {
        CompositionLocalProvider(LocalStrings provides strings()) {
            ShogunAiTheme(ThemeMode.LIGHT, content)
        }
    }

    @Test
    fun `the Homebrew notice reminds the command and copies it`() = runComposeUiTest {
        val state = state(Result.success(available(InstallChannel.HOMEBREW, UpdateAction.RunCommand("brew upgrade --cask shogunai"))))
        runBlocking { state.checkInBackground() }
        show { UpdateNotice(state) }

        onNodeWithText("ShogunAi 1.3.0 is out! You're on 1.2.0.").assertIsDisplayed()
        onNodeWithText("Installed with Homebrew? Run brew upgrade --cask shogunai to update.").assertIsDisplayed()
        onNodeWithText("Copy").performClick()

        assertEquals(listOf("brew upgrade --cask shogunai"), clipboard.writtenText)
    }

    @Test
    fun `the notice's info icon opens the notes of the new version`() = runComposeUiTest {
        val state = state(Result.success(available()))
        runBlocking { state.checkInBackground() }
        show {
            UpdateNotice(state)
            ReleaseNotesDialog(state)
        }

        onNodeWithContentDescription("Read what's new").performClick()

        onNodeWithText("What's new in 1.3.0").assertIsDisplayed()
        onNodeWithText("Faster start-up. Opens in a second.").assertIsDisplayed()
        assertEquals(emptyList(), opened)
    }

    @Test
    fun `What's new in the notice opens the notes and View on GitHub opens the newest page`() = runComposeUiTest {
        val state = state(Result.success(available()))
        runBlocking { state.checkInBackground() }
        show {
            UpdateNotice(state)
            ReleaseNotesDialog(state)
        }

        onNodeWithText("What's new").performClick()
        onNodeWithText("View on GitHub").performClick()
        onNodeWithText("Close").performClick()

        assertEquals(listOf("https://example.test/releases/1.3.0"), opened)
        assertNull(state.notes)
    }

    @Test
    fun `What's new in the manual check opens the same notes`() = runComposeUiTest {
        val state = state(Result.success(available()))
        runBlocking { state.checkNow() }
        show {
            UpdateCheckDialog(state)
            ReleaseNotesDialog(state)
        }

        onNodeWithText("What's new").performClick()

        onNodeWithText("What's new in 1.3.0").assertIsDisplayed()
    }

    @Test
    fun `the notes are shown in Spanish and follow a language switch`() = runComposeUiTest {
        var strings: Strings by mutableStateOf(SpanishStrings)
        val state = state(Result.success(available()))
        runBlocking { state.checkInBackground() }
        state.showNotes(state.notice!!)
        show(strings = { strings }) { ReleaseNotesDialog(state) }

        onNodeWithText("Novedades de la 1.3.0").assertIsDisplayed()
        onNodeWithText("Arranque más rápido. Abre en un segundo.").assertIsDisplayed()

        strings = EnglishStrings

        onNodeWithText("Faster start-up. Opens in a second.").assertIsDisplayed()
        onNodeWithText("Arranque más rápido. Abre en un segundo.").assertDoesNotExist()
    }

    @Test
    fun `a Spanish user sees the English notes when there are no Spanish ones`() = runComposeUiTest {
        val englishOnly = release.versionNotes().copy(notes = ReleaseNotes("Only in English", null))
        val state = state(Result.success(available(notes = listOf(englishOnly))))
        runBlocking { state.checkInBackground() }
        state.showNotes(state.notice!!)
        show(strings = { SpanishStrings }) { ReleaseNotesDialog(state) }

        onNodeWithText("Only in English").assertIsDisplayed()
    }

    @Test
    fun `several versions behind show each version's notes under its heading, newest first`() = runComposeUiTest {
        val older = VersionNotes(AppVersion(1, 2, 1), "https://example.test/releases/1.2.1", ReleaseNotes("- Fixed the sidebar", null))
        val state = state(Result.success(available(notes = listOf(release.versionNotes(), older))))
        runBlocking { state.checkInBackground() }
        state.showNotes(state.notice!!)
        show { ReleaseNotesDialog(state) }

        val newestHeading = onNodeWithText("Version 1.3.0").fetchSemanticsNode().boundsInRoot.top
        val olderHeading = onNodeWithText("Version 1.2.1").fetchSemanticsNode().boundsInRoot.top
        onNodeWithText("Fixed the sidebar").assertExists()
        assertTrue(newestHeading < olderHeading)
    }

    @Test
    fun `a version without notes says so and keeps the release page`() = runComposeUiTest {
        val empty = release.versionNotes().copy(notes = ReleaseNotes.NONE)
        val state = state(Result.success(available(notes = listOf(empty))))
        runBlocking { state.checkInBackground() }
        state.showNotes(state.notice!!)
        show { ReleaseNotesDialog(state) }

        onNodeWithText("No notes for this version.").assertIsDisplayed()
        onNodeWithText("View on GitHub").performClick()

        assertEquals(listOf("https://example.test/releases/1.3.0"), opened)
    }

    @Test
    fun `the Scoop and winget notices remind their commands`() = runComposeUiTest {
        val scoop = state(Result.success(available(InstallChannel.SCOOP, UpdateAction.RunCommand("scoop update shogunai"))))
        runBlocking { scoop.checkInBackground() }
        show { UpdateNotice(scoop) }

        onNodeWithText("Installed with Scoop? Run scoop update shogunai to update.").assertIsDisplayed()
    }

    @Test
    fun `the winget notice reminds its command`() = runComposeUiTest {
        val winget = state(Result.success(available(InstallChannel.WINGET, UpdateAction.RunCommand("winget upgrade LuxionServer.ShogunAi"))))
        runBlocking { winget.checkInBackground() }
        show { UpdateNotice(winget) }

        onNodeWithText("Installed with winget? Run winget upgrade LuxionServer.ShogunAi to update.").assertIsDisplayed()
    }

    @Test
    fun `the dmg notice offers to download the installer`() = runComposeUiTest {
        val state = state(Result.success(available(InstallChannel.DMG, UpdateAction.Download(dmg, null))))
        runBlocking { state.checkInBackground() }
        show { UpdateNotice(state) }

        onNodeWithText("Download the new installer to update.").assertIsDisplayed()
        onNodeWithText("Download").performClick()

        assertEquals(listOf("https://example.test/arm64.dmg"), opened)
    }

    @Test
    fun `the deb notice shows the apt command to run after downloading`() = runComposeUiTest {
        val action = UpdateAction.Download(deb, "sudo apt install ./shogunai_1.3.0-1_amd64.deb")
        val state = state(Result.success(available(InstallChannel.DEB, action)))
        runBlocking { state.checkInBackground() }
        show { UpdateNotice(state) }

        onNodeWithText("After downloading, run sudo apt install ./shogunai_1.3.0-1_amd64.deb").assertIsDisplayed()
    }

    @Test
    fun `an unknown install offers the release page`() = runComposeUiTest {
        val state = state(Result.success(available(InstallChannel.UNKNOWN, UpdateAction.OpenReleasePage(release.pageUrl))))
        runBlocking { state.checkInBackground() }
        show { UpdateNotice(state) }

        onNodeWithText("See the release page to update.").assertIsDisplayed()
        onNodeWithText("Open").performClick()

        assertEquals(listOf(release.pageUrl), opened)
    }

    @Test
    fun `skipping the version hides the notice and remembers it`() = runComposeUiTest {
        val state = state(Result.success(available(InstallChannel.HOMEBREW, UpdateAction.RunCommand("brew upgrade --cask shogunai"))))
        runBlocking { state.checkInBackground() }
        show { UpdateNotice(state) }

        onNodeWithText("Skip this version").performClick()

        onNodeWithText("Skip this version").assertDoesNotExist()
        assertNull(state.notice)
        assertEquals(AppVersion(1, 3, 0), preferences.preferences.skippedVersion)
    }

    @Test
    fun `closing the notice hides it without skipping`() = runComposeUiTest {
        val state = state(Result.success(available(InstallChannel.HOMEBREW, UpdateAction.RunCommand("brew upgrade --cask shogunai"))))
        runBlocking { state.checkInBackground() }
        show { UpdateNotice(state) }

        onNodeWithContentDescription("Close message").performClick()

        assertNull(state.notice)
        assertNull(preferences.preferences.skippedVersion)
    }

    @Test
    fun `a failed automatic check shows nothing`() = runComposeUiTest {
        val state = state(Result.failure(WorktreeError.UpdateCheckFailed("offline")))
        runBlocking { state.checkInBackground() }
        show { UpdateNotice(state) }

        assertNull(state.notice)
        assertNull(state.manualResult)
    }

    @Test
    fun `a later check that finds nothing or fails keeps the notice`() = runComposeUiTest {
        var result: Result<UpdateCheck> = Result.success(available(InstallChannel.HOMEBREW, UpdateAction.RunCommand("brew upgrade --cask shogunai")))
        val state = UpdateState(
            check = { result },
            preferences = preferences,
            clipboard = clipboard,
            openUrl = { opened += it },
        )
        runBlocking { state.checkInBackground() }

        result = Result.failure(WorktreeError.UpdateCheckFailed("offline"))
        runBlocking { state.checkInBackground() }
        result = Result.success(UpdateCheck.UpToDate(running))
        runBlocking { state.checkInBackground() }

        assertEquals(release, state.notice?.release)
    }

    @Test
    fun `the manual check says the app is up to date`() = runComposeUiTest {
        val state = state(Result.success(UpdateCheck.UpToDate(running)))
        runBlocking { state.checkNow() }
        show { UpdateCheckDialog(state) }

        onNodeWithText("ShogunAi is up to date (version 1.2.0).").assertIsDisplayed()
        onNodeWithText("Close").performClick()

        assertNull(state.manualResult)
    }

    @Test
    fun `the manual check names the newer version without the skip option`() = runComposeUiTest {
        val state = state(Result.success(available(InstallChannel.HOMEBREW, UpdateAction.RunCommand("brew upgrade --cask shogunai"))))
        runBlocking { state.checkNow() }
        show { UpdateCheckDialog(state) }

        onNodeWithText("ShogunAi 1.3.0 is available. You're on 1.2.0.").assertIsDisplayed()
        onNodeWithText("Skip this version").assertDoesNotExist()
    }

    @Test
    fun `the manual check says why it failed`() = runComposeUiTest {
        val state = state(Result.failure(WorktreeError.UpdateCheckFailed("offline")))
        runBlocking { state.checkNow() }
        show { UpdateCheckDialog(state) }

        onNodeWithText("Couldn't check for updates: offline").assertIsDisplayed()
    }

    @Test
    fun `no request is made at start-up once the option is turned off`() = runComposeUiTest {
        val source = FakeReleaseSource { Result.success(listOf(release)) }
        val state = UpdateState(
            check = CheckForUpdateUseCase(
                source = source,
                preferences = preferences,
                detectChannel = DetectInstallChannelUseCase(HostOs.LINUX, "/home/someone/shogunai", { false }, FakeShellCommandExecutor()),
                runningVersion = "1.2.0",
                architecture = HostArchitecture.X64,
            )::invoke,
            preferences = preferences,
            clipboard = clipboard,
            openUrl = { opened += it },
        )

        state.changeCheckAutomatically(false)
        runBlocking { state.checkInBackground() }

        assertEquals(false, preferences.preferences.checkAutomatically)
        assertEquals(0, source.requests)
        assertNull(state.notice)

        runBlocking { state.checkNow() }

        assertEquals(1, source.requests)
    }
}
