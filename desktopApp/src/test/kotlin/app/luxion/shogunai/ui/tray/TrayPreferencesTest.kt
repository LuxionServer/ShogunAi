package app.luxion.shogunai.ui.tray

import app.luxion.shogunai.domain.FakeShellPreferenceRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrayPreferencesTest {

    private val repository = FakeShellPreferenceRepository()

    private fun preferences(isSupported: Boolean = true) = TrayPreferences(repository, isSupported)

    @Test
    fun `a headless app shows no tray icon even when the preference shows it`() {
        val headless = runsHeadless { if (it == HEADLESS_PROPERTY) "true" else null }

        val preferences = preferences(isSupported = isTrayIconSupported(systemTraySupported = true, runsHeadless = headless))

        assertTrue(headless)
        assertTrue(preferences.showIcon)
        assertFalse(preferences.isIconShown)
        assertEquals(CloseAction.QUIT, preferences.closeAction)
    }

    @Test
    fun `an app run normally keeps its tray icon`() {
        assertFalse(runsHeadless { null })
        assertTrue(isTrayIconSupported(systemTraySupported = true, runsHeadless = false))
    }

    @Test
    fun `by default the icon is shown and closing the window quits`() {
        val preferences = preferences()

        assertTrue(preferences.isIconShown)
        assertFalse(preferences.keepRunning)
        assertEquals(CloseAction.QUIT, preferences.closeAction)
    }

    @Test
    fun `keeping the app running hides the window on close`() {
        val preferences = preferences()

        preferences.changeKeepRunning(true)

        assertEquals(CloseAction.HIDE, preferences.closeAction)
        assertTrue(repository.keepRunningInTray)
    }

    @Test
    fun `hiding the icon makes closing the window quit again`() {
        val preferences = preferences()
        preferences.changeKeepRunning(true)

        preferences.changeShowIcon(false)

        assertFalse(preferences.isIconShown)
        assertEquals(CloseAction.QUIT, preferences.closeAction)
        assertFalse(repository.showTrayIcon)
    }

    @Test
    fun `without a system tray the icon isn't shown and closing the window quits`() {
        repository.keepRunningInTray = true
        val preferences = preferences(isSupported = false)

        assertFalse(preferences.isIconShown)
        assertEquals(CloseAction.QUIT, preferences.closeAction)
    }

    @Test
    fun `the saved options are restored`() {
        repository.showTrayIcon = false
        repository.keepRunningInTray = true

        val preferences = preferences()

        assertFalse(preferences.showIcon)
        assertTrue(preferences.keepRunning)
    }
}
