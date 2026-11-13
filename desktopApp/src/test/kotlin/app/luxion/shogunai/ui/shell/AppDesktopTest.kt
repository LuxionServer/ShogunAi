package app.luxion.shogunai.ui.shell

import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.awt.desktop.AboutEvent
import java.awt.desktop.AboutHandler
import java.awt.desktop.AppReopenedEvent
import java.awt.desktop.AppReopenedListener
import java.awt.desktop.PreferencesEvent
import java.awt.desktop.PreferencesHandler
import java.awt.desktop.QuitEvent
import java.awt.desktop.QuitHandler
import java.awt.desktop.QuitResponse
import java.awt.desktop.SystemEventListener
import java.net.URI
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppDesktopTest {

    private class FakeDesktop(private val supported: Set<Desktop.Action>) : AppDesktop {
        val browsed = mutableListOf<URI>()
        var about: AboutHandler? = null
        var preferences: PreferencesHandler? = null
        var quit: QuitHandler? = null
        val listeners = mutableListOf<SystemEventListener>()

        override fun supports(action: Desktop.Action) = action in supported
        override fun browse(uri: URI) {
            browsed += uri
        }
        override fun setAboutHandler(handler: AboutHandler) {
            about = handler
        }
        override fun setPreferencesHandler(handler: PreferencesHandler) {
            preferences = handler
        }
        override fun setQuitHandler(handler: QuitHandler) {
            quit = handler
        }
        override fun addAppEventListener(listener: SystemEventListener) {
            listeners += listener
        }
    }

    private class RecordingQuitResponse : QuitResponse {
        var cancelled = false
        override fun performQuit() = Unit
        override fun cancelQuit() {
            cancelled = true
        }
    }

    private val calls = mutableListOf<String>()

    private fun install(desktop: AppDesktop?) = installMacAppMenuHandlers(
        onAbout = { calls += "about" },
        onPreferences = { calls += "preferences" },
        onQuit = { calls += "quit" },
        onReopen = { calls += "reopen" },
        desktop = desktop,
    )

    @Test
    fun `a link opens in the browser where the desktop can browse`() {
        val desktop = FakeDesktop(setOf(Desktop.Action.BROWSE))

        openInBrowser("https://example.test/releases", desktop)

        assertEquals(listOf(URI("https://example.test/releases")), desktop.browsed)
    }

    @Test
    fun `a link opens nowhere without a desktop or without a browser`() {
        val desktop = FakeDesktop(emptySet())

        openInBrowser("https://example.test/releases", desktop)
        openInBrowser("https://example.test/releases", null)

        assertTrue(desktop.browsed.isEmpty())
    }

    @Test
    fun `the app menu's about, settings, quit and reopening reach the app, and quitting asks it first`() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        val desktop = FakeDesktop(
            setOf(Desktop.Action.APP_ABOUT, Desktop.Action.APP_PREFERENCES, Desktop.Action.APP_QUIT_HANDLER, Desktop.Action.APP_EVENT_REOPENED),
        )
        val response = RecordingQuitResponse()

        install(desktop)
        desktop.about!!.handleAbout(AboutEvent())
        desktop.preferences!!.handlePreferences(PreferencesEvent())
        desktop.quit!!.handleQuitRequestWith(QuitEvent(), response)
        (desktop.listeners.single() as AppReopenedListener).appReopened(AppReopenedEvent())

        assertEquals(listOf("about", "preferences", "quit", "reopen"), calls)
        assertTrue(response.cancelled)
    }

    @Test
    fun `a desktop without app menu handlers gets none, and without a desktop nothing is installed`() {
        val desktop = FakeDesktop(emptySet())

        install(desktop)
        install(null)

        assertNull(desktop.about)
        assertNull(desktop.preferences)
        assertNull(desktop.quit)
        assertTrue(desktop.listeners.isEmpty())
    }

    @Test
    fun `the system desktop is only used where the system supports it`() {
        assertNull(systemDesktop(supported = false))
        assertEquals(Desktop.isDesktopSupported(), systemDesktop() != null)
    }

    @Test
    fun `the app menu handlers go to the system desktop by default`() {
        assumeTrue(Desktop.isDesktopSupported())

        installMacAppMenuHandlers(onAbout = {}, onPreferences = {}, onQuit = {}, onReopen = {})

        assertTrue(calls.isEmpty())
    }

    @Test
    fun `the system desktop answers what it supports and takes the app menu handlers`() {
        assumeTrue(Desktop.isDesktopSupported())
        val desktop = assertNotNull(systemDesktop(supported = true))

        desktop.supports(Desktop.Action.BROWSE)
        install(desktop)

        assertTrue(calls.isEmpty())
    }
}
