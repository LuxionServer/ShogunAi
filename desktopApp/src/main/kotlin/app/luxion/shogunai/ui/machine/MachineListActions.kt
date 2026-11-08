package app.luxion.shogunai.ui.machine

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.luxion.shogunai.domain.model.ComposeResourceKind
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.ui.components.DisabledReason
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.Strings

data class ListActionBlock(val reason: String, val running: Boolean)

@Composable
fun ListActionReason(block: ListActionBlock?, tag: String, button: @Composable (enabled: Boolean) -> Unit) {
    if (block != null && !block.running) {
        MachineStateText(block.reason, Modifier.padding(end = Spacing.sm).testTag(tag))
    }
    DisabledReason(block?.reason) { button(block == null) }
}

fun noneIdleReason(states: List<DaemonState>, strings: Strings): String {
    val busy = states.count { it == DaemonState.Busy }
    val unknown = states.size - busy
    return when {
        states.isEmpty() -> strings.stopIdleBlockedNoneIdle
        unknown == 0 -> strings.stopIdleAllBusy(busy)
        busy == 0 -> strings.stopIdleAllUnknown(unknown)
        else -> strings.stopIdleBusyAndUnknown(busy, unknown)
    }
}

fun mavenStopBlock(state: DaemonState, stopping: Boolean): DaemonStopBlock? = when {
    stopping -> DaemonStopBlock.STOPPING
    state is DaemonState.Idle -> null
    state == DaemonState.Busy -> DaemonStopBlock.BUSY
    else -> DaemonStopBlock.STATE_UNKNOWN
}

fun mavenStopIdleBlock(states: List<DaemonState>, stopping: Boolean, strings: Strings): ListActionBlock? = when {
    stopping -> ListActionBlock(strings.stopIdleBlockedStopping, running = true)
    states.none { it is DaemonState.Idle } -> ListActionBlock(noneIdleReason(states, strings), running = false)
    else -> null
}

fun derivedDataDeleteBlock(orphans: Int, deleting: Boolean, strings: Strings): ListActionBlock? = when {
    deleting -> ListActionBlock(strings.derivedDataDeleteBlockedDeleting, running = true)
    orphans == 0 -> ListActionBlock(strings.derivedDataDeleteBlockedNoOrphans, running = false)
    else -> null
}

fun simulatorOrphansDeleteBlock(deleting: Boolean, strings: Strings): ListActionBlock? =
    if (deleting) ListActionBlock(strings.simulatorOrphansDeleteBlockedDeleting, running = true) else null

fun composeTakeDownBlock(removedProjects: Int, takingDown: Boolean, strings: Strings): ListActionBlock? = when {
    takingDown -> ListActionBlock(strings.composeTakeDownBlockedRunning, running = true)
    removedProjects == 0 -> ListActionBlock(strings.composeTakeDownBlockedNone, running = false)
    else -> null
}

fun composeLeftoversBlock(kinds: List<ComposeResourceKind>, removing: Boolean, strings: Strings): ListActionBlock? = when {
    removing -> composeLeftoversRemovingBlock(strings)
    kinds.all { it == ComposeResourceKind.VOLUME } -> ListActionBlock(strings.composeLeftoversBlockedOnlyVolumes, running = false)
    else -> null
}

fun composeVolumesBlock(removing: Boolean, strings: Strings): ListActionBlock? =
    if (removing) composeLeftoversRemovingBlock(strings) else null

private fun composeLeftoversRemovingBlock(strings: Strings) = ListActionBlock(strings.composeLeftoversBlockedRemoving, running = true)
