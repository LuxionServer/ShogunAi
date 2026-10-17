package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class ProcessBuilderShellCommandExecutor(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ShellCommandExecutor {

    override suspend fun execute(
        command: List<String>,
        workingDirectory: String?,
    ): CommandResult = withContext(ioDispatcher) {
        val process = try {
            val builder = ProcessBuilder(command)
                .apply { workingDirectory?.let { directory(File(it)) } }
            builder.environment()["PATH"] = HomebrewPath.merge(builder.environment()["PATH"])
            builder.start()
        } catch (error: IOException) {
            throw CommandExecutionException(command, error)
        }

        try {
            coroutineScope {
                val stdout = async { process.inputStream.bufferedReader().use { it.readText() } }
                val stderr = async { process.errorStream.bufferedReader().use { it.readText() } }
                val exitCode = runInterruptible { process.waitFor() }
                CommandResult(
                    command = command,
                    exitCode = exitCode,
                    stdout = stdout.await().trimEnd(),
                    stderr = stderr.await().trimEnd(),
                )
            }
        } finally {
            if (process.isAlive) process.destroy()
        }
    }
}

internal object HomebrewPath {
    private val EXTRA_DIRS = listOf("/opt/homebrew/bin", "/opt/homebrew/sbin", "/usr/local/bin")

    fun merge(currentPath: String?): String {
        val existingDirs = currentPath.orEmpty().split(File.pathSeparator).filter { it.isNotEmpty() }
        return (EXTRA_DIRS + existingDirs).distinct().joinToString(File.pathSeparator)
    }
}
