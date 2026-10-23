package app.luxion.shogunai.ui.i18n

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.model.HolderProtection
import app.luxion.shogunai.domain.model.PoolHolder
import app.luxion.shogunai.domain.model.PoolHolderState
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.toLocalClockTime
import app.luxion.shogunai.domain.usecase.GitFailureCause

internal fun englishErrorTexts(error: Throwable, gitCause: GitFailureCause): ErrorTexts = when (error) {
    is WorktreeError.GitCommandFailed -> englishGitTexts(gitCause)
    is WorktreeError -> englishWorktreeTexts(error)
    is CommandExecutionException -> ErrorTexts(
        "Couldn't start ${error.command.first()}.",
        "Install ${error.command.first()} or add it to your PATH, then try again.",
    )
    else -> ErrorTexts("Something unexpected went wrong.", "Check the technical details.")
}

private fun englishGitTexts(cause: GitFailureCause): ErrorTexts = when (cause) {
    is GitFailureCause.LockFileExists -> ErrorTexts(
        "Another Git process seems to be running in the repository.",
        "Wait for it to finish, or remove ${cause.path?.let { "the lock file $it" } ?: "the lock file"} if no Git process is running.",
    )
    is GitFailureCause.PermissionDenied -> ErrorTexts(
        "Git was denied permission${cause.path?.let { " on $it" }.orEmpty()}.",
        "Check the permissions of that folder and try again.",
    )
    GitFailureCause.NotARepository -> ErrorTexts(
        "The folder isn't a Git repository.",
        "Check the repository path in the project's settings.",
    )
    is GitFailureCause.BranchExists -> ErrorTexts(
        "The branch \"${cause.name}\" already exists.",
        "Use another task id, or create the worktree from that existing branch.",
    )
    GitFailureCause.LocalChanges -> ErrorTexts(
        "The worktree has local changes.",
        "Commit or discard them first, or remove the worktree forcing it.",
    )
    GitFailureCause.Unknown -> ErrorTexts("The Git operation failed.", "Check the technical details.")
}

private fun englishWorktreeTexts(error: WorktreeError): ErrorTexts = when (error) {
    is WorktreeError.BaseRepositoryNotFound -> ErrorTexts(
        "The project's repository wasn't found at ${error.path}.",
        "Update the repository path in the project's settings.",
    )
    WorktreeError.AppNotAvailable -> ErrorTexts(
        "The ShogunAi desktop app isn't available from this install.",
        "Install the desktop app to use 'shogun app'.",
    )
    is WorktreeError.ProjectNotRegistered -> ErrorTexts(
        "No project is registered for ${error.path}.",
        "Add the repository as a project first.",
    )
    is WorktreeError.RepositoryNotFound -> ErrorTexts(
        "No Git repository was found at ${error.path}.",
        "Update the repository path in the project's settings.",
    )
    is WorktreeError.BareRepositoryNotSupported -> ErrorTexts(
        "${error.path} is a bare repository, which isn't supported yet.",
        "Use a repository with a working tree.",
    )
    WorktreeError.RepositoryHasNoCommits -> ErrorTexts(
        "The repository has no commits yet.",
        "Make a first commit and try again.",
    )
    is WorktreeError.InvalidClaudeSettings -> ErrorTexts(
        "Claude Code's settings file ${error.path} isn't valid JSON, so it was left unchanged.",
        "Fix the file (${error.detail}) and try again.",
    )
     is WorktreeError.LocalFileUnreadable ->
        if (error.path.endsWith("settings.json")) {
            ErrorTexts(
                "The settings file ${error.path} couldn't be read, so the default settings are in use.",
                "The unreadable file is kept next to it. Fix it or delete it to dismiss this.",
            )
        } else {
            ErrorTexts(
                "The projects file ${error.path} couldn't be read, so no projects are shown.",
                "Fix the file. If you add or remove a project, the unreadable file is first kept next to it.",
            )
        }
    is WorktreeError.WorktreeAlreadyExists -> ErrorTexts(
        "The folder ${error.path} already exists.",
        "Use another task id, or remove the existing folder.",
    )
    is WorktreeError.InvalidTaskId -> ErrorTexts(
        "\"${error.rawInput}\" isn't a valid task id.",
        "Use only letters, digits, \"-\", \"_\" and \"/\".",
    )
    is WorktreeError.SecretFileNotFound -> ErrorTexts(
        "These local files weren't found in ${error.basePath}: ${error.files.joinToString()}.",
        "Create them in the base repository, or remove them from the project's local files.",
    )
    is WorktreeError.InvalidRepositoryConfig -> ErrorTexts(
        "Couldn't read ${error.path}.",
        "Fix the file following the technical details.",
    )
    is WorktreeError.RepositoryConfigChanged -> ErrorTexts(
        "${error.path} changed on disk since the settings were opened.",
        "Reload the settings and apply your changes again.",
    )
    is WorktreeError.UpdateCheckFailed -> ErrorTexts(
        "Couldn't check for updates" + (error.reason?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "."),
        "Check your connection and try again later.",
    )
    is WorktreeError.NotAWorktree -> ErrorTexts(
        "${error.path} isn't a worktree of this repository.",
        "Reload the list; the folder may have been removed.",
    )
    is WorktreeError.SecretCopyFailed -> ErrorTexts(
        "Couldn't copy the local files into the worktree.",
        "Check that the worktree folder is writable.",
    )
    is WorktreeError.PostCreateCommandFailed -> ErrorTexts(
        "The worktree was created, but one of the repository's post-creation commands failed.",
        "Read the technical details, fix the command or its cause, and run it inside the worktree.",
    )
    is WorktreeError.ClipboardWriteFailed -> ErrorTexts(
        "Couldn't copy the launch command to the clipboard.",
        "Try again; another app may be holding the clipboard.",
    )
    WorktreeError.GitLfsNotFound -> ErrorTexts(
        "The repository uses Git LFS, but git-lfs isn't installed.",
        "Install Git LFS from https://git-lfs.com and run \"git lfs install\".",
    )
    is WorktreeError.BranchNotFound -> ErrorTexts(
        "The branch \"${error.branch}\" wasn't found.",
        "Reload the branch list; the branch may have been deleted.",
    )
    is WorktreeError.StartPointNotFound -> ErrorTexts(
        "\"${error.ref}\" doesn't name a commit.",
        "Choose an existing branch or commit as the start point.",
    )
    is WorktreeError.BranchAlreadyCheckedOut -> ErrorTexts(
        "The branch \"${error.branch}\" is already checked out${error.path?.let { " at $it" }.orEmpty()}.",
        "Open the existing worktree for that branch, or pick another branch.",
    )
    is WorktreeError.ProcessListingFailed -> ErrorTexts(
        "Couldn't list the running processes.",
        "Agent detection is unavailable; make sure lsof is installed.",
    )
    is WorktreeError.DockerUnavailable -> ErrorTexts(
        "Docker isn't available" + if (error.reason.isNullOrBlank()) "." else ": ${error.reason}",
        "Start Docker and try again.",
    )
    is WorktreeError.NoFreePort -> ErrorTexts(
        "No free port is left between ${error.from} and ${error.to}.",
        "Free ports in that range, or remove unused worktrees.",
    )
    is WorktreeError.NoEmulatorPort -> ErrorTexts(
        "No emulator port is left between ${error.from} and ${error.to}.",
        "Stop emulators of other worktrees, or remove unused worktrees.",
    )
    is WorktreeError.ResourcePoolFull -> ErrorTexts(
        "The limit of ${error.limit} ${englishKindName(error.kind)} is reached and none is free to release: " +
            error.holders.joinToString("; ") { "${it.displayName} (${englishHolderState(it.state)}${englishProtection(it)})" } + ".",
        "Wait for a lease to expire, stop one of them, or raise the limit in Preferences.",
    )
    is WorktreeError.AndroidSdkNotFound -> ErrorTexts(
        "The Android SDK wasn't found.",
        "Install the Android SDK or set ANDROID_HOME.",
    )
    WorktreeError.NoAvdAvailable -> ErrorTexts(
        "No Android emulator is available.",
        "Create one in Android Studio's Device Manager.",
    )
    is WorktreeError.EmulatorBootTimeout -> ErrorTexts(
        "${error.serial} didn't finish booting within ${error.seconds} s.",
        "Try again; the first boot can be slow.",
    )
    is WorktreeError.EmulatorExitedEarly -> ErrorTexts(
        "The emulator of ${error.serial} stopped before it finished booting.",
        "Check the emulator's log in the technical details; the AVD may be broken.",
    )
    is WorktreeError.AndroidToolFailed -> ErrorTexts(
        "An Android tool failed.",
        "Check the technical details.",
    )
    WorktreeError.NoIosRuntime -> ErrorTexts(
        "No iOS simulator runtime is installed.",
        "Install one in Xcode's Settings > Components.",
    )
    is WorktreeError.SimulatorBootTimeout -> ErrorTexts(
        "The simulator \"${error.name}\" didn't finish booting within ${error.seconds} s.",
        "Try again; the first boot can be slow.",
    )
    WorktreeError.IosSimulatorProviderNotDeclared -> ErrorTexts(
        "This repository doesn't use the iOS simulator provider.",
        "Turn the provider on in the project's settings.",
    )
    WorktreeError.EmulatorProviderNotDeclared -> ErrorTexts(
        "This repository doesn't use the emulator provider.",
        "Turn the provider on in the project's settings.",
    )
    WorktreeError.IosSimulatorsNotSupported -> ErrorTexts(
        "iOS simulators aren't available on this computer.",
        "They need macOS with Xcode installed.",
    )
    is WorktreeError.AppleToolFailed -> ErrorTexts(
        "An Xcode tool failed.",
        "Check the technical details.",
    )
    is WorktreeError.EnvironmentFileTracked -> ErrorTexts(
        "The environment file \"${error.path}\" is tracked by Git, so it wasn't overwritten.",
        "Choose an environment file that Git doesn't track.",
    )
    is WorktreeError.EnvironmentFileOutsideWorktree -> ErrorTexts(
        "The environment file \"${error.path}\" resolves outside the worktree, so it wasn't written.",
        "Choose an environment file whose folders are real folders inside the worktree.",
    )
    is WorktreeError.WorktreeTargetNotFound -> ErrorTexts(
        "No worktree matches \"${error.target}\".",
        "Check the task id, path or branch.",
    )
    is WorktreeError.AmbiguousWorktreeTarget -> ErrorTexts(
        "\"${error.target}\" matches more than one worktree.",
        "Use the path of the one you mean: ${error.candidates.joinToString()}.",
    )
    is WorktreeError.CannotRemoveMainWorktree -> ErrorTexts(
        "${error.path} is the main worktree.",
        "The main worktree can't be removed.",
    )
    is WorktreeError.WorktreeFolderInUse -> ErrorTexts(
        "A program is using the folder of ${error.path}.",
        "Close any terminal, editor or sync client using it and remove the worktree again.${if (error.unregistered) " Git already unregistered it, so removing it again finishes the job." else ""}",
    )
    is WorktreeError.AgentRunningInWorktree -> ErrorTexts(
        "${error.agents.joinToString()} is running in ${error.path}.",
        "Stop it first, or remove the worktree forcing it.",
    )
    is WorktreeError.InvalidMultirepo -> ErrorTexts(
        "The multirepo ${error.path} can't be used: ${error.detail}.",
        "Fix it with the shogun multirepo commands or the multirepo's settings.",
    )
    is WorktreeError.MultirepoNotFound -> ErrorTexts(
        "No multirepo was found at ${error.path}.",
        "Add the folder as a multirepo first.",
    )
    is WorktreeError.BaseBranchNotFound -> ErrorTexts(
        "The base branch of ${error.repository} couldn't be resolved.",
        "Set the project's main branch in its settings.",
    )
    is WorktreeError.InvalidRef -> ErrorTexts(
        "\"${error.ref}\" isn't a valid branch or start point.",
        "Branch names and start points can't start with \"-\".",
    )
    is WorktreeError.EnvironmentFileWriteFailed -> ErrorTexts(
        "Couldn't write the environment file \"${error.path}\".",
        "Check that the folder is writable.",
    )
    WorktreeError.HerdrNotInstalled -> ErrorTexts(
        "Herdr isn't installed.",
        "Install it from https://herdr.dev and try again.",
    )
    is WorktreeError.HerdrCommandFailed -> ErrorTexts(
        "Herdr couldn't open the worktree.",
        "Check the technical details.",
    )
    is WorktreeError.TerminalLaunchFailed -> ErrorTexts(
        "${error.terminal.displayName} couldn't open the worktree.",
        "Check the technical details.",
    )
    is WorktreeError.TerminalAutomationRefused -> ErrorTexts(
        "macOS didn't allow ShogunAi to control ${error.terminal.displayName}.",
        "Allow it in System Settings > Privacy & Security > Automation, then try again.",
    )
    is WorktreeError.TerminalUnsupported -> ErrorTexts(
        "${error.terminal.displayName} can't be used: ${error.reason}.",
        "Choose another terminal in Preferences.",
    )
    is WorktreeError.EditorLaunchFailed -> ErrorTexts(
        "${error.editor.displayName} couldn't open the worktree.",
        "Check the technical details.",
    )
    is WorktreeError.ClaudeDesktopLaunchFailed -> ErrorTexts(
        "Claude Desktop couldn't open the worktree.",
        "Check the technical details.",
    )
    is WorktreeError.FolderOpenFailed -> ErrorTexts(
        "Couldn't show \"${error.path}\".",
        "Check the technical details.",
    )
    is WorktreeError.GitCommandFailed -> englishGitTexts(GitFailureCause.Unknown)
}

private fun englishKindName(kind: ResourceKind): String = when (kind) {
    ResourceKind.EMULATOR -> "emulators"
    ResourceKind.IOS_SIMULATOR -> "iOS simulators"
    ResourceKind.GRADLE_DAEMON -> "Gradle daemons"
}

private fun englishHolderState(state: PoolHolderState): String = state.name.lowercase()

private fun englishProtection(holder: PoolHolder): String = when (holder.protection) {
    HolderProtection.AGENT_WORKING -> ", agent working"
    HolderProtection.LEASED -> ", lease until ${holder.leaseExpiresAt?.toLocalClockTime()}"
    null -> ""
}
