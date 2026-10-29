package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrcaComputerUseTest {

    private val app = "--app com.example.app"

    @Test
    fun `every acting command drives the desktop`() {
        listOf(
            "click $app --element-index 4 --json",
            "perform-secondary-action $app --element-index 4 --action AXShowMenu",
            "scroll $app --element-index 4 --direction down",
            "drag $app --from-x 1 --from-y 1 --to-x 9 --to-y 9",
            "type-text $app --text hello",
            "press-key $app --key Return",
            "hotkey $app --key CmdOrCtrl+A",
            "paste-text $app --text hello",
            "set-value $app --element-index 4 --value hello",
        ).forEach { assertTrue(OrcaComputerUse.drivesDesktop("orca computer $it"), it) }
    }

    @Test
    fun `read-only commands don't drive the desktop`() {
        listOf(
            "capabilities --json",
            "permissions --json",
            "list-apps --json",
            "list-windows $app --json",
            "get-app-state $app --json",
        ).forEach { assertFalse(OrcaComputerUse.drivesDesktop("orca computer $it"), it) }
    }

    @Test
    fun `restoring the window while reading drives the desktop`() {
        assertTrue(OrcaComputerUse.drivesDesktop("orca computer get-app-state $app --restore-window --json"))
    }

    @Test
    fun `the executable is recognized by its file name`() {
        assertTrue(OrcaComputerUse.drivesDesktop("/opt/homebrew/bin/orca computer click $app --x 10 --y 10"))
        assertTrue(OrcaComputerUse.drivesDesktop("orca-ide computer click $app --x 10 --y 10"))
        assertTrue(OrcaComputerUse.drivesDesktop("C:\\Tools\\Orca\\orca.exe computer click $app --x 10 --y 10"))
        assertTrue(OrcaComputerUse.drivesDesktop("\"/Applications/Orca.app/bin/orca\" computer click $app"))
    }

    @Test
    fun `options before the command are skipped`() {
        assertTrue(OrcaComputerUse.drivesDesktop("orca computer --json click $app"))
    }

    @Test
    fun `quoted words don't confuse the command`() {
        assertTrue(OrcaComputerUse.drivesDesktop("orca computer type-text --app 'Example App' --text \"hello world\""))
    }

    @Test
    fun `compound commands are read segment by segment`() {
        assertTrue(OrcaComputerUse.drivesDesktop("cd /tmp && orca computer hotkey $app --key CmdOrCtrl+A"))
        assertTrue(OrcaComputerUse.drivesDesktop("orca computer list-apps --json; orca computer click $app"))
        assertTrue(OrcaComputerUse.drivesDesktop("echo \$(orca computer press-key $app --key Escape)"))
        assertTrue(OrcaComputerUse.drivesDesktop("printf '%s' x | orca computer set-value $app --element-index 2 --value-stdin"))
        assertFalse(OrcaComputerUse.drivesDesktop("orca computer list-apps --json | head -c 50"))
        assertFalse(OrcaComputerUse.drivesDesktop("orca computer get-app-state $app --json && echo --restore-window"))
    }

    @Test
    fun `other commands don't drive the desktop`() {
        assertFalse(OrcaComputerUse.drivesDesktop("echo computer science"))
        assertFalse(OrcaComputerUse.drivesDesktop("orca --help"))
        assertFalse(OrcaComputerUse.drivesDesktop("orca computer"))
        assertFalse(OrcaComputerUse.drivesDesktop("orca worktree ps --json"))
        assertFalse(OrcaComputerUse.drivesDesktop("orca click --element @e1"))
        assertFalse(OrcaComputerUse.drivesDesktop("orcas computer click"))
        assertFalse(OrcaComputerUse.drivesDesktop("ls"))
    }
}
