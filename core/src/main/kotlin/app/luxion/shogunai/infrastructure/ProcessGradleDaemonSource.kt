package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.GradleDaemonSource
import app.luxion.shogunai.domain.io.HostEnvironment
import app.luxion.shogunai.domain.model.DaemonKind
import app.luxion.shogunai.domain.model.DaemonProcess
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.GradleClient
import app.luxion.shogunai.domain.model.HoldingBuild
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class ProcessGradleDaemonSource(
    private val executor: ShellCommandExecutor,
    private val gradleUserHome: String,
) : GradleDaemonSource {

    override suspend fun list(): List<DaemonProcess> {
        val result = executor.executeWithin(PS_COMMAND, CommandTimeouts.LISTING)
        if (!result.isSuccess) throw WorktreeError.ProcessListingFailed(result.command, result.exitCode)
        val processTable = ProcessTableReader()
        return parseDaemonProcesses(result.stdout).map { process ->
            if (process.kind != DaemonKind.GRADLE || process.gradleVersion == null) return@map process
            val log = readLogTail(daemonLogPath(process.gradleVersion, process.pid), LOG_TAIL_BYTES)
                ?: return@map process.copy(state = DaemonState.Unknown)
            val record = parseDaemonLog(log)
            val holder = record.latestBuild?.takeIf { record.state == DaemonState.Busy }?.let { build ->
                HoldingBuild(build.directory, build.startedAt, build.clientPort?.let { clientOf(process.pid, it, processTable) })
            }
            process.copy(state = record.state, lastBuildDirectory = record.lastBuildDirectory, holder = holder)
        }
    }

    private suspend fun clientOf(daemonPid: Long, clientPort: Int, processTable: ProcessTableReader): GradleClient? = optional {
        val pid = connectedProcesses(clientPort).firstOrNull { it != daemonPid } ?: return@optional null
        val table = processTable.read() ?: return@optional null
        val arguments = table[pid]?.command?.split(WHITESPACE) ?: return@optional null
        val entryPoint = arguments.indexOfLast { it in GRADLE_CLIENT_MAIN_CLASSES || it.endsWith(GRADLE_WRAPPER_JAR) }
            .takeIf { it >= 0 } ?: return@optional null
        GradleClient(pid, arguments.drop(entryPoint + 1), ancestorsOf(pid, table))
    }

    private suspend fun connectedProcesses(port: Int): List<Long> {
        val result = executor.executeWithin(lsofCommand(port), CommandTimeouts.LISTING)
        if (!result.isSuccess) return emptyList()
        return result.stdout.lineSequence().filter { it.startsWith("p") }.mapNotNull { it.drop(1).trim().toLongOrNull() }.toList()
    }

    private inner class ProcessTableReader {
        private var table: Map<Long, ProcessEntry>? = null
        private var read = false

        suspend fun read(): Map<Long, ProcessEntry>? {
            if (!read) {
                read = true
                val result = executor.executeWithin(PROCESS_TABLE_COMMAND, CommandTimeouts.LISTING)
                table = if (result.isSuccess) parseProcessTable(result.stdout) else null
            }
            return table
        }
    }

    private suspend fun <T> optional(block: suspend () -> T?): T? = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        null
    }

    private fun daemonLogPath(version: String, pid: Long): Path =
        Path.of(gradleUserHome, "daemon", version, "daemon-$pid.out.log")

    private companion object {
        val PS_COMMAND = listOf("ps", "-A", "-ww", "-o", "pid=,rss=,command=")
        val PROCESS_TABLE_COMMAND = listOf("ps", "-A", "-ww", "-o", "pid=,ppid=,command=")
        fun lsofCommand(port: Int) = listOf("lsof", "-nP", "-iTCP:$port", "-sTCP:ESTABLISHED", "-Fpc")
        const val LOG_TAIL_BYTES = 1L shl 20
    }
}

internal fun readLogTail(path: Path, bytes: Long): String? = try {
    if (!Files.isRegularFile(path)) {
        null
    } else {
        RandomAccessFile(path.toFile(), "r").use { file ->
            val start = maxOf(0L, file.length() - bytes)
            val tail = ByteArray((file.length() - start).toInt())
            file.seek(start)
            file.readFully(tail)
            String(tail, Charsets.UTF_8)
        }
    }
} catch (error: IOException) {
    null
}

fun defaultGradleUserHome(environment: HostEnvironment): String =
    environment.variable("GRADLE_USER_HOME")?.takeIf { it.isNotBlank() }
        ?: Path.of(environment.home, ".gradle").toString()

internal data class DaemonLogRecord(val state: DaemonState, val lastBuildDirectory: String?, val latestBuild: LoggedBuild? = null)

internal data class LoggedBuild(val directory: String, val startedAt: Instant?, val clientPort: Int?)

internal data class ProcessEntry(val parentPid: Long, val command: String)

private const val GRADLE_DAEMON_MAIN_CLASS = "org.gradle.launcher.daemon.bootstrap.GradleDaemon"
private const val KOTLIN_DAEMON_MAIN_CLASS = "org.jetbrains.kotlin.daemon.KotlinCompileDaemon"
private const val STATE_MARKER = "Marking the daemon as "
private const val BUILD_MARKER = "Received command: Build{"
private const val BUILD_DIRECTORY_FIELD = "currentDir="
private const val EXECUTING_MARKER = "Starting executing command: Build{"
private const val CONNECTION_MARKER = "} with connection: "
private const val CLIENT_END_MARKER = " to /"
private const val MAX_ANCESTORS = 64
private val GRADLE_CLIENT_MAIN_CLASSES = setOf("org.gradle.wrapper.GradleWrapperMain", "org.gradle.launcher.GradleMain")
private const val GRADLE_WRAPPER_JAR = "gradle-wrapper.jar"
private val LOG_TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
private val WHITESPACE = Regex("\\s+")

internal fun parseDaemonProcesses(psOutput: String): List<DaemonProcess> =
    psOutput.lineSequence().mapNotNull(::parseDaemonProcess).toList()

private fun parseDaemonProcess(line: String): DaemonProcess? {
    val fields = line.trim().split(WHITESPACE, limit = 3)
    if (fields.size < 3) return null
    val pid = fields[0].toLongOrNull() ?: return null
    val memoryBytes = (fields[1].toLongOrNull() ?: return null) * 1024
    val arguments = fields[2].split(WHITESPACE)
    val gradleIndex = arguments.indexOf(GRADLE_DAEMON_MAIN_CLASS)
    return when {
        gradleIndex >= 0 -> DaemonProcess(
            pid = pid,
            kind = DaemonKind.GRADLE,
            gradleVersion = arguments.getOrNull(gradleIndex + 1),
            residentMemoryBytes = memoryBytes,
            lastBuildDirectory = null,
            state = DaemonState.Unknown,
        )
        KOTLIN_DAEMON_MAIN_CLASS in arguments -> DaemonProcess(
            pid = pid,
            kind = DaemonKind.KOTLIN,
            gradleVersion = null,
            residentMemoryBytes = memoryBytes,
            lastBuildDirectory = null,
            state = DaemonState.Untracked,
        )
        else -> null
    }
}

internal fun parseDaemonLog(log: String): DaemonLogRecord {
    val lines = log.lines().asReversed()
    val stateLine = lines.firstOrNull { STATE_MARKER in it }
    val buildLine = lines.firstOrNull { BUILD_MARKER in it }
    val latestBuildLine = lines.firstOrNull { EXECUTING_MARKER in it || BUILD_MARKER in it }
    return DaemonLogRecord(
        state = stateLine?.let(::stateOf) ?: DaemonState.Unknown,
        lastBuildDirectory = buildLine?.let(::buildDirectoryOf),
        latestBuild = latestBuildLine?.let(::loggedBuildOf),
    )
}

private fun loggedBuildOf(line: String): LoggedBuild? {
    if (EXECUTING_MARKER !in line) return buildDirectoryOf(line)?.let { LoggedBuild(it, timestampOf(line), null) }
    val directory = line.substringAfter(BUILD_DIRECTORY_FIELD, "").substringBefore(CONNECTION_MARKER).ifEmpty { return null }
    val clientPort = line.substringAfterLast(CLIENT_END_MARKER, "").substringAfterLast(':').trimEnd('.').toIntOrNull()
    return LoggedBuild(directory, timestampOf(line), clientPort)
}

internal fun parseProcessTable(psOutput: String): Map<Long, ProcessEntry> =
    psOutput.lineSequence().mapNotNull { line ->
        val fields = line.trim().split(WHITESPACE, limit = 3)
        val pid = fields.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        val parent = fields.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
        pid to ProcessEntry(parent, fields.getOrNull(2).orEmpty())
    }.toMap()

internal fun ancestorsOf(pid: Long, table: Map<Long, ProcessEntry>): List<Long> =
    generateSequence(table[pid]?.parentPid) { table[it]?.parentPid }
        .takeWhile { it > 1 }
        .take(MAX_ANCESTORS)
        .toList()

private fun stateOf(line: String): DaemonState {
    val state = line.substringAfter(STATE_MARKER).substringBefore(',').trim()
    if (state != "idle") return DaemonState.Busy
    val since = timestampOf(line) ?: return DaemonState.Unknown
    return DaemonState.Idle(since)
}

private fun timestampOf(line: String): Instant? = try {
    OffsetDateTime.parse(line.substringBefore(' '), LOG_TIMESTAMP).toInstant()
} catch (error: DateTimeParseException) {
    null
}

private fun buildDirectoryOf(line: String): String? =
    line.substringAfter(BUILD_DIRECTORY_FIELD, "")
        .substringBeforeLast('}')
        .ifEmpty { null }
