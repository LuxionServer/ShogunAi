package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.PowerSource

class PmsetPowerSource(private val executor: ShellCommandExecutor) : PowerSource {

    override suspend fun isOnBattery(): Boolean? {
        val result = runCatching { executor.executeWithin(listOf("pmset", "-g", "batt"), CommandTimeouts.LISTING) }.getOrNull()
        if (result == null || !result.isSuccess) return null
        return parsePowerSource(result.stdout)
    }
}

internal fun parsePowerSource(output: String): Boolean? {
    val firstLine = output.lineSequence().first()
    return when {
        "'Battery Power'" in firstLine -> true
        "'AC Power'" in firstLine -> false
        else -> null
    }
}
