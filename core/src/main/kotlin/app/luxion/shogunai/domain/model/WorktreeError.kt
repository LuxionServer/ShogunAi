package app.luxion.shogunai.domain.model

sealed class WorktreeError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class BaseRepositoryNotFound(val path: String) :
        WorktreeError("Base repository doesn't exist: $path")

    class WorktreeAlreadyExists(val path: String) :
        WorktreeError("Worktree directory already exists: $path")

    class InvalidTaskId(val rawInput: String) :
        WorktreeError("Invalid task id: \"$rawInput\"")

    class SecretFileNotFound(val files: List<String>, val basePath: String) :
        WorktreeError("Secret files not found in $basePath: ${files.joinToString()}")

    class GitCommandFailed(
        val command: List<String>,
        val exitCode: Int,
        val errorOutput: String,
    ) : WorktreeError(
        "Git command failed (exit code $exitCode): ${command.joinToString(" ")}" +
            if (errorOutput.isNotBlank()) "\n$errorOutput" else "",
    )

    class InvalidRepositoryConfig(val path: String, val detail: String?) :
        WorktreeError("Can't read $path" + if (detail.isNullOrBlank()) "" else ": $detail")

    class SecretCopyFailed(cause: Throwable) :
        WorktreeError("Failed to copy secret files: ${cause.message}", cause)

    class ClipboardWriteFailed(cause: Throwable) :
        WorktreeError("Failed to copy the launch command to the clipboard: ${cause.message}", cause)

    object GitLfsNotFound : WorktreeError(
        "The repository requires Git LFS but 'git-lfs' isn't installed. " +
            "Install it from https://git-lfs.com and try again.",
    )

    class BranchNotFound(val branch: String) :
        WorktreeError("Branch not found: $branch")

    class ProcessListingFailed(val command: List<String>, val exitCode: Int) :
        WorktreeError("Couldn't list processes (exit code $exitCode): ${command.joinToString(" ")}")

    class BranchAlreadyCheckedOut(val branch: String, val path: String?) :
        WorktreeError(
            "Branch '$branch' is already checked out" + if (path != null) " at $path" else "",
        )
}
