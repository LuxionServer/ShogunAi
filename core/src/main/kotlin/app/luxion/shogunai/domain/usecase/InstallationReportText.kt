package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.HostPathSource
import app.luxion.shogunai.domain.model.CheckState
import app.luxion.shogunai.domain.model.InstallationCheck
import app.luxion.shogunai.domain.model.InstallationFinding
import app.luxion.shogunai.domain.model.InstallationReport
import app.luxion.shogunai.domain.model.problemText
import app.luxion.shogunai.domain.model.removalText

object InstallationReportText {
    private const val PREFERENCES = "the desktop app's Preferences › Claude Code"
    private const val GIT_DOWNLOADS = "https://git-scm.com/downloads"

    fun lines(report: InstallationReport): List<String> = report.checks.flatMap { check ->
        listOf("${marker(check.state).padEnd(MARKER_WIDTH)}${summary(check)}") +
            fix(check)?.lines().orEmpty().mapIndexed { index, line -> " ".repeat(MARKER_WIDTH) + (if (index == 0) "fix: " else "     ") + line }
    }

    fun marker(state: CheckState): String = when (state) {
        CheckState.PASSED -> "ok"
        CheckState.WARNING -> "warn"
        CheckState.FAILED -> "fail"
        CheckState.NOT_FOUND -> "--"
    }

    fun summary(check: InstallationCheck): String = when (val finding = check.finding) {
        is InstallationFinding.Git -> gitSummary(check.state, finding)
        is InstallationFinding.PathSource -> when (finding.source) {
            HostPathSource.LOGIN_SHELL -> "PATH read from your login shell (${finding.shell})"
            HostPathSource.PROCESS -> "PATH of the process that started ShogunAi"
            HostPathSource.FALLBACK -> "Couldn't read your login shell's PATH (${finding.shell ?: "SHELL isn't set"}), so the usual install folders were used"
        }
        is InstallationFinding.ShogunCommand -> when (finding.state) {
            CommandLineToolState.AVAILABLE -> "shogun command at ${finding.launcher}"
            CommandLineToolState.INSTALLED_NOT_ON_PATH -> "shogun is at ${finding.launcher}, but ${finding.folder} isn't on the PATH"
            CommandLineToolState.BROKEN -> "The shogun command at ${finding.launcher} points to a ShogunAi app that no longer exists"
            CommandLineToolState.MISSING -> "shogun command not found"
        }
        is InstallationFinding.Hooks -> hooksSummary(check.state, finding)
        is InstallationFinding.AgentTool ->
            if (finding.location != null) "${finding.tool.displayName} at ${finding.location}" else "${finding.tool.displayName} not found"
        is InstallationFinding.ProjectFiles -> projectSummary(finding)
    }

    fun fix(check: InstallationCheck): String? {
        if (check.state == CheckState.PASSED) return null
        return when (val finding = check.finding) {
            is InstallationFinding.Git -> when {
                finding.location == null -> "Install Git ${finding.minimum} or later ($GIT_DOWNLOADS) and make sure it's on your PATH"
                finding.version == null -> "Check that ${finding.location} runs, or install Git ${finding.minimum} or later ($GIT_DOWNLOADS)"
                else -> "Update Git to ${finding.minimum} or later ($GIT_DOWNLOADS)"
            }
            is InstallationFinding.PathSource ->
                "Make ${finding.shell ?: "your login shell"} start without errors or prompts within 5 seconds, then reopen ShogunAi"
            is InstallationFinding.ShogunCommand -> shogunFix(finding.state, finding.folder, finding.restartNeeded)
            is InstallationFinding.Hooks -> when {
                finding.state == null -> "Correct ${finding.settingsPath} so it's valid JSON, then check again"
                finding.state != HooksInstallState.INSTALLED -> "Run 'shogun hooks install', or install them from $PREFERENCES"
                else -> "The hooks run 'shogun'. " + shogunFix(finding.shogun, null, false)
            }
            is InstallationFinding.AgentTool -> "Install ${finding.tool.displayName} if you use it: ${finding.tool.website}"
            is InstallationFinding.ProjectFiles -> when {
                finding.baseMissing -> "Restore ${finding.basePath}, or remove the project from ShogunAi"
                finding.detail != null -> "Check that ${finding.basePath} is a Git repository you can read"
                else -> finding.unavailable.joinToString("\n") { "${it.path}: ${it.problemText()}, ${it.removalText()}" }
            }
        }
    }

    private fun gitSummary(state: CheckState, finding: InstallationFinding.Git): String = when {
        finding.location == null && finding.detail != null -> "Couldn't look for Git: ${finding.detail}"
        finding.location == null -> "Git not found on the PATH"
        finding.version == null -> "Couldn't read Git's version from ${finding.location}" + finding.detail.suffix()
        state == CheckState.PASSED -> "Git ${finding.version} (${finding.location})"
        else -> "Git ${finding.version} at ${finding.location} is older than ${finding.minimum}, which ShogunAi needs"
    }

    private fun hooksSummary(state: CheckState, finding: InstallationFinding.Hooks): String = when {
        finding.state == null -> "Couldn't read Claude Code's settings ${finding.settingsPath}" + finding.invalidDetail.suffix()
        finding.state == HooksInstallState.MISSING -> "Claude Code hooks aren't installed in ${finding.settingsPath}"
        finding.state == HooksInstallState.OUTDATED -> "Claude Code hooks are out of date in ${finding.settingsPath} (${finding.events.joinToString()})"
        state == CheckState.PASSED -> "Claude Code hooks installed in ${finding.settingsPath}"
        finding.shogun == CommandLineToolState.INSTALLED_NOT_ON_PATH -> "Claude Code hooks run 'shogun', which isn't on the PATH"
        finding.shogun == CommandLineToolState.BROKEN -> "Claude Code hooks run 'shogun', whose launcher no longer exists"
        else -> "Claude Code hooks run 'shogun', which isn't installed"
    }

    private fun projectSummary(finding: InstallationFinding.ProjectFiles): String = when {
        finding.baseMissing -> "${finding.project}: base folder ${finding.basePath} doesn't exist"
        finding.detail != null -> "${finding.project}: couldn't check its local files: ${finding.detail}"
        finding.unavailable.isEmpty() && finding.checked == 0 -> "${finding.project}: no local files to copy or link from ${finding.basePath}"
        finding.unavailable.isEmpty() -> "${finding.project}: ${count(finding.checked)} available in ${finding.basePath}"
        else -> "${finding.project}: ${count(finding.unavailable.size)} unavailable in ${finding.basePath}"
    }

    private fun shogunFix(state: CommandLineToolState, folder: String?, restartNeeded: Boolean): String = when (state) {
        CommandLineToolState.INSTALLED_NOT_ON_PATH ->
            if (restartNeeded) "Restart your terminal and Claude Code so they pick up ${folder ?: "its folder"}"
            else "Add ${folder ?: "its folder"} to your PATH"
        CommandLineToolState.BROKEN -> "Install the command again from $PREFERENCES"
        CommandLineToolState.MISSING, CommandLineToolState.AVAILABLE -> "Install the command from $PREFERENCES, or follow the installation guide"
    }

    private fun count(files: Int): String = if (files == 1) "1 local file" else "$files local files"

    private fun String?.suffix(): String = this?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()

    private const val MARKER_WIDTH = 6
}
