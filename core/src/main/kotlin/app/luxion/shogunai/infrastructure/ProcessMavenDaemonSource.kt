package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.MavenDaemonSource
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.MavenDaemonProcess
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.usecase.parseLsofWorkingDirectories
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

class ProcessMavenDaemonSource(
    private val executor: ShellCommandExecutor,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : MavenDaemonSource {

    override suspend fun list(): List<MavenDaemonProcess> {
        val result = executor.executeWithin(PS_COMMAND, CommandTimeouts.LISTING)
        if (!result.isSuccess) throw WorktreeError.ProcessListingFailed(result.command, result.exitCode)
        val daemons = parseMavenDaemons(result.stdout)
        if (daemons.isEmpty()) return emptyList()
        val directories = workingDirectories(daemons.map { it.pid })
        return daemons.map { daemon ->
            MavenDaemonProcess(
                pid = daemon.pid,
                residentMemoryBytes = daemon.residentMemoryBytes,
                workingDirectory = directories[daemon.pid],
                state = daemon.logPath?.let(::readState) ?: DaemonState.Unknown,
            )
        }
    }

    private fun readState(logPath: String): DaemonState? {
        val path = try {
            Path.of(logPath)
        } catch (error: InvalidPathException) {
            return null
        }
        val modifiedAt = try {
            Files.getLastModifiedTime(path).toInstant()
        } catch (error: IOException) {
            return null
        }
        val tail = readLogTail(path, LOG_TAIL_BYTES) ?: return null
        return parseMavenDaemonState(tail, modifiedAt, zone)
    }

    private suspend fun workingDirectories(pids: List<Long>): Map<Long, String> {
        val command = listOf("lsof", "-a", "-p", pids.joinToString(","), "-d", "cwd", "-Fn")
        val result = runCatching { executor.executeWithin(command, CommandTimeouts.LISTING) }.getOrNull() ?: return emptyMap()
        return parseLsofWorkingDirectories(result.stdout).mapNotNull { (pid, directory) -> pid.toLongOrNull()?.let { it to directory } }.toMap()
    }

    private companion object {
        private val PS_COMMAND = listOf("ps", "-A", "-ww", "-o", "pid=,rss=,command=")
        const val LOG_TAIL_BYTES = 1L shl 20
    }
}

internal data class MavenDaemonListing(val pid: Long, val residentMemoryBytes: Long, val logPath: String?)

private const val MVND_PACKAGE = "org.mvndaemon.mvnd"
private const val DAEMON_ID_ARGUMENT = "-Dmvnd.id="
private val WHITESPACE = Regex("\\s+")
private val LOG_FILE_ARGUMENT = Regex("-D(?:org\\.slf4j\\.simpleLogger\\.logFile|maven\\.logger\\.logFile)=(.+?\\.log)(?=\\s|$)")
private val STATE_LINE = Regex("^(\\d{2}:\\d{2}:\\d{2}\\.\\d{3}) I Updating state to: (\\w+)$")
private val BUSY_STATES = setOf("Busy", "Canceled", "StopRequested", "Stopped", "Broken")
private const val MODIFICATION_TOLERANCE_SECONDS = 60L

internal fun parseMavenDaemons(psOutput: String): List<MavenDaemonListing> =
    psOutput.lineSequence().mapNotNull { line ->
        val fields = line.trim().split(WHITESPACE, limit = 3)
        if (fields.size < 3) return@mapNotNull null
        val pid = fields[0].toLongOrNull() ?: return@mapNotNull null
        val memoryBytes = (fields[1].toLongOrNull() ?: return@mapNotNull null) * 1024
        if (fields[2].split(WHITESPACE).none(::isDaemonArgument)) return@mapNotNull null
        MavenDaemonListing(pid, memoryBytes, LOG_FILE_ARGUMENT.find(fields[2])?.let { it.groupValues[1] })
    }.toList()

private fun isDaemonArgument(argument: String): Boolean =
    argument.startsWith(DAEMON_ID_ARGUMENT) || argument.startsWith("$MVND_PACKAGE.")

internal fun parseMavenDaemonState(log: String, modifiedAt: Instant, zone: ZoneId): DaemonState =
    log.lines().asReversed().firstNotNullOfOrNull { STATE_LINE.matchEntire(it.trimEnd()) }
        .let { match -> if (match != null) stateOf(match.groupValues[2], match.groupValues[1], modifiedAt, zone) else DaemonState.Busy }

private fun stateOf(name: String, time: String, modifiedAt: Instant, zone: ZoneId): DaemonState = when {
    name == "Idle" -> idleSince(time, modifiedAt, zone)?.let { DaemonState.Idle(it) } ?: DaemonState.Unknown
    name in BUSY_STATES -> DaemonState.Busy
    else -> DaemonState.Unknown
}

private fun idleSince(time: String, modifiedAt: Instant, zone: ZoneId): Instant? {
    val markerTime = try {
        LocalTime.parse(time)
    } catch (error: DateTimeParseException) {
        return null
    }
    val modified = modifiedAt.atZone(zone)
    val sameDay = modified.toLocalDate().atTime(markerTime).atZone(zone)
    val since = if (sameDay.isAfter(modified.plusSeconds(MODIFICATION_TOLERANCE_SECONDS))) sameDay.minusDays(1) else sameDay
    return since.toInstant()
}
