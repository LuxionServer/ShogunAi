package app.luxion.shogunai.ui.i18n

import app.luxion.shogunai.domain.io.HostPathSource
import app.luxion.shogunai.domain.model.CheckState
import app.luxion.shogunai.domain.model.InstallationCheck
import app.luxion.shogunai.domain.model.InstallationFinding
import app.luxion.shogunai.domain.model.InstallationReport
import app.luxion.shogunai.domain.model.MULTIREPO_FILE_NAME
import app.luxion.shogunai.domain.model.MultirepoProblem
import app.luxion.shogunai.domain.model.ProviderToolId
import app.luxion.shogunai.domain.model.ProviderToolProblem
import app.luxion.shogunai.domain.model.UnavailableLocalFile
import app.luxion.shogunai.domain.model.UnavailableReason
import app.luxion.shogunai.domain.usecase.CommandLineToolState
import app.luxion.shogunai.domain.usecase.HooksInstallState

private const val ENGLISH_PREFERENCES = "Preferences › Claude Code"
private const val GIT_DOWNLOADS = "https://git-scm.com/downloads"

internal fun englishInstallationOverview(report: InstallationReport): String {
    val failed = report.checks.count { it.state == CheckState.FAILED }
    val warnings = report.checks.count { it.state == CheckState.WARNING }
    return when {
        failed == 0 && warnings == 0 -> "Everything ShogunAi needs is in place."
        failed == 0 -> if (warnings == 1) "1 warning; ShogunAi works, but see below." else "$warnings warnings; ShogunAi works, but see below."
        else -> (if (failed == 1) "1 problem stops ShogunAi from working" else "$failed problems stop ShogunAi from working") +
            if (warnings > 0) " and $warnings ${if (warnings == 1) "warning" else "warnings"}." else "."
    }
}

internal fun englishInstallationState(state: CheckState): String = when (state) {
    CheckState.PASSED -> "OK"
    CheckState.WARNING -> "Warning"
    CheckState.FAILED -> "Failed"
    CheckState.NOT_FOUND -> "Not installed"
}

internal fun englishInstallationSummary(check: InstallationCheck): String = when (val finding = check.finding) {
    is InstallationFinding.Git -> when {
        finding.location == null && finding.detail != null -> "Couldn't look for Git: ${finding.detail}"
        finding.location == null -> "Git isn't on the PATH"
        finding.version == null -> "Couldn't read Git's version from ${finding.location}${finding.detail.asSuffix()}"
        check.state == CheckState.PASSED -> "Git ${finding.version} at ${finding.location}"
        else -> "Git ${finding.version} at ${finding.location} is older than ${finding.minimum}, which ShogunAi needs"
    }
    is InstallationFinding.PathSource -> when (finding.source) {
        HostPathSource.LOGIN_SHELL -> "The PATH comes from your login shell (${finding.shell})"
        HostPathSource.PROCESS -> "The PATH comes from the process that started ShogunAi"
        HostPathSource.FALLBACK -> "Couldn't read your login shell's PATH (${finding.shell ?: "SHELL isn't set"}), so the usual install folders were used"
    }
    is InstallationFinding.ShogunCommand -> when (finding.state) {
        CommandLineToolState.AVAILABLE -> "The shogun command is at ${finding.launcher}"
        CommandLineToolState.INSTALLED_NOT_ON_PATH -> "The shogun command is at ${finding.launcher}, but ${finding.folder} isn't on the PATH"
        CommandLineToolState.BROKEN -> "The shogun command at ${finding.launcher} points to an app that is no longer there"
        CommandLineToolState.MISSING -> "The shogun command isn't installed"
    }
    is InstallationFinding.Hooks -> when {
        finding.state == null -> "Couldn't read Claude Code's settings ${finding.settingsPath}${finding.invalidDetail.asSuffix()}"
        finding.state == HooksInstallState.MISSING -> "ShogunAi's Claude Code hooks aren't installed"
        finding.state == HooksInstallState.OUTDATED -> "ShogunAi's Claude Code hooks are out of date (${finding.events.joinToString()})"
        check.state == CheckState.PASSED -> "ShogunAi's Claude Code hooks are installed in ${finding.settingsPath}"
        finding.shogun == CommandLineToolState.INSTALLED_NOT_ON_PATH -> "Claude Code's hooks run shogun, which isn't on the PATH"
        finding.shogun == CommandLineToolState.BROKEN -> "Claude Code's hooks run shogun, whose launcher is no longer there"
        else -> "Claude Code's hooks run shogun, which isn't installed"
    }
    is InstallationFinding.AgentTool ->
        if (finding.location == null) "${finding.tool.displayName} isn't installed" else "${finding.tool.displayName} is at ${finding.location}"
    is InstallationFinding.ProjectFiles -> when {
        finding.baseMissing -> "${finding.project}: its folder ${finding.basePath} doesn't exist"
        finding.detail != null -> "${finding.project}: couldn't check its local files: ${finding.detail}"
        finding.unavailable.isNotEmpty() ->
            "${finding.project}: ${englishFiles(finding.unavailable.size)} can't be copied or linked from ${finding.basePath}"
        finding.checked == 0 -> "${finding.project}: no local files to copy or link"
        else -> "${finding.project}: ${englishFiles(finding.checked)} available"
    }
    is InstallationFinding.ProviderTool -> englishProviderSummary(finding)
    is InstallationFinding.MultirepoCheck -> when {
        finding.problems.isEmpty() ->
            "Multirepo ${finding.name}: ${finding.repositories.joinToString()}; ${englishCount(finding.groups, "group", "groups")}"
        finding.problems.singleOrNull() == MultirepoProblem.DeclarationMissing -> "Multirepo ${finding.name}: ${finding.folder} no longer has $MULTIREPO_FILE_NAME"
        else -> "Multirepo ${finding.name}: ${englishCount(finding.problems.size, "problem", "problems")} in ${finding.folder}"
    }
}

internal fun englishInstallationFix(check: InstallationCheck): String? {
    if (check.state == CheckState.PASSED) return null
    return when (val finding = check.finding) {
        is InstallationFinding.Git -> when {
            finding.location == null -> "Install Git ${finding.minimum} or later from $GIT_DOWNLOADS and make sure it's on your PATH."
            finding.version == null -> "Check that ${finding.location} runs, or install Git ${finding.minimum} or later from $GIT_DOWNLOADS."
            else -> "Update Git to ${finding.minimum} or later from $GIT_DOWNLOADS."
        }
        is InstallationFinding.PathSource ->
            "Make ${finding.shell ?: "your login shell"} start without errors or prompts within 5 seconds, then reopen ShogunAi."
        is InstallationFinding.ShogunCommand -> englishShogunFix(finding.state, finding.folder, finding.restartNeeded)
        is InstallationFinding.Hooks -> when {
            finding.state == null -> "Correct ${finding.settingsPath} so it's valid JSON, then check again."
            finding.state != HooksInstallState.INSTALLED -> "Install them from $ENGLISH_PREFERENCES, or run shogun hooks install."
            else -> englishShogunFix(finding.shogun, null, false)
        }
        is InstallationFinding.AgentTool -> "Install ${finding.tool.displayName} if you use it: ${finding.tool.website}"
        is InstallationFinding.ProjectFiles -> when {
            finding.baseMissing -> "Restore ${finding.basePath}, or remove the project from ShogunAi."
            finding.detail != null -> "Check that ${finding.basePath} is a Git repository you can read."
            else -> (finding.unavailable.map(::englishLocalFileProblem) + englishLocalFilesFix(finding.unavailable)).joinToString("\n")
        }
        is InstallationFinding.ProviderTool -> finding.problem?.let { englishProviderFix(finding.tool, it) }
        is InstallationFinding.MultirepoCheck -> finding.problems.takeIf { it.isNotEmpty() }?.joinToString("\n") { englishMultirepoFix(finding, it) }
    }
}

private fun englishShogunFix(state: CommandLineToolState, folder: String?, restartNeeded: Boolean): String = when (state) {
    CommandLineToolState.INSTALLED_NOT_ON_PATH ->
        if (restartNeeded) "Restart your terminal and Claude Code so they pick up ${folder ?: "its folder"}."
        else "Add ${folder ?: "its folder"} to your PATH."
    CommandLineToolState.BROKEN -> "Install the command again from $ENGLISH_PREFERENCES."
    CommandLineToolState.MISSING, CommandLineToolState.AVAILABLE -> "Install the command from $ENGLISH_PREFERENCES."
}

private fun englishLocalFileProblem(file: UnavailableLocalFile): String = when (val reason = file.reason) {
    UnavailableReason.Missing -> "${file.path}: not found at ${file.fullPath}"
    is UnavailableReason.BrokenLink -> "${file.path}: a link to ${reason.target}, which doesn't exist"
    is UnavailableReason.Unreadable -> "${file.path}: can't be read${reason.detail.asSuffix()}"
}

private fun englishFiles(count: Int): String = if (count == 1) "1 local file" else "$count local files"

private fun englishProviderSummary(finding: InstallationFinding.ProviderTool): String {
    val subject = "${finding.project}: ${finding.tool.displayName}"
    return when (val problem = finding.problem) {
        null -> when (finding.tool) {
            ProviderToolId.ANDROID_SDK -> "$subject is at ${finding.location} (${englishCount(finding.installed ?: 0, "virtual device", "virtual devices")})"
            ProviderToolId.IOS_SIMULATOR -> "$subject has ${englishCount(finding.installed ?: 0, "iOS runtime", "iOS runtimes")}"
            else -> "$subject is at ${finding.location}"
        }
        is ProviderToolProblem.NotFound -> "$subject isn't installed; the ${finding.tool.provider} provider needs it (looked in ${(problem.searched + problem.commands.map { "$it on the PATH" }).joinToString()})"
        is ProviderToolProblem.Unusable -> "$subject${finding.location?.let { " at $it" }.orEmpty()} doesn't work${problem.detail.asSuffix()}"
        ProviderToolProblem.NeedsMacOs -> "${finding.project}: the ${finding.tool.provider} provider needs macOS with Xcode"
        ProviderToolProblem.NoAndroidVirtualDevice -> "$subject at ${finding.location} has no Android Virtual Device"
        ProviderToolProblem.NoIosRuntime -> "${finding.project}: no iOS simulator runtime is installed"
        is ProviderToolProblem.ProvidersUnreadable -> "${finding.project}: couldn't read its providers${problem.detail.asSuffix()}"
    }
}

private fun englishProviderFix(tool: ProviderToolId, problem: ProviderToolProblem): String = when (problem) {
    ProviderToolProblem.NeedsMacOs -> "Use the ${tool.provider} provider on a Mac with Xcode, or ignore this on other systems."
    ProviderToolProblem.NoAndroidVirtualDevice -> "Create a virtual device in Android Studio's Device Manager."
    ProviderToolProblem.NoIosRuntime -> "Install an iOS runtime from Xcode's Settings › Components."
    is ProviderToolProblem.ProvidersUnreadable -> "Correct the repository's .shogunai.json so it's valid."
    is ProviderToolProblem.NotFound, is ProviderToolProblem.Unusable -> when (tool) {
        ProviderToolId.GRADLE -> "Add the Gradle wrapper (gradlew) to the repository, or install Gradle from https://gradle.org/install/."
        ProviderToolId.MAVEN -> "Add the Maven wrapper (mvnw) to the repository, or install Maven from https://maven.apache.org/install.html."
        ProviderToolId.ANDROID_SDK -> "Install the Android SDK and its emulator with Android Studio (https://developer.android.com/studio), or set ANDROID_HOME to where it is."
        ProviderToolId.IOS_SIMULATOR, ProviderToolId.XCODE -> "Install Xcode from the App Store and select it with sudo xcode-select -s /Applications/Xcode.app."
        ProviderToolId.COCOAPODS -> "Install CocoaPods from https://cocoapods.org."
        ProviderToolId.DOCKER_COMPOSE -> "Install Docker with its Compose plugin, for example Docker Desktop from https://docs.docker.com/get-docker/."
        ProviderToolId.FLUTTER -> "Install Flutter (https://docs.flutter.dev/get-started/install), or FVM if the repository uses it, and make sure it's on your PATH."
        ProviderToolId.PROVIDERS -> "Correct the repository's .shogunai.json so it's valid."
    }
}

private fun englishMultirepoFix(finding: InstallationFinding.MultirepoCheck, problem: MultirepoProblem): String = when (problem) {
    MultirepoProblem.DeclarationMissing -> "Restore ${finding.folder}; otherwise ShogunAi forgets the multirepo the next time it starts."
    is MultirepoProblem.InvalidDeclaration -> "${problem.path}${problem.detail.asSuffix()}. Correct it."
    is MultirepoProblem.RepositoryMissing ->
        "${problem.alias}: ${problem.path} isn't a Git repository. Restore it, or change its path in ${finding.folder}/$MULTIREPO_FILE_NAME."
    MultirepoProblem.NotTrusted -> "It isn't trusted on this machine. Review it and run shogun multirepo trust ${finding.folder}."
    is MultirepoProblem.ListingFailed -> "Couldn't list the worktrees of ${problem.alias}${problem.detail.asSuffix()}. Check it's a Git repository you can read."
    is MultirepoProblem.GroupWorktreeMissing ->
        "Group ${problem.branch}: the folder ${problem.path} of its ${problem.alias} worktree doesn't exist. Restore it, or run git worktree prune in ${problem.alias}."
}

private fun englishCount(count: Int, one: String, many: String): String = if (count == 1) "1 $one" else "$count $many"
