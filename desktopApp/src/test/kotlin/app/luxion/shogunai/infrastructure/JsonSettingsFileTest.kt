package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.domain.model.ThemeMode
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.io.path.listDirectoryEntries
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonSettingsFileTest {

    private val tempDir = createTempDirectory("json-settings-file-test")
    private val storagePath = tempDir.resolve("settings.json")
    private val settingsFile = JsonSettingsFile(storagePath)
    private val themeRepository = JsonThemePreferenceRepository(settingsFile)
    private val languageRepository = JsonLanguagePreferenceRepository(settingsFile)

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
}
