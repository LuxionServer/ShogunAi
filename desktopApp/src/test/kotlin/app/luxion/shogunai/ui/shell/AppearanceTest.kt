package app.luxion.shogunai.ui.shell

import app.luxion.shogunai.domain.io.LanguagePreferenceRepository
import app.luxion.shogunai.domain.io.ThemePreferenceRepository
import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.domain.model.ThemeMode
import app.luxion.shogunai.ui.i18n.strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class AppearanceTest {

    private val themes = object : ThemePreferenceRepository {
        var saved = ThemeMode.SYSTEM
        override fun load() = saved
        override fun save(mode: ThemeMode) {
            saved = mode
        }
    }

    private val languages = object : LanguagePreferenceRepository {
        var saved = AppLanguage.entries.first()
        override fun load() = saved
        override fun save(language: AppLanguage) {
            saved = language
        }
    }

    @Test
    fun `changing the theme applies and saves it`() {
        val appearance = Appearance(themes, languages)

        appearance.changeThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, appearance.themeMode)
        assertEquals(ThemeMode.DARK, themes.saved)
    }

    @Test
    fun `changing the language switches the texts and saves it`() {
        val appearance = Appearance(themes, languages)
        val other = AppLanguage.entries.last()

        appearance.changeLanguage(other)

        assertEquals(other, languages.saved)
        assertEquals(other.strings(), appearance.strings)
    }

    @Test
    fun `cancelling the installation check cancels it instead of reporting a failure`() {
        val state = InstallationCheckState { throw CancellationException("closed") }

        assertFailsWith<CancellationException> { runBlocking { state.run() } }
        assertIs<InstallationCheckResult.Running>(state.result)
    }
}
