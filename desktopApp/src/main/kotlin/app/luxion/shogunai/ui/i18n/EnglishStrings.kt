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
    override val selectSecretFilesDialogTitle = "Select secret files"

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
    override val secretFilesSection = "Secret files"
    override val removeSecretFile = "Remove secret file"
    override val newSecretFileLabel = "New secret file"
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
        "Secret files not found in $basePath: ${files.joinToString()}"
    override fun gitCommandFailed(command: List<String>, exitCode: Int) =
        "Git command failed (exit code $exitCode): ${command.joinToString(" ")}"
    override fun secretCopyFailed(detail: String?) = "Failed to copy secret files" + detail.asSuffix()
    override fun clipboardWriteFailed(detail: String?) =
        "Failed to copy the launch command to the clipboard" + detail.asSuffix()
    override val gitLfsNotFound =
        "The repository requires Git LFS but 'git-lfs' isn't installed. Install it from https://git-lfs.com and try again."
    override fun branchNotFound(branch: String) = "Branch not found: $branch"
    override fun branchAlreadyCheckedOut(branch: String, path: String?) =
        "Branch '$branch' is already checked out" + if (path != null) " at $path" else ""
    override fun commandCouldNotStart(command: List<String>) =
        "Could not start the command: ${command.joinToString(" ")}"
    override fun unexpectedError(detail: String?) = "Unexpected error" + detail.asSuffix()
}

internal fun String?.asSuffix(): String = if (isNullOrBlank()) "" else ": $this"
