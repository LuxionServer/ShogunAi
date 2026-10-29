package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.GradleDaemonSource
import app.luxion.shogunai.domain.io.HostEnvironment
import app.luxion.shogunai.domain.model.DaemonKind
import app.luxion.shogunai.domain.model.DaemonProcess
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.WorktreeError
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
        return parseDaemonProcesses(result.stdout).map { process ->
            if (process.kind != DaemonKind.GRADLE || process.gradleVersion == null) return@map process
            val log = readLogTail(daemonLogPath(process.gradleVersion, process.pid), LOG_TAIL_BYTES)
                ?: return@map process.copy(state = DaemonState.Unknown)
            val record = parseDaemonLog(log)
            process.copy(state = record.state, lastBuildDirectory = record.lastBuildDirectory)
        }
    }

    private fun daemonLogPath(version: String, pid: Long): Path =
        Path.of(gradleUserHome, "daemon", version, "daemon-$pid.out.log")

    private companion object {
        private val PS_COMMAND = listOf("ps", "-A", "-ww", "-o", "pid=,rss=,command=")
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

internal data class DaemonLogRecord(val state: DaemonState, val lastBuildDirectory: String?)

private const val GRADLE_DAEMON_MAIN_CLASS = "org.gradle.launcher.daemon.bootstrap.GradleDaemon"
private const val KOTLIN_DAEMON_MAIN_CLASS = "org.jetbrains.kotlin.daemon.KotlinCompileDaemon"
private const val STATE_MARKER = "Marking the daemon as "
private const val BUILD_MARKER = "Received command: Build{"
private const val BUILD_DIRECTORY_FIELD = "currentDir="
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
    return DaemonLogRecord(
        state = stateLine?.let(::stateOf) ?: DaemonState.Unknown,
        lastBuildDirectory = buildLine?.let(::buildDirectoryOf),
    )
}

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
