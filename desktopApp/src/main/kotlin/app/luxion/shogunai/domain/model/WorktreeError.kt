package app.luxion.shogunai.domain.model

/**
 * Domain errors of the worktree flow.
 *
 * Use cases return `Result<T>`; when they fail in an expected way, the
 * failure carries one of these variants, so the upper layer (the future
 * GUI) can decide what message to show without inspecting strings.
 */
sealed class WorktreeError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** The configured base repository doesn't exist on disk. */
    class BaseRepositoryNotFound(val path: String) :
        WorktreeError("Base repository doesn't exist: $path")

    /** There's already a directory at the worktree's destination path. */
    class WorktreeAlreadyExists(val path: String) :
        WorktreeError("Worktree directory already exists: $path")

    /** The task id, even after normalizing whitespace, isn't a valid Git ref name. */
    class InvalidTaskId(val rawInput: String) :
        WorktreeError("Invalid task id: \"$rawInput\"")

    /** One or more secret files are missing in the base repository. */
    class SecretFileNotFound(val files: List<String>, val basePath: String) :
        WorktreeError("Secret files not found in $basePath: ${files.joinToString()}")

    /** A Git command exited with a non-zero exit code. */
    class GitCommandFailed(
        val command: List<String>,
        val exitCode: Int,
        val errorOutput: String,
    ) : WorktreeError(
        "Git command failed (exit code $exitCode): ${command.joinToString(" ")}" +
            if (errorOutput.isNotBlank()) "\n$errorOutput" else "",
    )

    /** Failed to copy a secret file after creating the worktree. */
    class SecretCopyFailed(cause: Throwable) :
        WorktreeError("Failed to copy secret files: ${cause.message}", cause)

    /** No terminal emulator is available to open the worktree. */
    object NoTerminalAvailable :
        WorktreeError("No terminal emulator available was found")

    /** The operating system failed to start the terminal process. */
    class TerminalLaunchFailed(cause: Throwable) :
        WorktreeError("Failed to launch the terminal: ${cause.message}", cause)

    /** The repository requires Git LFS but the `git-lfs` binary isn't installed. */
    object GitLfsNotFound : WorktreeError(
        "The repository requires Git LFS but 'git-lfs' isn't installed. " +
            "Install it from https://git-lfs.com and try again.",
    )

    /** No local branch with this name exists. */
    class BranchNotFound(val branch: String) :
        WorktreeError("Branch not found: $branch")

    /** The requested branch is already checked out in another worktree (or the base repository). */
    class BranchAlreadyCheckedOut(val branch: String, val path: String?) :
        WorktreeError(
            "Branch '$branch' is already checked out" + if (path != null) " at $path" else "",
        )
}
