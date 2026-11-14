package app.luxion.shogunai.ui.i18n

import app.luxion.shogunai.domain.model.HolderProtection
import app.luxion.shogunai.domain.model.PoolHolder
import app.luxion.shogunai.domain.model.PoolHolderState
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.toLocalClockTime

internal fun spanishKindName(kind: ResourceKind): String = when (kind) {
    ResourceKind.EMULATOR -> "emuladores"
    ResourceKind.IOS_SIMULATOR -> "simuladores de iOS"
    ResourceKind.GRADLE_DAEMON -> "daemons de Gradle"
}

internal fun spanishHolderState(state: PoolHolderState): String = when (state) {
    PoolHolderState.STOPPED -> "detenido"
    PoolHolderState.BOOTING -> "arrancando"
    PoolHolderState.RUNNING -> "en ejecución"
    PoolHolderState.UNRESPONSIVE -> "no responde"
    PoolHolderState.SHUTDOWN -> "apagado"
    PoolHolderState.BOOTED -> "arrancado"
    PoolHolderState.BUSY -> "ocupado"
    PoolHolderState.IDLE -> "inactivo"
    PoolHolderState.UNKNOWN -> "desconocido"
}

internal fun spanishWaiting(waiting: Int): String = when (waiting) {
    0 -> ""
    1 -> "; 1 worktree espera uno"
    else -> "; $waiting worktrees esperan uno"
}

internal fun spanishKindSingular(kind: ResourceKind): String = when (kind) {
    ResourceKind.EMULATOR -> "emulador"
    ResourceKind.IOS_SIMULATOR -> "simulador"
    ResourceKind.GRADLE_DAEMON -> "daemon de Gradle"
}

internal fun spanishProtection(holder: PoolHolder): String {
    val protection = holder.protection ?: return ""
    return when (protection) {
        HolderProtection.AGENT_WORKING -> ", agente trabajando"
        HolderProtection.LEASED -> ", concesión hasta ${holder.leaseExpiresAt?.toLocalClockTime()}"
    }
}
