package app.luxion.shogunai.domain.executor

import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

interface ShellCommandExecutor {
    suspend fun executeWithin(
        command: List<String>,
        timeout: Duration,
        workingDirectory: String? = null,
        environment: Map<String, String> = emptyMap(),
    ): CommandResult

    suspend fun executeStreaming(
        command: List<String>,
        timeout: Duration,
        workingDirectory: String?,
        environment: Map<String, String>,
        onOutput: (OutputLine) -> Unit,
    ): CommandResult
}

enum class OutputChannel { STANDARD, ERROR }

data class OutputLine(val channel: OutputChannel, val text: String)

object CommandTimeouts {
    val GIT_READ: Duration = 30.seconds
    val LISTING: Duration = 10.seconds
    val NETWORK: Duration = 60.seconds
    val LOCAL_TOOL: Duration = 60.seconds
    val LOCAL_WORK: Duration = 5.minutes
    val INSTALL: Duration = 30.minutes
}

open class CommandExecutionException(
    val command: List<String>,
    override val cause: Throwable,
    override val message: String = "Could not start the command: ${command.joinToString(" ")}",
) : Exception(message, cause) {

    class TimedOut(command: List<String>, val limit: Duration) : CommandExecutionException(
        command,
        TimeoutException("No answer within $limit"),
        "The command did not finish within $limit: ${command.joinToString(" ")}",
    )
}
