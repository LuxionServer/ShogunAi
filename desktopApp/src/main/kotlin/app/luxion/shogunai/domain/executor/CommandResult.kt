package app.luxion.shogunai.domain.executor

/**
 * Result of running a system command.
 *
 * Captures standard output, error output, and exit code, without
 * interpreting their meaning: each use case decides what counts as success.
 */
data class CommandResult(
    val command: List<String>,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    /** An exit code of 0 is the POSIX success convention on macOS and Linux. */
    val isSuccess: Boolean get() = exitCode == 0
}
