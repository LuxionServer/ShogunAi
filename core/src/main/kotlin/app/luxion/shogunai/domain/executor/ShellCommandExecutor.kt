package app.luxion.shogunai.domain.executor

interface ShellCommandExecutor {
    suspend fun execute(
        command: List<String>,
        workingDirectory: String? = null,
    ): CommandResult
}

class CommandExecutionException(
    val command: List<String>,
    cause: Throwable,
) : Exception("Could not start the command: ${command.joinToString(" ")}", cause)
