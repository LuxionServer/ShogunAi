package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import kotlin.time.Duration

internal class ToolCommandFailed(val result: CommandResult?, val detail: String) : Exception(detail)

internal suspend fun ShellCommandExecutor.runTool(command: List<String>, timeout: Duration = CommandTimeouts.LISTING): CommandResult {
    val result = try {
        executeWithin(command, timeout)
    } catch (error: CommandExecutionException) {
        throw ToolCommandFailed(null, error.cause.message ?: error.message)
    }
    if (!result.isSuccess) throw ToolCommandFailed(result, result.failureDetail())
    return result
}

internal fun CommandResult.failureDetail(): String =
    stderr.trim().ifBlank { stdout.trim() }.ifBlank { "exit code $exitCode" }
