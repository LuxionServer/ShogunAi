package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor

internal class ToolCommandFailed(val result: CommandResult?, val detail: String) : Exception(detail)

internal suspend fun ShellCommandExecutor.runTool(command: List<String>, bounded: Boolean = true): CommandResult {
    val result = try {
        if (bounded) executeWithin(command, CommandTimeouts.LISTING) else execute(command)
    } catch (error: CommandExecutionException) {
        throw ToolCommandFailed(null, error.cause?.message ?: error.message.orEmpty())
    }
    if (!result.isSuccess) throw ToolCommandFailed(result, result.failureDetail())
    return result
}

internal fun CommandResult.failureDetail(): String =
    stderr.trim().ifBlank { stdout.trim() }.ifBlank { "exit code $exitCode" }
