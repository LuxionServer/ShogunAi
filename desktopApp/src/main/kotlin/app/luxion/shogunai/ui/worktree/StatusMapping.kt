package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.ui.theme.Status

enum class WorktreeIndicator { MERGED, UNCOMMITTED_CHANGES, LOCKED, INACTIVE, FOLDER_MISSING, AGENT_RUNNING }

fun WorktreeIndicator.status(): Status = when (this) {
    WorktreeIndicator.MERGED -> Status.SUCCESS
    WorktreeIndicator.UNCOMMITTED_CHANGES -> Status.WARNING
    WorktreeIndicator.INACTIVE -> Status.WARNING
    WorktreeIndicator.AGENT_RUNNING -> Status.INFO
    WorktreeIndicator.FOLDER_MISSING -> Status.DANGER
    WorktreeIndicator.LOCKED -> Status.NEUTRAL
}

fun EmulatorState.status(): Status = when (this) {
    EmulatorState.RUNNING -> Status.SUCCESS
    EmulatorState.BOOTING -> Status.INFO
    EmulatorState.STOPPED -> Status.NEUTRAL
}

fun SimulatorState.status(): Status = when (this) {
    SimulatorState.BOOTED -> Status.SUCCESS
    SimulatorState.BOOTING -> Status.INFO
    SimulatorState.SHUTDOWN -> Status.NEUTRAL
}
