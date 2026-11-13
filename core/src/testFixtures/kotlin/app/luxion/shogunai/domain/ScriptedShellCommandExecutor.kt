package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.OutputChannel
import app.luxion.shogunai.domain.executor.OutputLine
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import kotlin.time.Duration

abstract class ScriptedShellCommandExecutor : ShellCommandExecutor {

    val timeouts = mutableMapOf<List<String>, Duration>()
    val environments = mutableMapOf<List<String>, Map<String, String>>()

    abstract suspend fun respond(command: List<String>, workingDirectory: String?): CommandResult

    override suspend fun executeWithin(
        command: List<String>,
        timeout: Duration,
        workingDirectory: String?,
        environment: Map<String, String>,
    ): CommandResult {
        timeouts[command] = timeout
        if (environment.isNotEmpty()) environments[command] = environment
        return respond(command, workingDirectory)
    }

    override suspend fun executeStreaming(
        command: List<String>,
        timeout: Duration,
        workingDirectory: String?,
        environment: Map<String, String>,
        onOutput: (OutputLine) -> Unit,
    ): CommandResult {
        timeouts[command] = timeout
        environments[command] = environment
        val result = respond(command, workingDirectory)
        result.stdout.lineSequence().filter { it.isNotEmpty() }.forEach { onOutput(OutputLine(OutputChannel.STANDARD, it)) }
        result.stderr.lineSequence().filter { it.isNotEmpty() }.forEach { onOutput(OutputLine(OutputChannel.ERROR, it)) }
        return result
    }
}
