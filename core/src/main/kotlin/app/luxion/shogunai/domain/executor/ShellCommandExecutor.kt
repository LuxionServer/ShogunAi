package app.luxion.shogunai.domain.executor

import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface ShellCommandExecutor {
    suspend fun execute(
        command: List<String>,
        workingDirectory: String? = null,
    ): CommandResult

    suspend fun executeWithin(
        command: List<String>,
        timeout: Duration,
        workingDirectory: String? = null,
    ): CommandResult = execute(command, workingDirectory)

    suspend fun executeStreaming(
        command: List<String>,
        workingDirectory: String?,
        environment: Map<String, String>,
        onOutput: (OutputLine) -> Unit,
    ): CommandResult {
        val result = execute(command, workingDirectory)
        result.stdout.lineSequence().filter { it.isNotEmpty() }.forEach { onOutput(OutputLine(OutputChannel.STANDARD, it)) }
        result.stderr.lineSequence().filter { it.isNotEmpty() }.forEach { onOutput(OutputLine(OutputChannel.ERROR, it)) }
        return result
    }
}

enum class OutputChannel { STANDARD, ERROR }

data class OutputLine(val channel: OutputChannel, val text: String)

object CommandTimeouts {
    val GIT_READ: Duration = 30.seconds
    val LISTING: Duration = 10.seconds
    val NETWORK: Duration = 60.seconds
}

open class CommandExecutionException(
    val command: List<String>,
    cause: Throwable,
    message: String = "Could not start the command: ${command.joinToString(" ")}",
) : Exception(message, cause) {

    class TimedOut(command: List<String>, val limit: Duration) : CommandExecutionException(
        command,
        TimeoutException("No answer within $limit"),
        "The command did not finish within $limit: ${command.joinToString(" ")}",
    )
}
