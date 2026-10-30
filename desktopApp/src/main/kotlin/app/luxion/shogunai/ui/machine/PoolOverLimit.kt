package app.luxion.shogunai.ui.machine

import app.luxion.shogunai.domain.model.PoolHolderState
import app.luxion.shogunai.domain.model.PoolUsage
import app.luxion.shogunai.domain.model.ResourceKind

data class BuildsHoldingPool(val busy: Int, val unknown: Int)

fun buildsHoldingPool(usage: PoolUsage): BuildsHoldingPool? {
    if (usage.kind != ResourceKind.GRADLE_DAEMON || !usage.isOverLimit) return null
    if (usage.holders.any { it.state == PoolHolderState.IDLE }) return null
    return BuildsHoldingPool(
        busy = usage.holders.count { it.state == PoolHolderState.BUSY },
        unknown = usage.holders.count { it.state == PoolHolderState.UNKNOWN },
    )
}
