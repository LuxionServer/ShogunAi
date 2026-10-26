package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.OutputLine
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import kotlin.time.Duration

class FakeShellCommandExecutor(
    private val responder: (command: List<String>) -> CommandResult = { success(it) },
) : ShellCommandExecutor {

    val executedCommands = mutableListOf<List<String>>()
    val workingDirectories = mutableListOf<String?>()

    override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
        executedCommands += command
        workingDirectories += workingDirectory
        return responder(command)
    }

    val timeouts = mutableMapOf<List<String>, Duration>()

    override suspend fun executeWithin(
        command: List<String>,
        timeout: Duration,
        workingDirectory: String?,
    ): CommandResult {
        timeouts[command] = timeout
        return execute(command, workingDirectory)
    }

    val environments = mutableMapOf<List<String>, Map<String, String>>()

    override suspend fun executeStreaming(
        command: List<String>,
        workingDirectory: String?,
        environment: Map<String, String>,
        onOutput: (OutputLine) -> Unit,
    ): CommandResult {
        environments[command] = environment
        return super.executeStreaming(command, workingDirectory, environment, onOutput)
    }

    fun executed(vararg args: String): Boolean = executedCommands.any { it == args.toList() }

    companion object {
        fun success(command: List<String>, stdout: String = ""): CommandResult =
            CommandResult(command = command, exitCode = 0, stdout = stdout, stderr = "")

        fun failure(command: List<String>, exitCode: Int = 1, stderr: String = "boom"): CommandResult =
            CommandResult(command = command, exitCode = exitCode, stdout = "", stderr = stderr)
    }
}
