package app.luxion.shogunai.domain.usecase

sealed interface GitFailureCause {
    data class LockFileExists(val path: String?) : GitFailureCause
    data class PermissionDenied(val path: String?) : GitFailureCause
    data object NotARepository : GitFailureCause
    data class BranchExists(val name: String) : GitFailureCause
    data object LocalChanges : GitFailureCause
    data object Unknown : GitFailureCause
}

fun classifyGitFailure(stderr: String): GitFailureCause {
    LOCK_FILE.find(stderr)?.let { return GitFailureCause.LockFileExists(it.groupValues[1]) }
    if (ANOTHER_PROCESS in stderr) return GitFailureCause.LockFileExists(null)
    if (PERMISSION_DENIED in stderr) return GitFailureCause.PermissionDenied(QUOTED_PATH.find(stderr)?.let { it.groupValues[1] })
    if (NOT_A_REPOSITORY in stderr) return GitFailureCause.NotARepository
    BRANCH_EXISTS.find(stderr)?.let { return GitFailureCause.BranchExists(it.groupValues[1]) }
    if (LOCAL_CHANGES in stderr) return GitFailureCause.LocalChanges
    return GitFailureCause.Unknown
}

fun GitFailureCause.hint(): String? = when (this) {
    is GitFailureCause.LockFileExists ->
        "Another Git process seems to be running. Wait for it to finish, or remove ${path ?: "the lock file"} if none is."
    is GitFailureCause.PermissionDenied ->
        "Git was denied permission${path?.let { " on $it" }.orEmpty()}. Check the permissions of that folder."
    GitFailureCause.NotARepository -> "The folder isn't a Git repository. Check the repository path."
    is GitFailureCause.BranchExists ->
        "The branch $name already exists. Use another task id, or run 'shogun new --branch $name' to use it."
    GitFailureCause.LocalChanges -> "The worktree has local changes. Commit or discard them first."
    GitFailureCause.Unknown -> null
}

fun isFolderInUseFailure(stderr: String): Boolean =
    FAILED_TO_DELETE in stderr && FOLDER_LOCK_REASONS.any { it in stderr }

fun isNotAWorkingTreeFailure(stderr: String): Boolean = NOT_A_WORKING_TREE in stderr

fun isPathTooLongFailure(stderr: String): Boolean = FILENAME_TOO_LONG in stderr

fun isLockedWorktreeFailure(stderr: String): Boolean = LOCKED_WORKTREE in stderr

fun lockReason(stderr: String): String? = LOCK_REASON.find(stderr)?.let { it.groupValues[1].trim().ifEmpty { null } }

private val LOCK_FILE = Regex("""Unable to create '([^']*\.lock)': File exists""")
private const val ANOTHER_PROCESS = "Another git process seems to be running"
private const val PERMISSION_DENIED = "Permission denied"
private val QUOTED_PATH = Regex("""'([^']+)'""")
private const val NOT_A_REPOSITORY = "not a git repository"
private val BRANCH_EXISTS = Regex("""[Aa] branch named '([^']*)' already exists""")
private const val LOCAL_CHANGES = "contains modified or untracked files"
private const val FAILED_TO_DELETE = "failed to delete"
private val FOLDER_LOCK_REASONS = listOf("Permission denied", "Device or resource busy", "being used by another process")
private const val NOT_A_WORKING_TREE = "is not a working tree"
private const val FILENAME_TOO_LONG = "Filename too long"
private const val LOCKED_WORKTREE = "cannot remove a locked working tree"
private val LOCK_REASON = Regex("""cannot remove a locked working tree, lock reason: (.*)""")
