package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.HostPathSource
import app.luxion.shogunai.domain.model.CheckState
import app.luxion.shogunai.domain.model.CommandLineToolState
import app.luxion.shogunai.domain.model.HooksInstallState
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.JavaSource
import app.luxion.shogunai.domain.model.InstallationCheck
import app.luxion.shogunai.domain.model.InstallationFinding
import app.luxion.shogunai.domain.model.InstallationReport
import app.luxion.shogunai.domain.model.MULTIREPO_FILE_NAME
import app.luxion.shogunai.domain.model.MultirepoProblem
import app.luxion.shogunai.domain.model.ProviderToolId
import app.luxion.shogunai.domain.model.ProviderToolProblem
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.joinPath
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
        is InstallationFinding.ProviderTool -> providerSummary(finding)
        is InstallationFinding.MultirepoCheck -> multirepoSummary(finding)
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
            is InstallationFinding.ProviderTool -> if (finding.problem != null) providerFix(finding.tool, finding.problem) else null
            is InstallationFinding.MultirepoCheck -> finding.problems.joinToString("\n") { multirepoFix(finding, it) }
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

    private fun providerSummary(finding: InstallationFinding.ProviderTool): String {
        val tool = finding.tool.displayName
        val subject = "${finding.project}: $tool"
        return when (val problem = finding.problem) {
            null -> passingSummary(finding, subject)
            is ProviderToolProblem.NotFound -> "$subject not found, needed by the ${finding.tool.provider} provider (looked in ${(problem.searched + problem.commands.map { "$it on the PATH" }).joinToString()})"
            is ProviderToolProblem.Unusable -> "$subject${finding.location?.let { " at $it" }.orEmpty()} doesn't work" + problem.detail.suffix()
            ProviderToolProblem.NeedsMacOs -> "${finding.project}: the ${finding.tool.provider} provider needs macOS with Xcode"
            ProviderToolProblem.NoAndroidVirtualDevice -> "$subject at ${finding.location} has no Android Virtual Device"
            ProviderToolProblem.NoIosRuntime -> "${finding.project}: no iOS simulator runtime is installed"
            is ProviderToolProblem.ProvidersUnreadable -> "${finding.project}: couldn't read its providers" + problem.detail.suffix()
            is ProviderToolProblem.JavaHomeInvalid -> "$subject not found: JAVA_HOME is ${problem.javaHome}, which has no bin/java"
            is ProviderToolProblem.JavaTooOld ->
                "$subject is Java ${problem.java}${finding.at()}, but ${buildTool(finding.tool)} ${problem.toolVersion} needs Java ${problem.required} or newer"
            is ProviderToolProblem.GradleTooOld ->
                "$subject is Java ${problem.java}${finding.at()}, which needs Gradle ${problem.required} or newer, but the project uses Gradle ${problem.gradle}"
            is ProviderToolProblem.JavaNotInstalled ->
                "$subject: Java ${problem.versions.joinToString(" and ")} ${if (problem.versions.size == 1) "isn't" else "aren't"} installed (looked in ${problem.searched.joinToString()})"
            is ProviderToolProblem.GradleJavaHomeInvalid ->
                "$subject not found: org.gradle.java.home is ${problem.javaHome} in ${problem.propertiesFile}, which has no bin/java"
            is ProviderToolProblem.DaemonCriteriaUnreadable -> "$subject: couldn't read toolchainVersion in ${problem.path}"
            is ProviderToolProblem.AndroidGradlePluginNeedsJava ->
                "${finding.project}: Android Gradle Plugin ${problem.agp} needs Java ${problem.required} or newer, but Gradle's daemon runs Java ${problem.java}"
            is ProviderToolProblem.TooOld ->
                "$subject ${problem.version} at ${finding.location} is older than ${problem.minimum}, the first with the 'docker compose ls' ShogunAi uses"
            is ProviderToolProblem.DaemonNotRunning -> "${finding.project}: Docker's daemon isn't running" + problem.detail.suffix()
        }
    }

    private fun buildTool(tool: ProviderToolId): String = if (tool == ProviderToolId.MAVEN_JAVA) "Maven" else "Gradle"

    private fun InstallationFinding.ProviderTool.at(): String = location?.let { " at $it" }.orEmpty()

    private fun passingSummary(finding: InstallationFinding.ProviderTool, subject: String): String = when {
        finding.tool == ProviderToolId.ANDROID_GRADLE_PLUGIN_JAVA -> "${finding.project}: Android Gradle Plugin ${finding.version} runs on Java ${finding.java}"
        finding.provisioned -> "$subject ${finding.version}, which Gradle downloads when it's missing"
        else -> subject + finding.version?.let { " $it" }.orEmpty() + finding.at() + installedSuffix(finding)
    }

    private fun installedSuffix(finding: InstallationFinding.ProviderTool): String = when (finding.tool) {
        ProviderToolId.ANDROID_SDK -> " (${plural(finding.installed ?: 0, "virtual device", "virtual devices")})"
        ProviderToolId.IOS_SIMULATOR -> " (${plural(finding.installed ?: 0, "iOS runtime", "iOS runtimes")})"
        ProviderToolId.DOCKER_DAEMON -> " is running"
        ProviderToolId.GRADLE_TOOLCHAINS -> " installed"
        else -> ""
    }

    private fun providerFix(tool: ProviderToolId, problem: ProviderToolProblem): String = when (problem) {
        ProviderToolProblem.NeedsMacOs -> "Use the ${tool.provider} provider on a Mac with Xcode, or ignore this on other systems"
        ProviderToolProblem.NoAndroidVirtualDevice -> "Create a virtual device in Android Studio's Device Manager"
        ProviderToolProblem.NoIosRuntime -> "Install an iOS runtime from Xcode's Settings › Components"
        is ProviderToolProblem.ProvidersUnreadable -> "Correct the repository's ${RepositoryProvisioning.CONFIG_FILE_NAME} so it's valid"
        is ProviderToolProblem.NotFound, is ProviderToolProblem.Unusable -> installFix(tool)
        is ProviderToolProblem.JavaHomeInvalid -> "Point JAVA_HOME at a JDK's folder (the one with bin/java), or unset it to use the java on your PATH"
        is ProviderToolProblem.JavaTooOld -> newerJava(problem.source, problem.required)
        is ProviderToolProblem.GradleTooOld -> "Update the project's Gradle wrapper to ${problem.required} or newer, or ${olderJava(problem.source)}"
        is ProviderToolProblem.JavaNotInstalled -> missingJava(tool, problem.versions.joinToString(" and "))
        is ProviderToolProblem.GradleJavaHomeInvalid ->
            "Point org.gradle.java.home in ${problem.propertiesFile} at a JDK's folder (the one with bin/java), or remove it"
        is ProviderToolProblem.DaemonCriteriaUnreadable -> "Regenerate the daemon JVM criteria with './gradlew updateDaemonJvm --jvm-version=<version>'"
        is ProviderToolProblem.AndroidGradlePluginNeedsJava -> newerJava(problem.source, problem.required)
        is ProviderToolProblem.TooOld -> "Update Docker, or its Compose plugin, to Compose ${problem.minimum} or newer: https://docs.docker.com/compose/install/"
        is ProviderToolProblem.DaemonNotRunning -> when (problem.os) {
            HostOs.LINUX -> "Start the Docker service with 'sudo systemctl start docker', or start Docker Desktop, and check again"
            HostOs.MACOS, HostOs.WINDOWS -> "Start Docker Desktop, or the Docker engine you use, and check again"
        }
    }

    private fun newerJava(source: JavaSource, required: Int): String = when (source) {
        JavaSource.ENVIRONMENT -> "Install Java $required or newer and point JAVA_HOME at it"
        JavaSource.GRADLE_PROPERTY -> "Install Java $required or newer and point org.gradle.java.home at it"
        JavaSource.DAEMON_CRITERIA -> "Raise the daemon's Java to $required or newer: './gradlew updateDaemonJvm --jvm-version=$required'"
    }

    private fun olderJava(source: JavaSource): String = when (source) {
        JavaSource.ENVIRONMENT -> "point JAVA_HOME at an older Java"
        JavaSource.GRADLE_PROPERTY -> "point org.gradle.java.home at an older Java"
        JavaSource.DAEMON_CRITERIA -> "lower toolchainVersion in gradle/gradle-daemon-jvm.properties"
    }

    private fun missingJava(tool: ProviderToolId, versions: String): String = if (tool == ProviderToolId.GRADLE_TOOLCHAINS) {
        "Install a Java $versions JDK (for example from https://adoptium.net), or apply the 'org.gradle.toolchains.foojay-resolver-convention' plugin in the settings script so Gradle downloads it"
    } else {
        "Install a Java $versions JDK (for example from https://adoptium.net), or add a toolchain download repository and run './gradlew updateDaemonJvm' so Gradle downloads it"
    }

    private fun installFix(tool: ProviderToolId): String = when (tool) {
        ProviderToolId.GRADLE -> "Add the Gradle wrapper (gradlew) to the repository, or install Gradle: https://gradle.org/install/"
        ProviderToolId.MAVEN -> "Add the Maven wrapper (mvnw) to the repository, or install Maven: https://maven.apache.org/install.html"
        ProviderToolId.GRADLE_JAVA, ProviderToolId.MAVEN_JAVA, ProviderToolId.GRADLE_DAEMON_JAVA, ProviderToolId.GRADLE_TOOLCHAINS,
        ProviderToolId.ANDROID_GRADLE_PLUGIN_JAVA,
        ->
            "Install a JDK (for example from https://adoptium.net) and set JAVA_HOME to it or put its java on your PATH; on macOS, /usr/bin/java only works once a JDK is installed"
        ProviderToolId.ANDROID_SDK -> "Install the Android SDK and its emulator with Android Studio (https://developer.android.com/studio), or set ANDROID_HOME to where it is"
        ProviderToolId.IOS_SIMULATOR, ProviderToolId.XCODE -> "Install Xcode from the App Store and select it: sudo xcode-select -s /Applications/Xcode.app"
        ProviderToolId.COCOAPODS -> "Install CocoaPods: https://cocoapods.org"
        ProviderToolId.DOCKER_COMPOSE, ProviderToolId.DOCKER_DAEMON -> "Install Docker with its Compose plugin, for example Docker Desktop: https://docs.docker.com/get-docker/"
        ProviderToolId.FLUTTER -> "Install Flutter (https://docs.flutter.dev/get-started/install), or FVM if the repository uses it, and make sure it's on your PATH"
        ProviderToolId.NODE, ProviderToolId.NPM -> "Install Node.js, which includes npm (https://nodejs.org), and make sure it's on your PATH"
        ProviderToolId.PNPM, ProviderToolId.YARN -> "Enable it with Corepack (corepack enable) or install it, and make sure it's on your PATH"
        ProviderToolId.BUN -> "Install bun (https://bun.sh) and make sure it's on your PATH"
        ProviderToolId.PROVIDERS -> "Correct the repository's ${RepositoryProvisioning.CONFIG_FILE_NAME} so it's valid"
    }

    private fun multirepoSummary(finding: InstallationFinding.MultirepoCheck): String {
        val subject = "Multirepo ${finding.name} (${finding.folder})"
        return when {
            finding.problems.isEmpty() -> "$subject: ${finding.repositories.joinToString()}; ${plural(finding.groups, "group", "groups")}"
            finding.problems.singleOrNull() == MultirepoProblem.DeclarationMissing -> "$subject: ${MULTIREPO_FILE_NAME} is gone"
            else -> "$subject: ${plural(finding.problems.size, "problem", "problems")}"
        }
    }

    private fun forgetCommand(finding: InstallationFinding.MultirepoCheck): String = "'shogun multirepo trust ${finding.folder} --revoke'"

    private fun multirepoFix(finding: InstallationFinding.MultirepoCheck, problem: MultirepoProblem): String = when (problem) {
        MultirepoProblem.DeclarationMissing -> "Restore ${finding.folder}, or forget it with ${forgetCommand(finding)}"
        is MultirepoProblem.InvalidDeclaration -> "${problem.path}${problem.detail.suffix()}; correct it, or forget it with ${forgetCommand(finding)}"
        is MultirepoProblem.RepositoryMissing ->
            "'${problem.alias}': ${problem.path} isn't a Git repository; restore it, or change its path in ${joinPath(finding.folder, MULTIREPO_FILE_NAME)}"
        MultirepoProblem.NotTrusted -> "It isn't trusted on this machine; review it and run 'shogun multirepo trust ${finding.folder}'"
        is MultirepoProblem.ListingFailed -> "Couldn't list the worktrees of '${problem.alias}'${problem.detail.suffix()}; check it's a Git repository you can read"
        is MultirepoProblem.GroupWorktreeMissing ->
            "Group ${problem.branch}: the '${problem.alias}' worktree's folder ${problem.path} doesn't exist; restore it, or run 'git worktree prune' in '${problem.alias}'"
    }

    private fun plural(count: Int, one: String, many: String): String = if (count == 1) "1 $one" else "$count $many"

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
