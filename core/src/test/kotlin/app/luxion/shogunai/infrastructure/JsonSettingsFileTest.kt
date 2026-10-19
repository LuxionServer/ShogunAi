package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.domain.model.AppVersion
import app.luxion.shogunai.domain.model.UpdatePreferences
import app.luxion.shogunai.domain.model.GroupedView
import app.luxion.shogunai.domain.model.EditorId
import app.luxion.shogunai.domain.model.Grouping
import app.luxion.shogunai.domain.model.TerminalId
import app.luxion.shogunai.domain.model.ThemeMode
import app.luxion.shogunai.domain.model.WindowBounds
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.io.path.listDirectoryEntries
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.concurrent.thread
import kotlin.io.path.name
import kotlin.test.assertTrue

class JsonSettingsFileTest {

    private val tempDir = createTempDirectory("json-settings-file-test")
    private val storagePath = tempDir.resolve("settings.json")
    private val settingsFile = JsonSettingsFile(storagePath)
    private val themeRepository = JsonThemePreferenceRepository(settingsFile)
    private val languageRepository = JsonLanguagePreferenceRepository(settingsFile)
    private val shellRepository = JsonShellPreferenceRepository(settingsFile)
    private val updateRepository = JsonUpdatePreferenceRepository(settingsFile)

    @AfterTest
    fun tearDown() {
        tempDir.listDirectoryEntries().forEach { it.deleteIfExists() }
        tempDir.deleteIfExists()
    }

    @Test
    fun `defaults to automatic theme and English when the file doesn't exist`() {
        assertEquals(ThemeMode.SYSTEM, themeRepository.load())
        assertEquals(AppLanguage.ENGLISH, languageRepository.load())
    }

    @Test
    fun `defaults to English when the file was written before the language existed`() {
        Files.writeString(storagePath, """{ "themeMode": "DARK" }""")

        assertEquals(ThemeMode.DARK, themeRepository.load())
        assertEquals(AppLanguage.ENGLISH, languageRepository.load())
    }

    @Test
    fun `falls back to defaults when the file can't be parsed`() {
        Files.writeString(storagePath, "{ not json")

        assertEquals(ThemeMode.SYSTEM, themeRepository.load())
        assertEquals(AppLanguage.ENGLISH, languageRepository.load())
    }

    @Test
    fun `falls back to defaults when a stored value is unknown`() {
        Files.writeString(storagePath, """{ "themeMode": "DARK", "language": "KLINGON" }""")

        assertEquals(ThemeMode.SYSTEM, themeRepository.load())
        assertEquals(AppLanguage.ENGLISH, languageRepository.load())
    }

    @Test
    fun `saving the language keeps the theme mode and vice versa`() {
        themeRepository.save(ThemeMode.DARK)
        languageRepository.save(AppLanguage.SPANISH)
        themeRepository.save(ThemeMode.LIGHT)

        val reloadedFile = JsonSettingsFile(storagePath)
        assertEquals(ThemeMode.LIGHT, JsonThemePreferenceRepository(reloadedFile).load())
        assertEquals(AppLanguage.SPANISH, JsonLanguagePreferenceRepository(reloadedFile).load())
    }

    @Test
    fun `has no shell state when the file was written by the previous version`() {
        Files.writeString(storagePath, """{ "themeMode": "DARK", "language": "SPANISH" }""")

        assertNull(shellRepository.loadLastDestination())
        assertNull(shellRepository.loadWindowBounds())
        assertFalse(shellRepository.loadSidebarCollapsed())
        assertTrue(shellRepository.loadCollapsedProjects().isEmpty())
        assertNull(shellRepository.loadLastCreateProject())
        assertEquals(ThemeMode.DARK, themeRepository.load())
    }

    @Test
    fun `has no shell state when the file can't be parsed`() {
        Files.writeString(storagePath, "{ not json")

        assertNull(shellRepository.loadLastDestination())
        assertNull(shellRepository.loadWindowBounds())
        assertFalse(shellRepository.loadSidebarCollapsed())
    }

    @Test
    fun `saving the last destination keeps the other keys`() {
        themeRepository.save(ThemeMode.DARK)
        shellRepository.saveSidebarCollapsed(true)

        shellRepository.saveLastDestination("project:main-repo")

        val reloaded = JsonShellPreferenceRepository(JsonSettingsFile(storagePath))
        assertEquals("project:main-repo", reloaded.loadLastDestination())
        assertTrue(reloaded.loadSidebarCollapsed())
        assertEquals(ThemeMode.DARK, themeRepository.load())
    }

    @Test
    fun `saving the window bounds keeps the other keys`() {
        languageRepository.save(AppLanguage.SPANISH)
        shellRepository.saveLastDestination("project:main-repo")
        val bounds = WindowBounds(x = 40, y = 60, width = 1280, height = 800, maximized = true)

        shellRepository.saveWindowBounds(bounds)

        val reloaded = JsonShellPreferenceRepository(JsonSettingsFile(storagePath))
        assertEquals(bounds, reloaded.loadWindowBounds())
        assertEquals("project:main-repo", reloaded.loadLastDestination())
        assertEquals(AppLanguage.SPANISH, languageRepository.load())
    }

    @Test
    fun `saving the sidebar state keeps the other keys`() {
        val bounds = WindowBounds(x = 0, y = 0, width = 1024, height = 768)
        shellRepository.saveWindowBounds(bounds)
        themeRepository.save(ThemeMode.LIGHT)

        shellRepository.saveSidebarCollapsed(true)
        shellRepository.saveSidebarCollapsed(false)

        val reloaded = JsonShellPreferenceRepository(JsonSettingsFile(storagePath))
        assertFalse(reloaded.loadSidebarCollapsed())
        assertEquals(bounds, reloaded.loadWindowBounds())
        assertEquals(ThemeMode.LIGHT, themeRepository.load())
    }

    @Test
    fun `collapsed projects and the last create project are remembered with the other keys`() {
        shellRepository.saveLastDestination("all")

        shellRepository.saveCollapsedProjects(setOf("main-repo", "api"))
        shellRepository.saveLastCreateProject("api")

        val reloaded = JsonShellPreferenceRepository(JsonSettingsFile(storagePath))
        assertEquals(setOf("api", "main-repo"), reloaded.loadCollapsedProjects())
        assertEquals("api", reloaded.loadLastCreateProject())
        assertEquals("all", reloaded.loadLastDestination())
    }

    @Test
    fun `recent palette items are remembered in order with the other keys`() {
        shellRepository.saveLastDestination("all")

        shellRepository.saveRecentPaletteItems(listOf("action:reload", "project:api"))

        val reloaded = JsonShellPreferenceRepository(JsonSettingsFile(storagePath))
        assertEquals(listOf("action:reload", "project:api"), reloaded.loadRecentPaletteItems())
        assertEquals("all", reloaded.loadLastDestination())
    }

    @Test
    fun `there are no recent palette items by default`() {
        assertTrue(shellRepository.loadRecentPaletteItems().isEmpty())
    }

    @Test
    fun `each view's grouping defaults and is remembered independently`() {
        assertEquals(Grouping.BY_STATE, shellRepository.loadGrouping(GroupedView.ACTIVE))
        assertEquals(Grouping.BY_PROJECT, shellRepository.loadGrouping(GroupedView.ALL))

        shellRepository.saveGrouping(GroupedView.ACTIVE, Grouping.BY_PROJECT)

        val reloaded = JsonShellPreferenceRepository(JsonSettingsFile(storagePath))
        assertEquals(Grouping.BY_PROJECT, reloaded.loadGrouping(GroupedView.ACTIVE))
        assertEquals(Grouping.BY_PROJECT, reloaded.loadGrouping(GroupedView.ALL))
        reloaded.saveGrouping(GroupedView.ALL, Grouping.BY_STATE)
        assertEquals(Grouping.BY_PROJECT, reloaded.loadGrouping(GroupedView.ACTIVE))
        assertEquals(Grouping.BY_STATE, reloaded.loadGrouping(GroupedView.ALL))
    }

    @Test
    fun `no terminal or editor is chosen by default or in a file written before they existed`() {
        val tools = JsonToolPreferenceRepository(settingsFile)
        assertNull(tools.loadTerminal())
        assertNull(tools.loadEditor())

        Files.writeString(storagePath, """{ "themeMode": "DARK" }""")

        assertNull(tools.loadTerminal())
        assertNull(tools.loadEditor())
    }

    @Test
    fun `the chosen terminal and editor are remembered with the other keys and can be cleared`() {
        val tools = JsonToolPreferenceRepository(settingsFile)
        themeRepository.save(ThemeMode.DARK)

        tools.saveTerminal(TerminalId.ITERM2)
        tools.saveEditor(EditorId.VS_CODE)

        val reloaded = JsonToolPreferenceRepository(JsonSettingsFile(storagePath))
        assertEquals(TerminalId.ITERM2, reloaded.loadTerminal())
        assertEquals(EditorId.VS_CODE, reloaded.loadEditor())
        assertEquals(ThemeMode.DARK, themeRepository.load())
        reloaded.saveTerminal(null)
        assertNull(reloaded.loadTerminal())
        assertEquals(EditorId.VS_CODE, reloaded.loadEditor())
    }

    @Test
    fun `an unreadable file reads as defaults and reports the problem`() {
        Files.writeString(storagePath, "{ not json")

        assertEquals(ThemeMode.SYSTEM, themeRepository.load())

        assertEquals(storagePath.toString(), settingsFile.lastReadProblem?.path)
        assertEquals("{ not json", Files.readString(storagePath))
    }

    @Test
    fun `keeps the unreadable file before the first write`() {
        Files.writeString(storagePath, "{ not json")

        themeRepository.save(ThemeMode.DARK)

        val kept = tempDir.listDirectoryEntries().single { it.name.startsWith("settings.json.unreadable-") }
        assertEquals("{ not json", Files.readString(kept))
        assertEquals(ThemeMode.DARK, JsonThemePreferenceRepository(JsonSettingsFile(storagePath)).load())
    }

    @Test
    fun `a readable file reports no problem`() {
        themeRepository.save(ThemeMode.DARK)

        themeRepository.load()

        assertNull(settingsFile.lastReadProblem)
    }

    @Test
    fun `two writers changing different keys at once keep both changes`() {
        val first = thread { repeat(30) { JsonSettingsFile(storagePath).update { it.copy(themeMode = ThemeMode.DARK) } } }
        val second = thread { repeat(30) { JsonSettingsFile(storagePath).update { it.copy(sidebarCollapsed = true) } } }
        first.join()
        second.join()

        val settings = JsonSettingsFile(storagePath).read()
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertTrue(settings.sidebarCollapsed)
    }

    @Test
    fun `update checks are on with no history when the file was written before they existed`() {
        Files.writeString(storagePath, """{ "themeMode": "DARK" }""")

        assertEquals(UpdatePreferences(), updateRepository.load())
    }

    @Test
    fun `saves the update preferences next to the other settings`() {
        themeRepository.save(ThemeMode.DARK)

        updateRepository.setCheckAutomatically(false)
        updateRepository.skip(AppVersion(1, 3, 0))

        assertEquals(UpdatePreferences(false, AppVersion(1, 3, 0)), updateRepository.load())
        assertEquals(ThemeMode.DARK, themeRepository.load())
    }
}
