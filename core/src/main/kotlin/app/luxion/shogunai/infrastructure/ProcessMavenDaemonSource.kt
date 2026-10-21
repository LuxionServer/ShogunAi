package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.MavenDaemonSource
import app.luxion.shogunai.domain.model.MavenDaemonProcess
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.usecase.parseLsofWorkingDirectories

class ProcessMavenDaemonSource(private val executor: ShellCommandExecutor) : MavenDaemonSource {

    override suspend fun list(): List<MavenDaemonProcess> {
        val result = executor.executeWithin(PS_COMMAND, CommandTimeouts.LISTING)
        if (!result.isSuccess) throw WorktreeError.ProcessListingFailed(result.command, result.exitCode)
        val daemons = parseMavenDaemons(result.stdout)
        if (daemons.isEmpty()) return emptyList()
        val directories = workingDirectories(daemons.map { it.first })
        return daemons.map { (pid, memory) -> MavenDaemonProcess(pid, memory, directories[pid]) }
    }

    private suspend fun workingDirectories(pids: List<Long>): Map<Long, String> {
        val command = listOf("lsof", "-a", "-p", pids.joinToString(","), "-d", "cwd", "-Fn")
        val result = runCatching { executor.executeWithin(command, CommandTimeouts.LISTING) }.getOrNull() ?: return emptyMap()
        return parseLsofWorkingDirectories(result.stdout).mapNotNull { (pid, directory) -> pid.toLongOrNull()?.let { it to directory } }.toMap()
    }

    private companion object {
        val PS_COMMAND = listOf("ps", "-A", "-ww", "-o", "pid=,rss=,command=")
    }
}

private const val MVND_PACKAGE = "org.mvndaemon.mvnd"
private val WHITESPACE = Regex("\\s+")

internal fun parseMavenDaemons(psOutput: String): List<Pair<Long, Long>> =
    psOutput.lineSequence().mapNotNull { line ->
        val fields = line.trim().split(WHITESPACE, limit = 3)
        if (fields.size < 3) return@mapNotNull null
        val pid = fields[0].toLongOrNull() ?: return@mapNotNull null
        val memoryBytes = (fields[1].toLongOrNull() ?: return@mapNotNull null) * 1024
        if (fields[2].split(WHITESPACE).none { it.startsWith("$MVND_PACKAGE.") }) return@mapNotNull null
        pid to memoryBytes
    }.toList()
