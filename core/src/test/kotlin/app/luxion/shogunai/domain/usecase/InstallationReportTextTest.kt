package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.HostPathSource
import app.luxion.shogunai.domain.model.AgentToolId
import app.luxion.shogunai.domain.model.CheckState
import app.luxion.shogunai.domain.model.InstallationCheck
import app.luxion.shogunai.domain.model.InstallationFinding
import app.luxion.shogunai.domain.model.InstallationReport
import app.luxion.shogunai.domain.model.LocalFileOrigin
import app.luxion.shogunai.domain.model.UnavailableLocalFile
import app.luxion.shogunai.domain.model.UnavailableReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstallationReportTextTest {

    private val settings = "/home/dev/.claude/settings.json"

    private fun summary(state: CheckState, finding: InstallationFinding) = InstallationReportText.summary(InstallationCheck(state, finding))

    private fun fix(state: CheckState, finding: InstallationFinding) = InstallationReportText.fix(InstallationCheck(state, finding))

    @Test
    fun `git texts`() {
        val passed = InstallationFinding.Git("/usr/bin/git", "2.45.1", "2.29")
        assertEquals("Git 2.45.1 (/usr/bin/git)", summary(CheckState.PASSED, passed))
        assertNull(fix(CheckState.PASSED, passed))

        val old = InstallationFinding.Git("/usr/bin/git", "2.25.1", "2.29")
        assertEquals("Git 2.25.1 at /usr/bin/git is older than 2.29, which ShogunAi needs", summary(CheckState.FAILED, old))
        assertEquals("Update Git to 2.29 or later (https://git-scm.com/downloads)", fix(CheckState.FAILED, old))

        val missing = InstallationFinding.Git(null, null, "2.29")
        assertEquals("Git not found on the PATH", summary(CheckState.FAILED, missing))
        assertEquals("Install Git 2.29 or later (https://git-scm.com/downloads) and make sure it's on your PATH", fix(CheckState.FAILED, missing))

        val unreadable = InstallationFinding.Git("/usr/bin/git", null, "2.29", "permission denied")
        assertEquals("Couldn't read Git's version from /usr/bin/git: permission denied", summary(CheckState.FAILED, unreadable))
        assertEquals("Check that /usr/bin/git runs, or install Git 2.29 or later (https://git-scm.com/downloads)", fix(CheckState.FAILED, unreadable))

        assertEquals("Couldn't look for Git: boom", summary(CheckState.FAILED, InstallationFinding.Git(null, null, "2.29", "boom")))
    }

    @Test
    fun `PATH texts`() {
        assertEquals("PATH read from your login shell (/bin/zsh)", summary(CheckState.PASSED, InstallationFinding.PathSource(HostPathSource.LOGIN_SHELL, "/bin/zsh")))
        assertEquals("PATH of the process that started ShogunAi", summary(CheckState.PASSED, InstallationFinding.PathSource(HostPathSource.PROCESS, "/bin/zsh")))
        val fallback = InstallationFinding.PathSource(HostPathSource.FALLBACK, "/bin/zsh")
        assertEquals("Couldn't read your login shell's PATH (/bin/zsh), so the usual install folders were used", summary(CheckState.WARNING, fallback))
        assertEquals("Make /bin/zsh start without errors or prompts within 5 seconds, then reopen ShogunAi", fix(CheckState.WARNING, fallback))
        val noShell = InstallationFinding.PathSource(HostPathSource.FALLBACK, null)
        assertEquals("Couldn't read your login shell's PATH (SHELL isn't set), so the usual install folders were used", summary(CheckState.WARNING, noShell))
        assertEquals("Make your login shell start without errors or prompts within 5 seconds, then reopen ShogunAi", fix(CheckState.WARNING, noShell))
    }

    @Test
    fun `shogun command texts`() {
        val launcher = "/home/dev/.local/bin/shogun"
        val folder = "/home/dev/.local/bin"
        assertEquals("shogun command at $launcher", summary(CheckState.PASSED, InstallationFinding.ShogunCommand(CommandLineToolState.AVAILABLE, launcher, folder, false)))

        val offPath = InstallationFinding.ShogunCommand(CommandLineToolState.INSTALLED_NOT_ON_PATH, launcher, folder, false)
        assertEquals("shogun is at $launcher, but $folder isn't on the PATH", summary(CheckState.WARNING, offPath))
        assertEquals("Add $folder to your PATH", fix(CheckState.WARNING, offPath))
        assertEquals(
            "Restart your terminal and Claude Code so they pick up $folder",
            fix(CheckState.WARNING, offPath.copy(restartNeeded = true)),
        )

        val broken = InstallationFinding.ShogunCommand(CommandLineToolState.BROKEN, launcher, folder, false)
        assertEquals("The shogun command at $launcher points to a ShogunAi app that no longer exists", summary(CheckState.FAILED, broken))
        assertEquals("Install the command again from the desktop app's Preferences › Claude Code", fix(CheckState.FAILED, broken))

        val missing = InstallationFinding.ShogunCommand(CommandLineToolState.MISSING, null, null, false)
        assertEquals("shogun command not found", summary(CheckState.FAILED, missing))
        assertEquals("Install the command from the desktop app's Preferences › Claude Code, or follow the installation guide", fix(CheckState.FAILED, missing))
    }

    @Test
    fun `hooks texts`() {
        val installed = InstallationFinding.Hooks(settings, HooksInstallState.INSTALLED)
        assertEquals("Claude Code hooks installed in $settings", summary(CheckState.PASSED, installed))

        val missing = InstallationFinding.Hooks(settings, HooksInstallState.MISSING)
        assertEquals("Claude Code hooks aren't installed in $settings", summary(CheckState.WARNING, missing))
        assertEquals("Run 'shogun hooks install', or install them from the desktop app's Preferences › Claude Code", fix(CheckState.WARNING, missing))

        val outdated = InstallationFinding.Hooks(settings, HooksInstallState.OUTDATED, listOf("SessionStart", "Stop"))
        assertEquals("Claude Code hooks are out of date in $settings (SessionStart, Stop)", summary(CheckState.WARNING, outdated))

        val noShogun = InstallationFinding.Hooks(settings, HooksInstallState.INSTALLED, shogun = CommandLineToolState.MISSING)
        assertEquals("Claude Code hooks run 'shogun', which isn't installed", summary(CheckState.FAILED, noShogun))
        assertEquals(
            "The hooks run 'shogun'. Install the command from the desktop app's Preferences › Claude Code, or follow the installation guide",
            fix(CheckState.FAILED, noShogun),
        )
        assertEquals(
            "Claude Code hooks run 'shogun', whose launcher no longer exists",
            summary(CheckState.FAILED, noShogun.copy(shogun = CommandLineToolState.BROKEN)),
        )
        assertEquals(
            "Claude Code hooks run 'shogun', which isn't on the PATH",
            summary(CheckState.WARNING, noShogun.copy(shogun = CommandLineToolState.INSTALLED_NOT_ON_PATH)),
        )

        val invalid = InstallationFinding.Hooks(settings, null, invalidDetail = "Unexpected JSON token")
        assertEquals("Couldn't read Claude Code's settings $settings: Unexpected JSON token", summary(CheckState.FAILED, invalid))
        assertEquals("Correct $settings so it's valid JSON, then check again", fix(CheckState.FAILED, invalid))
    }

    @Test
    fun `agent tool texts`() {
        assertEquals("Herdr at /opt/homebrew/bin/herdr", summary(CheckState.PASSED, InstallationFinding.AgentTool(AgentToolId.HERDR, "/opt/homebrew/bin/herdr")))
        val orca = InstallationFinding.AgentTool(AgentToolId.ORCA, null)
        assertEquals("Orca not found", summary(CheckState.NOT_FOUND, orca))
        assertEquals("Install Orca if you use it: https://www.onorca.dev", fix(CheckState.NOT_FOUND, orca))
    }

    @Test
    fun `project texts`() {
        val base = "/home/dev/projects/main-repo"
        assertEquals("main-repo: 1 local file available in $base", summary(CheckState.PASSED, InstallationFinding.ProjectFiles("main-repo", base, false, 1, emptyList())))
        assertEquals("main-repo: no local files to copy or link from $base", summary(CheckState.PASSED, InstallationFinding.ProjectFiles("main-repo", base, false, 0, emptyList())))
        assertEquals("main-repo: 3 local files available in $base", summary(CheckState.PASSED, InstallationFinding.ProjectFiles("main-repo", base, false, 3, emptyList())))

        val gone = InstallationFinding.ProjectFiles("main-repo", base, true, 0, emptyList())
        assertEquals("main-repo: base folder $base doesn't exist", summary(CheckState.FAILED, gone))
        assertEquals("Restore $base, or remove the project from ShogunAi", fix(CheckState.FAILED, gone))

        val broken = UnavailableLocalFile("keys.properties", "$base/keys.properties", LocalFileOrigin.REPOSITORY, UnavailableReason.BrokenLink("/missing/keys.properties"))
        val missing = UnavailableLocalFile("local.properties", "$base/local.properties", LocalFileOrigin.PROJECT, UnavailableReason.Missing)
        val unavailable = InstallationFinding.ProjectFiles("main-repo", base, false, 2, listOf(broken, missing))
        assertEquals("main-repo: 2 local files unavailable in $base", summary(CheckState.FAILED, unavailable))
        assertEquals(
            "keys.properties: $base/keys.properties is a link to /missing/keys.properties, which doesn't exist. " +
                "Restore /missing/keys.properties or replace the link with the file, or stop matching it in .shogunai.json or .worktreeinclude\n" +
                "local.properties: not found at $base/local.properties. Create it there, or remove it from the project's local files",
            fix(CheckState.FAILED, unavailable),
        )

        val unresolved = InstallationFinding.ProjectFiles("main-repo", base, false, 0, emptyList(), "not a git repository")
        assertEquals("main-repo: couldn't check its local files: not a git repository", summary(CheckState.FAILED, unresolved))
        assertEquals("Check that $base is a Git repository you can read", fix(CheckState.FAILED, unresolved))
    }

    @Test
    fun `lines put the fix under its check`() {
        val report = InstallationReport(
            listOf(
                InstallationCheck(CheckState.PASSED, InstallationFinding.Git("/usr/bin/git", "2.45.1", "2.29")),
                InstallationCheck(CheckState.WARNING, InstallationFinding.Hooks(settings, HooksInstallState.MISSING)),
                InstallationCheck(CheckState.NOT_FOUND, InstallationFinding.AgentTool(AgentToolId.ORCA, null)),
                InstallationCheck(
                    CheckState.FAILED,
                    InstallationFinding.ProjectFiles(
                        "main-repo", "/p", false, 2,
                        listOf(
                            UnavailableLocalFile("a", "/p/a", LocalFileOrigin.PROJECT, UnavailableReason.Missing),
                            UnavailableLocalFile("b", "/p/b", LocalFileOrigin.PROJECT, UnavailableReason.Unreadable(null)),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                "ok    Git 2.45.1 (/usr/bin/git)",
                "warn  Claude Code hooks aren't installed in $settings",
                "      fix: Run 'shogun hooks install', or install them from the desktop app's Preferences › Claude Code",
                "--    Orca not found",
                "      fix: Install Orca if you use it: https://www.onorca.dev",
                "fail  main-repo: 2 local files unavailable in /p",
                "      fix: a: not found at /p/a. Create it there, or remove it from the project's local files",
                "           b: /p/b can't be read. Make it readable for your user, or remove it from the project's local files",
            ),
            InstallationReportText.lines(report),
        )
    }

    @Test
    fun `markers name every state`() {
        assertTrue(CheckState.entries.map(InstallationReportText::marker).distinct().size == CheckState.entries.size)
    }
    @Test
    fun `details are added only when there is one`() {
        assertEquals("Couldn't read Git's version from /usr/bin/git", summary(CheckState.FAILED, InstallationFinding.Git("/usr/bin/git", null, "2.29")))
        assertEquals("Couldn't read Git's version from /usr/bin/git", summary(CheckState.FAILED, InstallationFinding.Git("/usr/bin/git", null, "2.29", " ")))
    }

    @Test
    fun `shogun fixes name its folder or say its folder`() {
        val unknownFolder = InstallationFinding.ShogunCommand(CommandLineToolState.INSTALLED_NOT_ON_PATH, "/opt/shogun", null, true)

        assertEquals("Restart your terminal and Claude Code so they pick up its folder", fix(CheckState.WARNING, unknownFolder))
        assertEquals(
            "The hooks run 'shogun'. Add its folder to your PATH",
            fix(CheckState.WARNING, InstallationFinding.Hooks(settings, HooksInstallState.INSTALLED, shogun = CommandLineToolState.INSTALLED_NOT_ON_PATH)),
        )
        assertEquals(
            "The hooks run 'shogun'. Install the command from the desktop app's Preferences › Claude Code, or follow the installation guide",
            fix(CheckState.FAILED, InstallationFinding.Hooks(settings, HooksInstallState.INSTALLED, shogun = CommandLineToolState.AVAILABLE)),
        )
    }
}
