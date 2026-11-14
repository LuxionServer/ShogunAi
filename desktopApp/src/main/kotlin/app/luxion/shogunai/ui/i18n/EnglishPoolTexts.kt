package app.luxion.shogunai.ui.i18n

import app.luxion.shogunai.domain.model.HolderProtection
import app.luxion.shogunai.domain.model.PoolHolder
import app.luxion.shogunai.domain.model.PoolHolderState
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.toLocalClockTime

internal fun englishKindName(kind: ResourceKind): String = when (kind) {
    ResourceKind.EMULATOR -> "emulators"
    ResourceKind.IOS_SIMULATOR -> "iOS simulators"
    ResourceKind.GRADLE_DAEMON -> "Gradle daemons"
}

internal fun englishHolderState(state: PoolHolderState): String = state.name.lowercase()

internal fun englishWaiting(waiting: Int): String = when (waiting) {
    0 -> ""
    1 -> "; 1 worktree is waiting for one"
    else -> "; $waiting worktrees are waiting for one"
}

internal fun englishProtection(holder: PoolHolder): String {
    val protection = holder.protection ?: return ""
    return when (protection) {
        HolderProtection.AGENT_WORKING -> ", agent working"
        HolderProtection.LEASED -> ", lease until ${holder.leaseExpiresAt?.toLocalClockTime()}"
    }
}
