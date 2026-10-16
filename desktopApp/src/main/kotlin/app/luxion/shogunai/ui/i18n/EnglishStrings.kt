package app.luxion.shogunai.ui.i18n

object EnglishStrings : Strings {
    override val languageName = "English"

    override val themeLight = "Light"
    override val themeDark = "Dark"
    override val themeSystem = "Automatic"

    override val reload = "Reload"
    override val cancel = "Cancel"
    override val delete = "Delete"
    override val browse = "Browse…"
    override val expand = "Expand"
    override val collapse = "Collapse"

    override val selectFolderDialogTitle = "Select a folder"
    override val selectLocalFilesDialogTitle = "Select local files"

    override val projectsTitle = "Projects"
    override val newProject = "New project"
    override val search = "Search"
    override val noProjectsYet = "No projects yet. Create one to get started."
    override fun noProjectsMatch(query: String) = "No project matches \"$query\"."
    override val editProject = "Edit project"
    override val deleteProject = "Delete project"
    override val deleteProjectTitle = "Delete project?"
    override fun deleteProjectMessage(projectName: String) = "\"$projectName\" will be removed from the project list."

    override val projectConfigTitle = "Project settings"
    override val identitySection = "Identity"
    override val nameLabel = "Name"
    override val nameEmptyError = "The name can't be empty"
    override val pathsSection = "Paths"
    override val baseRepositoryPathLabel = "Base repository path"
    override val baseRepositoryPathEmptyError = "The repository path can't be empty"
    override val worktreesRootLabel = "Worktrees root folder"
    override val mainBranchLabel = "Main branch"
    override val mainBranchPlaceholder = "Detected from the repository"
    override val localFilesSection = "Local files"
    override val removeLocalFile = "Remove local file"
    override val newLocalFileLabel = "New local file"
    override val localFilesDescription =
        "Exact paths copied into each new worktree. They're stored on this machine only."
    override val repositoryProvisioningSection = "From the repository"
    override val repositoryProvisioningDescription =
        "Patterns versioned in .shogunai.json and .worktreeinclude. Every untracked file that matches is copied into each new worktree."
    override val repositoryProvisioningEmpty = "The repository declares nothing to copy."
    override fun repositoryConfigUnreadable(path: String) =
        "Can't read $path. Fix it before creating worktrees."
    override val moveToRepositoryConfig = "Move to .shogunai.json"
    override val pendingSave = "on save"
    override fun saveFailed(detail: String?) = "Couldn't save" + detail.asSuffix()
    override val add = "Add"
    override val agentSection = "Agent"
    override val agentCommandLabel = "Agent command"
    override val useHeadroomLabel = "Use Headroom (headroom wrap)"
    override val headroomDescription = "Headroom wraps the agent command to compress the context before launching it."
    override val save = "Save"
    override val discardChangesTitle = "Discard changes?"
    override val discardChangesMessage = "There are unsaved changes that will be lost if you continue."
    override val discard = "Discard"
    override val keepEditing = "Keep editing"

    override val closeError = "Close error"
    override val closeMessage = "Close message"
    override val newWorktreeSection = "New worktree"
    override val newBranchMode = "New branch"
    override val existingBranchMode = "Existing branch"
    override val taskIdLabel = "Task id"
    override val taskIdHint = "E.g.: TASK-123 (spaces become hyphens)"
    override val invalidTaskId = "Invalid task id"
    override val createWorktree = "Create worktree"
    override val loadingBranches = "Loading branches..."
    override val noBranchesAvailable = "No branches available to create a worktree."
    override fun branchAlreadyHasWorktree(branch: String) = "$branch (already has a worktree)"
    override val selectBranchPlaceholder = "Select a branch"
    override val otherBranchesGroup = "other"
    override val worktreesTitle = "Worktrees"
    override val removeWorktreeTitle = "Delete worktree?"
    override fun removeWorktreeMessage(path: String) = "Delete the worktree \"$path\"?"
    override val forceRemoveTitle = "Force deletion?"
    override fun forceRemoveMessage(path: String) =
        "\"$path\" has unsaved changes. They will be lost if the worktree is deleted."
    override fun alsoDeleteLocalBranch(branch: String) = "Also delete the local branch ($branch)"
    override val forceRemove = "Force deletion"
    override val detachedHead = "(detached)"
    override fun uncommittedChanges(count: Int) = if (count == 1) "1 change" else "$count changes"
    override val upstream = "upstream"
    override fun mergedInto(branch: String) = "merged into $branch"
    override val statusUnavailable = "Status unavailable"
    override val folderMissing = "Folder missing"
    override val originShogunAi = "ShogunAi"
    override val originClaudeCode = "Claude Code"
    override val originOther = "other location"
    override val inactive = "inactive"
    override fun worktreesSummary(count: Int, totalSize: String) =
        (if (count == 1) "1 worktree" else "$count worktrees") + " · $totalSize"
    override val locked = "locked"
    override val cleanUp = "Clean up"
    override val cleanUpTitle = "Clean up worktrees"
    override fun cleanUpMessage(count: Int) =
        if (count == 1) "This worktree is merged or its folder is missing:" else "These $count worktrees are merged or their folder is missing:"
    override val alsoDeleteLocalBranches = "Also delete their local branches"
    override fun worktreesCleanedUp(count: Int) = if (count == 1) "1 worktree cleaned up" else "$count worktrees cleaned up"
    override fun cleanupFailed(path: String, detail: String) = "Couldn't remove \"$path\": $detail"
    override fun branchKept(branch: String) = "Branch \"$branch\" kept: it isn't merged into the main branch"
    override val simulatorSection = "iOS simulator"
    override val simulatorSectionDescription =
        "This repository gives each worktree its own iOS simulator (IOS_SIMULATOR_UDID). These settings apply to this machine only."
    override val simulatorRuntimeLabel = "Runtime"
    override val simulatorDeviceLabel = "Device"
    override val simulatorAutomatic = "Automatic"
    override fun notOnThisMachine(name: String) = "$name (not on this machine)"
    override val simulatorTestsWired = "KMP simulator tests use each worktree's simulator."
    override val simulatorTestsNotWired =
        "KMP simulator tests don't use each worktree's simulator yet. Add this to the module's build.gradle.kts:"
    override val copySnippet = "Copy"
    override val installInitScript = "Install for every project on this machine"
    override val installInitScriptTitle = "Install the Gradle init script?"
    override fun installInitScriptMessage(path: String) =
        "ShogunAi will write the Gradle init script $path, which makes the iOS simulator tests of every Gradle " +
            "project on this machine run on IOS_SIMULATOR_UDID when it's set. Remove it with 'shogun simulator gradle-init uninstall'."
    override val install = "Install"
    override val simulatorLabel = "iOS simulator"
    override val simulatorShutdown = "shut down"
    override val simulatorBooting = "booting"
    override val simulatorBooted = "booted"
    override val startSimulator = "Start"
    override val stopSimulator = "Stop"
    override val alsoDeleteSimulators = "Also delete their iOS simulators"
    override fun orphanedSimulators(names: List<String>) =
        "Simulators whose worktree is gone, also deleted: ${names.joinToString()}"
    override fun simulatorsDeleted(count: Int) = if (count == 1) "1 simulator deleted" else "$count simulators deleted"
    override val emulatorSection = "Android emulator"
    override val emulatorSectionDescription =
        "This repository gives each worktree its own emulator (ANDROID_SERIAL). These settings apply to this machine only."
    override val emulatorAvdLabel = "AVD"
    override val emulatorAutomaticAvd = "Automatic (the repository's preferred AVD, or the first one)"
    override fun emulatorMissingAvd(name: String) = "$name (not on this machine)"
    override val emulatorNoWindowLabel = "Run without a window"
    override val emulatorLabel = "Emulator"
    override val emulatorStopped = "stopped"
    override val emulatorBooting = "booting"
    override val emulatorRunning = "running"
    override val startEmulator = "Start"
    override val stopEmulator = "Stop"
    override val alsoStopEmulators = "Also stop their emulators"
    override fun emulatorsStopped(count: Int) = if (count == 1) "1 emulator stopped" else "$count emulators stopped"
    override val alsoStopIdleGradleDaemons = "Also stop their idle Gradle daemons"
    override val gradleDaemonsTitle = "Gradle daemons"
    override fun gradleDaemonsSummary(count: Int, totalMemory: String) =
        (if (count == 1) "1 daemon" else "$count daemons") + " · $totalMemory"
    override val noGradleDaemons = "No Gradle or Kotlin daemons running"
    override val gradleDaemonsUnavailable = "Daemons can't be inspected on this system"
    override fun gradleDaemonLabel(version: String?) = listOfNotNull("Gradle", version).joinToString(" ")
    override val kotlinDaemonLabel = "Kotlin compiler"
    override val daemonBusy = "busy"
    override fun daemonIdle(since: String) = "idle · $since"
    override val daemonStateUnknown = "state unknown"
    override val daemonOwnerRemoved = "removed worktree"
    override val daemonOwnerOther = "other project"
    override val stopDaemon = "Stop"
    override val stopIdleDaemons = "Stop idle daemons"
    override val gradleBuildCacheOff =
        "Gradle's build cache is off: add org.gradle.caching=true to gradle.properties so a new worktree reuses what other worktrees already built."
    override fun gradleWorktreeSettings(daemonHeap: String?, idleTimeoutMinutes: Int?, kotlinDaemonHeap: String?) =
        "Worktree builds: " + listOfNotNull(
            daemonHeap?.let { "$it heap" },
            idleTimeoutMinutes?.let { "idle after $it min" },
            kotlinDaemonHeap?.let { "Kotlin $it" },
        ).joinToString(" · ")
    override fun daemonsStopped(stopped: Int, kept: Int, failed: Int) = listOfNotNull(
        if (stopped == 1) "1 daemon stopped" else "$stopped daemons stopped",
        kept.takeIf { it > 0 }?.let { if (it == 1) "1 kept because it's in use" else "$it kept because they're in use" },
        failed.takeIf { it > 0 }?.let { if (it == 1) "1 couldn't be stopped" else "$it couldn't be stopped" },
    ).joinToString(", ")
    override val justNow = "just now"
    override fun minutesAgo(count: Long) = if (count == 1L) "1 minute ago" else "$count minutes ago"
    override fun hoursAgo(count: Long) = if (count == 1L) "1 hour ago" else "$count hours ago"
    override fun daysAgo(count: Long) = if (count == 1L) "1 day ago" else "$count days ago"
    override fun monthsAgo(count: Long) = if (count == 1L) "1 month ago" else "$count months ago"
    override fun yearsAgo(count: Long) = if (count == 1L) "1 year ago" else "$count years ago"
    override val copied = "Copied"
    override val copyCommand = "Copy command"
    override fun worktreeCreated(path: String) = "Worktree \"$path\" created"
    override val worktreesRepairedAfterMove = "Worktrees repaired after moving the repository"

    override fun baseRepositoryNotFound(path: String) = "Base repository doesn't exist: $path"
    override fun worktreeAlreadyExists(path: String) = "Worktree directory already exists: $path"
    override fun invalidTaskIdError(rawInput: String) = "Invalid task id: \"$rawInput\""
    override fun secretFilesNotFound(files: List<String>, basePath: String) =
        "Local files not found in $basePath: ${files.joinToString()}"
    override fun invalidRepositoryConfig(path: String, detail: String?) = "Can't read $path" + detail.asSuffix()
    override fun repositoryNotFound(path: String) = "No Git repository found at $path"
    override fun notAWorktree(path: String) = "$path is not a linked worktree of this repository"
    override fun gitCommandFailed(command: List<String>, exitCode: Int) =
        "Git command failed (exit code $exitCode): ${command.joinToString(" ")}"
    override fun secretCopyFailed(detail: String?) = "Failed to copy files into the worktree" + detail.asSuffix()
    override fun clipboardWriteFailed(detail: String?) =
        "Failed to copy the launch command to the clipboard" + detail.asSuffix()
    override val gitLfsNotFound =
        "The repository requires Git LFS but 'git-lfs' isn't installed. Install it from https://git-lfs.com and try again."
    override fun branchNotFound(branch: String) = "Branch not found: $branch"
    override fun branchAlreadyCheckedOut(branch: String, path: String?) =
        "Branch '$branch' is already checked out" + if (path != null) " at $path" else ""
    override fun noFreePort(from: Int, to: Int) = "No free port left between $from and $to for this worktree"
    override fun noEmulatorPort(from: Int, to: Int) =
        "No emulator port left between $from and $to; stop or remove other worktrees' emulators"
    override fun androidSdkNotFound(checked: List<String>) = "Android SDK not found. Checked: ${checked.joinToString()}"
    override val noAvdAvailable =
        "No Android Virtual Device found. Create one in Android Studio's Device Manager and try again."
    override fun emulatorBootTimeout(serial: String, seconds: Long) =
        "$serial didn't finish booting within $seconds s; it's still starting"
    override fun androidToolFailed(command: List<String>, exitCode: Int) =
        "Android tool failed (exit code $exitCode): ${command.joinToString(" ")}"
    override val noIosRuntime =
        "No iOS simulator runtime is installed. Install one from Xcode's Settings > Components and try again."
    override fun simulatorBootTimeout(name: String, seconds: Long) =
        "The simulator \"$name\" didn't finish booting within $seconds s; it's still starting"
    override val iosSimulatorProviderNotDeclared =
        "This repository doesn't use the iOS simulator provider. Add \"providers\": { \"ios-simulator\": {} } to .shogunai.json."
    override val iosSimulatorsNotSupported = "iOS simulators need macOS with Xcode installed."
    override fun appleToolFailed(command: List<String>, exitCode: Int) =
        "Xcode tool failed (exit code $exitCode): ${command.joinToString(" ")}"
    override val emulatorProviderNotDeclared =
        "This repository doesn't use the emulator provider. Add \"providers\": { \"emulator\": {} } to .shogunai.json."
    override fun environmentFileTracked(path: String) =
        "The environment file \"$path\" is tracked by Git, so it wasn't overwritten"
    override fun environmentFileWriteFailed(path: String, detail: String?) =
        "Couldn't write the environment file \"$path\"" + if (detail.isNullOrBlank()) "" else ": $detail"
    override fun processListingFailed(command: List<String>, exitCode: Int) =
        "Couldn't list processes (exit code $exitCode): ${command.joinToString(" ")}"
    override fun commandCouldNotStart(command: List<String>) =
        "Could not start the command: ${command.joinToString(" ")}"
    override fun unexpectedError(detail: String?) = "Unexpected error" + detail.asSuffix()
}

internal fun String?.asSuffix(): String = if (isNullOrBlank()) "" else ": $this"
