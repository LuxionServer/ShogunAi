package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor

class FakeShellCommandExecutor(
    private val responder: (command: List<String>) -> CommandResult = { success(it) },
) : ShellCommandExecutor {

    val executedCommands = mutableListOf<List<String>>()

    override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
        executedCommands += command
        return responder(command)
    }

    fun executed(vararg args: String): Boolean = executedCommands.any { it == args.toList() }

    companion object {
        fun success(command: List<String>, stdout: String = ""): CommandResult =
            CommandResult(command = command, exitCode = 0, stdout = stdout, stderr = "")

        fun failure(command: List<String>, exitCode: Int = 1, stderr: String = "boom"): CommandResult =
            CommandResult(command = command, exitCode = exitCode, stdout = "", stderr = stderr)
    }
}
