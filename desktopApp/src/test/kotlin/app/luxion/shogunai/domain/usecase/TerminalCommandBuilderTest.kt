package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.TerminalEmulator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalCommandBuilderTest {

    private val path = "/home/dev/projects/TASK-123"
    private val command = "headroom wrap claude"

    @Test
    fun `builds osascript command for macOS Terminal, forcing a new tab via Cmd+T with a new-window fallback`() {
        val argv = TerminalCommandBuilder.build(TerminalEmulator.MACOS_TERMINAL, path, command)

        assertEquals("osascript", argv[0])
        assertTrue(argv.any { it.contains("Terminal") })
        assertTrue(argv.any { it.contains(path) })
        assertTrue(argv.any { it.contains(command) })
        assertTrue(argv.any { it.contains("in front window") })
        assertTrue(argv.any { it.contains("keystroke \"t\" using command down") })
        assertTrue(argv.any { it.contains("if (count of windows) = 0 then") })
    }

    @Test
    fun `builds osascript command for iTerm2, preferring a tab in the current window`() {
        val argv = TerminalCommandBuilder.build(TerminalEmulator.ITERM2, path, command)

        assertEquals("osascript", argv[0])
        assertTrue(argv.any { it.contains("iTerm2") })
        assertTrue(argv.any { it.contains(path) })
        assertTrue(argv.any { it.contains(command) })
        assertTrue(argv.any { it.contains("create tab with default profile") })
        assertTrue(argv.any { it.contains("if (count of windows) > 0") })
    }

    @Test
    fun `builds open command for Warp`() {
        val argv = TerminalCommandBuilder.build(TerminalEmulator.WARP, path, command)

        assertEquals(listOf("open", "-a", "Warp", path), argv)
    }

    @Test
    fun `builds gnome-terminal command with working directory flag`() {
        val argv = TerminalCommandBuilder.build(TerminalEmulator.GNOME_TERMINAL, path, command)

        assertEquals(
            listOf("gnome-terminal", "--working-directory=$path", "--", "bash", "-c", "$command; exec bash"),
            argv,
        )
    }

    @Test
    fun `builds konsole command with workdir flag`() {
        val argv = TerminalCommandBuilder.build(TerminalEmulator.KONSOLE, path, command)

        assertEquals(
            listOf("konsole", "--workdir", path, "-e", "bash", "-c", "$command; exec bash"),
            argv,
        )
    }

    @Test
    fun `builds xterm command with cd prefix`() {
        val argv = TerminalCommandBuilder.build(TerminalEmulator.XTERM, path, command)

        assertEquals(
            listOf("xterm", "-e", "bash", "-c", "cd '$path' && $command; exec bash"),
            argv,
        )
    }

    @Test
    fun `substitutes path and command placeholders per argv element`() {
        val template = listOf("my-term", "--cwd", "{path}", "--run", "{command}")

        val argv = TerminalCommandBuilder.buildCustom(template, path, command)

        assertEquals(listOf("my-term", "--cwd", path, "--run", command), argv)
    }
}
