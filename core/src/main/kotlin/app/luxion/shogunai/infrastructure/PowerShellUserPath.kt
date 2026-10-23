package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.UserPathEntries

class PowerShellUserPath(private val executor: ShellCommandExecutor) : UserPathEntries {
    override suspend fun contains(folder: String): Boolean {
        val result = run("[Environment]::GetEnvironmentVariable('Path', 'User')")
        if (!result.isSuccess) return false
        return entries(result.stdout).any { sameFolder(it, folder) }
    }

    override suspend fun add(folder: String) {
        val result = run("\$current = [Environment]::GetEnvironmentVariable('Path', 'User'); " +
            "[Environment]::SetEnvironmentVariable('Path', (@(\$current, ${quote(folder)}) | Where-Object { \$_ }) -join ';', 'User')")
        check(result.isSuccess) { "Could not add $folder to the user PATH: ${result.stderr.trim()}" }
    }

    override suspend fun remove(folder: String) {
        val result = run("\$kept = ([Environment]::GetEnvironmentVariable('Path', 'User') -split ';') | " +
            "Where-Object { \$_ -and (\$_.TrimEnd('\\') -ne ${quote(folder.trimEnd('\\'))}) }; " +
            "[Environment]::SetEnvironmentVariable('Path', \$kept -join ';', 'User')")
        check(result.isSuccess) { "Could not remove $folder from the user PATH: ${result.stderr.trim()}" }
    }

    private suspend fun run(script: String) =
        executor.executeWithin(listOf("powershell", "-NoProfile", "-NonInteractive", "-Command", script), CommandTimeouts.LISTING)

    private fun entries(path: String) = path.trim().split(';').filter { it.isNotBlank() }

    private fun sameFolder(first: String, second: String) =
        first.trimEnd('\\', '/').equals(second.trimEnd('\\', '/'), ignoreCase = true)

    private fun quote(value: String) = "'" + value.replace("'", "''") + "'"
}
