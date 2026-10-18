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
    if (PERMISSION_DENIED in stderr) return GitFailureCause.PermissionDenied(QUOTED_PATH.find(stderr)?.groupValues?.get(1))
    if (NOT_A_REPOSITORY in stderr) return GitFailureCause.NotARepository
    BRANCH_EXISTS.find(stderr)?.let { return GitFailureCause.BranchExists(it.groupValues[1]) }
    if (LOCAL_CHANGES in stderr) return GitFailureCause.LocalChanges
    return GitFailureCause.Unknown
}

private val LOCK_FILE = Regex("""Unable to create '([^']*\.lock)': File exists""")
private const val ANOTHER_PROCESS = "Another git process seems to be running"
private const val PERMISSION_DENIED = "Permission denied"
private val QUOTED_PATH = Regex("""'([^']+)'""")
private const val NOT_A_REPOSITORY = "not a git repository"
private val BRANCH_EXISTS = Regex("""[Aa] branch named '([^']*)' already exists""")
private const val LOCAL_CHANGES = "contains modified or untracked files"
