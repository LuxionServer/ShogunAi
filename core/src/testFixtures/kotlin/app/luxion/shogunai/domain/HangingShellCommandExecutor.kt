package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.CommandResult
import kotlin.time.Duration
import kotlinx.coroutines.delay

class HangingShellCommandExecutor(
    var hangs: (command: List<String>) -> Boolean,
    private val waitsForTheLimit: Boolean = true,
    private val responder: suspend (command: List<String>, workingDirectory: String?) -> CommandResult = { command, _ -> FakeShellCommandExecutor.success(command) },
) : ScriptedShellCommandExecutor() {

    constructor(
        hangs: (command: List<String>) -> Boolean,
        responder: (command: List<String>) -> CommandResult,
    ) : this(hangs, responder = { command, _ -> responder(command) })

    val executedCommands = mutableListOf<List<String>>()

    override suspend fun executeWithin(
        command: List<String>,
        timeout: Duration,
        workingDirectory: String?,
        environment: Map<String, String>,
    ): CommandResult {
        timeouts[command] = timeout
        executedCommands += command
        if (hangs(command)) {
            if (waitsForTheLimit) delay(timeout)
            throw CommandExecutionException.TimedOut(command, timeout)
        }
        return responder(command, workingDirectory)
    }

    override suspend fun respond(command: List<String>, workingDirectory: String?): CommandResult = responder(command, workingDirectory)
}
