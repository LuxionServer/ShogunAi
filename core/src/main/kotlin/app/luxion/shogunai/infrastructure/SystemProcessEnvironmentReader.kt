package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.ProcessEnvironmentReader
import app.luxion.shogunai.domain.model.HostOs
import kotlinx.coroutines.CancellationException
import java.nio.file.Path
import kotlin.io.path.readBytes

class SystemProcessEnvironmentReader(
    private val executor: ShellCommandExecutor,
    private val os: HostOs = HostOs.current(),
    private val procRoot: String = "/proc",
) : ProcessEnvironmentReader {

    override suspend fun variable(pid: Long, name: String): String? =
        try {
            when (os) {
                HostOs.MACOS -> fromPs(pid, name)
                HostOs.LINUX -> fromProc(pid, name)
                HostOs.WINDOWS -> null
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            null
        }

    private suspend fun fromPs(pid: Long, name: String): String? {
        val result = executor.executeWithin(listOf("ps", "-E", "-ww", "-o", "command=", "-p", pid.toString()), CommandTimeouts.LISTING)
        if (!result.isSuccess) return null
        return valueOf(result.stdout.split(' ', '\n'), name)
    }

    private fun fromProc(pid: Long, name: String): String? {
        val entries = String(Path.of(procRoot, pid.toString(), "environ").readBytes()).split('\u0000')
        return valueOf(entries, name)
    }

    private fun valueOf(entries: List<String>, name: String): String? =
        entries.lastOrNull { it.startsWith("$name=") }?.let { it.substringAfter('=').ifEmpty { null } }
}
