package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.OutputChannel
import app.luxion.shogunai.domain.executor.OutputLine
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
import java.io.InputStream
import kotlin.time.Duration

class ProcessBuilderShellCommandExecutor(
    private val path: (() -> List<String>)?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val osName: String = System.getProperty("os.name"),
) : ShellCommandExecutor {

    override suspend fun execute(
        command: List<String>,
        workingDirectory: String?,
    ): CommandResult = run(command, workingDirectory, null, emptyMap())

    override suspend fun executeWithin(
        command: List<String>,
        timeout: Duration,
        workingDirectory: String?,
    ): CommandResult = run(command, workingDirectory, timeout, emptyMap())

    override suspend fun executeWithEnvironment(
        command: List<String>,
        environment: Map<String, String>,
        workingDirectory: String?,
    ): CommandResult = run(command, workingDirectory, null, environment)

    override suspend fun executeStreaming(
        command: List<String>,
        workingDirectory: String?,
        environment: Map<String, String>,
        onOutput: (OutputLine) -> Unit,
    ): CommandResult {
        val lock = Any()
        return run(command, workingDirectory, null, environment) { line -> synchronized(lock) { onOutput(line) } }
    }

    private suspend fun run(
        command: List<String>,
        workingDirectory: String?,
        timeout: Duration?,
        environment: Map<String, String>,
        onOutput: ((OutputLine) -> Unit)? = null,
    ): CommandResult = withContext(ioDispatcher) {
        val process = try {
            val builder = ProcessBuilder(WindowsArguments.prepare(GitLongPaths.prepare(command, osName), osName))
                .redirectInput(ProcessBuilder.Redirect.from(File(NULL_DEVICE)))
                .apply { workingDirectory?.let { directory(File(it)) } }
            path?.let { builder.environment()["PATH"] = it().joinToString(File.pathSeparator) }
            GitLocale.applyTo(command, builder.environment())
            builder.environment().putAll(environment)
            builder.start()
        } catch (error: IOException) {
            throw CommandExecutionException(command, error)
        }

        val readers = CoroutineScope(ioDispatcher)
        val stdout = readers.async { readStream(process.inputStream, OutputChannel.STANDARD, onOutput) }
        val stderr = readers.async { readStream(process.errorStream, OutputChannel.ERROR, onOutput) }
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

    private fun readStream(stream: InputStream, channel: OutputChannel, onOutput: ((OutputLine) -> Unit)?): String {
        if (onOutput == null) return stream.bufferedReader().use { it.readText() }
        return stream.bufferedReader().useLines { lines ->
            lines.onEach { onOutput(OutputLine(channel, it)) }.joinToString("\n")
        }
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

internal object GitLongPaths {
    private const val GIT_EXECUTABLE = "git"

    fun prepare(command: List<String>, osName: String): List<String> {
        if (!osName.startsWith("Windows") || command.firstOrNull() != GIT_EXECUTABLE) return command
        return listOf(GIT_EXECUTABLE, "-c", "core.longpaths=true") + command.drop(1)
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
