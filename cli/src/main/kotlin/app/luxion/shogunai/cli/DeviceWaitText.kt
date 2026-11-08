package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.DeviceWaitProgress
import app.luxion.shogunai.domain.model.HolderProtection
import app.luxion.shogunai.domain.model.PoolHolder
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.toLocalClockTime

internal fun waitProgressLine(progress: DeviceWaitProgress): String {
    val device = if (progress.kind == ResourceKind.EMULATOR) "an emulator" else "a simulator"
    val holders = progress.holders.joinToString("; ") { it.waitDescription() }
    val next = progress.nextLeaseExpiry?.let { "; next lease expires at ${it.toLocalClockTime()}" }.orEmpty()
    return "waiting for $device: ${ordinal(progress.position)} of ${progress.waiting} in the queue, " +
        "limit ${progress.limit}: $holders$next"
}

private fun PoolHolder.waitDescription(): String {
    val protectionText = when (protection) {
        HolderProtection.AGENT_WORKING -> ", agent working"
        HolderProtection.LEASED -> ", lease until ${leaseExpiresAt!!.toLocalClockTime()}"
        else -> ""
    }
    return "$displayName ${state.name.lowercase()}$protectionText"
}

internal fun ordinal(position: Int): String {
    val suffix = when {
        position % 100 in 11..13 -> "th"
        position % 10 == 1 -> "st"
        position % 10 == 2 -> "nd"
        position % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$position$suffix"
}
