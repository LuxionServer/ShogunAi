package app.luxion.shogunai.domain.model

import java.time.Duration
import java.time.Instant

sealed class WorktreeError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class BaseRepositoryNotFound(val path: String) :
        WorktreeError("Base repository doesn't exist: $path")

    class WorktreeAlreadyExists(val path: String) :
        WorktreeError("Worktree directory already exists: $path")

    class InvalidTaskId(val rawInput: String) :
        WorktreeError("Invalid task id: \"$rawInput\"")

    class LocalFilesUnavailable(val basePath: String, val files: List<UnavailableLocalFile>) :
        WorktreeError(localFilesUnavailableMessage(basePath, files))

    class GitCommandFailed(
        val command: List<String>,
        val exitCode: Int,
        val errorOutput: String,
    ) : WorktreeError(
        "Git command failed (exit code $exitCode): ${command.joinToString(" ")}" +
            if (errorOutput.isNotBlank()) "\n$errorOutput" else "",
    )

    class RepositoryNotFound(val path: String) :
        WorktreeError("No Git repository found at $path")

    class BareRepositoryNotSupported(val path: String) :
        WorktreeError("$path is a bare repository; bare repositories aren't supported yet")

    object RepositoryHasNoCommits :
        WorktreeError("The repository has no commits yet; make a first commit and try again")

    class LocalFileUnreadable(val path: String, val reason: String?) :
        WorktreeError("Can't read $path" + if (reason.isNullOrBlank()) "" else ": $reason")

    class ProjectNotRegistered(val path: String) :
        WorktreeError("No project is registered for $path")

    object AppNotAvailable : WorktreeError(
        "The ShogunAi desktop app isn't available from this install; install the app to use 'shogun app'",
    )

    class UpdateCheckFailed(val reason: String?) :
        WorktreeError("Couldn't check for updates" + if (reason.isNullOrBlank()) "" else ": $reason")

    class NotAWorktree(val path: String) :
        WorktreeError("$path is not a linked worktree of this repository")

    class InvalidRepositoryConfig(val path: String, val detail: String?) :
        WorktreeError("Can't read $path" + if (detail.isNullOrBlank()) "" else ": $detail")

    class RepositoryConfigChanged(val path: String) :
        WorktreeError("$path changed on disk since it was read")

    class SecretCopyFailed(cause: Throwable) :
        WorktreeError("Failed to copy secret files: ${cause.message}", cause)

    class LinkedFolderOccupied(val entry: String, val worktreePath: String) :
        WorktreeError("Can't link $entry: the worktree's $entry folder holds files ShogunAi didn't copy")

    class PostCreateCommandFailed(
        val worktreePath: String,
        val command: List<String>,
        val exitCode: Int?,
        val errorOutput: String,
    ) : WorktreeError(
        "Post-creation command failed" + (exitCode?.let { " (exit code $it)" } ?: "") + ": ${command.joinToString(" ")}" +
            if (errorOutput.isNotBlank()) "\n$errorOutput" else "",
    )

    class ClipboardWriteFailed(cause: Throwable) :
        WorktreeError("Failed to copy the launch command to the clipboard: ${cause.message}", cause)

    object GitLfsNotFound : WorktreeError(
        "The repository requires Git LFS but 'git-lfs' isn't installed. " +
            "Install it from https://git-lfs.com and try again.",
    )

    class BranchNotFound(val branch: String) :
        WorktreeError("Branch not found: $branch")

    class StartPointNotFound(val ref: String) :
        WorktreeError("Start point doesn't name a commit: $ref")

    class NotABranch(val ref: String) :
        WorktreeError("\"$ref\" isn't a local branch or a branch of a remote")

    class ProcessListingFailed(val command: List<String>, val exitCode: Int) :
        WorktreeError("Couldn't list processes (exit code $exitCode): ${command.joinToString(" ")}")

    class DockerUnavailable(val reason: String?) :
        WorktreeError("Docker isn't available" + if (reason.isNullOrBlank()) "" else ": $reason")

    class ComposeCommandFailed(val project: String, val detail: String) :
        WorktreeError("Docker Compose failed for $project: $detail")

    class NoFreePort(val from: Int, val to: Int) :
        WorktreeError("No free port left between $from and $to for this worktree")

    class NoEmulatorPort(val from: Int, val to: Int) :
        WorktreeError("No emulator port left between $from and $to; stop or remove other worktrees' emulators")

    class ResourcePoolFull(
        val kind: ResourceKind,
        val limit: Int,
        val holders: List<PoolHolder>,
        val waiting: Int = 0,
    ) : WorktreeError(
        "The limit of $limit ${kind.pluralName(limit)} is reached and none can be released: " +
            holders.holderList() + waitingNote(waiting) +
            ". Wait for one with --wait, stop one, or raise the limit in Preferences",
    )

    class DeviceWaitTimedOut(
        val kind: ResourceKind,
        val waited: Duration,
        val limit: Int,
        val holders: List<PoolHolder>,
    ) : WorktreeError(
        "Waited ${waited.toWaitText()} for ${kind.article()} ${kind.singularName()} and none could be released: " +
            holders.holderList() + ". Stop one, or raise the limit of $limit in Preferences",
    )

    class DesktopTurnTaken(val holder: DesktopTurn?, val waiting: Int = 0) : WorktreeError(
        desktopTakenSummary(holder, waiting) + ". Wait for it with --wait",
    ) {
        val summary: String get() = desktopTakenSummary(holder, waiting)
    }

    class DesktopTurnWaitTimedOut(val waited: Duration, val holder: DesktopTurn?) : WorktreeError(
        "Waited ${waited.toWaitText()} for the desktop" +
            (holder?.let { ", still taken by ${it.displayName} until ${it.expiryClockTime()}" } ?: ", still behind other waiters"),
    )

    class DesktopTurnHeldByOther(val holder: DesktopTurn) : WorktreeError(
        "The desktop turn belongs to ${holder.displayName}; only that worktree can release it",
    )

    class AndroidSdkNotFound(val checked: List<String>) :
        WorktreeError("Android SDK not found. Checked: ${checked.joinToString()}")

    object NoAvdAvailable : WorktreeError(
        "No Android Virtual Device found. Create one in Android Studio's Device Manager and try again.",
    )

    class EmulatorBootTimeout(val serial: String, val seconds: Long) :
        WorktreeError("$serial didn't finish booting within $seconds s; it's still starting")

    class EmulatorExitedEarly(val serial: String, val lastLogLines: String) :
        WorktreeError(
            "The emulator process of $serial exited before it finished booting" +
                if (lastLogLines.isNotBlank()) ":\n$lastLogLines" else "",
        )

    class AndroidToolFailed(val command: List<String>, val exitCode: Int, val output: String) :
        WorktreeError(
            "Android tool failed (exit code $exitCode): ${command.joinToString(" ")}" +
                if (output.isNotBlank()) "\n$output" else "",
        )

    object NoIosRuntime : WorktreeError(
        "No iOS simulator runtime is installed. Install one from Xcode's Settings > Components and try again.",
    )

    class SimulatorBootTimeout(val name: String, val seconds: Long) :
        WorktreeError("The simulator \"$name\" didn't finish booting within $seconds s; it's still starting")

    object IosSimulatorProviderNotDeclared : WorktreeError(
        "This repository doesn't use the iOS simulator provider. Add \"providers\": { \"ios-simulator\": {} } to .shogunai.json.",
    )

    object IosSimulatorsNotSupported : WorktreeError("iOS simulators need macOS with Xcode installed.")

    class AppleToolFailed(val command: List<String>, val exitCode: Int, val output: String) :
        WorktreeError(
            "Xcode tool failed (exit code $exitCode): ${command.joinToString(" ")}" +
                if (output.isNotBlank()) "\n$output" else "",
        )

    object EmulatorProviderNotDeclared : WorktreeError(
        "This repository doesn't use the emulator provider. Add \"providers\": { \"emulator\": {} } to .shogunai.json.",
    )

    object DesktopAppProviderNotDeclared : WorktreeError(
        "This repository doesn't use the desktop app provider. " +
            "Add \"providers\": { \"desktop-app\": { \"module\": \":desktopApp\" } } to .shogunai.json, naming the app's Gradle module.",
    )

    object HeadlessAppsNotSupported : WorktreeError("Running a desktop app in the background is only supported on macOS.")

    class HeadlessAppLaunchFailed(val logPath: String, val lastLogLines: String) :
        WorktreeError(
            "Launching the desktop app failed; full log in $logPath" +
                if (lastLogLines.isNotBlank()) ":\n$lastLogLines" else "",
        )

    class HeadlessAppExitedEarly(val logPath: String, val lastLogLines: String) :
        WorktreeError(
            "The desktop app exited before showing its window; full log in $logPath" +
                if (lastLogLines.isNotBlank()) ":\n$lastLogLines" else "",
        )

    class HeadlessAppWindowTimeout(val pid: Long, val seconds: Long) :
        WorktreeError("The desktop app (pid $pid) showed no window within $seconds s; it's still running, stop it with shogun headless stop")

    object HeadlessAppNotRunning : WorktreeError("This worktree's desktop app isn't running. Start it with shogun headless start.")

    object HeadlessAppHasNoWindow : WorktreeError("This worktree's desktop app has no window to capture right now.")

    class MacWindowToolFailed(val detail: String) :
        WorktreeError("A macOS window tool failed" + if (detail.isBlank()) "" else ": $detail")

    class HeadlessAppStopFailed(val reason: String?) :
        WorktreeError("Couldn't stop this worktree's desktop app" + if (reason.isNullOrBlank()) "" else ": $reason")

    class MacPermissionMissing(val permission: MacPermission) :
        WorktreeError(
            "macOS hasn't granted ${permission.displayName} to the application that runs shogun (such as your terminal). " +
                "Grant it in System Settings › ${permission.settingsPane} and try again.",
        )

    class EnvironmentFileTracked(val path: String) :
        WorktreeError("Won't write the environment file over $path because Git tracks it")

    class EnvironmentFileOutsideWorktree(val path: String) :
        WorktreeError("Won't write the environment file $path because its real location is outside the worktree")

    class InvalidRef(val ref: String) :
        WorktreeError("Invalid branch or start point: \"$ref\"")

    class EnvironmentFileWriteFailed(val path: String, cause: Throwable) :
        WorktreeError("Failed to write the environment file $path: ${cause.message}", cause)

    class BranchAlreadyCheckedOut(val branch: String, val path: String?) :
        WorktreeError(
            "Branch '$branch' is already checked out" + if (path != null) " at $path" else "",
        )

    class InvalidClaudeSettings(val path: String, val detail: String) :
        WorktreeError("Claude Code's settings file $path isn't valid: $detail")

    object HerdrNotInstalled : WorktreeError(
        "Herdr isn't installed: the herdr command wasn't found. Install it from https://herdr.dev",
    )

    class HerdrCommandFailed(val command: List<String>, val detail: String) :
        WorktreeError("Herdr couldn't open the worktree: $detail")

    class HerdrAgentNotStarted(val paneId: String, val command: String) :
        WorktreeError("The agent `$command` didn't start in Herdr pane $paneId within $AGENT_START_SECONDS seconds; the pane is left open")

    object OrcaNotInstalled : WorktreeError(
        "Orca isn't installed: the orca command wasn't found. Install it from https://onorca.dev",
    )

    object OrcaNotStarted : WorktreeError(
        "Orca didn't start: it wasn't accepting commands $ORCA_START_SECONDS seconds after being opened",
    )

    class OrcaCommandFailed(val command: List<String>, val detail: String) :
        WorktreeError("Orca couldn't open the worktree: $detail")

    class OrcaAgentNotStarted(val terminalHandle: String, val command: String) :
        WorktreeError("The agent `$command` didn't start in Orca terminal $terminalHandle within $AGENT_START_SECONDS seconds; the terminal is left open")

    class OrcaMultirepoFolderNotOpen(val folder: String) : WorktreeError(
        "Orca has no workspace for $folder, and only Orca's app can add a folder that isn't a Git repository. " +
            "In Orca, add $folder as a project and import its repositories as a group, then add a folder workspace to that group; " +
            "or add it and choose \"Open as Folder\". Then try again",
    )

    class TerminalLaunchFailed(val terminal: TerminalId, val detail: String) :
        WorktreeError("${terminal.displayName} couldn't open the worktree" + if (detail.isBlank()) "" else ": $detail")

    class TerminalAutomationRefused(val terminal: TerminalId) :
        WorktreeError(
            "macOS didn't allow ShogunAi to control ${terminal.displayName}. " +
                "Allow it in System Settings > Privacy & Security > Automation.",
        )

    class TerminalUnsupported(val terminal: TerminalId, val reason: String) :
        WorktreeError("${terminal.displayName} can't be used: $reason")

    class EditorLaunchFailed(val editor: EditorId, val detail: String) :
        WorktreeError("${editor.displayName} couldn't open the worktree" + if (detail.isBlank()) "" else ": $detail")

    class ClaudeDesktopLaunchFailed(val detail: String) :
        WorktreeError("Claude Desktop couldn't open the worktree" + if (detail.isBlank()) "" else ": $detail")

    class FolderOpenFailed(val path: String, val detail: String) :
        WorktreeError("Couldn't show $path" + if (detail.isBlank()) "" else ": $detail")

    class WorktreeTargetNotFound(val target: String) :
        WorktreeError("No worktree matches \"$target\"")

    class AmbiguousWorktreeTarget(val target: String, val candidates: List<String>) :
        WorktreeError("\"$target\" matches more than one worktree:\n" + candidates.joinToString("\n") { "  $it" })

    class CannotRemoveMainWorktree(val path: String) :
        WorktreeError("$path is the main worktree and can't be removed")

    class AgentRunningInWorktree(val path: String, val agents: List<String>) :
        WorktreeError("${agents.joinToString()} is running in $path")

    class InvalidMultirepo(val path: String, val detail: String) :
        WorktreeError("$path: $detail")

    class MultirepoNotFound(val path: String) :
        WorktreeError("No multirepo found at $path")

    class MultirepoNeedsReview(val path: String) :
        WorktreeError("$path isn't approved on this machine; review it with 'shogun multirepo trust'")

    class WorktreeFolderInUse(
        val path: String,
        val unregistered: Boolean,
        val command: List<String>,
        val exitCode: Int,
        val errorOutput: String,
    ) : WorktreeError(
        "A program is using $path, so it can't be deleted" +
            (if (unregistered) "; Git already unregistered the worktree, so removing it again finishes the job" else "") +
            if (errorOutput.isNotBlank()) ":\n$errorOutput" else "",
    )

    class BaseBranchNotFound(val repository: String) :
        WorktreeError("Couldn't resolve the base branch of $repository; set the project's main branch")

    class TourCreationFailed(val command: List<String>, val detail: String) :
        WorktreeError("Couldn't create the tour's sample repository: ${command.joinToString(" ")}" + if (detail.isNotBlank()) "\n$detail" else "")

    class TourAlreadyExists(val path: String) :
        WorktreeError("The tour's folder already exists: $path")

}

private fun List<PoolHolder>.holderList(): String =
    joinToString("; ") { "${it.displayName} (${it.state.name.lowercase()}${it.protectionNote()})" }

private fun ResourceKind.singularName(): String = name.lowercase().replace('_', ' ')

private fun ResourceKind.pluralName(count: Int): String = singularName() + if (count == 1) "" else "s"

private fun ResourceKind.article(): String = if (this == ResourceKind.EMULATOR) "an" else "a"

private fun waitingNote(waiting: Int): String = when (waiting) {
    0 -> ""
    1 -> "; 1 worktree is waiting for one"
    else -> "; $waiting worktrees are waiting for one"
}

private fun desktopWaitingNote(waiting: Int): String = when (waiting) {
    0 -> ""
    1 -> "; 1 worktree is waiting for it"
    else -> "; $waiting worktrees are waiting for it"
}

private fun desktopTakenSummary(holder: DesktopTurn?, waiting: Int): String =
    holder?.let { "The desktop is taken by ${it.displayName} until ${it.expiryClockTime()}" + desktopWaitingNote(waiting) } ?: desktopQueueNote(waiting)

private fun desktopQueueNote(waiting: Int): String =
    if (waiting == 1) "1 worktree is waiting for the desktop first" else "$waiting worktrees are waiting for the desktop first"

private fun DesktopTurn.expiryClockTime(): String = Instant.ofEpochMilli(expiresAt).toLocalClockTime()

fun Duration.toWaitText(): String = when {
    toMinutes() >= 1 && toSecondsPart() == 0 -> "${toMinutes()} min"
    toMinutes() >= 1 -> "${toMinutes()} min ${toSecondsPart()} s"
    else -> "$seconds s"
}

private fun PoolHolder.protectionNote(): String = when (protection) {
    HolderProtection.AGENT_WORKING -> ", agent working"
    HolderProtection.LEASED -> ", lease until ${leaseExpiresAt?.toLocalClockTime()}"
    null -> ""
}
