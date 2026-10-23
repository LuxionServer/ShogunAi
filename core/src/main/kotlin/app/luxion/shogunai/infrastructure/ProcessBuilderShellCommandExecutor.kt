package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import kotlin.time.Duration

class ProcessBuilderShellCommandExecutor(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val osName: String = System.getProperty("os.name"),
) : ShellCommandExecutor {

    override suspend fun execute(
        command: List<String>,
        workingDirectory: String?,
    ): CommandResult = run(command, workingDirectory, null)

    override suspend fun executeWithin(
        command: List<String>,
        timeout: Duration,
        workingDirectory: String?,
    ): CommandResult = run(command, workingDirectory, timeout)

    private suspend fun run(
        command: List<String>,
        workingDirectory: String?,
        timeout: Duration?,
    ): CommandResult = withContext(ioDispatcher) {
        val process = try {
            val builder = ProcessBuilder(WindowsArguments.prepare(command, osName))
                .redirectInput(ProcessBuilder.Redirect.from(File(NULL_DEVICE)))
                .apply { workingDirectory?.let { directory(File(it)) } }
            builder.environment()["PATH"] = HomebrewPath.merge(builder.environment()["PATH"])
            GitLocale.applyTo(command, builder.environment())
            builder.start()
        } catch (error: IOException) {
            throw CommandExecutionException(command, error)
        }

        val readers = CoroutineScope(ioDispatcher)
        val stdout = readers.async { process.inputStream.bufferedReader().use { it.readText() } }
        val stderr = readers.async { process.errorStream.bufferedReader().use { it.readText() } }
        val exitCode = try {
            if (timeout == null) waitFor(process) else withTimeoutOrNull(timeout) { waitFor(process) }
        } finally {
            if (process.isAlive) killWithDescendants(process)
        }
        if (exitCode == null) throw CommandExecutionException.TimedOut(command, timeout!!)
        CommandResult(
            command = command,
            exitCode = exitCode,
            stdout = stdout.await().trimEnd(),
            stderr = stderr.await().trimEnd(),
        )
    }

    private suspend fun waitFor(process: Process): Int = runInterruptible { process.waitFor() }

    private fun killWithDescendants(process: Process) {
        process.descendants().forEach { it.destroyForcibly() }
        process.destroyForcibly()
    }

    private companion object {
        val NULL_DEVICE = if (System.getProperty("os.name").startsWith("Windows")) "NUL" else "/dev/null"
    }
}

internal object HomebrewPath {
    private val EXTRA_DIRS = listOf("/opt/homebrew/bin", "/opt/homebrew/sbin", "/usr/local/bin")

    fun merge(currentPath: String?): String {
        val existingDirs = currentPath.orEmpty().split(File.pathSeparator).filter { it.isNotEmpty() }
        return (EXTRA_DIRS + existingDirs).distinct().joinToString(File.pathSeparator)
    }
}

internal object GitLocale {
    private const val GIT_EXECUTABLE = "git"

    fun applyTo(command: List<String>, environment: MutableMap<String, String>) {
        if (command.firstOrNull() != GIT_EXECUTABLE) return
        environment["LC_ALL"] = "C"
        environment["GIT_TERMINAL_PROMPT"] = "0"
    }
}
