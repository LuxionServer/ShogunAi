package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.domain.model.GroupedView
import app.luxion.shogunai.domain.model.Grouping
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
import kotlin.test.assertTrue

class JsonSettingsFileTest {

    private val tempDir = createTempDirectory("json-settings-file-test")
    private val storagePath = tempDir.resolve("settings.json")
    private val settingsFile = JsonSettingsFile(storagePath)
    private val themeRepository = JsonThemePreferenceRepository(settingsFile)
    private val languageRepository = JsonLanguagePreferenceRepository(settingsFile)
    private val shellRepository = JsonShellPreferenceRepository(settingsFile)

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
}
